package com.uptm.schoolfund.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.uptm.schoolfund.R

/**
 * Simple login screen. Since this is a single-teacher, single-device offline app,
 * "login" stores the teacher's name locally via SharedPreferences rather than
 * authenticating against a server.
 */
class MainActivity : BaseActivity() {

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Skip login if teacher name already saved
        val savedName = prefs.getString(KEY_TEACHER_NAME, null)
        if (!savedName.isNullOrBlank()) {
            goToDashboard()
            return
        }

        val nameInput = findViewById<EditText>(R.id.editTeacherName)
        val loginButton = findViewById<Button>(R.id.buttonLogin)
        // See RoleLoginActivity for why this is needed instead of relying on XML backgroundTint.
        loginButton.backgroundTintList = null

        loginButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString(KEY_TEACHER_NAME, name).apply()
            goToDashboard()
        }
    }

    private fun goToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    companion object {
        const val PREFS_NAME = "school_fund_prefs"
        const val KEY_TEACHER_NAME = "teacher_name"
    }
}
