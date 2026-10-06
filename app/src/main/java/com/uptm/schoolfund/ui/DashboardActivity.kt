package com.uptm.schoolfund.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.uptm.schoolfund.R
import com.uptm.schoolfund.adapter.FundSwitchAdapter
import com.uptm.schoolfund.adapter.FundSwitchItem
import com.uptm.schoolfund.adapter.TransactionAdapter
import com.uptm.schoolfund.data.DatabaseHelper
import com.uptm.schoolfund.util.LocaleHelper
import com.uptm.schoolfund.util.ThemeHelper
import java.text.NumberFormat
import java.util.Locale

/**
 * Home screen: shows the current class fund balance at a glance (FR: View Class Fund Balance)
 * and provides navigation to the other core features via card-style action tiles.
 *
 * "Scope" — the balance card can show either the general Class Fund or one Event's running
 * balance (income - expense; events have no pre-set budget cap). Tapping the balance card
 * opens a "Switch Fund" sheet listing both; the chosen scope is remembered on-device and also
 * carried into "Add transaction" so new entries land in the right place by default.
 */
class DashboardActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var balanceText: TextView
    private lateinit var balanceLabel: TextView
    private lateinit var recentList: RecyclerView
    private lateinit var scopePrefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        db = DatabaseHelper(this)
        scopePrefs = getSharedPreferences("school_fund_prefs", Context.MODE_PRIVATE)

        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val teacherName = prefs.getString(MainActivity.KEY_TEACHER_NAME, "")
        findViewById<TextView>(R.id.textTeacherName).text = getString(R.string.dashboard_welcome, teacherName)

        balanceText = findViewById(R.id.textBalance)
        balanceLabel = findViewById(R.id.textBalanceLabel)
        recentList = findViewById(R.id.recyclerRecent)
        recentList.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.buttonSwitchFund).setOnClickListener { showFundSwitchSheet() }

        findViewById<View>(R.id.buttonAddTransaction).setOnClickListener {
            startActivity(scopedAddTransactionIntent())
        }
        findViewById<View>(R.id.buttonEventBudgets).setOnClickListener {
            startActivity(Intent(this, EventListActivity::class.java))
        }
        findViewById<View>(R.id.buttonHistory).setOnClickListener {
            startActivity(Intent(this, TransactionHistoryActivity::class.java))
        }
        findViewById<View>(R.id.buttonReport).setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
        findViewById<View>(R.id.buttonRequestFundsDashboard).setOnClickListener {
            startActivity(Intent(this, RoleLoginActivity::class.java))
        }
        findViewById<View>(R.id.buttonBackupData).setOnClickListener {
            startActivity(Intent(this, BackupActivity::class.java))
        }
        findViewById<View>(R.id.buttonChangePin).setOnClickListener {
            startActivity(
                Intent(this, PinActivity::class.java).putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_CHANGE)
            )
        }

        setupLanguageToggle()
        setupThemeToggle()
    }

    /** Builds an "Add transaction" intent pre-scoped to whatever fund is currently selected. */
    private fun scopedAddTransactionIntent(): Intent {
        val intent = Intent(this, AddTransactionActivity::class.java)
        if (currentScopeType() == SCOPE_EVENT) {
            intent.putExtra(AddTransactionActivity.EXTRA_PRESELECT_EVENT_ID, currentScopeId())
        }
        return intent
    }

    private fun currentScopeType() = scopePrefs.getString(KEY_SCOPE_TYPE, SCOPE_CLASSFUND) ?: SCOPE_CLASSFUND
    private fun currentScopeId() = scopePrefs.getLong(KEY_SCOPE_ID, -1L)

    private fun setScope(type: String, id: Long) {
        scopePrefs.edit().putString(KEY_SCOPE_TYPE, type).putLong(KEY_SCOPE_ID, id).apply()
    }

    private fun setupLanguageToggle() {
        val button = findViewById<TextView>(R.id.buttonLanguageToggle)
        val current = LocaleHelper.getSavedLanguage(this)
        button.text = if (current == LocaleHelper.LANG_EN) "MY" else "EN"

        button.setOnClickListener {
            LocaleHelper.toggleLanguage(this)
            recreate()
        }
    }

    private fun setupThemeToggle() {
        val button = findViewById<LinearLayout>(R.id.buttonThemeToggle)
        val icon = findViewById<ImageView>(R.id.imageThemeToggle)
        icon.setImageResource(
            if (ThemeHelper.isNightMode(this)) R.drawable.ic_moon else R.drawable.ic_sun
        )

        button.setOnClickListener {
            ThemeHelper.toggleNightMode(this)
            recreate()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBalance()
        refreshRecentTransactions()
    }

    /** Shows whichever fund is currently selected: Class Fund, or one Event's running balance. */
    private fun refreshBalance() {
        val formatter = NumberFormat.getCurrencyInstance(Locale("ms", "MY"))
        if (currentScopeType() == SCOPE_EVENT) {
            val event = db.getAllEvents().find { it.id == currentScopeId() }
            if (event == null) {
                setScope(SCOPE_CLASSFUND, -1L)
                refreshBalance()
                return
            }
            balanceLabel.text = event.name.uppercase()
            balanceText.text = formatter.format(db.getEventBalance(event.id))
        } else {
            balanceLabel.text = getString(R.string.label_class_fund_balance)
            balanceText.text = formatter.format(db.getClassFundBalance())
        }
    }

    private fun refreshRecentTransactions() {
        val all = db.getAllTransactions().take(5)
        val events = db.getAllEvents()
        recentList.adapter = TransactionAdapter(all, events, onDelete = null)
    }

    // ---------- Switch Fund sheet ----------

    private fun buildFundSwitchItems(): List<FundSwitchItem> {
        val items = mutableListOf<FundSwitchItem>()
        items.add(
            FundSwitchItem(
                key = SCOPE_CLASSFUND,
                title = getString(R.string.label_class_fund_balance),
                subtitle = "",
                amount = db.getClassFundBalance(),
                deletable = false
            )
        )
        db.getAllEvents().forEach { e ->
            items.add(
                FundSwitchItem(
                    key = "event:${e.id}",
                    title = e.name,
                    subtitle = "",
                    amount = db.getEventBalance(e.id),
                    deletable = false
                )
            )
        }
        return items
    }

    private fun showFundSwitchSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_switch_fund, null)
        sheet.setContentView(view)

        val selectedKey = if (currentScopeType() == SCOPE_EVENT) "event:${currentScopeId()}" else SCOPE_CLASSFUND

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerFundSwitch)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = FundSwitchAdapter(
            items = buildFundSwitchItems(),
            selectedKey = selectedKey,
            onSelect = { item ->
                if (item.key.startsWith("event:")) {
                    setScope(SCOPE_EVENT, item.key.removePrefix("event:").toLong())
                } else {
                    setScope(SCOPE_CLASSFUND, -1L)
                }
                refreshBalance()
                sheet.dismiss()
            },
            onLongPressDelete = { }
        )

        view.findViewById<ImageView>(R.id.buttonCloseFundSwitch).setOnClickListener { sheet.dismiss() }

        sheet.show()
    }

    companion object {
        private const val KEY_SCOPE_TYPE = "dashboard_scope_type"
        private const val KEY_SCOPE_ID = "dashboard_scope_id"
        private const val SCOPE_CLASSFUND = "classfund"
        private const val SCOPE_EVENT = "event"
    }
}
