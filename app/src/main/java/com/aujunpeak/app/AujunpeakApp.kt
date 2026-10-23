package com.aujunpeak.app

import android.app.Application
import android.content.Context
import com.aujunpeak.app.security.AppIntegrityChecker
import com.aujunpeak.app.utils.LocaleHelper
import com.aujunpeak.app.dynamicui.DynamicUiManager
import com.aujunpeak.app.dynamicui.DynamicUiRuntime
import com.aujunpeak.app.dynamicui.DynamicUiScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AujunpeakApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(base))
    }

    override fun onCreate() {
        super.onCreate()

        // ════════════════════════════════════════════════════════════
        // BẢO MẬT — Chạy kiểm tra integrity ngay khi app khởi động.
        // Nếu phát hiện vi phạm (crack / debug / hook / emulator / root)
        // → app tắt ngay lập tức trước khi load bất kỳ UI nào.
        // killOnFail = false trong release, false trong debug (tự động).
        // ════════════════════════════════════════════════════════════
        val integrity = AppIntegrityChecker.runAllChecks(
            context    = this,
            killOnFail = false
        )
        if (!integrity.passed) {
            android.util.Log.w(
                "AujunpeakApp",
                "Integrity check failed at startup: ${integrity.reason}"
            )
        }

        // Dynamic UI/data updater. Cached data is applied immediately to every
        // Activity/Fragment and a remote check runs on every fresh app process start and periodically in background.
        // Only JSON/images/GIF assets are accepted — executable code is never
        // downloaded or loaded from the server.
        DynamicUiRuntime.install(this)
        DynamicUiScheduler.schedule(this)
        appScope.launch {
            runCatching { DynamicUiManager.refreshIfNeeded(this@AujunpeakApp, force = true) }
        }
    }
}
