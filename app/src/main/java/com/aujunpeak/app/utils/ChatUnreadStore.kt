package com.aujunpeak.app.utils

import android.content.Context

/**
 * Theo dõi tin nhắn chat chưa đọc từ admin/bot khi khung chat đang đóng.
 * Lưu id tin nhắn cuối cùng người dùng đã xem (lastSeenId) vào SharedPreferences.
 * MainActivity poll định kỳ [checkUnreadCount] để cập nhật số badge đỏ cạnh icon chat.
 */
object ChatUnreadStore {

    private const val PREF         = "chat_unread_store_v1"
    private const val KEY_LAST_SEEN = "last_seen_msg_id"

    fun getLastSeenId(ctx: Context): Int = prefs(ctx).getInt(KEY_LAST_SEEN, 0)

    /** Đánh dấu đã xem đến messageId này (chỉ tăng, không lùi lại). */
    fun markSeen(ctx: Context, messageId: Int) {
        if (messageId > getLastSeenId(ctx)) {
            prefs(ctx).edit().putInt(KEY_LAST_SEEN, messageId).apply()
        }
    }

    /**
     * Kiểm tra server có tin nhắn mới từ admin/bot mà người dùng chưa xem không.
     * Trả về số lượng tin nhắn chưa đọc (0 nếu không có session hoặc session đã đóng).
     */
    suspend fun checkUnreadCount(ctx: Context, deviceId: String): Int {
        val sessionResult = ChatApiClient.getSession(deviceId)
        val session = sessionResult.session ?: return 0
        if (session.status == "closed") return 0

        val lastSeen = getLastSeenId(ctx)
        val result = ChatApiClient.getMessages(session.id, deviceId, afterId = lastSeen)
        return result.messages.count { it.sender != "user" }
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
