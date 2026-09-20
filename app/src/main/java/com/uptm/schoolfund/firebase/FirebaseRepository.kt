package com.uptm.schoolfund.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.uptm.schoolfund.model.FundRequest

/**
 * Wraps Firebase Auth + Firestore access for the multi-user extension of the app.
 *
 * Collections used:
 *  - "users/{uid}"        -> { name, role: "teacher" | "pengerusi" | "bendahari" }
 *  - "fund_requests/{id}" -> FundRequest documents, visible to relevant roles in real time
 *
 * Roles:
 *  - teacher   — submits fund requests. Anyone can self-register as a teacher.
 *  - pengerusi — Pengerusi PIBG. Approves/rejects requests (after discussing with the
 *                Pengetua outside the app). Accounts are created by the school admin
 *                directly in the Firebase Console — self-registration as this role is
 *                blocked by firestore.rules.
 *  - bendahari — Bendahari PIBG. Marks an approved request as "funds disbursed" once the
 *                money has actually been paid out. Also admin-created only.
 *
 * Note: This is the ONLINE, shared-data extension of the project. The original
 * offline SQLite flow (DatabaseHelper) still handles day-to-day class fund
 * transactions and event budgets on-device; only fund requests that need
 * PIBG approval are synced online.
 */
object FirebaseRepository {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private const val COLLECTION_USERS = "users"
    private const val COLLECTION_REQUESTS = "fund_requests"

    val currentUid: String?
        get() = auth.currentUser?.uid

    // ---------- Auth ----------

    /**
     * Signs in to an EXISTING account only — never creates a new one. Use [createAccount]
     * explicitly for new sign-ups. Keeping these separate (instead of one "register-or-login"
     * action) avoids silently creating a blank duplicate account when someone mistypes their
     * email on the login screen.
     */
    fun login(
        email: String,
        password: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { onResult(true, "Logged in") }
            .addOnFailureListener { e ->
                onResult(false, e.message ?: "Emel atau password salah, atau akaun belum wujud.")
            }
    }

    /**
     * Creates a brand new account. [role] is only actually honoured by the server
     * (firestore.rules) for "teacher" — self-registering as "pengerusi"/"bendahari" is
     * blocked, since the school admin creates those two accounts directly in the Firebase
     * Console.
     */
    fun createAccount(
        email: String,
        password: String,
        name: String,
        role: String, // "teacher", "pengerusi" or "bendahari"
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                val userDoc = mapOf("name" to name, "role" to role, "email" to email)
                db.collection(COLLECTION_USERS).document(uid).set(userDoc)
                    .addOnSuccessListener { onResult(true, "Account created") }
                    .addOnFailureListener { e ->
                        // Roll back the orphaned auth account so the user can retry cleanly.
                        result.user?.delete()
                        auth.signOut()
                        val msg = if (e is FirebaseFirestoreException &&
                            e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                        ) {
                            "Akaun ini belum didaftarkan oleh admin sekolah. Sila hubungi admin."
                        } else {
                            e.message ?: "Failed to save profile"
                        }
                        onResult(false, msg)
                    }
            }
            .addOnFailureListener { e ->
                val msg = if (e.message?.contains("already in use", ignoreCase = true) == true) {
                    "Emel ini sudah didaftarkan. Sila guna 'Log Masuk'."
                } else {
                    e.message ?: "Pendaftaran gagal"
                }
                onResult(false, msg)
            }
    }

    /** Sends a Firebase password-reset email. Only works if [email] is a real inbox the
     *  user can actually open — made-up/fake emails used for quick testing won't receive it. */
    fun sendPasswordReset(email: String, onResult: (Boolean, String) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onResult(true, "Emel reset password dihantar ke $email") }
            .addOnFailureListener { e -> onResult(false, e.message ?: "Gagal menghantar emel reset") }
    }

    fun getUserRole(
        uid: String,
        onResult: (role: String?, name: String?, mustChangePassword: Boolean) -> Unit
    ) {
        db.collection(COLLECTION_USERS).document(uid).get()
            .addOnSuccessListener { doc ->
                onResult(doc.getString("role"), doc.getString("name"), doc.getBoolean("mustChangePassword") ?: false)
            }
            .addOnFailureListener { onResult(null, null, false) }
    }

    /** Called once the user has changed their initial admin-set password, so the
     *  "please change your password" reminder stops showing on future logins. */
    fun clearMustChangePassword(uid: String) {
        db.collection(COLLECTION_USERS).document(uid).update("mustChangePassword", false)
    }

    fun logout() = auth.signOut()

    // ---------- Fund requests ----------

    fun submitFundRequest(request: FundRequest, onResult: (Boolean) -> Unit) {
        db.collection(COLLECTION_REQUESTS).add(request)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    /** Live updates for a teacher's own requests. */
    fun listenToOwnRequests(teacherUid: String, onUpdate: (List<FundRequest>) -> Unit) {
        db.collection(COLLECTION_REQUESTS)
            .whereEqualTo("teacherUid", teacherUid)
            .orderBy("requestedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Most likely cause: this compound where()+orderBy() query needs a
                    // Firestore composite index that hasn't been created yet — Firestore then
                    // fails the query instead of returning results, so log it loudly instead
                    // of silently showing an empty list.
                    Log.e("FirebaseRepository", "listenToOwnRequests failed", error)
                }
                val list = snapshot?.documents?.map { doc ->
                    doc.toObject(FundRequest::class.java)?.apply { id = doc.id } ?: FundRequest()
                } ?: emptyList()
                onUpdate(list)
            }
    }

    /** Live updates for the finance dashboard — all requests, newest first. */
    fun listenToAllRequests(onUpdate: (List<FundRequest>) -> Unit) {
        db.collection(COLLECTION_REQUESTS)
            .orderBy("requestedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseRepository", "listenToAllRequests failed", error)
                }
                val list = snapshot?.documents?.map { doc ->
                    doc.toObject(FundRequest::class.java)?.apply { id = doc.id } ?: FundRequest()
                } ?: emptyList()
                onUpdate(list)
            }
    }

    fun decideRequest(
        requestId: String,
        approve: Boolean,
        remarks: String,
        decidedByName: String,
        onResult: (Boolean) -> Unit
    ) {
        val updates = mapOf(
            "status" to if (approve) "approved" else "rejected",
            "financeRemarks" to remarks,
            "decidedAt" to System.currentTimeMillis(),
            "decidedBy" to decidedByName
        )
        db.collection(COLLECTION_REQUESTS).document(requestId).update(updates)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    /** Bendahari PIBG marks an already-approved request as paid out. */
    fun markDisbursed(
        requestId: String,
        disbursedByName: String,
        onResult: (Boolean) -> Unit
    ) {
        val updates = mapOf(
            "status" to "disbursed",
            "disbursedAt" to System.currentTimeMillis(),
            "disbursedBy" to disbursedByName
        )
        db.collection(COLLECTION_REQUESTS).document(requestId).update(updates)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
}
