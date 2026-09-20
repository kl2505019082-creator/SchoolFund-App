package com.uptm.schoolfund.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

/**
 * Handles switching the app's display language between English ("en") and
 * Bahasa Malaysia ("ms"). The chosen language is persisted and re-applied to
 * every screen via BaseActivity.attachBaseContext.
 */
object LocaleHelper {

    private const val PREFS_NAME = "school_fund_prefs"
    private const val KEY_LANGUAGE = "app_language"
    const val LANG_EN = "en"
    const val LANG_MS = "ms"

    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, LANG_EN) ?: LANG_EN
    }

    fun setLanguage(context: Context, language: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    /** Returns the opposite language of the one currently saved — used by the MY/EN toggle. */
    fun toggleLanguage(context: Context): String {
        val current = getSavedLanguage(context)
        val next = if (current == LANG_EN) LANG_MS else LANG_EN
        setLanguage(context, next)
        return next
    }

    /** Wraps the given context so all string resources resolve in the saved language. */
    fun applyLocale(context: Context): Context {
        val language = getSavedLanguage(context)
        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
