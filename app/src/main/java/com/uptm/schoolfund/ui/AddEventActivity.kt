package com.uptm.schoolfund.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.uptm.schoolfund.R
import com.uptm.schoolfund.data.DatabaseHelper
import com.uptm.schoolfund.model.EventBudget
import java.text.SimpleDateFormat
import java.util.*

class AddEventActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_event)

        val db = DatabaseHelper(this)
        val nameInput = findViewById<EditText>(R.id.editEventName)

        val saveButton = findViewById<Button>(R.id.buttonSaveEvent)
        saveButton.backgroundTintList = null
        saveButton.setOnClickListener {
            val name = nameInput.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this, "Enter an event name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // No pre-set budget cap — the teacher records income and expense against this
            // event directly, and its balance is simply income minus expense.
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            db.insertEvent(EventBudget(name = name, budget = 0.0, date = date))
            Toast.makeText(this, "Event created", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK)
            finish()
        }
    }
}
