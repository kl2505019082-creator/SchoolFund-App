package com.uptm.schoolfund.util

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Stores a 4-digit app PIN as a salted SHA-256 hash (never the PIN itself) and enforces a short
 * lockout after repeated wrong attempts. This guards against casual access on an unlocked phone;
 * it is not a substitute for device encryption.
 */
object PinHelper {

    private const val PREFS = "school_fund_pin_prefs"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_FAILS = "pin_fails"
    private const val KEY_LOCK_UNTIL = "pin_lock_until"
    private const val MAX_FAILS = 5
    private const val LOCK_MS = 30_000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasPin(context: Context) = prefs(context).getString(KEY_HASH, null) != null

    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltText = Base64.encodeToString(salt, Base64.NO_WRAP)
        prefs(context).edit()
            .putString(KEY_SALT, saltText)
            .putString(KEY_HASH, hash(saltText, pin))
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCK_UNTIL, 0L)
            .apply()
    }

    fun verify(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val salt = p.getString(KEY_SALT, null) ?: return false
        val ok = hash(salt, pin) == p.getString(KEY_HASH, null)
        if (ok) {
            p.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0L).apply()
        } else {
            val fails = p.getInt(KEY_FAILS, 0) + 1
            val editor = p.edit()
            if (fails >= MAX_FAILS) {
                editor.putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + LOCK_MS)
            } else {
                editor.putInt(KEY_FAILS, fails)
            }
            editor.apply()
        }
        return ok
    }

    /** Milliseconds left on the lockout, or 0 if not locked. */
    fun lockRemainingMs(context: Context): Long =
        (prefs(context).getLong(KEY_LOCK_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun clear(context: Context) = prefs(context).edit().clear().apply()

    private fun hash(salt: String, pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
