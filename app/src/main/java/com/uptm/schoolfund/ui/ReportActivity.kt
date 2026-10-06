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

        // One summary card per event, styled like the class fund card above.
        val events = db.getAllEvents()
        val cards = findViewById<LinearLayout>(R.id.layoutEventCards)
        if (events.isEmpty()) {
            val empty = findViewById<TextView>(R.id.textEventSummary)
            empty.text = "No event budgets recorded."
            empty.visibility = android.view.View.VISIBLE
        }
        events.forEach { ev ->
            val card = layoutInflater.inflate(R.layout.item_report_event, cards, false)
            card.findViewById<TextView>(R.id.textReportEventName).text = ev.name
            card.findViewById<TextView>(R.id.textReportEventIncome).text =
                "Total income: ${formatter.format(db.getEventIncome(ev.id))}"
            card.findViewById<TextView>(R.id.textReportEventExpense).text =
                "Total expense: ${formatter.format(db.getEventSpent(ev.id))}"
            card.findViewById<TextView>(R.id.textReportEventBalance).text =
                "Balance: ${formatter.format(db.getEventBalance(ev.id))}"
            cards.addView(card)
        }

        findViewById<LinearLayout>(R.id.buttonGeneratePdf).setOnClickListener {
            startActivity(Intent(this, SelectEventsForReportActivity::class.java))
        }
    }
}
