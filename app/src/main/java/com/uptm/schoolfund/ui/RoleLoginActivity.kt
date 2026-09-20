package com.uptm.schoolfund.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import android.widget.RadioButton
import com.uptm.schoolfund.R
import com.uptm.schoolfund.firebase.FirebaseRepository

/**
 * Online login screen for the multi-user extension. Two explicit modes — "Log Masuk" (email
 * + password only, for a returning user) and "Daftar Akaun Baru" (name + role + email +
 * password, for a first-time sign-up) — kept separate on purpose, instead of one button that
 * silently registers-or-logs-in. That silent-fallback approach was confusing and risked
 * quietly creating a duplicate blank account whenever someone mistyped their email while
 * trying to log in.
 *
 * Only "teacher" can be self-registered — see FirebaseRepository/firestore.rules. Pengerusi
 * and Bendahari accounts are created by the school admin directly in the Firebase Console.
 */
class RoleLoginActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_login)

        val modeGroup = findViewById<RadioGroup>(R.id.radioGroupMode)
        val radioModeLogin = findViewById<RadioButton>(R.id.radioModeLogin)
        val radioModeRegister = findViewById<RadioButton>(R.id.radioModeRegister)
        val registerFields = findViewById<LinearLayout>(R.id.layoutRegisterFields)

        val nameInput = findViewById<EditText>(R.id.editName)
        val emailInput = findViewById<EditText>(R.id.editEmail)
        val passwordInput = findViewById<EditText>(R.id.editPassword)
        val forgotPasswordText = findViewById<TextView>(R.id.textForgotPassword)
        val submitButton = findViewById<Button>(R.id.buttonRoleLogin)
        val progress = findViewById<ProgressBar>(R.id.progressLogin)

        // android:backgroundTint="@null" in the XML isn't reliably honoured by AppCompat's
        // button tinting on every device/theme combination — clearing it here in code is the
        // only way that's guaranteed to actually show the intended white button + blue text
        // instead of a dark, low-contrast, theme-tinted one.
        submitButton.backgroundTintList = null

        // Remember the last email used on this device so returning users don't have to
        // retype/remember it.
        val loginPrefs = getSharedPreferences("school_fund_login_prefs", Context.MODE_PRIVATE)
        loginPrefs.getString("last_email", null)?.let { emailInput.setText(it) }

        fun styleTab(tab: RadioButton, selected: Boolean) {
            if (selected) {
                tab.setBackgroundResource(R.drawable.card_white)
                tab.setTextColor(resources.getColor(R.color.accent_blue, theme))
            } else {
                tab.background = null
                tab.setTextColor(resources.getColor(R.color.text_secondary, theme))
            }
        }

        fun applyMode(isRegister: Boolean) {
            styleTab(radioModeLogin, !isRegister)
            styleTab(radioModeRegister, isRegister)
            registerFields.visibility = if (isRegister) View.VISIBLE else View.GONE
            forgotPasswordText.visibility = if (isRegister) View.GONE else View.VISIBLE
            submitButton.text = if (isRegister) getString(R.string.btn_register) else getString(R.string.btn_login)
        }

        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            applyMode(checkedId == R.id.radioModeRegister)
        }
        applyMode(false)

        forgotPasswordText.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, getString(R.string.msg_enter_email_first), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            FirebaseRepository.sendPasswordReset(email) { _, message ->
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }

        fun afterAuthSuccess(email: String, fallbackName: String) {
            val uid = FirebaseRepository.currentUid
            if (uid == null) {
                progress.visibility = View.GONE
                Toast.makeText(this, getString(R.string.msg_login_failed), Toast.LENGTH_SHORT).show()
                return
            }
            FirebaseRepository.getUserRole(uid) { actualRole, actualName, mustChangePassword ->
                progress.visibility = View.GONE
                if (actualRole == null) {
                    FirebaseRepository.logout()
                    Toast.makeText(this, getString(R.string.msg_cannot_load_profile), Toast.LENGTH_SHORT).show()
                    return@getUserRole
                }

                val prefs = getSharedPreferences("school_fund_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("current_name", actualName ?: fallbackName)
                    .putString("current_role", actualRole)
                    .apply()
                loginPrefs.edit().putString("last_email", email).apply()

                fun proceedToDashboard() {
                    val intent = when (actualRole) {
                        "pengerusi" -> Intent(this, PengerusiDashboardActivity::class.java)
                        "bendahari" -> Intent(this, BendahariDashboardActivity::class.java)
                        else -> Intent(this, FundRequestListActivity::class.java)
                    }
                    startActivity(intent)
                    finish()
                }

                if (mustChangePassword) {
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.dialog_change_password_title))
                        .setMessage(getString(R.string.dialog_change_password_msg))
                        .setCancelable(false)
                        .setPositiveButton(getString(R.string.btn_send_reset_email)) { _, _ ->
                            FirebaseRepository.sendPasswordReset(email) { _, message ->
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            }
                            FirebaseRepository.clearMustChangePassword(uid)
                            proceedToDashboard()
                        }
                        .setNegativeButton(getString(R.string.btn_later)) { _, _ -> proceedToDashboard() }
                        .show()
                } else {
                    proceedToDashboard()
                }
            }
        }

        submitButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            val isRegister = modeGroup.checkedRadioButtonId == R.id.radioModeRegister

            if (email.isEmpty() || password.length < 6) {
                Toast.makeText(this, getString(R.string.msg_fill_email_password), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isRegister) {
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, getString(R.string.msg_fill_full_name), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                progress.visibility = View.VISIBLE
                FirebaseRepository.createAccount(email, password, name, "teacher") { success, message ->
                    if (!success) {
                        progress.visibility = View.GONE
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        return@createAccount
                    }
                    afterAuthSuccess(email, name)
                }
            } else {
                progress.visibility = View.VISIBLE
                FirebaseRepository.login(email, password) { success, message ->
                    if (!success) {
                        progress.visibility = View.GONE
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        return@login
                    }
                    afterAuthSuccess(email, "")
                }
            }
        }
    }
}
