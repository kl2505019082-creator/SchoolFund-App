package com.uptm.schoolfund.ui

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.uptm.schoolfund.util.LocaleHelper
import com.uptm.schoolfund.util.ThemeHelper

/**
 * Base class for every screen in the app. Ensures the saved language (English/Malay)
 * and light/dark mode preference are applied consistently, no matter which screen
 * is opened first.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val localeContext = LocaleHelper.applyLocale(newBase)
        super.attachBaseContext(localeContext)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        ThemeHelper.applySavedMode(this)
        super.onCreate(savedInstanceState)
    }
}
