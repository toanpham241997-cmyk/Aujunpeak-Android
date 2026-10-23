package com.aujunpeak.app.dynamicui

import com.aujunpeak.app.utils.KeyManager

object DynamicUiConstants {
    const val PREFS = "aujunpeak_dynamic_ui"
    const val PREF_ACTIVE_VERSION = "active_version"
    const val PREF_LAST_CHECK = "last_check"
    const val DEFAULT_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    const val MAX_CONFIG_BYTES = 2 * 1024 * 1024
    const val MAX_ASSET_BYTES = 20 * 1024 * 1024

    /**
     * Primary static-data endpoint. Upload the bundled `aujunpeak-data` folder
     * to the website so this URL serves manifest.json.
     *
     * A second candidate based on the existing API host is tried automatically.
     */
    val manifestCandidates: List<String>
        get() = listOf(
            "http://103.140.249.74/aujunpeak-data/manifest.json",
            "https://huanha.shop/aujunpeak-data/manifest.json",
            KeyManager.API_BASE_URL.removeSuffix("/api") + "/dynamic-ui/manifest.json"
        )
}
