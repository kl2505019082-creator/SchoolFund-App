package com.uptm.schoolfund.adapter

import android.app.AlertDialog
import android.content.Intent
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private val onClear: ((FundRequest) -> Unit)? = null
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
        val clearBtn: TextView = view.findViewById(R.id.buttonClearRequest)
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
        val req = items[position]
        holder.teacher.text = req.teacherName
        holder.amount.text = formatter.format(req.amount)
        holder.reason.text = req.reason
        val eventPart = if (req.eventName.isNotBlank()) " · ${req.eventName}" else ""
        holder.meta.text = dateFormat.format(Date(req.requestedAt)) + eventPart

        holder.status.text = when (req.status) {
            "approved" -> "Diluluskan"
            "rejected" -> "Ditolak"
            "disbursed" -> "Duit Dikeluarkan"
            else -> "Pending"
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
            val verb = if (req.status == "approved") "Diluluskan" else "Ditolak"
            holder.remarks.text = "$verb oleh ${req.decidedBy}: “${req.financeRemarks}”"
        }

        holder.workPaperBtn.visibility = if (req.workPaperBase64.isNotBlank()) View.VISIBLE else View.GONE
        holder.workPaperBtn.setOnClickListener { openWorkPaper(holder, req) }

        // "Clear" only ever hides a request from THIS teacher's own list on THIS device —
        // it never touches the shared Firestore record, so Pengerusi/Bendahari still see the
        // full approval history. Only offered for already-decided requests (not "pending",
        // which is still active and shouldn't be dismissible).
        val canClear = viewerRole == "teacher" && req.status != "pending" && onClear != null
        holder.clearBtn.visibility = if (canClear) View.VISIBLE else View.GONE
        holder.clearBtn.setOnClickListener { onClear?.invoke(req) }

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
            val fileName = req.workPaperFileName.ifBlank { "kertas_kerja.pdf" }
            val file = File(dir, "${req.id}_$fileName")
            FileOutputStream(file).use { it.write(bytes) }

            val intent = Intent(context, WorkPaperViewerActivity::class.java).apply {
                putExtra(WorkPaperViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                putExtra(WorkPaperViewerActivity.EXTRA_FILE_NAME, fileName)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membuka kertas kerja: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun promptDecision(holder: ViewHolder, req: FundRequest, approve: Boolean) {
        val context = holder.itemView.context
        val input = EditText(context).apply {
            hint = if (approve) "Catatan (pilihan)" else "Sebab ditolak (wajib) — guru akan nampak ni"
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(if (approve) "Luluskan permohonan" else "Tolak permohonan")
            .setMessage("${req.teacherName} — ${formatter.format(req.amount)}")
            .setView(input)
            .setPositiveButton(if (approve) "Luluskan" else "Tolak", null)
            .setNegativeButton("Batal", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val remarks = input.text.toString().trim()
                if (!approve && remarks.isEmpty()) {
                    input.error = "Sila nyatakan sebab penolakan"
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
            .setTitle("Tandakan duit dikeluarkan")
            .setMessage("Sahkan RM ${formatter.format(req.amount)} untuk ${req.teacherName} telah dibayar?")
            .setPositiveButton("Sahkan") { _, _ -> onDisburse?.invoke(req) }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun getItemCount() = items.size
}
