package com.uptm.schoolfund.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.uptm.schoolfund.R
import com.uptm.schoolfund.adapter.TransactionAdapter
import com.uptm.schoolfund.data.DatabaseHelper
import com.uptm.schoolfund.util.DeleteHelper

/**
 * FR: View Transaction History — full list of past income/expense entries across the app,
 * with a text search (description, event/tabung name, date, amount) and an All/Income/Expense filter.
 */
class TransactionHistoryActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchInput: EditText
    private lateinit var filterGroup: RadioGroup
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_history)

        db = DatabaseHelper(this)
        recyclerView = findViewById(R.id.recyclerHistory)
        recyclerView.layoutManager = LinearLayoutManager(this)
        searchInput = findViewById(R.id.editSearchHistory)
        filterGroup = findViewById(R.id.radioGroupFilter)
        emptyText = findViewById(R.id.textHistoryEmpty)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = loadData()
        })
        filterGroup.setOnCheckedChangeListener { _, _ ->
            styleFilterTabs()
            loadData()
        }
        styleFilterTabs()
        loadData()
    }

    private fun styleFilterTabs() {
        listOf(R.id.radioFilterAll, R.id.radioFilterIncome, R.id.radioFilterExpense).forEach { id ->
            val tab = findViewById<RadioButton>(id)
            if (tab.id == filterGroup.checkedRadioButtonId) {
                tab.setBackgroundResource(R.drawable.card_white)
                tab.setTextColor(resources.getColor(R.color.accent_blue, theme))
            } else {
                tab.background = null
                tab.setTextColor(resources.getColor(R.color.text_secondary, theme))
            }
        }
    }

    private fun loadData() {
        val events = db.getAllEvents()
        val tabungs = db.getAllTabung()
        val query = searchInput.text.toString().trim().lowercase()

        val typeFilter = when (filterGroup.checkedRadioButtonId) {
            R.id.radioFilterIncome -> "income"
            R.id.radioFilterExpense -> "expense"
            else -> null
        }

        val filtered = db.getAllTransactions().filter { tx ->
            if (typeFilter != null && tx.type != typeFilter) return@filter false
            if (query.isEmpty()) return@filter true
            val scopeName = tx.eventId?.let { id -> events.find { it.id == id }?.name }
                ?: tx.tabungId?.let { id -> tabungs.find { it.id == id }?.name }
                ?: ""
            tx.description.lowercase().contains(query) ||
                scopeName.lowercase().contains(query) ||
                tx.date.contains(query) ||
                String.format("%.2f", tx.amount).contains(query)
        }

        emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.adapter = TransactionAdapter(filtered, events, tabungs) { tx ->
            DeleteHelper.confirmDeleteTransaction(this, db, tx) { loadData() }
        }
    }
}
