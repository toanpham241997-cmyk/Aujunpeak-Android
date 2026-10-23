package com.aujunpeak.app.dynamicui

import android.content.Context
import com.aujunpeak.app.R
import org.json.JSONObject
import java.io.File

object DynamicUiStore {
    private fun prefs(context: Context) =
        context.getSharedPreferences(DynamicUiConstants.PREFS, Context.MODE_PRIVATE)

    fun activeVersion(context: Context): Int =
        prefs(context).getInt(DynamicUiConstants.PREF_ACTIVE_VERSION, 0)

    fun setActiveVersion(context: Context, version: Int) {
        prefs(context).edit()
            .putInt(DynamicUiConstants.PREF_ACTIVE_VERSION, version)
            .putLong(DynamicUiConstants.PREF_LAST_CHECK, System.currentTimeMillis())
            .apply()
    }

    fun setLastCheck(context: Context) {
        prefs(context).edit()
            .putLong(DynamicUiConstants.PREF_LAST_CHECK, System.currentTimeMillis())
            .apply()
    }

    fun lastCheck(context: Context): Long =
        prefs(context).getLong(DynamicUiConstants.PREF_LAST_CHECK, 0L)

    fun rootDir(context: Context): File = File(context.filesDir, "dynamic_ui")
    fun versionDir(context: Context, version: Int): File = File(rootDir(context), "v$version")
    fun configFile(context: Context, version: Int): File = File(versionDir(context, version), "ui.json")
    fun assetDir(context: Context, version: Int): File = File(versionDir(context, version), "assets")

    fun loadConfig(context: Context): JSONObject {
        val version = activeVersion(context)
        if (version > 0) {
            runCatching {
                val f = configFile(context, version)
                if (f.isFile) return JSONObject(f.readText(Charsets.UTF_8))
            }
        }
        return runCatching {
            context.resources.openRawResource(R.raw.dynamic_ui_default)
                .bufferedReader(Charsets.UTF_8)
                .use { JSONObject(it.readText()) }
        }.getOrElse { JSONObject() }
    }

    fun assetFile(context: Context, assetName: String): File? {
        val version = activeVersion(context)
        if (version <= 0) return null
        val safe = assetName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
        val f = File(assetDir(context, version), safe)
        return f.takeIf { it.isFile }
    }

    fun cleanupOldVersions(context: Context, keepVersion: Int) {
        rootDir(context).listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name != "v$keepVersion") {
                runCatching { dir.deleteRecursively() }
            }
        }
    }
}
