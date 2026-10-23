package com.aujunpeak.app.utils

import android.content.Context

/** Lưu nickname, custom avatar, account ID của người dùng */
object UserProfile {

    private const val PREFS_NAME      = "aujunpeak_key_prefs"
    private const val KEY_NICKNAME    = "profile_nickname"
    private const val KEY_AVATAR_URI  = "profile_avatar_uri"   // URI string of custom photo
    private const val KEY_ACCOUNT_ID  = "profile_account_id"

    /* ── Nickname ─────────────────────────────────────── */
    fun saveNickname(ctx: Context, name: String) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_NICKNAME, name.trim()).apply()
    }

    fun getNickname(ctx: Context): String =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_NICKNAME, "") ?: ""

    /** Trả về display name: nickname → Google name → "Người dùng" */
    fun getDisplayName(ctx: Context): String {
        val nick = getNickname(ctx)
        if (nick.isNotEmpty()) return nick
        val google = KeyManager.getGoogleName(ctx)
        if (google.isNotEmpty()) return google
        return "Người dùng"
    }

    /* ── Custom avatar URI ────────────────────────────── */
    fun saveAvatarUri(ctx: Context, uri: String) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AVATAR_URI, uri).apply()
    }

    fun getAvatarUri(ctx: Context): String =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AVATAR_URI, "") ?: ""

    fun clearAvatar(ctx: Context) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_AVATAR_URI).apply()
    }

    /* ── Account ID (UUID per-install, permanent) ─────── */
    fun getOrCreateAccountId(ctx: Context): String {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_ACCOUNT_ID, null)
        if (!saved.isNullOrEmpty()) return saved
        val id = "AJP-" + java.util.UUID.randomUUID().toString()
            .uppercase().replace("-", "").take(12)
        prefs.edit().putString(KEY_ACCOUNT_ID, id).apply()
        return id
    }
}
