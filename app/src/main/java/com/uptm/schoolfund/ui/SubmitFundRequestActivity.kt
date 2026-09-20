package com.uptm.schoolfund.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import com.uptm.schoolfund.R
import com.uptm.schoolfund.data.DatabaseHelper
import com.uptm.schoolfund.firebase.FirebaseRepository
import com.uptm.schoolfund.model.FundRequest
import android.util.Base64

/**
 * Lets a teacher request additional funding / approval from the school finance department.
 * A "kertas kerja" (work paper) PDF — with a title, objectives, and planned spending, per the
 * school's requirement — is MANDATORY. Since this project has no cloud file storage set up
 * (Firebase Storage needs a paid plan), the PDF is embedded directly in the Firestore document
 * as base64, so it must stay small (500KB raw file size limit enforced below).
 */
class SubmitFundRequestActivity : BaseActivity() {

    private var workPaperUri: Uri? = null
    private var workPaperFileName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_submit_fund_request)

        val db = DatabaseHelper(this)
        val prefs = getSharedPreferences("school_fund_prefs", Context.MODE_PRIVATE)
        val teacherName = prefs.getString("current_name", "Teacher") ?: "Teacher"
        val teacherUid = FirebaseRepository.currentUid ?: ""

        val amountInput = findViewById<EditText>(R.id.editRequestAmount)
        val reasonInput = findViewById<EditText>(R.id.editRequestReason)
        val eventSpinner = findViewById<Spinner>(R.id.spinnerRequestEvent)
        val attachButton = findViewById<LinearLayout>(R.id.buttonAttachWorkPaper)
        val submitButton = findViewById<Button>(R.id.buttonSubmitRequest)
        submitButton.backgroundTintList = null

        val events = db.getAllEvents()
        val eventNames = mutableListOf("Not linked to a specific event")
        val eventIds = mutableListOf<Long?>(null)
        events.forEach { eventNames.add(it.name); eventIds.add(it.id) }
        eventSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, eventNames)

        attachButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/pdf" }
            try {
                startActivityForResult(Intent.createChooser(intent, "Pilih kertas kerja (PDF)"), REQUEST_PICK_PDF)
            } catch (e: Exception) {
                Toast.makeText(this, "Tiada aplikasi fail tersedia", Toast.LENGTH_SHORT).show()
            }
        }

        submitButton.setOnClickListener {
            val amount = amountInput.text.toString().toDoubleOrNull()
            val reason = reasonInput.text.toString().trim()

            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (reason.isEmpty()) {
                Toast.makeText(this, "Explain what the funds are for", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val pdfUri = workPaperUri
            if (pdfUri == null) {
                Toast.makeText(this, "Sila lampirkan kertas kerja (PDF) — wajib", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val pdfBase64 = try {
                encodePdfToBase64(pdfUri)
            } catch (e: FileTooLargeException) {
                Toast.makeText(this, "PDF terlalu besar (maksimum 500KB). Sila kecilkan fail.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal membaca PDF: ${e.message}", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val selectedPos = eventSpinner.selectedItemPosition
            val request = FundRequest(
                teacherName = teacherName,
                teacherUid = teacherUid,
                eventId = eventIds.getOrNull(selectedPos),
                eventName = if (selectedPos > 0) eventNames[selectedPos] else "",
                amount = amount,
                reason = reason,
                workPaperBase64 = pdfBase64,
                workPaperFileName = workPaperFileName
            )

            submitButton.isEnabled = false
            FirebaseRepository.submitFundRequest(request) { success ->
                submitButton.isEnabled = true
                if (success) {
                    Toast.makeText(this, "Request sent to finance department", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Failed to send request. Check internet connection.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private class FileTooLargeException : Exception()

    private fun encodePdfToBase64(uri: Uri): String {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw Exception("Tidak dapat baca fail")
        if (bytes.size > MAX_PDF_BYTES) throw FileTooLargeException()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_PDF && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            workPaperUri = uri
            workPaperFileName = queryFileName(uri) ?: "kertas_kerja.pdf"
            findViewById<TextView>(R.id.textAttachWorkPaperLabel).text = workPaperFileName
        }
    }

    /** Resolves the human-readable file name for a content:// Uri, if the provider exposes one. */
    private fun queryFileName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val REQUEST_PICK_PDF = 301
        private const val MAX_PDF_BYTES = 500 * 1024 // 500KB, keeps the Firestore document well under 1MB
    }
}
