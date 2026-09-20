package com.uptm.schoolfund.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.uptm.schoolfund.R
import java.io.File

/**
 * Displays a previously attached receipt photo full-screen, so a teacher can look back
 * at proof of a transaction (FR: Attach Receipt Photo — viewing side of the same feature).
 * Opened by tapping any transaction row that has a receipt attached.
 */
class ReceiptViewerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_viewer)

        val path = intent.getStringExtra(EXTRA_RECEIPT_PATH)
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""
        val date = intent.getStringExtra(EXTRA_DATE) ?: ""

        findViewById<TextView>(R.id.textReceiptTitle).text = description.ifBlank { "Receipt" }
        findViewById<TextView>(R.id.textReceiptDate).text = date

        val imageView = findViewById<ImageView>(R.id.imageReceiptFull)
        val file = path?.let { File(it) }

        if (file != null && file.exists()) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
            } else {
                Toast.makeText(this, "Could not load receipt image", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Receipt photo not found on this device", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageView>(R.id.buttonCloseReceipt).setOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_RECEIPT_PATH = "receipt_path"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_DATE = "date"
    }
}
