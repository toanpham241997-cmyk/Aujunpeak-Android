package com.aujunpeak.app.activities

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aujunpeak.app.R
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dịch vụ FCM — xử lý thông báo đẩy từ server ngay cả khi app đóng.
 *
 * Cấu hình cần thiết:
 *  1. Thêm google-services.json vào thư mục app/
 *  2. Plugin "com.google.gms.google-services" đã được bật trong build.gradle.kts
 *  3. Biến môi trường FIREBASE_SERVICE_ACCOUNT_JSON trên API server
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID   = "aujunpeak_push"
        const val CHANNEL_NAME = "Thông báo Aujunpeak"
        private const val API_BASE = "https://aujunpeak-api.onrender.com"

        // Tên SharedPreferences và key phải khớp hoàn toàn với KeyManager
        private const val KEY_PREFS_NAME     = "aujunpeak_key_prefs"
        private const val KEY_ACTIVATION_KEY = "activation_key"
        private const val KEY_DEVICE_ID      = "device_id"

        /** Tạo notification channel — gọi sớm trong SplashActivity/MainActivity */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Thông báo từ Aujunpeak App"
                    enableVibration(true)
                    enableLights(true)
                }
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(ch)
            }
        }

        /**
         * Lấy token FCM hiện tại và đăng ký lên server.
         * Gọi hàm này sau khi user đăng nhập thành công để đảm bảo
         * server có token kết hợp với deviceId + deviceKey đúng.
         */
        fun refreshAndRegisterToken(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val token = FirebaseMessaging.getInstance().token.await()
                    if (token.isNotEmpty()) {
                        // Cập nhật token lưu local
                        context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                            .edit().putString("fcm_token", token).apply()
                        // Gửi lên server
                        registerTokenToServer(context, token)
                    }
                } catch (_: Exception) {
                    // Firebase chưa khởi tạo hoặc không có mạng — bỏ qua
                }
            }
        }

        /**
         * Gửi FCM token lên API server.
         * ⚠ Đọc SharedPreferences đúng tên "aujunpeak_key_prefs" (khớp với KeyManager)
         *   và đúng key "activation_key" (khớp với KEY_ACTIVATION_KEY trong KeyManager)
         */
        internal fun registerTokenToServer(context: Context, token: String) {
            try {
                val prefs     = context.getSharedPreferences(KEY_PREFS_NAME, Context.MODE_PRIVATE)
                val deviceKey = prefs.getString(KEY_ACTIVATION_KEY, "") ?: ""
                val deviceId  = prefs.getString(KEY_DEVICE_ID, "")      ?: ""

                val url  = URL("$API_BASE/api/devices/register-fcm")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 10_000
                conn.readTimeout    = 10_000

                val body = JSONObject().apply {
                    put("fcmToken", token)
                    put("deviceKey", deviceKey)
                    put("deviceId", deviceId)
                }.toString()

                OutputStreamWriter(conn.outputStream).use { it.write(body) }
                val code = conn.responseCode   // trigger send
                conn.disconnect()

                // Nếu server trả về 2xx, lưu cờ đã đăng ký thành công
                if (code in 200..299) {
                    context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("fcm_registered", true).apply()
                }
            } catch (_: Exception) {
                // Không có internet hoặc server chưa sẵn sàng — sẽ retry khi token mới / khi login lại
            }
        }
    }

    // ── Nhận token FCM mới → gửi lên server ──────────────────────────────────
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Lưu token local
        getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .putBoolean("fcm_registered", false)
            .apply()
        // Gửi lên API (non-blocking)
        CoroutineScope(Dispatchers.IO).launch {
            registerTokenToServer(applicationContext, token)
        }
    }

    // ── Nhận tin nhắn FCM ─────────────────────────────────────────────────────
    override fun onMessageReceived(msg: RemoteMessage) {
        super.onMessageReceived(msg)

        val title = msg.notification?.title
            ?: msg.data["title"]
            ?: "Aujunpeak"
        val body = msg.notification?.body
            ?: msg.data["body"]
            ?: ""

        showNotification(title, body)
    }

    // ── Hiển thị notification ─────────────────────────────────────────────────
    private fun showNotification(title: String, body: String) {
        val intent = Intent(this, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 100, 250))
            .build()

        val nm = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) {
            nm.notify(System.currentTimeMillis().toInt(), notif)
        }
    }
}
