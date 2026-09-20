package com.uptm.schoolfund.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.uptm.schoolfund.R
import com.uptm.schoolfund.data.DatabaseHelper
import com.uptm.schoolfund.model.EventBudget
import java.text.NumberFormat
import java.util.Locale

class EventAdapter(
    private val items: List<EventBudget>,
    private val db: DatabaseHelper,
    private val onClick: (EventBudget) -> Unit
) : RecyclerView.Adapter<EventAdapter.ViewHolder>() {

    private val formatter = NumberFormat.getCurrencyInstance(Locale("ms", "MY"))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.textEventItemName)
        val amounts: TextView = view.findViewById(R.id.textEventItemAmounts)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = items[position]
        val balance = db.getEventBalance(event.id)

        holder.name.text = event.name
        holder.amounts.text = "Baki: ${formatter.format(balance)}"
        holder.amounts.setTextColor(
            if (balance < 0) 0xFFC0392B.toInt() else 0xFF2E6B4F.toInt()
        )
        holder.itemView.setOnClickListener { onClick(event) }
    }

    override fun getItemCount() = items.size
}
