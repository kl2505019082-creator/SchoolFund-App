package com.uptm.schoolfund.adapter

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.uptm.schoolfund.R
import com.uptm.schoolfund.model.EventBudget
import com.uptm.schoolfund.model.Tabung
import com.uptm.schoolfund.model.TransactionItem
import com.uptm.schoolfund.ui.ReceiptViewerActivity
import java.io.File
import java.text.NumberFormat
import java.util.Locale

class TransactionAdapter(
    private val items: List<TransactionItem>,
    private val events: List<EventBudget>,
    private val tabungs: List<Tabung> = emptyList(),
    private val onDelete: ((TransactionItem) -> Unit)?
) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

    private val formatter = NumberFormat.getCurrencyInstance(Locale("ms", "MY"))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconCircle: LinearLayout = view.findViewById(R.id.iconCircleTx)
        val icon: ImageView = view.findViewById(R.id.imageTxIcon)
        val description: TextView = view.findViewById(R.id.textTxDescription)
        val meta: TextView = view.findViewById(R.id.textTxMeta)
        val receiptThumb: ImageView = view.findViewById(R.id.imageReceiptThumb)
        val amount: TextView = view.findViewById(R.id.textTxAmount)
        val deleteBtn: ImageButton = view.findViewById(R.id.buttonDeleteTx)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tx = items[position]
        holder.description.text = tx.description.ifBlank { "(no description)" }

        val eventName = tx.eventId?.let { id -> events.find { it.id == id }?.name }
        val tabungName = tx.tabungId?.let { id -> tabungs.find { it.id == id }?.name }
        val scopeName = eventName ?: tabungName
        holder.meta.text = if (scopeName != null) "${tx.date} · $scopeName" else tx.date

        val isIncome = tx.type == "income"
        holder.iconCircle.setBackgroundResource(
            if (isIncome) R.drawable.icon_circle_green else R.drawable.icon_circle_red
        )
        holder.icon.setImageResource(
            if (isIncome) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down
        )

        val sign = if (isIncome) "+" else "-"
        holder.amount.text = "$sign${formatter.format(tx.amount)}"
        holder.amount.setTextColor(
            if (isIncome) 0xFF2E6B4F.toInt() else 0xFFC0392B.toInt()
        )

        // Show a real thumbnail of the attached receipt (if any) directly in the list,
        // so the teacher can see at a glance which transactions have proof attached.
        val hasReceipt = !tx.receiptPath.isNullOrBlank() && File(tx.receiptPath).exists()
        if (hasReceipt) {
            holder.receiptThumb.visibility = View.VISIBLE
            holder.receiptThumb.setImageBitmap(decodeSampledBitmap(tx.receiptPath!!, 80, 80))
        } else {
            holder.receiptThumb.visibility = View.GONE
        }

        // Tap the row to view the full-size receipt photo, if any.
        if (hasReceipt) {
            holder.itemView.setOnClickListener {
                val context = holder.itemView.context
                val intent = Intent(context, ReceiptViewerActivity::class.java).apply {
                    putExtra(ReceiptViewerActivity.EXTRA_RECEIPT_PATH, tx.receiptPath)
                    putExtra(ReceiptViewerActivity.EXTRA_DESCRIPTION, tx.description)
                    putExtra(ReceiptViewerActivity.EXTRA_DATE, tx.date)
                }
                context.startActivity(intent)
            }
        } else {
            holder.itemView.setOnClickListener(null)
            holder.itemView.isClickable = false
        }

        if (onDelete != null) {
            holder.deleteBtn.visibility = View.VISIBLE
            holder.deleteBtn.setOnClickListener { onDelete.invoke(tx) }
        } else {
            holder.deleteBtn.visibility = View.GONE
        }
    }

    override fun getItemCount() = items.size

    /** Decodes a bitmap at a small target size to keep list scrolling smooth. */
    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)

            var sampleSize = 1
            var halfWidth = options.outWidth / 2
            var halfHeight = options.outHeight / 2
            while (halfWidth / sampleSize >= reqWidth && halfHeight / sampleSize >= reqHeight) {
                sampleSize *= 2
            }

            val finalOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(path, finalOptions)
        } catch (e: Exception) {
            null
        }
    }
}
