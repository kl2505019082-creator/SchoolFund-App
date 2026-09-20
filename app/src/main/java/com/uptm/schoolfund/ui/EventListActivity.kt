package com.uptm.schoolfund.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.uptm.schoolfund.R
import com.uptm.schoolfund.adapter.EventAdapter
import com.uptm.schoolfund.data.DatabaseHelper

/** FR: Create and Track Event Budget — lists all event budgets with spent/remaining progress. */
class EventListActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_list)

        db = DatabaseHelper(this)
        recyclerView = findViewById(R.id.recyclerEvents)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val addButton = findViewById<Button>(R.id.buttonAddEvent)
        addButton.backgroundTintList = null
        addButton.setOnClickListener {
            startActivity(Intent(this, AddEventActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val events = db.getAllEvents()
        recyclerView.adapter = EventAdapter(events, db) { event ->
            val intent = Intent(this, EventDetailActivity::class.java)
            intent.putExtra("event_id", event.id)
            startActivity(intent)
        }
    }
}
