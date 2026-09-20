package com.uptm.schoolfund.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.uptm.schoolfund.R
import com.uptm.schoolfund.adapter.FundRequestAdapter
import com.uptm.schoolfund.firebase.FirebaseRepository
import com.uptm.schoolfund.model.FundRequest

/**
 * Teacher-facing screen: shows live status (pending/approved/rejected/disbursed) of their own
 * requests. "Clear" on an already-decided request only hides it from THIS list on THIS device
 * (a locally-stored set of dismissed request IDs) — it never deletes or alters the shared
 * Firestore record, so Pengerusi/Bendahari still see the complete approval history. This keeps
 * the teacher's view uncluttered after many requests without ever destroying financial records.
 */
class FundRequestListActivity : BaseActivity() {

    private var allRequests: List<FundRequest> = emptyList()
    private lateinit var recycler: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var clearedPrefs: android.content.SharedPreferences
    private var clearedIds: MutableSet<String> = mutableSetOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fund_request_list)

        recycler = findViewById(R.id.recyclerRequests)
        recycler.layoutManager = LinearLayoutManager(this)
        emptyText = findViewById(R.id.textRequestsEmpty)

        val uid = FirebaseRepository.currentUid
        clearedPrefs = getSharedPreferences("school_fund_cleared_requests", Context.MODE_PRIVATE)
        clearedIds = (clearedPrefs.getStringSet(prefKey(uid), emptySet()) ?: emptySet()).toMutableSet()

        val newRequestButton = findViewById<Button>(R.id.buttonNewRequest)
        newRequestButton.backgroundTintList = null
        newRequestButton.setOnClickListener {
            startActivity(Intent(this, SubmitFundRequestActivity::class.java))
        }

        if (uid != null) {
            FirebaseRepository.listenToOwnRequests(uid) { requests ->
                allRequests = requests
                refreshList()
            }
        }
    }

    private fun prefKey(uid: String?) = "cleared_${uid ?: "unknown"}"

    private fun refreshList() {
        val visible = allRequests.filter { it.id !in clearedIds }

        emptyText.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE

        recycler.adapter = FundRequestAdapter(
            items = visible,
            viewerRole = "teacher",
            onDecide = null,
            onDisburse = null,
            onClear = { req -> clearRequest(req) }
        )
    }

    private fun clearRequest(req: FundRequest) {
        clearedIds.add(req.id)
        clearedPrefs.edit().putStringSet(prefKey(FirebaseRepository.currentUid), clearedIds).apply()
        refreshList()
    }
}
