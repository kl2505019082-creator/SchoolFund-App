package com.uptm.schoolfund.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.uptm.schoolfund.R
import com.uptm.schoolfund.data.DatabaseHelper
import com.uptm.schoolfund.util.PinHelper

/**
 * 4-digit PIN screen with three modes: UNLOCK (enter the existing PIN), SETUP (create a PIN,
 * entered twice) and CHANGE (verify the current PIN, then create a new one). "Forgot PIN" proves
 * ownership with the phone's own screen lock, then lets the user create a new PIN without losing
 * data; only a phone with no screen lock falls back to erasing the app's data.
 */
class PinActivity : BaseActivity() {

    private enum class Stage { ENTER_CURRENT, ENTER_NEW, CONFIRM_NEW }

    private lateinit var mode: String
    private var stage = Stage.ENTER_CURRENT
    private var firstPin = ""
    private val entered = StringBuilder()
    private var lockTimer: CountDownTimer? = null

    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var error: TextView
    private lateinit var dots: LinearLayout
    private lateinit var forgot: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_UNLOCK
        stage = if (mode == MODE_SETUP) Stage.ENTER_NEW else Stage.ENTER_CURRENT

        title = findViewById(R.id.textPinTitle)
        subtitle = findViewById(R.id.textPinSubtitle)
        error = findViewById(R.id.textPinError)
        dots = findViewById(R.id.layoutPinDots)
        forgot = findViewById(R.id.textForgotPin)

        buildDots()
        buildKeypad()
        forgot.setOnClickListener { startForgotPin() }
        refreshLabels()
        showLockIfNeeded()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun buildDots() {
        repeat(PIN_LENGTH) {
            val dot = TextView(this).apply {
                textSize = 28f
                setPadding(dp(8), 0, dp(8), 0)
            }
            dots.addView(dot)
        }
        renderDots()
    }

    private fun renderDots() {
        for (i in 0 until dots.childCount) {
            val dot = dots.getChildAt(i) as TextView
            dot.text = if (i < entered.length) "●" else "○"
            dot.setTextColor(resources.getColor(R.color.primary_green, theme))
        }
    }

    private fun buildKeypad() {
        val grid = findViewById<GridLayout>(R.id.gridKeypad)
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        keys.forEach { key ->
            val cell = TextView(this).apply {
                text = key
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(resources.getColor(R.color.text_primary, theme))
                if (key.isNotEmpty()) {
                    setBackgroundResource(R.drawable.card_white)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onKey(key) }
                }
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dp(72)
                    height = dp(64)
                    setMargins(dp(8), dp(6), dp(8), dp(6))
                }
            }
            grid.addView(cell)
        }
    }

    private fun onKey(key: String) {
        if (PinHelper.lockRemainingMs(this) > 0) return
        error.text = ""
        if (key == "⌫") {
            if (entered.isNotEmpty()) entered.deleteCharAt(entered.length - 1)
        } else if (entered.length < PIN_LENGTH) {
            entered.append(key)
        }
        renderDots()
        if (entered.length == PIN_LENGTH) onComplete(entered.toString())
    }

    private fun onComplete(pin: String) {
        entered.setLength(0)
        when (stage) {
            Stage.ENTER_CURRENT -> {
                if (PinHelper.verify(this, pin)) {
                    if (mode == MODE_UNLOCK) {
                        setResult(Activity.RESULT_OK)
                        finish()
                        return
                    }
                    stage = Stage.ENTER_NEW
                    refreshLabels()
                } else {
                    error.text = getString(R.string.pin_error_wrong)
                    showLockIfNeeded()
                }
            }
            Stage.ENTER_NEW -> {
                firstPin = pin
                stage = Stage.CONFIRM_NEW
                refreshLabels()
            }
            Stage.CONFIRM_NEW -> {
                if (pin == firstPin) {
                    PinHelper.setPin(this, pin)
                    Toast.makeText(this, getString(R.string.pin_saved), Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                    return
                }
                firstPin = ""
                stage = Stage.ENTER_NEW
                error.text = getString(R.string.pin_error_mismatch)
                refreshLabels()
            }
        }
        renderDots()
    }

    private fun refreshLabels() {
        when (stage) {
            Stage.ENTER_CURRENT -> {
                title.text = getString(if (mode == MODE_UNLOCK) R.string.pin_title_unlock else R.string.pin_title_current)
                subtitle.text = ""
            }
            Stage.ENTER_NEW -> {
                title.text = getString(R.string.pin_title_new)
                subtitle.text = getString(R.string.pin_subtitle_new)
            }
            Stage.CONFIRM_NEW -> {
                title.text = getString(R.string.pin_title_confirm)
                subtitle.text = ""
            }
        }
        forgot.visibility = if (stage == Stage.ENTER_CURRENT) View.VISIBLE else View.GONE
    }

    private fun showLockIfNeeded() {
        lockTimer?.cancel()
        val remaining = PinHelper.lockRemainingMs(this)
        if (remaining <= 0) return
        lockTimer = object : CountDownTimer(remaining, 1000) {
            override fun onTick(msLeft: Long) {
                error.text = getString(R.string.pin_locked, (msLeft / 1000 + 1).toInt())
            }
            override fun onFinish() {
                error.text = ""
            }
        }.start()
    }

    // ---------- Forgot PIN ----------

    @Suppress("DEPRECATION")
    private fun startForgotPin() {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguard.isDeviceSecure) {
            val intent = keyguard.createConfirmDeviceCredentialIntent(
                getString(R.string.pin_verify_device_title),
                getString(R.string.pin_verify_device_desc)
            )
            if (intent != null) {
                startActivityForResult(intent, REQUEST_DEVICE_CREDENTIAL)
                return
            }
        }
        confirmEraseData()
    }

    private fun confirmEraseData() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pin_no_device_lock_title))
            .setMessage(getString(R.string.pin_no_device_lock_msg))
            .setPositiveButton(getString(R.string.btn_erase_data)) { _, _ ->
                DatabaseHelper.databaseFile(this).let { db ->
                    java.io.File(db.parentFile, db.name).delete()
                    listOf("-journal", "-wal", "-shm").forEach { java.io.File(db.parentFile, db.name + it).delete() }
                }
                PinHelper.clear(this)
                mode = MODE_SETUP
                stage = Stage.ENTER_NEW
                entered.setLength(0)
                refreshLabels()
                renderDots()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DEVICE_CREDENTIAL && resultCode == Activity.RESULT_OK) {
            mode = MODE_SETUP
            stage = Stage.ENTER_NEW
            entered.setLength(0)
            error.text = ""
            refreshLabels()
            renderDots()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        super.onBackPressed()
    }

    override fun onDestroy() {
        lockTimer?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MODE = "pin_mode"
        const val MODE_UNLOCK = "unlock"
        const val MODE_SETUP = "setup"
        const val MODE_CHANGE = "change"
        private const val PIN_LENGTH = 4
        private const val REQUEST_DEVICE_CREDENTIAL = 501
    }
}
