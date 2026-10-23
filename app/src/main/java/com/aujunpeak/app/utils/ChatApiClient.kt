package com.aujunpeak.app.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Kết nối tới API chat của Aujunpeak */
object ChatApiClient {

    private val BASE = KeyManager.API_BASE_URL  // "https://aujunpeak-api.onrender.com/api"

    data class ChatSession(
        val id: Int,
        val deviceId: String,
        val email: String,
        val displayName: String,
        val status: String,           // "pending" | "accepted" | "closed"
        val adminAvatar: String,
        val adminName: String,
        val adminOnline: Boolean,
    )

    data class ChatMessage(
        val id: Int,
        val sessionId: Int,
        val sender: String,   // "user" | "bot" | "admin"
        val content: String,
        val type: String,     // "text" | "image" | "emoji"
        val imageData: String?,
        val createdAt: String,
    )

    data class SessionResult(val session: ChatSession?, val error: String? = null)
    data class MessageResult(val messages: List<ChatMessage>, val session: ChatSession?, val error: String? = null)
    data class SendResult(val success: Boolean, val error: String? = null)

    // ── Tạo / lấy session ────────────────────────────────────────────────
    suspend fun getOrCreateSession(
        deviceId: String,
        email: String,
        displayName: String,
    ): SessionResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("deviceId", deviceId)
                put("email", email)
                put("displayName", displayName)
            }.toString()
            val response = post("$BASE/chat/session", body)
            if (response.has("session") && !response.isNull("session")) {
                return@withContext SessionResult(parseSession(response.getJSONObject("session")))
            }
            SessionResult(null, response.optString("error", "Lỗi không xác định"))
        } catch (e: Exception) {
            SessionResult(null, e.message)
        }
    }

    // ── Lấy trạng thái session ────────────────────────────────────────────
    suspend fun getSession(deviceId: String): SessionResult = withContext(Dispatchers.IO) {
        try {
            val response = get("$BASE/chat/session?deviceId=${encode(deviceId)}")
            if (response.has("session") && !response.isNull("session")) {
                return@withContext SessionResult(parseSession(response.getJSONObject("session")))
            }
            SessionResult(null)
        } catch (e: Exception) {
            SessionResult(null, e.message)
        }
    }

    // ── Gửi tin nhắn ─────────────────────────────────────────────────────
    suspend fun sendMessage(
        sessionId: Int,
        deviceId: String,
        content: String,
        type: String = "text",
        imageData: String? = null,
    ): SendResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("sessionId", sessionId)
                put("deviceId", deviceId)
                put("content", content)
                put("type", type)
                if (imageData != null) put("imageData", imageData)
            }.toString()
            val response = post("$BASE/chat/message", body)
            if (response.has("message")) SendResult(true)
            else SendResult(false, response.optString("error", "Lỗi không xác định"))
        } catch (e: Exception) {
            SendResult(false, e.message)
        }
    }

    // ── Poll tin nhắn mới ─────────────────────────────────────────────────
    suspend fun getMessages(
        sessionId: Int,
        deviceId: String,
        afterId: Int? = null,
    ): MessageResult = withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append("$BASE/chat/messages?sessionId=$sessionId&deviceId=${encode(deviceId)}")
                if (afterId != null) append("&after=$afterId")
            }
            val response = get(url)
            val msgs = mutableListOf<ChatMessage>()
            val arr = response.optJSONArray("messages") ?: JSONArray()
            for (i in 0 until arr.length()) {
                msgs.add(parseMessage(arr.getJSONObject(i)))
            }
            val session = if (response.has("session") && !response.isNull("session"))
                parseSession(response.getJSONObject("session")) else null
            MessageResult(msgs, session)
        } catch (e: Exception) {
            MessageResult(emptyList(), null, e.message)
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────
    private fun parseSession(obj: JSONObject) = ChatSession(
        id          = obj.getInt("id"),
        deviceId    = obj.optString("deviceId", obj.optString("device_id", "")),
        email       = obj.optString("email", ""),
        displayName = obj.optString("displayName", obj.optString("display_name", "")),
        status      = obj.optString("status", "pending"),
        adminAvatar = obj.optString("adminAvatar", obj.optString("admin_avatar", "")),
        adminName   = obj.optString("adminName", obj.optString("admin_name", "")),
        adminOnline = obj.optBoolean("adminOnline", obj.optBoolean("admin_online", false)),
    )

    private fun parseMessage(obj: JSONObject) = ChatMessage(
        id        = obj.getInt("id"),
        sessionId = obj.optInt("sessionId", obj.optInt("session_id", 0)),
        sender    = obj.optString("sender", "bot"),
        content   = obj.optString("content", ""),
        type      = obj.optString("type", "text"),
        imageData = if (obj.has("imageData") && !obj.isNull("imageData")) obj.getString("imageData")
                    else if (obj.has("image_data") && !obj.isNull("image_data")) obj.getString("image_data")
                    else null,
        createdAt = obj.optString("createdAt", obj.optString("created_at", "")),
    )

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun post(urlStr: String, body: String): JSONObject {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.outputStream.use { out: OutputStream -> out.write(body.toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.readText() ?: "{}"
        conn.disconnect()
        return JSONObject(text)
    }

    private fun get(urlStr: String): JSONObject {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.readText() ?: "{}"
        conn.disconnect()
        return JSONObject(text)
    }
}
