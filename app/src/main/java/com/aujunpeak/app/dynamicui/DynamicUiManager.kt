package com.aujunpeak.app.dynamicui

import android.content.Context
import com.aujunpeak.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object DynamicUiManager {

    data class RefreshResult(
        val checked: Boolean,
        val updated: Boolean,
        val activeVersion: Int,
        val requiresNativeUpdate: Boolean = false,
        val error: String? = null
    )

    suspend fun refreshIfNeeded(context: Context, force: Boolean = false): RefreshResult =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            if (!force) {
                val elapsed = System.currentTimeMillis() - DynamicUiStore.lastCheck(appContext)
                if (elapsed in 0 until DynamicUiConstants.DEFAULT_CHECK_INTERVAL_MS) {
                    return@withContext RefreshResult(
                        checked = false,
                        updated = false,
                        activeVersion = DynamicUiStore.activeVersion(appContext)
                    )
                }
            }

            var lastError: String? = null
            for (manifestUrl in DynamicUiConstants.manifestCandidates) {
                val result = runCatching { refreshFromManifest(appContext, manifestUrl) }
                    .getOrElse {
                        lastError = it.message ?: it.javaClass.simpleName
                        null
                    }
                if (result != null) return@withContext result
            }

            DynamicUiStore.setLastCheck(appContext)
            RefreshResult(
                checked = true,
                updated = false,
                activeVersion = DynamicUiStore.activeVersion(appContext),
                error = lastError
            )
        }

    private fun refreshFromManifest(context: Context, manifestUrl: String): RefreshResult {
        val manifestBytes = download(manifestUrl, DynamicUiConstants.MAX_CONFIG_BYTES)
        val manifestJson = JSONObject(manifestBytes.toString(Charsets.UTF_8))
        val manifest = DynamicUiManifest.fromJson(manifestJson, manifestUrl)

        if (manifest.schema !in 1..2) throw IllegalStateException("Unsupported dynamic UI schema ${manifest.schema}")
        if (manifest.minNativeVersionCode > BuildConfig.VERSION_CODE) {
            DynamicUiStore.setLastCheck(context)
            return RefreshResult(
                checked = true,
                updated = false,
                activeVersion = DynamicUiStore.activeVersion(context),
                requiresNativeUpdate = true
            )
        }

        val active = DynamicUiStore.activeVersion(context)
        if (manifest.version <= active) {
            DynamicUiStore.setLastCheck(context)
            return RefreshResult(true, false, active)
        }

        val configBytes = download(manifest.configUrl, DynamicUiConstants.MAX_CONFIG_BYTES)
        verifySha256(configBytes, manifest.configSha256, "ui.json")
        // Parse before committing so malformed JSON never replaces a working UI.
        JSONObject(configBytes.toString(Charsets.UTF_8))

        val root = DynamicUiStore.rootDir(context).apply { mkdirs() }
        val tmpDir = File(root, ".v${manifest.version}.tmp")
        if (tmpDir.exists()) tmpDir.deleteRecursively()
        tmpDir.mkdirs()

        try {
            File(tmpDir, "ui.json").writeBytes(configBytes)
            val tmpAssets = File(tmpDir, "assets").apply { mkdirs() }
            manifest.assets.forEach { asset ->
                val bytes = download(asset.url, DynamicUiConstants.MAX_ASSET_BYTES)
                verifySha256(bytes, asset.sha256, asset.name)
                FileOutputStream(File(tmpAssets, asset.name)).use { it.write(bytes) }
            }

            val finalDir = DynamicUiStore.versionDir(context, manifest.version)
            if (finalDir.exists()) finalDir.deleteRecursively()
            if (!tmpDir.renameTo(finalDir)) {
                copyDirectory(tmpDir, finalDir)
                tmpDir.deleteRecursively()
            }

            DynamicUiStore.setActiveVersion(context, manifest.version)
            DynamicUiStore.cleanupOldVersions(context, manifest.version)
            DynamicUiRuntime.reapplyAll()
            return RefreshResult(true, true, manifest.version)
        } catch (t: Throwable) {
            tmpDir.deleteRecursively()
            throw t
        }
    }

    private fun download(url: String, maxBytes: Int): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 7000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json,image/*,application/octet-stream,*/*;q=0.7")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code for $url")
            val declared = conn.contentLengthLong
            if (declared > maxBytes) throw IllegalStateException("Remote file is too large")
            val out = ByteArrayOutputStream(minOf(maxBytes, 256 * 1024))
            conn.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) throw IllegalStateException("Remote file exceeded size limit")
                    out.write(buffer, 0, read)
                }
            }
            return out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }

    private fun verifySha256(bytes: ByteArray, expected: String, label: String) {
        if (expected.isBlank()) return
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        if (!actual.equals(expected.trim(), ignoreCase = true)) {
            throw SecurityException("SHA-256 mismatch for $label")
        }
    }

    private fun copyDirectory(source: File, target: File) {
        target.mkdirs()
        source.listFiles()?.forEach { item ->
            val dst = File(target, item.name)
            if (item.isDirectory) copyDirectory(item, dst)
            else item.copyTo(dst, overwrite = true)
        }
    }
}
