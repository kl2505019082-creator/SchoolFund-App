package com.uptm.schoolfund.adapter

import android.app.AlertDialog
import android.content.Intent
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.uptm.schoolfund.R
import com.uptm.schoolfund.model.FundRequest
import com.uptm.schoolfund.ui.WorkPaperViewerActivity
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class FundRequestAdapter(
    private val items: List<FundRequest>,
    private val viewerRole: String, // "teacher", "pengerusi" or "bendahari"
    private val onDecide: ((FundRequest, Boolean, String) -> Unit)?,
    private val onDisburse: ((FundRequest) -> Unit)?,
    private val selectable: Boolean = false, // teacher list: tick requests to hide/unhide locally
    private val selectedIds: Set<String> = emptySet(),
    private val onSelectionChanged: ((FundRequest, Boolean) -> Unit)? = null
) : RecyclerView.Adapter<FundRequestAdapter.ViewHolder>() {

    private val formatter = NumberFormat.getCurrencyInstance(Locale("ms", "MY"))
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val teacher: TextView = view.findViewById(R.id.textReqTeacher)
        val amount: TextView = view.findViewById(R.id.textReqAmount)
        val reason: TextView = view.findViewById(R.id.textReqReason)
        val meta: TextView = view.findViewById(R.id.textReqMeta)
        val status: TextView = view.findViewById(R.id.textReqStatus)
        val remarks: TextView = view.findViewById(R.id.textReqRemarks)
        val selectBox: CheckBox = view.findViewById(R.id.checkReqSelect)
        val workPaperBtn: TextView = view.findViewById(R.id.buttonViewWorkPaper)
        val actions: LinearLayout = view.findViewById(R.id.layoutReqActions)
        val approveBtn: TextView = view.findViewById(R.id.buttonApprove)
        val rejectBtn: TextView = view.findViewById(R.id.buttonReject)
        val disburseBtn: TextView = view.findViewById(R.id.buttonDisburse)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_fund_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val context = holder.itemView.context
        val req = items[position]
        holder.teacher.text = req.teacherName
        holder.amount.text = formatter.format(req.amount)
        holder.reason.text = req.reason
        val eventPart = if (req.eventName.isNotBlank()) " · ${req.eventName}" else ""
        holder.meta.text = dateFormat.format(Date(req.requestedAt)) + eventPart

        holder.status.text = when (req.status) {
            "approved" -> context.getString(R.string.status_approved)
            "rejected" -> context.getString(R.string.status_rejected)
            "disbursed" -> context.getString(R.string.status_disbursed)
            else -> context.getString(R.string.status_pending)
        }
        holder.status.setTextColor(
            when (req.status) {
                "approved" -> 0xFF2E6B4F.toInt()
                "rejected" -> 0xFFC0392B.toInt()
                "disbursed" -> 0xFF4A6FA1.toInt()
                else -> 0xFFB9770E.toInt()
            }
        )

        // Show the Pengerusi's remarks (why approved/rejected) to everyone, especially the
        // teacher — previously captured but never displayed anywhere, so a rejected teacher
        // had no way to see the reason.
        val showRemarks = (req.status == "approved" || req.status == "rejected") && req.financeRemarks.isNotBlank()
        holder.remarks.visibility = if (showRemarks) View.VISIBLE else View.GONE
        if (showRemarks) {
            val template = if (req.status == "approved") R.string.remarks_approved_by else R.string.remarks_rejected_by
            holder.remarks.text = context.getString(template, req.decidedBy, req.financeRemarks)
        }

        holder.workPaperBtn.visibility = if (req.workPaperBase64.isNotBlank()) View.VISIBLE else View.GONE
        holder.workPaperBtn.setOnClickListener { openWorkPaper(holder, req) }

        // Hiding only ever affects THIS teacher's own list on THIS device — it never touches the
        // shared Firestore record, so Pengerusi/Bendahari still see the full approval history.
        // Only offered for already-decided requests (a "pending" one is still active).
        val canSelect = viewerRole == "teacher" && req.status != "pending" && selectable
        holder.selectBox.setOnCheckedChangeListener(null)
        holder.selectBox.visibility = if (canSelect) View.VISIBLE else View.GONE
        holder.selectBox.isChecked = req.id in selectedIds
        holder.selectBox.setOnCheckedChangeListener { _, checked -> onSelectionChanged?.invoke(req, checked) }

        val canDecide = viewerRole == "pengerusi" && req.status == "pending" && onDecide != null
        val canDisburse = viewerRole == "bendahari" && req.status == "approved" && onDisburse != null

        holder.actions.visibility = if (canDecide || canDisburse) View.VISIBLE else View.GONE
        holder.approveBtn.visibility = if (canDecide) View.VISIBLE else View.GONE
        holder.rejectBtn.visibility = if (canDecide) View.VISIBLE else View.GONE
        holder.disburseBtn.visibility = if (canDisburse) View.VISIBLE else View.GONE

        holder.approveBtn.setOnClickListener { promptDecision(holder, req, true) }
        holder.rejectBtn.setOnClickListener { promptDecision(holder, req, false) }
        holder.disburseBtn.setOnClickListener { promptDisburse(holder, req) }
    }

    /** Decodes the embedded base64 PDF to a cache file and opens it in the app's own PDF
     *  viewer screen (WorkPaperViewerActivity) — works even with no external PDF app installed. */
    private fun openWorkPaper(holder: ViewHolder, req: FundRequest) {
        val context = holder.itemView.context
        try {
            val bytes = Base64.decode(req.workPaperBase64, Base64.NO_WRAP)
            val dir = File(context.cacheDir, "work_papers").apply { mkdirs() }
            val fileName = req.workPaperFileName.ifBlank { context.getString(R.string.default_work_paper_name) }
            val file = File(dir, "${req.id}_$fileName")
            FileOutputStream(file).use { it.write(bytes) }

            val intent = Intent(context, WorkPaperViewerActivity::class.java).apply {
                putExtra(WorkPaperViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                putExtra(WorkPaperViewerActivity.EXTRA_FILE_NAME, fileName)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.msg_failed_open_work_paper, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun promptDecision(holder: ViewHolder, req: FundRequest, approve: Boolean) {
        val context = holder.itemView.context
        val input = EditText(context).apply {
            hint = if (approve) context.getString(R.string.hint_remarks_optional) else context.getString(R.string.hint_reject_reason_mandatory)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(if (approve) context.getString(R.string.dialog_approve_title) else context.getString(R.string.dialog_reject_title))
            .setMessage("${req.teacherName} — ${formatter.format(req.amount)}")
            .setView(input)
            .setPositiveButton(if (approve) context.getString(R.string.btn_approve_confirm) else context.getString(R.string.btn_reject_confirm), null)
            .setNegativeButton(context.getString(R.string.btn_cancel), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val remarks = input.text.toString().trim()
                if (!approve && remarks.isEmpty()) {
                    input.error = context.getString(R.string.error_state_reason)
                    return@setOnClickListener
                }
                onDecide?.invoke(req, approve, remarks)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun promptDisburse(holder: ViewHolder, req: FundRequest) {
        val context = holder.itemView.context
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.dialog_disburse_title))
            .setMessage(context.getString(R.string.dialog_disburse_msg, formatter.format(req.amount), req.teacherName))
            .setPositiveButton(context.getString(R.string.btn_confirm)) { _, _ -> onDisburse?.invoke(req) }
            .setNegativeButton(context.getString(R.string.btn_cancel), null)
            .show()
    }

    override fun getItemCount() = items.size
}
