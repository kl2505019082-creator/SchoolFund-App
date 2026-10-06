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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
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
    private lateinit var scopeChips: ChipGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_history)

        db = DatabaseHelper(this)
        recyclerView = findViewById(R.id.recyclerHistory)
        recyclerView.layoutManager = LinearLayoutManager(this)
        searchInput = findViewById(R.id.editSearchHistory)
        filterGroup = findViewById(R.id.radioGroupFilter)
        emptyText = findViewById(R.id.textHistoryEmpty)
        scopeChips = findViewById(R.id.chipGroupScope)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = loadData()
        })
        filterGroup.setOnCheckedChangeListener { _, _ ->
            styleFilterTabs()
            loadData()
        }
        buildScopeChips()
        styleFilterTabs()
        loadData()
    }

    /**
     * Tick-boxes for which fund(s) to show: Class Fund and/or any event. Starts with whatever the
     * Dashboard is currently showing, so opening History from the Class Fund doesn't dump event
     * transactions into it. Ticking nothing shows everything.
     */
    private fun buildScopeChips() {
        val prefs = getSharedPreferences("school_fund_prefs", MODE_PRIVATE)
        val scopeType = prefs.getString("dashboard_scope_type", "classfund")
        val scopeId = prefs.getLong("dashboard_scope_id", -1L)

        fun addChip(label: String, key: Long, checked: Boolean) {
            val chip = layoutInflater.inflate(R.layout.item_filter_chip, scopeChips, false) as Chip
            chip.text = label
            chip.tag = key
            chip.isChecked = checked
            chip.setOnCheckedChangeListener { _, _ -> loadData() }
            scopeChips.addView(chip)
        }

        val events = db.getAllEvents()
        val eventScoped = scopeType == "event" && events.any { it.id == scopeId }
        addChip(getString(R.string.filter_class_fund), CLASS_FUND_KEY, !eventScoped)
        events.forEach { addChip(it.name, it.id, eventScoped && it.id == scopeId) }
    }

    private fun selectedScopeKeys(): Set<Long> =
        (0 until scopeChips.childCount)
            .map { scopeChips.getChildAt(it) as Chip }
            .filter { it.isChecked }
            .map { it.tag as Long }
            .toSet()

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

        val scopeKeys = selectedScopeKeys()

        val filtered = db.getAllTransactions().filter { tx ->
            if (typeFilter != null && tx.type != typeFilter) return@filter false
            if (scopeKeys.isNotEmpty() && (tx.eventId ?: CLASS_FUND_KEY) !in scopeKeys) return@filter false
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

    companion object {
        private const val CLASS_FUND_KEY = -1L
    }
}
