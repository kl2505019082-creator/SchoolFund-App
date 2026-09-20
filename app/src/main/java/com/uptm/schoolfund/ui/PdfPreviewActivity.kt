package com.uptm.schoolfund.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.uptm.schoolfund.R
import com.uptm.schoolfund.data.DatabaseHelper
import com.uptm.schoolfund.pdf.PdfReportGenerator
import java.io.File

/**
 * Renders every page of the generated PDF report as images inside the app so the teacher
 * can review the whole thing (class fund + each event, possibly across multiple pages)
 * before sending it anywhere. Sharing only happens when "Share" (top-right) is tapped.
 */
class PdfPreviewActivity : BaseActivity() {

    private var pdfFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_preview)

        val selectedIds = intent.getLongArrayExtra(EXTRA_SELECTED_EVENT_IDS)?.toSet()
        val includeClassFund = intent.getBooleanExtra(EXTRA_INCLUDE_CLASS_FUND, true)

        val prefs = getSharedPreferences("school_fund_prefs", MODE_PRIVATE)
        val teacherName = prefs.getString("current_name", prefs.getString("teacher_name", "Teacher")) ?: "Teacher"

        val db = DatabaseHelper(this)

        try {
            val file = PdfReportGenerator.generate(this, db, teacherName, "SMK Perlis", selectedIds, includeClassFund)
            pdfFile = file
            renderAllPages(file)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to generate PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }

        findViewById<ImageView>(R.id.buttonBackPreview).setOnClickListener { finish() }
        findViewById<LinearLayout>(R.id.buttonSharePreview).setOnClickListener { shareReport() }
    }

    /** Uses Android's built-in PdfRenderer to draw every page into its own preview image. */
    private fun renderAllPages(file: File) {
        val container = findViewById<LinearLayout>(R.id.layoutPdfPages)
        container.removeAllViews()

        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val imageView = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { it.bottomMargin = 24 }
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageBitmap(bitmap)
                }
                container.addView(imageView)
            }

            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not preview PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareReport() {
        val file = pdfFile ?: return
        val uri = PdfReportGenerator.getShareableUri(this, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share class fund report"))
    }

    companion object {
        const val EXTRA_SELECTED_EVENT_IDS = "selected_event_ids"
        const val EXTRA_INCLUDE_CLASS_FUND = "include_class_fund"
    }
}
