package com.uptm.schoolfund.ui

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.uptm.schoolfund.R
import com.uptm.schoolfund.adapter.TransactionAdapter
import com.uptm.schoolfund.data.DatabaseHelper

/** FR: View Transaction History — full list of past income/expense entries across the app. */
class TransactionHistoryActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_history)

        db = DatabaseHelper(this)
        recyclerView = findViewById(R.id.recyclerHistory)
        recyclerView.layoutManager = LinearLayoutManager(this)
        loadData()
    }

    private fun loadData() {
        val transactions = db.getAllTransactions()
        val events = db.getAllEvents()
        val tabungs = db.getAllTabung()
        recyclerView.adapter = TransactionAdapter(transactions, events, tabungs) { tx ->
            db.deleteTransaction(tx.id)
            loadData()
        }
    }
}
