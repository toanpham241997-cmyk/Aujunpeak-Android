package com.aujunpeak.app.utils

import android.content.Context
import android.content.SharedPreferences

object ThemeManager {

    private const val PREFS = "aujunpeak_prefs"
    private const val KEY_COLOR = "theme_color"
    private const val DEFAULT_COLOR = "#FF1744"

    fun getSavedColor(context: Context): String =
        getPrefs(context).getString(KEY_COLOR, DEFAULT_COLOR) ?: DEFAULT_COLOR

    fun saveColor(context: Context, hexColor: String) {
        getPrefs(context).edit().putString(KEY_COLOR, hexColor).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
