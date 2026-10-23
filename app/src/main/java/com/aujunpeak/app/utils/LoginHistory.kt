package com.aujunpeak.app.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object LoginHistory {

    private const val PREFS_NAME = "aujunpeak_key_prefs"
    private const val KEY_HISTORY = "login_history_v2"
    private const val MAX_ENTRIES = 10

    data class Entry(
        val timestampMs: Long,
        val deviceName: String,
        val ip: String,
        val country: String
    ) {
        val timeFormatted: String
            get() {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                return sdf.format(Date(timestampMs))
            }
    }

    fun addEntry(ctx: Context, deviceName: String, ip: String = "", country: String = "") {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr   = try {
            JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
        } catch (_: Exception) { JSONArray() }

        val entry = JSONObject().apply {
            put("ts",      System.currentTimeMillis())
            put("device",  deviceName)
            put("ip",      ip)
            put("country", country)
        }

        // Prepend new entry
        val newArr = JSONArray()
        newArr.put(entry)
        for (i in 0 until minOf(arr.length(), MAX_ENTRIES - 1)) {
            newArr.put(arr.getJSONObject(i))
        }

        prefs.edit().putString(KEY_HISTORY, newArr.toString()).apply()
    }

    fun getAll(ctx: Context): List<Entry> {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return try {
            val arr = JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val obj = arr.getJSONObject(i)
                    Entry(
                        timestampMs = obj.optLong("ts", 0L),
                        deviceName  = obj.optString("device", ""),
                        ip          = obj.optString("ip", ""),
                        country     = obj.optString("country", "")
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_HISTORY).apply()
    }
}
