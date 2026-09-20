package com.uptm.schoolfund.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.core.content.FileProvider
import com.uptm.schoolfund.data.DatabaseHelper
import com.uptm.schoolfund.model.TransactionItem
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generates a full PDF report of the class fund: school logo, a clearly separate
 * "Class Fund" section, one clearly separate section PER selected event, and a
 * signature block for the teacher and the finance department.
 *
 * Class fund transactions and event transactions are never merged into a single table —
 * each event gets its own labelled section with its own budget/spent/remaining recap,
 * so it's obvious what belongs where.
 *
 * Uses Android's built-in PdfDocument API — no external PDF library dependency needed.
 */
object PdfReportGenerator {

    private const val PAGE_WIDTH = 595  // A4 at 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val BOTTOM_LIMIT = PAGE_HEIGHT - 100f // leave room for signature block

    fun generate(
        context: Context,
        db: DatabaseHelper,
        teacherName: String,
        schoolName: String,
        selectedEventIds: Set<Long>? = null,
        includeClassFund: Boolean = true
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true }
        val subPaint = Paint().apply { color = Color.DKGRAY; textSize = 10f }
        val sectionPaint = Paint().apply { color = Color.rgb(46, 107, 79); textSize = 13f; isFakeBoldText = true }
        val headerPaint = Paint().apply { color = Color.BLACK; textSize = 10f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { color = Color.BLACK; textSize = 10f }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
        val sectionLinePaint = Paint().apply { color = Color.rgb(46, 107, 79); strokeWidth = 1.5f }

        val formatter = NumberFormat.getCurrencyInstance(Locale("ms", "MY"))
        var y = MARGIN

        fun newPageIfNeeded(spaceNeeded: Float) {
            if (y + spaceNeeded > BOTTOM_LIMIT) {
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN
            }
        }

        fun drawTransactionTable(transactions: List<TransactionItem>) {
            canvas.drawText("Date", MARGIN, y, headerPaint)
            canvas.drawText("Description", MARGIN + 70, y, headerPaint)
            canvas.drawText("Type", MARGIN + 320, y, headerPaint)
            canvas.drawText("Amount", MARGIN + 400, y, headerPaint)
            y += 6
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 14

            if (transactions.isEmpty()) {
                canvas.drawText("No transactions recorded.", MARGIN, y, subPaint)
                y += 16
                return
            }

            for (tx in transactions) {
                newPageIfNeeded(14f)
                canvas.drawText(tx.date, MARGIN, y, bodyPaint)
                canvas.drawText(tx.description.ifBlank { "-" }.take(38), MARGIN + 70, y, bodyPaint)
                canvas.drawText(tx.type, MARGIN + 320, y, bodyPaint)
                canvas.drawText(formatter.format(tx.amount), MARGIN + 400, y, bodyPaint)
                y += 14
            }
            y += 12
        }

        // ---- Logo ----
        val logoResId = context.resources.getIdentifier("ic_school_logo", "drawable", context.packageName)
        if (logoResId != 0) {
            val logo = BitmapFactory.decodeResource(context.resources, logoResId)
            canvas.drawBitmap(logo, null, RectF(MARGIN, y, MARGIN + 50, y + 50), null)
        }

        // ---- Header ----
        canvas.drawText(schoolName, MARGIN + 60, y + 18, titlePaint)
        canvas.drawText("Class Fund and Event Budget Report", MARGIN + 60, y + 34, subPaint)
        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        canvas.drawText("Generated: $dateStr", MARGIN + 60, y + 48, subPaint)
        y += 70
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 24

        // ============ SECTION 1: CLASS FUND (only when selected — general transactions only) ============
        if (includeClassFund) {
            val classFundTx = db.getAllTransactions().filter { it.eventId == null }
            val totalIncome = classFundTx.filter { it.type == "income" }.sumOf { it.amount }
            val totalExpense = classFundTx.filter { it.type == "expense" }.sumOf { it.amount }
            val balance = db.getClassFundBalance()

            canvas.drawText("CLASS FUND", MARGIN, y, sectionPaint); y += 4
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, sectionLinePaint); y += 18

            canvas.drawText("Total income: ${formatter.format(totalIncome)}", MARGIN, y, bodyPaint); y += 14
            canvas.drawText("Total expense: ${formatter.format(totalExpense)}", MARGIN, y, bodyPaint); y += 14
            canvas.drawText("Current balance: ${formatter.format(balance)}", MARGIN, y, headerPaint); y += 20

            drawTransactionTable(classFundTx.sortedByDescending { it.date })
        }

        // ============ SECTION 2+: ONE PER SELECTED EVENT ============
        val allEvents = db.getAllEvents()
        val events = if (selectedEventIds != null) allEvents.filter { it.id in selectedEventIds } else allEvents

        for (event in events) {
            newPageIfNeeded(60f)
            val eventTx = db.getTransactionsForEvent(event.id)
            val income = db.getEventIncome(event.id)
            val spent = db.getEventSpent(event.id)
            val balance = income - spent

            canvas.drawText("EVENT: ${event.name.uppercase()}", MARGIN, y, sectionPaint); y += 4
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, sectionLinePaint); y += 18

            canvas.drawText("Total income: ${formatter.format(income)}", MARGIN, y, bodyPaint); y += 14
            canvas.drawText("Total expense: ${formatter.format(spent)}", MARGIN, y, bodyPaint); y += 14
            canvas.drawText("Balance: ${formatter.format(balance)}", MARGIN, y, headerPaint); y += 20

            drawTransactionTable(eventTx.sortedByDescending { it.date })
        }

        // ---- Signature block (fixed near bottom of the LAST page) ----
        newPageIfNeeded(90f)
        val sigY = PAGE_HEIGHT - 90f
        canvas.drawLine(MARGIN, sigY, MARGIN + 180, sigY, linePaint)
        canvas.drawText("Prepared by: $teacherName", MARGIN, sigY + 14, bodyPaint)
        canvas.drawText("(Class Teacher)", MARGIN, sigY + 28, subPaint)

        canvas.drawLine(PAGE_WIDTH - MARGIN - 180, sigY, PAGE_WIDTH - MARGIN, sigY, linePaint)
        canvas.drawText("Approved by: ______________", PAGE_WIDTH - MARGIN - 180, sigY + 14, bodyPaint)
        canvas.drawText("(School Finance Department)", PAGE_WIDTH - MARGIN - 180, sigY + 28, subPaint)

        document.finishPage(page)

        // ---- Save to app-specific external storage ----
        val outputDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "reports")
        if (!outputDir.exists()) outputDir.mkdirs()
        val fileName = "class_fund_report_${System.currentTimeMillis()}.pdf"
        val file = File(outputDir, fileName)
        document.writeTo(FileOutputStream(file))
        document.close()

        return file
    }

    /** Returns a shareable content:// URI for the generated PDF, via FileProvider. */
    fun getShareableUri(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
