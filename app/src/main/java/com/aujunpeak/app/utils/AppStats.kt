package com.aujunpeak.app.utils

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object AppStats {

    private const val PREFS_NAME      = "aujunpeak_key_prefs"
    private const val KEY_OPEN_COUNT  = "stat_open_count"
    private const val KEY_OPT_COUNT   = "stat_optimize_count"
    private const val KEY_ONLINE_SEC  = "stat_online_seconds"
    private const val KEY_JOIN_DATE   = "stat_join_date_ms"
    private const val KEY_SESSION_START = "stat_session_start_ms"

    /* ── App open counter ─────────────────────────────── */
    fun incrementOpenCount(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cur   = prefs.getLong(KEY_OPEN_COUNT, 0L)
        // Set join date on first open
        if (cur == 0L) {
            prefs.edit().putLong(KEY_JOIN_DATE, System.currentTimeMillis()).apply()
        }
        prefs.edit().putLong(KEY_OPEN_COUNT, cur + 1L).apply()
    }

    fun getOpenCount(ctx: Context): Long =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_OPEN_COUNT, 0L)

    /* ── Optimize counter ─────────────────────────────── */
    fun incrementOptimizeCount(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cur   = prefs.getLong(KEY_OPT_COUNT, 0L)
        prefs.edit().putLong(KEY_OPT_COUNT, cur + 1L).apply()
    }

    fun getOptimizeCount(ctx: Context): Long =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_OPT_COUNT, 0L)

    /* ── Online time tracking ─────────────────────────── */
    fun startSession(ctx: Context) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_SESSION_START, System.currentTimeMillis()).apply()
    }

    fun endSession(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val start = prefs.getLong(KEY_SESSION_START, 0L)
        if (start == 0L) return
        val elapsed = (System.currentTimeMillis() - start) / 1000L
        val total   = prefs.getLong(KEY_ONLINE_SEC, 0L)
        prefs.edit()
            .putLong(KEY_ONLINE_SEC, total + elapsed)
            .remove(KEY_SESSION_START)
            .apply()
    }

    fun getOnlineTimeFormatted(ctx: Context): String {
        val prefs   = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var seconds = prefs.getLong(KEY_ONLINE_SEC, 0L)
        // Add current session if active
        val start = prefs.getLong(KEY_SESSION_START, 0L)
        if (start > 0L) seconds += (System.currentTimeMillis() - start) / 1000L

        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return when {
            h > 0  -> "${h}h ${m}m"
            m > 0  -> "${m} phút"
            else   -> "${seconds}s"
        }
    }

    /* ── Join date ────────────────────────────────────── */
    fun getJoinDateFormatted(ctx: Context): String {
        val ms = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_JOIN_DATE, 0L)
        if (ms == 0L) return "—"
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date(ms))
    }
}
