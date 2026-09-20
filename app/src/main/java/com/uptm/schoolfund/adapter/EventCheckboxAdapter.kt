package com.uptm.schoolfund.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.uptm.schoolfund.R
import com.uptm.schoolfund.data.DatabaseHelper
import com.uptm.schoolfund.model.EventBudget
import java.text.NumberFormat
import java.util.Locale

/**
 * Checkbox list used on the "select events to include" screen before generating a PDF report,
 * so old/unused events don't automatically clutter every report (FYP2 enhancement).
 */
class EventCheckboxAdapter(
    private val events: List<EventBudget>,
    private val checkedIds: MutableSet<Long>,
    private val db: DatabaseHelper
) : RecyclerView.Adapter<EventCheckboxAdapter.ViewHolder>() {

    private val formatter = NumberFormat.getCurrencyInstance(Locale("ms", "MY"))

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.checkboxEvent)
        val subtitle: TextView = view.findViewById(R.id.textEventCheckboxBudget)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event_checkbox, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        holder.checkbox.text = event.name
        holder.checkbox.isChecked = checkedIds.contains(event.id)
        holder.subtitle.text = "Baki: ${formatter.format(db.getEventBalance(event.id))} · ${event.date}"

        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checkedIds.add(event.id) else checkedIds.remove(event.id)
        }
    }

    override fun getItemCount() = events.size
}
