package com.uptm.schoolfund.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.uptm.schoolfund.R
import java.text.NumberFormat
import java.util.Locale

/** One row in the "Switch Fund" sheet — Class Fund, a Tabung, or an Event. */
data class FundSwitchItem(
    val key: String, // "classfund", "tabung:<id>" or "event:<id>"
    val title: String,
    val subtitle: String,
    val amount: Double,
    val deletable: Boolean
)

class FundSwitchAdapter(
    private val items: List<FundSwitchItem>,
    private val selectedKey: String,
    private val onSelect: (FundSwitchItem) -> Unit,
    private val onLongPressDelete: (FundSwitchItem) -> Unit
) : RecyclerView.Adapter<FundSwitchAdapter.ViewHolder>() {

    private val formatter = NumberFormat.getCurrencyInstance(Locale("ms", "MY"))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.textFundName)
        val subtitle: TextView = view.findViewById(R.id.textFundSubtitle)
        val amount: TextView = view.findViewById(R.id.textFundAmount)
        val check: TextView = view.findViewById(R.id.textFundCheck)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_fund_switch, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.title
        holder.subtitle.text = item.subtitle
        holder.subtitle.visibility = if (item.subtitle.isBlank()) View.GONE else View.VISIBLE
        holder.amount.text = formatter.format(item.amount)
        holder.check.visibility = if (item.key == selectedKey) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onSelect(item) }
        holder.itemView.setOnLongClickListener {
            if (item.deletable) {
                onLongPressDelete(item)
                true
            } else {
                false
            }
        }
    }

    override fun getItemCount() = items.size
}
