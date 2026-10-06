package com.uptm.schoolfund.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.uptm.schoolfund.R
import com.uptm.schoolfund.adapter.FundRequestAdapter
import com.uptm.schoolfund.firebase.FirebaseRepository
import com.uptm.schoolfund.model.FundRequest

/**
 * Teacher-facing screen: shows live status (pending/approved/rejected/disbursed) of their own
 * requests. The teacher can tick decided requests and "Hide" them, and later view the "Hidden"
 * tab to tick and bring them back. Hiding only affects THIS list on THIS device (a locally-stored
 * set of request IDs) — it never deletes or alters the shared Firestore record, so
 * Pengerusi/Bendahari still see the complete approval history.
 */
class FundRequestListActivity : BaseActivity() {

    private var allRequests: List<FundRequest> = emptyList()
    private lateinit var recycler: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var tabGroup: RadioGroup
    private lateinit var tabActive: RadioButton
    private lateinit var tabHidden: RadioButton
    private lateinit var actionButton: Button
    private lateinit var hiddenPrefs: android.content.SharedPreferences
    private var hiddenIds: MutableSet<String> = mutableSetOf()
    private val selectedIds: MutableSet<String> = mutableSetOf()

    private val showingHidden get() = tabGroup.checkedRadioButtonId == R.id.radioTabHidden

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fund_request_list)

        recycler = findViewById(R.id.recyclerRequests)
        recycler.layoutManager = LinearLayoutManager(this)
        emptyText = findViewById(R.id.textRequestsEmpty)
        tabGroup = findViewById(R.id.radioGroupRequestTabs)
        tabActive = findViewById(R.id.radioTabActive)
        tabHidden = findViewById(R.id.radioTabHidden)
        actionButton = findViewById(R.id.buttonHideSelected)
        actionButton.backgroundTintList = null

        val uid = FirebaseRepository.currentUid
        // Same preference file/key as before, so requests hidden under the old "Clear" stay hidden.
        hiddenPrefs = getSharedPreferences("school_fund_cleared_requests", Context.MODE_PRIVATE)
        hiddenIds = (hiddenPrefs.getStringSet(prefKey(uid), emptySet()) ?: emptySet()).toMutableSet()

        val newRequestButton = findViewById<Button>(R.id.buttonNewRequest)
        newRequestButton.backgroundTintList = null
        newRequestButton.setOnClickListener {
            startActivity(Intent(this, SubmitFundRequestActivity::class.java))
        }

        tabGroup.setOnCheckedChangeListener { _, _ ->
            selectedIds.clear()
            refreshList()
        }
        actionButton.setOnClickListener { applyToSelected() }

        if (uid != null) {
            FirebaseRepository.listenToOwnRequests(uid) { requests ->
                allRequests = requests
                refreshList()
            }
        }
    }

    private fun prefKey(uid: String?) = "cleared_${uid ?: "unknown"}"

    private fun styleTabs() {
        listOf(tabActive, tabHidden).forEach { tab ->
            if (tab.id == tabGroup.checkedRadioButtonId) {
                tab.setBackgroundResource(R.drawable.card_white)
                tab.setTextColor(resources.getColor(R.color.accent_blue, theme))
            } else {
                tab.background = null
                tab.setTextColor(resources.getColor(R.color.text_secondary, theme))
            }
        }
    }

    private fun refreshList() {
        styleTabs()
        val hiddenCount = allRequests.count { it.id in hiddenIds }
        tabHidden.text = getString(R.string.tab_requests_hidden, hiddenCount)

        val hiding = showingHidden
        val shown = allRequests.filter { (it.id in hiddenIds) == hiding }

        emptyText.text = getString(if (hiding) R.string.msg_no_hidden_requests else R.string.msg_no_requests_yet)
        emptyText.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE

        recycler.adapter = FundRequestAdapter(
            items = shown,
            viewerRole = "teacher",
            onDecide = null,
            onDisburse = null,
            selectable = true,
            selectedIds = selectedIds,
            onSelectionChanged = { req, checked ->
                if (checked) selectedIds.add(req.id) else selectedIds.remove(req.id)
                updateActionButton()
            }
        )
        updateActionButton()
    }

    private fun updateActionButton() {
        actionButton.visibility = if (selectedIds.isEmpty()) View.GONE else View.VISIBLE
        val label = if (showingHidden) R.string.btn_unhide_selected else R.string.btn_hide_selected
        actionButton.text = getString(label, selectedIds.size)
    }

    private fun applyToSelected() {
        if (showingHidden) hiddenIds.removeAll(selectedIds) else hiddenIds.addAll(selectedIds)
        hiddenPrefs.edit().putStringSet(prefKey(FirebaseRepository.currentUid), hiddenIds).apply()
        selectedIds.clear()
        refreshList()
    }
}
