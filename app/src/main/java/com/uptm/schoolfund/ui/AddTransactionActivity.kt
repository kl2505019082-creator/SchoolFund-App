package com.uptm.schoolfund.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.uptm.schoolfund.R
import com.uptm.schoolfund.data.DatabaseHelper
import com.uptm.schoolfund.model.TransactionItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen for recording a new income or expense transaction.
 * Implements FR: Record Income and Expense, and FR: Attach Receipt Photo.
 * Offers both "take photo" (camera) and "choose from gallery" since many emulators
 * and some devices don't have a working camera app.
 */
class AddTransactionActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private var photoUri: Uri? = null
    private var photoPath: String? = null
    private var eventIds: List<Long?> = listOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        db = DatabaseHelper(this)

        val typeGroup = findViewById<RadioGroup>(R.id.radioGroupType)
        val radioIncome = findViewById<RadioButton>(R.id.radioIncome)
        val radioExpense = findViewById<RadioButton>(R.id.radioExpense)
        val amountInput = findViewById<EditText>(R.id.editAmount)
        val descInput = findViewById<EditText>(R.id.editDescription)
        val eventSpinner = findViewById<Spinner>(R.id.spinnerEvent)
        val attachButton = findViewById<LinearLayout>(R.id.buttonAttachReceipt)
        val saveButton = findViewById<Button>(R.id.buttonSave)
        saveButton.backgroundTintList = null
        val receiptPreview = findViewById<ImageView>(R.id.imageReceiptPreview)

        fun updateTypeToggle(incomeSelected: Boolean) {
            if (incomeSelected) {
                radioIncome.setBackgroundResource(R.drawable.card_white)
                radioIncome.setTextColor(resources.getColor(R.color.primary_green, theme))
                radioExpense.background = null
                radioExpense.setTextColor(resources.getColor(R.color.text_secondary, theme))
            } else {
                radioExpense.setBackgroundResource(R.drawable.card_white)
                radioExpense.setTextColor(resources.getColor(R.color.danger_red, theme))
                radioIncome.background = null
                radioIncome.setTextColor(resources.getColor(R.color.text_secondary, theme))
            }
        }
        typeGroup.setOnCheckedChangeListener { _, checkedId ->
            updateTypeToggle(checkedId == R.id.radioIncome)
        }

        // Populate the destination dropdown: Class Fund (general), then every Event —
        // exactly one of them ends up selected (eventId set, or null for Class Fund).
        val events = db.getAllEvents()
        val destNames = mutableListOf("Class Fund (general)")
        eventIds = mutableListOf<Long?>(null)
        events.forEach {
            destNames.add(it.name)
            (eventIds as MutableList<Long?>).add(it.id)
        }
        eventSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, destNames)

        // If opened from an Event Detail (or a scoped Event on the Dashboard), pre-select
        // that destination so expenses can't accidentally end up in the wrong place.
        val preselectEventId = intent.getLongExtra(EXTRA_PRESELECT_EVENT_ID, -1L)
        if (preselectEventId != -1L) {
            val position = eventIds.indexOf(preselectEventId)
            if (position >= 0) eventSpinner.setSelection(position)
        }

        val recordingUnderLabel = findViewById<TextView>(R.id.textRecordingUnder)
        fun updateRecordingUnderLabel() {
            recordingUnderLabel.text = "Recording under: ${destNames.getOrElse(eventSpinner.selectedItemPosition) { "Class Fund" }}"
        }
        updateRecordingUnderLabel()
        eventSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updateRecordingUnderLabel()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        attachButton.setOnClickListener { showReceiptSourceDialog() }

        saveButton.setOnClickListener {
            val amountStr = amountInput.text.toString()
            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val type = if (typeGroup.checkedRadioButtonId == R.id.radioIncome) "income" else "expense"
            val description = descInput.text.toString().trim()
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val selectedEventId = eventIds.getOrNull(eventSpinner.selectedItemPosition)

            val tx = TransactionItem(
                type = type,
                amount = amount,
                description = description,
                date = date,
                receiptPath = photoPath,
                eventId = selectedEventId
            )

            if (type == "expense") {
                checkAvailableBalanceThenSave(tx)
            } else {
                saveTransaction(tx)
            }
        }
    }

    /**
     * Blocks an expense that would exceed the available balance for its destination —
     * either the general class fund balance, or the remaining budget of the selected
     * event. The balance is never allowed to go negative.
     */
    private fun checkAvailableBalanceThenSave(tx: TransactionItem) {
        val formatter = java.text.NumberFormat.getCurrencyInstance(Locale("ms", "MY"))
        val available: Double
        val scopeLabel: String

        when {
            tx.eventId != null -> {
                val event = db.getAllEvents().find { it.id == tx.eventId }
                available = db.getEventBalance(tx.eventId)
                scopeLabel = "acara \"${event?.name ?: ""}\""
            }
            else -> {
                available = db.getClassFundBalance()
                scopeLabel = "tabung kelas"
            }
        }

        if (tx.amount > available) {
            AlertDialog.Builder(this)
                .setTitle("Duit tidak cukup")
                .setMessage(
                    "Perbelanjaan ini (${formatter.format(tx.amount)}) melebihi baki $scopeLabel " +
                        "yang ada (${formatter.format(available)}). Sila kurangkan jumlah atau " +
                        "rekod pendapatan dahulu."
                )
                .setPositiveButton("OK", null)
                .show()
        } else {
            saveTransaction(tx)
        }
    }

    private fun saveTransaction(tx: TransactionItem) {
        db.insertTransaction(tx)
        Toast.makeText(this, "Transaction saved", Toast.LENGTH_SHORT).show()
        setResult(Activity.RESULT_OK)
        finish()
    }

    /** Lets the teacher pick between taking a new photo or choosing an existing one from gallery. */
    private fun showReceiptSourceDialog() {
        val options = arrayOf("Take photo", "Choose from gallery")
        AlertDialog.Builder(this)
            .setTitle("Attach receipt")
            .setItems(options) { _, which ->
                if (which == 0) checkCameraPermissionAndOpen() else openGallery()
            }
            .show()
    }

    // ---------- Camera flow ----------

    private fun checkCameraPermissionAndOpen() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            openCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera permission is needed to take a receipt photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun receiptsDir(): File {
        val dir = File(getExternalFilesDir(null), "receipts")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun openCamera() {
        try {
            val photoFile = File(receiptsDir(), "receipt_${System.currentTimeMillis()}.jpg")
            photoPath = photoFile.absolutePath
            photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            if (intent.resolveActivity(packageManager) != null) {
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
            } else {
                Toast.makeText(this, "No camera app found — try 'Choose from gallery' instead", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Gallery flow ----------

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        try {
            startActivityForResult(Intent.createChooser(intent, "Select receipt photo"), REQUEST_GALLERY_PICK)
        } catch (e: Exception) {
            Toast.makeText(this, "No gallery app available", Toast.LENGTH_SHORT).show()
        }
    }

    /** Copies the picked gallery image into app storage so it persists reliably. */
    private fun copyGalleryImageToReceipts(sourceUri: Uri) {
        try {
            val destFile = File(receiptsDir(), "receipt_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            photoPath = destFile.absolutePath
            onReceiptAttached()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not import photo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onReceiptAttached() {
        findViewById<TextView>(R.id.textAttachReceiptLabel).text = "Change receipt photo"
        val preview = findViewById<ImageView>(R.id.imageReceiptPreview)
        preview.visibility = android.view.View.VISIBLE
        photoPath?.let { path ->
            android.graphics.BitmapFactory.decodeFile(path)?.let { preview.setImageBitmap(it) }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) {
            return
        }
        when (requestCode) {
            REQUEST_IMAGE_CAPTURE -> onReceiptAttached()
            REQUEST_GALLERY_PICK -> {
                data?.data?.let { copyGalleryImageToReceipts(it) }
            }
        }
    }

    companion object {
        const val EXTRA_PRESELECT_EVENT_ID = "preselect_event_id"
        private const val REQUEST_IMAGE_CAPTURE = 101
        private const val REQUEST_GALLERY_PICK = 102
        private const val REQUEST_CAMERA_PERMISSION = 201
    }
}
