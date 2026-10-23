package com.aujunpeak.app.utils

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

object KeyManager {

    private const val PREFS_NAME          = "aujunpeak_key_prefs"
    private const val KEY_ACTIVATION_KEY  = "activation_key"
    private const val KEY_EXPIRY_DATE     = "expiry_date_ms"
    private const val KEY_EXPIRY_LABEL    = "expiry_label"
    private const val KEY_LOGGED_IN       = "is_logged_in"
    private const val KEY_DEVICE_ID       = "device_id"
    private const val KEY_MAX_DEVICES     = "max_devices"
    private const val KEY_DEVICE_COUNT    = "device_count"
    private const val KEY_TIER            = "key_tier"   // "free" | "vip"
    private const val KEY_NOTE            = "key_note"
    private const val KEY_LOGIN_TIMESTAMP = "login_timestamp_ms"   // when key was first validated

    // ── Google Account (độc lập với key, có thể đăng nhập riêng) ──────────
    private const val KEY_GOOGLE_LOGGED_IN  = "google_logged_in"
    private const val KEY_GOOGLE_NAME       = "google_name"
    private const val KEY_GOOGLE_EMAIL      = "google_email"
    private const val KEY_GOOGLE_PHOTO_URL  = "google_photo_url"

    const val API_BASE_URL = "http://103.140.249.74:3000/api"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /* ── Device ID ─────────────────────────────────────────── */

    fun getOrCreateDeviceId(ctx: Context): String {
        // Ưu tiên ANDROID_ID — tồn tại qua cài lại app, chỉ đổi khi factory reset.
        val androidId = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ANDROID_ID
        )
        if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") {
            return androidId
        }
        val saved = prefs(ctx).getString(KEY_DEVICE_ID, null)
        if (!saved.isNullOrEmpty()) return saved
        val newId = UUID.randomUUID().toString()
        prefs(ctx).edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    fun getDeviceName(): String =
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

    fun getDeviceRamInfo(ctx: Context): String {
        return try {
            val am   = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val info = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val totalGb = info.totalMem / (1024.0 * 1024.0 * 1024.0)
            val availGb = info.availMem / (1024.0 * 1024.0 * 1024.0)
            val usedGb  = totalGb - availGb
            "%.1f/%.1fGB".format(usedGb, totalGb)
        } catch (_: Exception) { "--/--GB" }
    }

    /* ── Login state ──────────────────────────────────────── */

    fun isLoggedIn(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_LOGGED_IN, false)

    fun saveKey(
        ctx: Context,
        key: String,
        expiryMs: Long?,
        daysLeft: Int?,
        maxDevices: Int,
        deviceCount: Int,
        tier: String = "free",
        note: String = "",
    ) {
        val label = when {
            daysLeft != null && daysLeft > 0 -> "$daysLeft ngày"
            expiryMs == null                 -> "Vĩnh viễn"
            else                             -> "Hết hạn"
        }
        prefs(ctx).edit()
            .putString(KEY_ACTIVATION_KEY, key)
            .putLong(KEY_EXPIRY_DATE, expiryMs ?: 0L)
            .putString(KEY_EXPIRY_LABEL, label)
            .putBoolean(KEY_LOGGED_IN, true)
            .putInt(KEY_MAX_DEVICES, maxDevices)
            .putInt(KEY_DEVICE_COUNT, deviceCount)
            .putString(KEY_TIER, tier)
            .putString(KEY_NOTE, note)
            .apply()
    }

    fun logout(ctx: Context) {
        prefs(ctx).edit()
            .remove(KEY_ACTIVATION_KEY)
            .remove(KEY_EXPIRY_DATE)
            .remove(KEY_EXPIRY_LABEL)
            .remove(KEY_MAX_DEVICES)
            .remove(KEY_DEVICE_COUNT)
            .remove(KEY_TIER)
            .remove(KEY_NOTE)
            .putBoolean(KEY_LOGGED_IN, false)
            // Đăng xuất key đồng thời xóa Google account
            .putBoolean(KEY_GOOGLE_LOGGED_IN, false)
            .remove(KEY_GOOGLE_NAME)
            .remove(KEY_GOOGLE_EMAIL)
            .remove(KEY_GOOGLE_PHOTO_URL)
            .apply()
    }

    /* ── Google Account ──────────────────────────────────── */

    fun saveGoogleAccount(ctx: Context, name: String, email: String, photoUrl: String) {
        prefs(ctx).edit()
            .putBoolean(KEY_GOOGLE_LOGGED_IN, true)
            .putString(KEY_GOOGLE_NAME, name)
            .putString(KEY_GOOGLE_EMAIL, email)
            .putString(KEY_GOOGLE_PHOTO_URL, photoUrl)
            .apply()
    }

    fun clearGoogleAccount(ctx: Context) {
        prefs(ctx).edit()
            .putBoolean(KEY_GOOGLE_LOGGED_IN, false)
            .remove(KEY_GOOGLE_NAME)
            .remove(KEY_GOOGLE_EMAIL)
            .remove(KEY_GOOGLE_PHOTO_URL)
            .apply()
    }

    fun isGoogleLoggedIn(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_GOOGLE_LOGGED_IN, false)

    fun getGoogleName(ctx: Context): String  = prefs(ctx).getString(KEY_GOOGLE_NAME, "") ?: ""
    fun getGoogleEmail(ctx: Context): String = prefs(ctx).getString(KEY_GOOGLE_EMAIL, "") ?: ""
    fun getGooglePhotoUrl(ctx: Context): String = prefs(ctx).getString(KEY_GOOGLE_PHOTO_URL, "") ?: ""

    suspend fun sendGoogleLoginNotify(ctx: Context, name: String, email: String) =
        withContext(Dispatchers.IO) {
            try {
                val deviceId   = getOrCreateDeviceId(ctx)
                val deviceName = getDeviceName()
                val body = JSONObject().apply {
                    put("deviceId",    deviceId)
                    put("deviceName",  deviceName)
                    put("googleEmail", email)
                    put("googleName",  name)
                }
                postJson("$API_BASE_URL/keys/google-login-notify", body)
            } catch (_: Exception) { }
        }

    /* ── Key info getters ─────────────────────────────────── */

    fun getKey(ctx: Context): String  = prefs(ctx).getString(KEY_ACTIVATION_KEY, "") ?: ""
    fun getExpiryLabel(ctx: Context): String = prefs(ctx).getString(KEY_EXPIRY_LABEL, "—") ?: "—"
    fun getTier(ctx: Context): String = prefs(ctx).getString(KEY_TIER, "free") ?: "free"
    fun getNote(ctx: Context): String = prefs(ctx).getString(KEY_NOTE, "") ?: ""
    fun isVip(ctx: Context): Boolean  = getTier(ctx) == "vip"

    fun getExpiryDateString(ctx: Context): String {
        val ms = prefs(ctx).getLong(KEY_EXPIRY_DATE, 0L)
        if (ms == 0L) return "Không giới hạn"
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(ms))
    }

    fun isKeyExpired(ctx: Context): Boolean {
        val ms = prefs(ctx).getLong(KEY_EXPIRY_DATE, 0L)
        return ms != 0L && System.currentTimeMillis() > ms
    }

    fun getStatusText(ctx: Context): String = when {
        !isLoggedIn(ctx)  -> "Chưa kích hoạt"
        isKeyExpired(ctx) -> "⛔  Đã hết hạn"
        else              -> "✅  Đang hoạt động"
    }

    fun getMaxDevices(ctx: Context): Int  = prefs(ctx).getInt(KEY_MAX_DEVICES, 1)
    fun getDeviceCount(ctx: Context): Int = prefs(ctx).getInt(KEY_DEVICE_COUNT, 1)

    fun getExpiryMs(ctx: Context): Long =
        prefs(ctx).getLong(KEY_EXPIRY_DATE, 0L)

    fun getLoginTimestampMs(ctx: Context): Long =
        prefs(ctx).getLong(KEY_LOGIN_TIMESTAMP, 0L)

    fun getExpiryWarningDaysLeft(ctx: Context): Int? {
        if (!isLoggedIn(ctx)) return null
        val expiryMs = prefs(ctx).getLong(KEY_EXPIRY_DATE, 0L)
        if (expiryMs == 0L) return null
        val daysLeft = ((expiryMs - System.currentTimeMillis()) / 86_400_000L).toInt()
        return if (daysLeft in 0..3) daysLeft else null
    }

    /* ── Network helpers ──────────────────────────────────── */

    private fun parseNotificationsSafe(resp: JSONObject): List<AppNotification> {
        val arr = resp.optJSONArray("notifications") ?: return emptyList()
        val result = mutableListOf<AppNotification>()
        for (i in 0 until arr.length()) {
            try {
                val n = arr.optJSONObject(i) ?: continue
                val id    = n.optInt("id", -1).takeIf { it >= 0 } ?: continue
                val title = n.optString("title", "").takeIf { it.isNotEmpty() } ?: continue
                val body  = n.optString("body", "")
                result.add(AppNotification(id, title, body))
            } catch (_: Exception) { }
        }
        return result
    }

    private fun parseActiveIdsSafe(resp: JSONObject): Set<Int>? {
        val arr = resp.optJSONArray("activeNotificationIds") ?: return null
        val result = mutableSetOf<Int>()
        for (i in 0 until arr.length()) {
            val id = arr.optInt(i, -1)
            if (id >= 0) result.add(id)
        }
        return result
    }

    private fun postJson(urlStr: String, body: JSONObject): JSONObject {
        val url  = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 10_000
        conn.readTimeout    = 10_000
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code   = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text   = stream?.bufferedReader()?.readText() ?: "{}"
        return JSONObject(text)
    }

    private fun getJson(urlStr: String): JSONObject {
        val url  = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8_000
        conn.readTimeout    = 8_000
        val code   = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text   = stream?.bufferedReader()?.readText() ?: "{}"
        return JSONObject(text)
    }

    /* ── Force Update Check ───────────────────────────────── */

    /**
     * Kết quả kiểm tra phiên bản từ server.
     * [forceUpdate] = true nghĩa là app hiện tại bị block, người dùng bắt buộc cập nhật.
     * [minVersionCode] = phiên bản tối thiểu server yêu cầu.
     * [downloadUrl] = link tải APK mới.
     */
    data class VersionCheckResult(
        val forceUpdate: Boolean,
        val minVersionCode: Int,
        val downloadUrl: String,
    )

    /**
     * Gọi API GET /api/app/version-check để kiểm tra xem có force update không.
     * Nếu server lỗi / không kết nối được → trả về [forceUpdate=false] (không block user).
     */
    suspend fun checkForceUpdate(): VersionCheckResult =
        withContext(Dispatchers.IO) {
            try {
                val resp = getJson("$API_BASE_URL/app/version-check")
                VersionCheckResult(
                    forceUpdate    = resp.optBoolean("forceUpdate", false),
                    minVersionCode = resp.optInt("minVersionCode", 0),
                    downloadUrl    = resp.optString("downloadUrl", ""),
                )
            } catch (_: Exception) {
                // Nếu không kết nối được server → không block user
                VersionCheckResult(forceUpdate = false, minVersionCode = 0, downloadUrl = "")
            }
        }

    /* ── API Validation ───────────────────────────────────── */

    suspend fun validateKeyOnline(ctx: Context, key: String): ValidationResult =
        withContext(Dispatchers.IO) {
            try {
                val deviceId   = getOrCreateDeviceId(ctx)
                val deviceName = getDeviceName()
                val deviceOs   = "Android ${android.os.Build.VERSION.RELEASE}"
                val deviceSdk  = android.os.Build.VERSION.SDK_INT
                val deviceRam  = getDeviceRamInfo(ctx)
                val body = JSONObject().apply {
                    put("key", key)
                    put("deviceId", deviceId)
                    put("deviceName", deviceName)
                    put("deviceOs", deviceOs)
                    put("deviceSdk", deviceSdk)
                    put("deviceRam", deviceRam)
                }
                val resp = postJson("$API_BASE_URL/keys/validate", body)
                if (resp.optBoolean("ok", false)) {
                    val expiresAtStr = resp.optString("expiresAt", "")
                    val expiryMs: Long? = if (expiresAtStr.isNullOrEmpty() || expiresAtStr == "null") {
                        null
                    } else {
                        try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                            sdf.timeZone = TimeZone.getTimeZone("UTC")
                            sdf.parse(expiresAtStr)?.time
                        } catch (_: Exception) { null }
                    }
                    val daysLeft   = if (resp.isNull("daysLeft")) null else resp.optInt("daysLeft")
                    val maxDevices = resp.optInt("maxDevices", 1)
                    val devCount   = resp.optInt("deviceCount", 1)
                    val tier       = resp.optString("tier", "free")
                    val note       = resp.optString("note", "")
                    saveKey(ctx, key, expiryMs, daysLeft, maxDevices, devCount, tier, note)
                    val ipInfo = fetchIpInfo()
                    LoginHistory.addEntry(ctx, deviceName, ipInfo.first, ipInfo.second)
                    prefs(ctx).edit().putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis()).apply()
                    ValidationResult.Ok(key)
                } else {
                    val msg = resp.optString("message", "Key không hợp lệ")
                    ValidationResult.Fail(msg)
                }
            } catch (e: Exception) {
                ValidationResult.Fail("Không thể kết nối server. Kiểm tra mạng và thử lại.")
            }
        }

    private fun fetchIpInfo(): Pair<String, String> = try {
        val url  = java.net.URL("https://ipapi.co/json/")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 4_000; conn.readTimeout = 4_000
        val text = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(text)
        val ip      = json.optString("ip", "")
        val country = json.optString("country_name", "")
        Pair(ip, country)
    } catch (_: Exception) { Pair("", "") }

    /* ── Heartbeat ────────────────────────────────────────── */

    data class HeartbeatResult(
        val ok: Boolean,
        val revoked: Boolean = false,
        val expired: Boolean = false,
        val newTier: String? = null,
        val newNote: String? = null,
        val notifications: List<AppNotification> = emptyList(),
        val activeNotificationIds: Set<Int>? = null,
        val newMaxDevices: Int? = null,
        val newDeviceCount: Int? = null,
    )

    data class AppNotification(val id: Int, val title: String, val body: String)

    suspend fun sendHeartbeat(ctx: Context): HeartbeatResult =
        withContext(Dispatchers.IO) {
            try {
                val key        = getKey(ctx)
                val deviceId   = getOrCreateDeviceId(ctx)
                if (key.isEmpty()) return@withContext HeartbeatResult(ok = false)

                val deviceName = getDeviceName()
                val deviceOs   = "Android ${android.os.Build.VERSION.RELEASE}"
                val deviceSdk  = android.os.Build.VERSION.SDK_INT
                val deviceRam  = getDeviceRamInfo(ctx)
                val body = JSONObject().apply {
                    put("key", key)
                    put("deviceId", deviceId)
                    put("deviceName", deviceName)
                    put("deviceOs", deviceOs)
                    put("deviceSdk", deviceSdk)
                    put("deviceRam", deviceRam)
                }
                val resp     = postJson("$API_BASE_URL/keys/heartbeat", body)
                val ok       = resp.optBoolean("ok", false)
                val revoked  = resp.optBoolean("revoked", false)
                val expired  = resp.optBoolean("expired", false)

                if (!ok) {
                    val notificationsFail = parseNotificationsSafe(resp)
                    if (revoked || expired) logout(ctx)
                    return@withContext HeartbeatResult(
                        ok = false,
                        revoked = revoked,
                        expired = expired,
                        notifications = notificationsFail
                    )
                }

                val newTier = resp.optString("tier", "").takeIf { it.isNotEmpty() }
                val newNote = resp.optString("note", "").takeIf { it.isNotEmpty() }
                if (newTier != null && newTier != getTier(ctx)) {
                    prefs(ctx).edit().putString(KEY_TIER, newTier).apply()
                }
                if (newNote != null && newNote != getNote(ctx)) {
                    prefs(ctx).edit().putString(KEY_NOTE, newNote).apply()
                }

                val newMaxDevices = resp.optInt("maxDevices", -1).takeIf { it >= 0 }
                val newDeviceCount = resp.optInt("deviceCount", -1).takeIf { it >= 0 }
                if (newMaxDevices != null && newMaxDevices != getMaxDevices(ctx)) {
                    prefs(ctx).edit().putInt(KEY_MAX_DEVICES, newMaxDevices).apply()
                }
                if (newDeviceCount != null && newDeviceCount != getDeviceCount(ctx)) {
                    prefs(ctx).edit().putInt(KEY_DEVICE_COUNT, newDeviceCount).apply()
                }

                val notifications = parseNotificationsSafe(resp)
                val activeIds = parseActiveIdsSafe(resp)

                if (activeIds != null) {
                    NotificationStore.pruneMissing(ctx, activeIds)
                }

                HeartbeatResult(
                    ok = true,
                    newTier = newTier,
                    newNote = newNote,
                    notifications = notifications,
                    activeNotificationIds = activeIds,
                    newMaxDevices = newMaxDevices,
                    newDeviceCount = newDeviceCount,
                )
            } catch (_: Exception) {
                HeartbeatResult(ok = false)
            }
        }

    /* ── Mark notifications read ──────────────────────────── */

    suspend fun markNotificationsRead(ctx: Context, ids: List<Int>) {
        if (ids.isEmpty()) return
        withContext(Dispatchers.IO) {
            try {
                val deviceId = getOrCreateDeviceId(ctx)
                val arr = JSONArray().apply { ids.forEach { put(it) } }
                val body = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("ids", arr)
                }
                postJson("$API_BASE_URL/keys/notifications/read", body)
            } catch (_: Exception) {}
        }
    }

    /* ── Logout ───────────────────────────────────────────── */

    suspend fun sendLogout(ctx: Context) =
        withContext(Dispatchers.IO) {
            try {
                val key      = getKey(ctx)
                val deviceId = getOrCreateDeviceId(ctx)
                if (key.isEmpty()) return@withContext
                val body = JSONObject().apply {
                    put("key", key)
                    put("deviceId", deviceId)
                }
                postJson("$API_BASE_URL/keys/logout", body)
            } catch (_: Exception) {}
        }


    /* ── GET KEY FREE ─────────────────────────────────────── */

    sealed class FreeLinkResult {
        data class Ok(val link4mUrl: String) : FreeLinkResult()
        data class Fail(val message: String) : FreeLinkResult()
    }

    suspend fun generateFreeLink(ctx: Context): FreeLinkResult =
        withContext(Dispatchers.IO) {
            try {
                val deviceId   = getOrCreateDeviceId(ctx)
                val deviceName = getDeviceName()
                val body = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("deviceName", deviceName)
                }
                val resp = postJson("$API_BASE_URL/keys/generate-free-link", body)
                val url  = resp.optString("link4mUrl", "")
                if (url.isNotEmpty()) FreeLinkResult.Ok(url)
                else FreeLinkResult.Fail("Không lấy được link. Thử lại sau.")
            } catch (e: Exception) {
                FreeLinkResult.Fail("Lỗi kết nối: ${e.message}")
            }
        }

    sealed class ValidationResult {
        data class Ok(val key: String) : ValidationResult()
        data class Fail(val message: String) : ValidationResult()
    }
}
