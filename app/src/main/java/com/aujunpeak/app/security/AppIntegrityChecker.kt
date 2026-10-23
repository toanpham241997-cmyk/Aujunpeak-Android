package com.aujunpeak.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.util.Log
import com.aujunpeak.app.BuildConfig
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest

/**
 * AppIntegrityChecker — Lớp bảo vệ chống crack, chống debug, chống giả mạo APK.
 *
 * Gồm các lớp kiểm tra:
 * 1. Chữ ký APK   — phát hiện repackaging / resign
 * 2. Anti-debug   — phát hiện debugger attach
 * 3. Anti-emulator — phát hiện chạy trên máy ảo
 * 4. Anti-root    — phát hiện thiết bị root
 * 5. Anti-hook    — phát hiện Xposed / Frida framework
 */
object AppIntegrityChecker {

    private const val TAG = "AIC"

    // ─── KẾT QUẢ KIỂM TRA ────────────────────────────────────────────────────
    data class IntegrityResult(
        val passed: Boolean,
        val reason: String = ""
    )

    /**
     * Chạy toàn bộ kiểm tra integrity.
     * Gọi trong Application.onCreate() trước khi làm bất kỳ điều gì.
     *
     * @param context Context
     * @param killOnFail true → tắt app ngay nếu phát hiện vi phạm (khuyến nghị cho release)
     * @return IntegrityResult
     */
    fun runAllChecks(context: Context, killOnFail: Boolean = true): IntegrityResult {

        // Chỉ chạy đầy đủ trong release build
        if (BuildConfig.DEBUG) {
            return IntegrityResult(passed = true, reason = "debug_skip")
        }

        val checks = listOf(
            ::checkSignature to context,
            ::checkDebugger  to context,
            ::checkEmulator  to context,
            ::checkRoot      to context,
            ::checkHookFramework to context
        )

        for ((check, ctx) in checks) {
            val result = check(ctx)
            if (!result.passed) {
                if (killOnFail) killApp()
                return result
            }
        }

        return IntegrityResult(passed = true)
    }

    // ═══════════════════════════════════════════════════════════════
    // KIỂM TRA 1 — CHỮ KÝ APK (Chống repackaging / resign)
    // ═══════════════════════════════════════════════════════════════
    private fun checkSignature(context: Context): IntegrityResult {
        return try {
            val expectedSha256 = BuildConfig.RELEASE_CERT_SHA256
            // Bỏ qua nếu chưa cấu hình fingerprint
            if (expectedSha256 == "YOUR_RELEASE_CERT_SHA256_HERE" || expectedSha256.isBlank()) {
                return IntegrityResult(passed = true, reason = "sig_not_configured")
            }

            val packageManager = context.packageManager
            val packageName = context.packageName

            val sigBytes: ByteArray = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners[0].toByteArray()
                } else {
                    signingInfo.signingCertificateHistory[0].toByteArray()
                }
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                ).signatures[0].toByteArray()
            }

            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(sigBytes)
            val actualSha256 = hash.joinToString("") { "%02X".format(it) }

            if (actualSha256.equals(expectedSha256, ignoreCase = true)) {
                IntegrityResult(passed = true)
            } else {
                IntegrityResult(passed = false, reason = "sig_mismatch")
            }
        } catch (e: Exception) {
            IntegrityResult(passed = false, reason = "sig_error:${e.javaClass.simpleName}")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // KIỂM TRA 2 — ANTI-DEBUG (Chống attach debugger)
    // ═══════════════════════════════════════════════════════════════
    private fun checkDebugger(context: Context): IntegrityResult {
        // Debug.isDebuggerConnected() — Java-level debugger
        if (Debug.isDebuggerConnected()) {
            return IntegrityResult(passed = false, reason = "debugger_connected")
        }

        // Debug.waitingForDebugger() — đang chờ attach
        if (Debug.waitingForDebugger()) {
            return IntegrityResult(passed = false, reason = "waiting_debugger")
        }

        // Kiểm tra flag JDWP trong /proc/self/status
        try {
            val statusFile = File("/proc/self/status")
            if (statusFile.exists()) {
                val content = statusFile.readText()
                // TracerPid > 0 nghĩa là có process đang trace (ptrace attach)
                val tracerPidLine = content.lines()
                    .firstOrNull { it.startsWith("TracerPid:") }
                val tracerPid = tracerPidLine?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
                if (tracerPid > 0) {
                    return IntegrityResult(passed = false, reason = "ptrace_detected:$tracerPid")
                }
            }
        } catch (_: Exception) {}

        // ApplicationInfo.FLAG_DEBUGGABLE trong APK
        val flags = context.applicationInfo.flags
        if (flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            return IntegrityResult(passed = false, reason = "app_debuggable_flag")
        }

        return IntegrityResult(passed = true)
    }

    // ═══════════════════════════════════════════════════════════════
    // KIỂM TRA 3 — ANTI-EMULATOR (Chống chạy trên máy ảo)
    // ═══════════════════════════════════════════════════════════════
    private fun checkEmulator(context: Context): IntegrityResult {
        var score = 0

        // Build fingerprint của emulator
        val fingerprint = Build.FINGERPRINT.lowercase()
        if (fingerprint.contains("generic") ||
            fingerprint.contains("sdk_gphone") ||
            fingerprint.contains("emulator") ||
            fingerprint.contains("android_sdk") ||
            fingerprint.startsWith("unknown")) {
            score += 2
        }

        // Build model / manufacturer
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()

        val emulatorKeywords = listOf(
            "google_sdk", "emulator", "android sdk", "sdk_gphone", "vbox", "vbox86",
            "nox", "droid4x", "bluestacks", "genymotion", "andy", "youwave",
            "windroye", "memu", "leapdroid", "ldplayer", "mumu"
        )

        for (kw in emulatorKeywords) {
            if (model.contains(kw) || manufacturer.contains(kw) ||
                brand.contains(kw) || device.contains(kw) || product.contains(kw)) {
                score += 3
                break
            }
        }

        // Hardware identifier thường gặp trên emulator
        if (Build.HARDWARE.equals("goldfish", ignoreCase = true) ||
            Build.HARDWARE.equals("ranchu", ignoreCase = true) ||
            Build.HARDWARE.equals("vbox86", ignoreCase = true)) {
            score += 2
        }

        // Board
        if (Build.BOARD.equals("unknown", ignoreCase = true) ||
            Build.BOARD.lowercase().contains("goldfish")) {
            score += 1
        }

        // Kiểm tra file đặc trưng của emulator
        val emulatorFiles = listOf(
            "/dev/socket/qemud",
            "/dev/qemu_pipe",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/sys/qemu_trace",
            "/system/bin/qemu-props",
            "/dev/socket/genyd"
        )
        val fileHits = emulatorFiles.count { File(it).exists() }
        score += fileHits

        // Kiểm tra Telephony cho số serial emulator
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            val imei = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                null // không cần IMEI để check
            } else {
                @Suppress("DEPRECATION")
                try { tm?.deviceId } catch (_: Exception) { null }
            }
            val simSerial = try { tm?.simSerialNumber } catch (_: Exception) { null }
            if (imei == "000000000000000" || simSerial == "89014103211118510720") {
                score += 2
            }
        } catch (_: Exception) {}

        // Threshold: nếu score >= 4 thì coi là emulator
        return if (score >= 4) {
            IntegrityResult(passed = false, reason = "emulator_detected:score=$score")
        } else {
            IntegrityResult(passed = true)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // KIỂM TRA 4 — ANTI-ROOT (Chống thiết bị root)
    // ═══════════════════════════════════════════════════════════════
    private fun checkRoot(context: Context): IntegrityResult {
        // Kiểm tra file su (superuser binary)
        val suPaths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        for (path in suPaths) {
            if (File(path).exists()) {
                return IntegrityResult(passed = false, reason = "su_binary:$path")
            }
        }

        // Kiểm tra app root phổ biến được cài
        val rootApps = listOf(
            "com.topjohnwu.magisk",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.noshufou.android.su",
            "com.yellowes.su",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.smedialink.oneclickroot",
            "com.zhiqupk.root.global",
            "com.alephzain.framaroot"
        )
        val pm = context.packageManager
        for (pkg in rootApps) {
            try {
                pm.getPackageInfo(pkg, 0)
                return IntegrityResult(passed = false, reason = "root_app:$pkg")
            } catch (_: PackageManager.NameNotFoundException) {}
        }

        // Thử chạy lệnh 'su' — nếu thành công thì device đã root
        try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            process.destroy()
            if (!result.isNullOrEmpty()) {
                return IntegrityResult(passed = false, reason = "su_which_found")
            }
        } catch (_: Exception) {}

        // Kiểm tra build tags (test-keys thường là dấu hiệu root)
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return IntegrityResult(passed = false, reason = "test_keys_build")
        }

        return IntegrityResult(passed = true)
    }

    // ═══════════════════════════════════════════════════════════════
    // KIỂM TRA 5 — ANTI-HOOK (Chống Xposed, Frida, LSPosed...)
    // ═══════════════════════════════════════════════════════════════
    private fun checkHookFramework(context: Context): IntegrityResult {

        // Kiểm tra Xposed / LSPosed package
        val hookPackages = listOf(
            "de.robv.android.xposed.installer",
            "org.meowcat.edxposed.manager",
            "org.lsposed.manager",
            "io.github.lsposed",
            "com.android.xposed"
        )
        val pm = context.packageManager
        for (pkg in hookPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return IntegrityResult(passed = false, reason = "hook_app:$pkg")
            } catch (_: PackageManager.NameNotFoundException) {}
        }

        // Kiểm tra Xposed bridge class trong classpath
        try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            return IntegrityResult(passed = false, reason = "xposed_bridge_found")
        } catch (_: ClassNotFoundException) {}
        try {
            Class.forName("de.robv.android.xposed.XposedHelpers")
            return IntegrityResult(passed = false, reason = "xposed_helpers_found")
        } catch (_: ClassNotFoundException) {}

        // Kiểm tra file Xposed
        val xposedFiles = listOf(
            "/system/framework/XposedBridge.jar",
            "/system/lib/libxposed_art.so",
            "/system/lib64/libxposed_art.so",
            "/data/app/de.robv.android.xposed.installer-1.apk",
            "/data/app/de.robv.android.xposed.installer-2.apk"
        )
        for (file in xposedFiles) {
            if (File(file).exists()) {
                return IntegrityResult(passed = false, reason = "xposed_file:$file")
            }
        }

        // Kiểm tra Frida (dynamic instrumentation) qua native maps
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                val content = mapsFile.readText().lowercase()
                if (content.contains("frida") ||
                    content.contains("gadget") ||
                    content.contains("gum-js-loop") ||
                    content.contains("frida-agent")) {
                    return IntegrityResult(passed = false, reason = "frida_detected_maps")
                }
            }
        } catch (_: Exception) {}

        // Kiểm tra Frida qua TCP port 27042 (Frida server mặc định)
        try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("127.0.0.1", 27042), 200)
            socket.close()
            return IntegrityResult(passed = false, reason = "frida_port_open")
        } catch (_: Exception) {
            // Không kết nối được → không có Frida server, đây là kết quả tốt
        }

        // Kiểm tra stack trace cho dấu vết Xposed hook
        try {
            throw RuntimeException()
        } catch (e: RuntimeException) {
            val stackTrace = e.stackTrace.joinToString("\n") { it.className }
            if (stackTrace.contains("xposed", ignoreCase = true) ||
                stackTrace.contains("lsposed", ignoreCase = true)) {
                return IntegrityResult(passed = false, reason = "hook_in_stacktrace")
            }
        }

        return IntegrityResult(passed = true)
    }

    // ═══════════════════════════════════════════════════════════════
    // TERMINATE APP — Tắt app ngay khi phát hiện vi phạm
    // ═══════════════════════════════════════════════════════════════
    private fun killApp() {
        try {
            android.os.Process.killProcess(android.os.Process.myPid())
        } catch (_: Exception) {}
        try {
            Runtime.getRuntime().exit(0)
        } catch (_: Exception) {}
    }
}
