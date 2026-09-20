package com.uptm.schoolfund.ui

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.uptm.schoolfund.R
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a "kertas kerja" (work paper) PDF attached to a fund request directly inside the
 * app — using Android's built-in PdfRenderer — so it always works even on a device/emulator
 * with no dedicated PDF viewer app installed. Also offers Share (send to any app) and
 * Download (save a copy to the device's public Downloads folder).
 */
class WorkPaperViewerActivity : BaseActivity() {

    private var sourceFile: File? = null
    private var displayName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_work_paper_viewer)

        displayName = getString(R.string.default_work_paper_name)
        val path = intent.getStringExtra(EXTRA_FILE_PATH)
        displayName = intent.getStringExtra(EXTRA_FILE_NAME) ?: displayName
        findViewById<TextView>(R.id.textWorkPaperTitle).text = displayName

        if (path == null) {
            Toast.makeText(this, getString(R.string.msg_file_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val file = File(path)
        sourceFile = file
        renderAllPages(file)

        findViewById<ImageView>(R.id.buttonBackWorkPaper).setOnClickListener { finish() }
        findViewById<LinearLayout>(R.id.buttonShareWorkPaper).setOnClickListener { shareWorkPaper() }
        findViewById<LinearLayout>(R.id.buttonDownloadWorkPaper).setOnClickListener { downloadWorkPaper() }
    }

    private fun renderAllPages(file: File) {
        val container = findViewById<LinearLayout>(R.id.layoutWorkPaperPages)
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
            Toast.makeText(this, getString(R.string.msg_cannot_display_pdf, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareWorkPaper() {
        val file = sourceFile ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
    }

    /** Saves a copy into the device's public Downloads folder, visible in any Files app. */
    private fun downloadWorkPaper() {
        val file = sourceFile ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    Toast.makeText(this, getString(R.string.msg_saved_downloads), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.msg_save_failed), Toast.LENGTH_SHORT).show()
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destFile = File(downloadsDir, displayName)
                FileOutputStream(destFile).use { out -> file.inputStream().use { it.copyTo(out) } }
                Toast.makeText(this, getString(R.string.msg_saved_to_path, destFile.path), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.msg_download_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_FILE_PATH = "work_paper_file_path"
        const val EXTRA_FILE_NAME = "work_paper_file_name"
    }
}
