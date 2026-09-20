package com.uptm.schoolfund.ui

import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.uptm.schoolfund.R
import com.uptm.schoolfund.adapter.EventCheckboxAdapter
import com.uptm.schoolfund.data.DatabaseHelper
import java.text.NumberFormat
import java.util.Locale

/**
 * Lets the teacher choose which sections to include in the next PDF report — the general
 * Class Fund section and/or individual event budgets — so old/completed events (or the
 * class fund itself) don't automatically clutter every future report.
 */
class SelectEventsForReportActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private val checkedIds = mutableSetOf<Long>()
    private lateinit var adapter: EventCheckboxAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_events)

        db = DatabaseHelper(this)
        val events = db.getAllEvents()
        // Default: everything selected, matching the previous "include everything" behaviour.
        checkedIds.addAll(events.map { it.id })

        val classFundCheckbox = findViewById<CheckBox>(R.id.checkboxClassFund)
        val formatter = NumberFormat.getCurrencyInstance(Locale("ms", "MY"))
        findViewById<TextView>(R.id.textClassFundBalance).text =
            "Baki semasa: ${formatter.format(db.getClassFundBalance())}"

        val recycler = findViewById<RecyclerView>(R.id.recyclerEventCheckboxes)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = EventCheckboxAdapter(events, checkedIds, db)
        recycler.adapter = adapter

        findViewById<TextView>(R.id.buttonSelectAll).setOnClickListener {
            classFundCheckbox.isChecked = true
            checkedIds.clear()
            checkedIds.addAll(events.map { it.id })
            adapter.notifyDataSetChanged()
        }
        findViewById<TextView>(R.id.buttonSelectNone).setOnClickListener {
            classFundCheckbox.isChecked = false
            checkedIds.clear()
            adapter.notifyDataSetChanged()
        }

        findViewById<LinearLayout>(R.id.buttonGenerateSelected).setOnClickListener {
            proceedToPreview(classFundCheckbox.isChecked)
        }
    }

    private fun proceedToPreview(includeClassFund: Boolean) {
        val intent = Intent(this, PdfPreviewActivity::class.java).apply {
            putExtra(PdfPreviewActivity.EXTRA_SELECTED_EVENT_IDS, checkedIds.toLongArray())
            putExtra(PdfPreviewActivity.EXTRA_INCLUDE_CLASS_FUND, includeClassFund)
        }
        startActivity(intent)
    }
}
