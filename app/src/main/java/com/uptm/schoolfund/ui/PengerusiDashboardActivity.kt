package com.uptm.schoolfund.ui

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.uptm.schoolfund.R
import com.uptm.schoolfund.adapter.FundRequestAdapter
import com.uptm.schoolfund.firebase.FirebaseRepository

/**
 * Pengerusi PIBG screen: lists ALL teachers' fund requests in real time, with Approve /
 * Reject actions. The Pengerusi discusses each request with the Pengetua outside the app
 * before making the decision here.
 */
class PengerusiDashboardActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pengerusi_dashboard)

        val prefs = getSharedPreferences("school_fund_prefs", Context.MODE_PRIVATE)
        val pengerusiName = prefs.getString("current_name", "Pengerusi PIBG") ?: "Pengerusi PIBG"
        findViewById<TextView>(R.id.textPengerusiUser).text = pengerusiName

        val recycler = findViewById<RecyclerView>(R.id.recyclerAllRequests)
        recycler.layoutManager = LinearLayoutManager(this)

        FirebaseRepository.listenToAllRequests { requests ->
            recycler.adapter = FundRequestAdapter(requests, viewerRole = "pengerusi",
                onDecide = { request, approve, remarks ->
                    FirebaseRepository.decideRequest(request.id, approve, remarks, pengerusiName) { }
                },
                onDisburse = null
            )
        }
    }
}
