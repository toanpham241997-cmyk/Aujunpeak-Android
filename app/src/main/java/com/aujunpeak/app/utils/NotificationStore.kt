package com.aujunpeak.app.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lưu trữ lịch sử thông báo cục bộ (SharedPreferences / JSON).
 * Tối đa 50 thông báo gần nhất, mới nhất ở đầu danh sách.
 */
object NotificationStore {

    data class StoredNotification(
        val id: Int,
        val title: String,
        val body: String,
        val timestamp: Long,
        val isRead: Boolean = false,
    )

    private const val PREF    = "notif_store_v1"
    private const val KEY_LIST = "stored_list"
    private const val MAX_ITEMS = 50

    /* ── Lưu danh sách thông báo mới (bỏ qua id đã có) ── */
    fun save(ctx: Context, notifs: List<KeyManager.AppNotification>) {
        if (notifs.isEmpty()) return
        val existing    = getAll(ctx).toMutableList()
        val existingIds = existing.map { it.id }.toMutableSet()
        val now         = System.currentTimeMillis()
        // Dedup cả trong batch lẫn với danh sách đã lưu (tránh server gửi trùng)
        notifs.filter { it.id !in existingIds }.forEach { n ->
            existing.add(0, StoredNotification(n.id, n.title, n.body, now, isRead = false))
            existingIds.add(n.id)
        }
        persist(ctx, existing.take(MAX_ITEMS))
    }

    /* ── Lấy tất cả, mới nhất trước ── */
    fun getAll(ctx: Context): List<StoredNotification> {
        val json = prefs(ctx).getString(KEY_LIST, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val o = arr.getJSONObject(i)
                    val id = o.optInt("id", -1).takeIf { it >= 0 } ?: return@mapNotNull null
                    StoredNotification(
                        id        = id,
                        title     = o.optString("title", ""),
                        body      = o.optString("body", ""),
                        timestamp = o.optLong("ts", 0L),
                        isRead    = o.optBoolean("read", false),
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    /* ── Đánh dấu tất cả đã đọc ── */
    fun markAllRead(ctx: Context) {
        persist(ctx, getAll(ctx).map { it.copy(isRead = true) })
    }

    /* ── Số thông báo chưa đọc ── */
    fun getUnreadCount(ctx: Context): Int = getAll(ctx).count { !it.isRead }

    /* ── Xoá toàn bộ ── */
    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_LIST).apply()
    }

    /**
     * Dọn dẹp những thông báo đã bị admin/bot xoá khỏi server (vd: /xoathongbao,
     * /xoatatthongbao, hoặc DELETE /admin/notifications). Server trả về danh sách
     * id còn tồn tại (`activeIds`) mỗi lần heartbeat; bất kỳ mục cục bộ nào không
     * còn nằm trong danh sách đó sẽ bị loại bỏ khỏi lịch sử hiển thị trên máy.
     */
    fun pruneMissing(ctx: Context, activeIds: Set<Int>) {
        val current = getAll(ctx)
        if (current.isEmpty()) return
        val kept = current.filter { it.id in activeIds }
        if (kept.size != current.size) {
            persist(ctx, kept)
        }
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private fun persist(ctx: Context, list: List<StoredNotification>) {
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(JSONObject().apply {
                put("id",    n.id)
                put("title", n.title)
                put("body",  n.body)
                put("ts",    n.timestamp)
                put("read",  n.isRead)
            })
        }
        prefs(ctx).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
