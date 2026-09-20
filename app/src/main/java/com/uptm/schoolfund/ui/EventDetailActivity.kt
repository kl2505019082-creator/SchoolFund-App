package com.uptm.schoolfund.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.uptm.schoolfund.R
import com.uptm.schoolfund.adapter.TransactionAdapter
import com.uptm.schoolfund.data.DatabaseHelper
import java.text.NumberFormat
import java.util.Locale

class EventDetailActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private var eventId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        db = DatabaseHelper(this)
        eventId = intent.getLongExtra("event_id", -1)

        findViewById<LinearLayout>(R.id.buttonAddEventTransaction).setOnClickListener {
            // Pre-selects this event in the transaction form, so expenses are always
            // recorded against the right event instead of silently defaulting to the
            // general class fund.
            val intent = Intent(this, AddTransactionActivity::class.java).apply {
                putExtra(AddTransactionActivity.EXTRA_PRESELECT_EVENT_ID, eventId)
            }
            startActivity(intent)
        }

        findViewById<ImageView>(R.id.buttonDeleteEvent).setOnClickListener {
            val event = db.getAllEvents().find { it.id == eventId } ?: return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("Padam ${event.name}?")
                .setMessage("Acara ini dan semua transaksi di bawahnya akan dipadam. Tindakan ini tidak boleh dibatalkan.")
                .setPositiveButton("Padam") { _, _ ->
                    db.deleteEvent(eventId)
                    finish()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val event = db.getAllEvents().find { it.id == eventId } ?: run { finish(); return }
        val income = db.getEventIncome(eventId)
        val spent = db.getEventSpent(eventId)
        val balance = income - spent
        val formatter = NumberFormat.getCurrencyInstance(Locale("ms", "MY"))

        findViewById<TextView>(R.id.textEventName).text = event.name
        findViewById<TextView>(R.id.textEventBudget).text = "Total income: ${formatter.format(income)}"
        findViewById<TextView>(R.id.textEventSpent).text = "Total expense: ${formatter.format(spent)}"
        findViewById<TextView>(R.id.textEventRemaining).text = "Balance: ${formatter.format(balance)}"

        val recycler = findViewById<RecyclerView>(R.id.recyclerEventTransactions)
        recycler.layoutManager = LinearLayoutManager(this)
        val txList = db.getTransactionsForEvent(eventId)
        recycler.adapter = TransactionAdapter(txList, listOf(event)) { tx ->
            db.deleteTransaction(tx.id)
            refresh()
        }
    }
}
