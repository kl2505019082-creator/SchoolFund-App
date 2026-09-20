package com.uptm.schoolfund.ui

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.uptm.schoolfund.R
import com.uptm.schoolfund.data.DatabaseHelper
import java.text.NumberFormat
import java.util.Locale

/**
 * FR: Generate Basic Report — summarises class fund and event budget activity.
 * Tapping "Generate and share PDF report" first opens a screen to choose which
 * event budgets to include, then a preview of the PDF before actually sharing it.
 */
class ReportActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        db = DatabaseHelper(this)
        val formatter = NumberFormat.getCurrencyInstance(Locale("ms", "MY"))

        // Class fund totals only count general (non-event) transactions, matching how the
        // balance itself is calculated — event spending is tracked separately below.
        val classFundTx = db.getAllTransactions().filter { it.eventId == null }
        val totalIncome = classFundTx.filter { it.type == "income" }.sumOf { it.amount }
        val totalExpense = classFundTx.filter { it.type == "expense" }.sumOf { it.amount }
        val balance = db.getClassFundBalance()

        findViewById<TextView>(R.id.textTotalIncome).text = "Total income: ${formatter.format(totalIncome)}"
        findViewById<TextView>(R.id.textTotalExpense).text = "Total expense: ${formatter.format(totalExpense)}"
        findViewById<TextView>(R.id.textBalanceReport).text = "Class fund balance: ${formatter.format(balance)}"

        val events = db.getAllEvents()
        val sb = StringBuilder()
        events.forEach { ev ->
            val spent = db.getEventSpent(ev.id)
            sb.append("${ev.name}: budget ${formatter.format(ev.budget)}, spent ${formatter.format(spent)}, remaining ${formatter.format(ev.budget - spent)}\n\n")
        }
        findViewById<TextView>(R.id.textEventSummary).text =
            if (sb.isEmpty()) "No event budgets recorded." else sb.toString()

        findViewById<LinearLayout>(R.id.buttonGeneratePdf).setOnClickListener {
            startActivity(Intent(this, SelectEventsForReportActivity::class.java))
        }
    }
}
