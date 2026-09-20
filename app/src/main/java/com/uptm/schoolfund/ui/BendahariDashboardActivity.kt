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
 * Bendahari PIBG screen: lists ALL fund requests in real time. Once a request has been
 * approved by the Pengerusi, the Bendahari marks it here as "funds disbursed" after
 * actually paying the money out.
 */
class BendahariDashboardActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bendahari_dashboard)

        val prefs = getSharedPreferences("school_fund_prefs", Context.MODE_PRIVATE)
        val bendahariName = prefs.getString("current_name", "Bendahari PIBG") ?: "Bendahari PIBG"
        findViewById<TextView>(R.id.textBendahariUser).text = bendahariName

        val recycler = findViewById<RecyclerView>(R.id.recyclerAllRequests)
        recycler.layoutManager = LinearLayoutManager(this)

        FirebaseRepository.listenToAllRequests { requests ->
            recycler.adapter = FundRequestAdapter(requests, viewerRole = "bendahari",
                onDecide = null,
                onDisburse = { request ->
                    FirebaseRepository.markDisbursed(request.id, bendahariName) { }
                }
            )
        }
    }
}
