package com.aujunpeak.app.dynamicui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aujunpeak.app.activities.AboutActivity
import com.aujunpeak.app.activities.CrosshairActivity
import com.aujunpeak.app.activities.FeedbackActivity
import com.aujunpeak.app.activities.FloatingBoostService
import com.aujunpeak.app.activities.FloatingCrosshairService
import com.aujunpeak.app.activities.GameBoostActivity
import com.aujunpeak.app.activities.OptimizerSuperActivity
import com.aujunpeak.app.activities.PolicyActivity
import com.aujunpeak.app.activities.SocialLinksActivity
import com.aujunpeak.app.activities.UpdateActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.aujunpeak.app.utils.UiModal
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Safe, declarative action engine used by server-driven UI.
 * It intentionally does NOT download/execute dex/jar/so or arbitrary native code.
 */
object DynamicActionEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun execute(activity: Activity, root: View, raw: Any?) {
        when (raw) {
            is JSONObject -> executeObject(activity, root, raw)
            is JSONArray -> for (i in 0 until raw.length()) execute(activity, root, raw.opt(i))
        }
    }

    private fun executeObject(activity: Activity, root: View, action: JSONObject) {
        when (action.optString("type").trim().uppercase()) {
            "", "NONE" -> Unit
            "SEQUENCE" -> execute(activity, root, action.optJSONArray("actions"))
            "DELAY" -> {
                val ms = action.optLong("ms", 250L).coerceIn(0L, 60_000L)
                val next = action.opt("action")
                mainHandler.postDelayed({ execute(activity, root, next) }, ms)
            }
            "TOAST" -> UiModal.show(activity, action.optString("message"), title = action.optString("title", "Thông báo"), type = UiModal.Type.INFO, autoDismissMillis = 1500L)
            "DIALOG" -> AlertDialog.Builder(activity)
                .setTitle(action.optString("title", "Aujunpeak"))
                .setMessage(action.optString("message"))
                .setPositiveButton(action.optString("positive", "OK"), null)
                .show()
            "VFX_MODAL", "MODAL" -> DynamicVfxModal.show(activity, action)
            "OPEN_URL" -> openUrl(activity, action.optString("url"))
            "OPEN_APP" -> openApp(activity, action.optString("package"))
            "SHARE_TEXT" -> share(activity, action.optString("text"), action.optString("title", "Share"))
            "COPY_TEXT" -> copy(activity, action.optString("text"), action.optString("label", "Aujunpeak"))
            "INTERNAL_ACTIVITY" -> openInternalActivity(activity, action.optString("screen"))
            "CLICK_VIEW" -> find(root, action.optString("viewId"))?.performClick()
            "SET_TEXT" -> (find(root, action.optString("viewId")) as? TextView)?.text = action.optString("text")
            "SET_VISIBILITY" -> find(root, action.optString("viewId"))?.visibility = when (action.optString("value").lowercase()) {
                "gone" -> View.GONE
                "invisible" -> View.INVISIBLE
                else -> View.VISIBLE
            }
            "SET_ENABLED" -> find(root, action.optString("viewId"))?.isEnabled = action.optBoolean("value", true)
            "SET_CHECKED" -> (find(root, action.optString("viewId")) as? CompoundButton)?.isChecked = action.optBoolean("value", false)
            "SET_PREF" -> setPref(activity, action)
            "REMOVE_PREF" -> prefs(activity, action).edit().remove(action.optString("key")).apply()
            "TOGGLE_PREF" -> {
                val p = prefs(activity, action)
                val key = action.optString("key")
                p.edit().putBoolean(key, !p.getBoolean(key, action.optBoolean("default", false))).apply()
            }
            "REFRESH_DATA" -> scope.launch {
                DynamicUiManager.refreshIfNeeded(activity, force = true)
                DynamicUiRuntime.reapplyAll()
            }
            "RELOAD_UI" -> DynamicUiRuntime.reapplyAll()
            "APP_SETTINGS" -> activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
            "OVERLAY_SETTINGS" -> activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}")))
            "HTTP_REQUEST" -> executeHttp(activity, root, action)
            "START_FLOATING_BOOST" -> {
                if (!Settings.canDrawOverlays(activity)) {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}")))
                } else {
                    val i = Intent(activity, FloatingBoostService::class.java).apply { this.action = FloatingBoostService.ACTION_START_BOOST }
                    activity.startService(i)
                }
            }
            "STOP_FLOATING_BOOST" -> activity.stopService(Intent(activity, FloatingBoostService::class.java))
            "START_CROSSHAIR" -> {
                if (!Settings.canDrawOverlays(activity)) {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}")))
                } else {
                    val i = Intent(activity, FloatingCrosshairService::class.java).apply {
                        putExtra(FloatingCrosshairService.EXTRA_TYPE, action.optString("crosshairType", "DOT"))
                        putExtra(FloatingCrosshairService.EXTRA_SIZE, action.optDouble("size", 40.0).toFloat())
                        putExtra(FloatingCrosshairService.EXTRA_OFFSET_X, action.optDouble("offsetX", 0.0).toFloat())
                        putExtra(FloatingCrosshairService.EXTRA_OFFSET_Y, action.optDouble("offsetY", 0.0).toFloat())
                        putExtra(FloatingCrosshairService.EXTRA_ROTATION_SPEED, action.optDouble("rotationSpeed", 0.0).toFloat())
                        putExtra(FloatingCrosshairService.EXTRA_PULSE, action.optDouble("pulse", 35.0).toFloat())
                    }
                    activity.startService(i)
                }
            }
            "STOP_CROSSHAIR" -> activity.stopService(Intent(activity, FloatingCrosshairService::class.java))
            "CLOSE_ACTIVITY" -> activity.finish()
        }
    }

    private fun executeHttp(activity: Activity, root: View, action: JSONObject) {
        val url = action.optString("url").trim()
        if (!isHttpUrl(url)) return
        val method = action.optString("method", "GET").uppercase().takeIf { it in setOf("GET", "POST", "PUT", "PATCH", "DELETE") } ?: "GET"
        val body = action.optString("body", "")
        val headers = action.optJSONObject("headers") ?: JSONObject()
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { httpRequest(url, method, body, headers) }
            }
            if (result.isSuccess) {
                if (action.optBoolean("toastResponse", false)) {
                    UiModal.show(activity, result.getOrNull().orEmpty().take(180), title = "Phản hồi", type = UiModal.Type.INFO, autoDismissMillis = 1700L)
                }
                execute(activity, root, action.opt("onSuccess"))
            } else {
                execute(activity, root, action.opt("onError"))
            }
        }
    }

    private fun httpRequest(url: String, method: String, body: String, headers: JSONObject): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 6000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json,text/plain,*/*;q=0.5")
            for (key in headers.keys()) setRequestProperty(key, headers.optString(key))
            if (method != "GET" && body.isNotEmpty()) {
                doOutput = true
                setRequestProperty("Content-Type", actionContentType(headers))
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        try {
            val code = conn.responseCode
            val input = if (code in 200..299) conn.inputStream else conn.errorStream
            val out = ByteArrayOutputStream()
            input?.use { stream ->
                val buffer = ByteArray(4096)
                var total = 0
                while (true) {
                    val n = stream.read(buffer)
                    if (n <= 0) break
                    total += n
                    if (total > 256 * 1024) break
                    out.write(buffer, 0, n)
                }
            }
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return out.toString(Charsets.UTF_8.name())
        } finally {
            conn.disconnect()
        }
    }

    private fun actionContentType(headers: JSONObject): String =
        headers.optString("Content-Type").takeIf { it.isNotBlank() } ?: "application/json; charset=utf-8"

    private fun setPref(context: Context, action: JSONObject) {
        val key = action.optString("key")
        if (key.isBlank()) return
        val editor = prefs(context, action).edit()
        when (val value = action.opt("value")) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Double -> editor.putFloat(key, value.toFloat())
            else -> editor.putString(key, value?.toString().orEmpty())
        }
        editor.apply()
    }

    private fun prefs(context: Context, action: JSONObject) =
        context.getSharedPreferences(action.optString("prefs", "dynamic_ui_actions"), Context.MODE_PRIVATE)

    private fun openInternalActivity(activity: Activity, screen: String) {
        val cls = when (screen.trim().lowercase()) {
            "about" -> AboutActivity::class.java
            "feedback" -> FeedbackActivity::class.java
            "policy" -> PolicyActivity::class.java
            "update" -> UpdateActivity::class.java
            "social" -> SocialLinksActivity::class.java
            "crosshair" -> CrosshairActivity::class.java
            "optimizer" -> OptimizerSuperActivity::class.java
            "gameboost", "game_boost" -> GameBoostActivity::class.java
            else -> null
        } ?: return
        activity.startActivity(Intent(activity, cls))
    }

    private fun openUrl(activity: Activity, raw: String) {
        if (!isHttpUrl(raw) && !raw.startsWith("mailto:") && !raw.startsWith("tel:")) return
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(raw))) }
    }

    private fun openApp(activity: Activity, packageName: String) {
        if (packageName.isBlank()) return
        val launch = activity.packageManager.getLaunchIntentForPackage(packageName) ?: return
        runCatching { activity.startActivity(launch) }
    }

    private fun share(activity: Activity, text: String, title: String) {
        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        activity.startActivity(Intent.createChooser(i, title))
    }

    private fun copy(activity: Activity, text: String, label: String) {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) UiModal.show(activity, "Đã sao chép", title = "Clipboard", type = UiModal.Type.SUCCESS, autoDismissMillis = 1200L)
    }

    private fun find(root: View, name: String): View? = DynamicViewLookup.find(root, name)
    private fun isHttpUrl(url: String) = url.startsWith("https://") || url.startsWith("http://")
}
