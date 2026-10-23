package com.aujunpeak.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object GameModuleInstaller {
    enum class Module(
        val title: String,
        val assetName: String,
        val prefPrefix: String,
        val expected: Map<String, String>
    ) {
        AIMLOCK_PRO(
            "AIMLOCK PRO",
            "modules/AIMLOCK_PRO_V5.pkg",
            "aimlock_pro",
            mapOf(
                "Assembly-CSharp-patch.bytes" to "6063b36d48252fcadcb40d76ebc634ca4706f57f995612bce8b902a9b04c0a26",
                "localConfig.json" to "3e5c4915bb8c0718aa6fbb8143cf954ff32221f9c0ef0b9d83fd0b1fc1c95535"
            )
        ),
        ESP_AIMLOCK(
            "ESP_AIMLOCK",
            "modules/ESP_AIMLOCK_V5.pkg",
            "esp_aimlock",
            mapOf(
                "Assembly-CSharp-patch.bytes" to "03fec822d7f7c978a7b517ac2b86e4377dc68181157de710a66b881918c1ec47",
                "localConfig.json" to "3e5c4915bb8c0718aa6fbb8143cf954ff32221f9c0ef0b9d83fd0b1fc1c95535"
            )
        ),
        AIMLOCK_BOX(
            "AIMLOCK-BOX",
            "modules/AIMLOCK_BOX_V5.pkg",
            "aimlock_box",
            mapOf(
                "Assembly-CSharp-patch.bytes" to "f9c335e36cbb0cacefacebdc8bdac52e32df75f1cd1c2bfb5ffce46376f8e4ff",
                "localConfig.json" to "3e5c4915bb8c0718aa6fbb8143cf954ff32221f9c0ef0b9d83fd0b1fc1c95535"
            )
        )
    }

    private const val TARGET_DIR = "/storage/emulated/0/Android/data/com.dts.freefireth/files/"
    private const val MAGIC = "AUJMOD2"
    private const val VERSION = 2
    private const val ITERATIONS = 180000
    private const val PREFS = "game_module_installer_v5"

    private val password: CharArray = "67gw%X$9f9B7aJpY!n4mGMLxXe3w64TJ%oDBmuR3wogDPsYu".toCharArray()

    data class InstallResult(val ok: Boolean, val code: String, val message: String)

    fun buildStorageAccessIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )

    fun hasRequiredStorageAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return Environment.isExternalStorageLegacy() || Environment.isExternalStorageManager() || rootAvailable()
    }

    fun isTargetWritable(): Boolean {
        val dir = File(TARGET_DIR)
        return runCatching {
            if (!dir.exists()) dir.mkdirs()
            if (!dir.isDirectory) return@runCatching false
            val p = File(dir, ".aujunpeak_write_test")
            FileOutputStream(p).use { it.write(byteArrayOf(65)) }
            p.delete()
            true
        }.getOrElse { false }
    }

    fun install(context: Context, module: Module, callback: (InstallResult) -> Unit) {
        Thread {
            val result = runCatching { installInternal(context, module) }.getOrElse {
                InstallResult(false, "INSTALL_FAILED", "${module.title}: ${it.message ?: "install failed"}")
            }
            callback(result)
        }.start()
    }

    fun remove(context: Context, module: Module, callback: (InstallResult) -> Unit) {
        Thread {
            val result = runCatching { removeInternal(context, module) }.getOrElse {
                InstallResult(false, "REMOVE_FAILED", "${module.title}: ${it.message ?: "remove failed"}")
            }
            callback(result)
        }.start()
    }

    private fun installInternal(context: Context, module: Module): InstallResult {
        val encrypted = context.assets.open(module.assetName).use { it.readBytes() }
        val zipBytes = decrypt(encrypted)
        val extracted = linkedMapOf<String, ByteArray>()

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                if (e.isDirectory || e.name.contains('/') || e.name.contains('\\') || e.name.contains("..")) {
                    zis.closeEntry()
                    continue
                }
                val data = zis.readBytesAll()
                val hash = sha256(data)
                require(module.expected[e.name]?.equals(hash, true) == true) {
                    "Integrity check failed: ${e.name}"
                }
                extracted[e.name] = data
                zis.closeEntry()
            }
        }
        require(extracted.size == module.expected.size) { "Module file count mismatch" }

        val target = File(TARGET_DIR)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isTargetWritable()) {
            return InstallResult(
                false,
                "TARGET_DIR_UNWRITABLE",
                "Android 11+ blocks direct writes to another app's Android/data directory: $TARGET_DIR"
            )
        }
        if (!target.exists() && !target.mkdirs()) {
            return InstallResult(false, "TARGET_DIR_UNWRITABLE", "Cannot create target directory: $TARGET_DIR")
        }
        if (!target.isDirectory) return InstallResult(false, "TARGET_DIR_UNWRITABLE", "Target is not a directory: $TARGET_DIR")

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for ((name,data) in extracted) {
            val out = File(target, name)
            FileOutputStream(out).use { it.write(data) }
            require(out.isFile && out.length() == data.size.toLong()) { "Write verification failed: $name" }
            prefs.edit().putString("${module.prefPrefix}_$name", sha256(data)).apply()
        }
        prefs.edit().putBoolean("${module.prefPrefix}_enabled", true).apply()
        return InstallResult(true, "OK", "${module.title} applied")
    }

    private fun removeInternal(context: Context, module: Module): InstallResult {
        val target = File(TARGET_DIR)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        module.expected.keys.forEach { name ->
            val f = File(target, name)
            val saved = prefs.getString("${module.prefPrefix}_$name", null)
            if (f.isFile && saved != null && runCatching { sha256(f.readBytes()).equals(saved, true) }.getOrDefault(false)) f.delete()
        }
        prefs.edit().putBoolean("${module.prefPrefix}_enabled", false).apply()
        return InstallResult(true, "OK", "${module.title} disabled")
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        val magic = MAGIC.toByteArray(Charsets.US_ASCII)
        require(blob.size > magic.size + 1 + 16 + 12 + 16) { "Invalid module package" }
        require(blob.copyOfRange(0, magic.size).contentEquals(magic)) { "Invalid module package header" }
        require(blob[magic.size].toInt() == VERSION) { "Unsupported module package" }

        val salt = blob.copyOfRange(8, 24)
        val iv = blob.copyOfRange(24, 36)
        val ciphertext = blob.copyOfRange(36, blob.size - 16)
        val tag = blob.copyOfRange(blob.size - 16, blob.size)

        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        // AES-GCM authentication tag is appended to ciphertext; doFinal() expects both.
        val ciphertextAndTag = ByteArray(ciphertext.size + tag.size)
        System.arraycopy(ciphertext, 0, ciphertextAndTag, 0, ciphertext.size)
        System.arraycopy(tag, 0, ciphertextAndTag, ciphertext.size, tag.size)
        return cipher.doFinal(ciphertextAndTag)
    }

    private fun ZipInputStream.readBytesAll(): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun sha256(data: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(data)
        .joinToString("") { "%02x".format(it) }

    private fun rootAvailable(): Boolean = runCatching {
        Runtime.getRuntime().exec(arrayOf("su","-c","id")).waitFor() == 0
    }.getOrDefault(false)
}
