package com.aujunpeak.app.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    private const val PREFS      = "aujunpeak_prefs"
    private const val KEY_LANG   = "selected_lang"
    const val DEFAULT_LANG       = "en"   // English is the default language

    fun getSavedLocale(context: Context): String {
        return getPrefs(context).getString(KEY_LANG, DEFAULT_LANG) ?: DEFAULT_LANG
    }

    fun setLocale(context: Context, langCode: String) {
        getPrefs(context).edit().putString(KEY_LANG, langCode).apply()
    }

    fun applyLocale(context: Context): Context {
        val lang   = getSavedLocale(context)
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
