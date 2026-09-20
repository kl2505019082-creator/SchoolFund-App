package com.uptm.schoolfund.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Handles switching the app between light and dark mode, persisted across sessions.
 * The app theme is Theme.MaterialComponents.DayNight, so this works with the
 * values-night/ resource overrides automatically once applied.
 */
object ThemeHelper {

    private const val PREFS_NAME = "school_fund_prefs"
    private const val KEY_NIGHT_MODE = "app_night_mode"

    fun isNightMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NIGHT_MODE, false)
    }

    fun setNightMode(context: Context, isNight: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_NIGHT_MODE, isNight).apply()
        applySavedMode(context)
    }

    fun toggleNightMode(context: Context): Boolean {
        val next = !isNightMode(context)
        setNightMode(context, next)
        return next
    }

    /** Applies the saved light/dark preference — call once on app start (e.g. in BaseActivity). */
    fun applySavedMode(context: Context) {
        val mode = if (isNightMode(context)) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
