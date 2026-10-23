package com.aujunpeak.app.dynamicui

import org.json.JSONArray
import org.json.JSONObject

data class DynamicUiAsset(
    val name: String,
    val url: String,
    val sha256: String = ""
)

data class DynamicUiManifest(
    val schema: Int,
    val version: Int,
    val configUrl: String,
    val configSha256: String,
    val minNativeVersionCode: Int,
    val assets: List<DynamicUiAsset>
) {
    companion object {
        fun fromJson(json: JSONObject, manifestUrl: String): DynamicUiManifest {
            val schema = json.optInt("schema", 1)
            val version = json.getInt("version")
            val configUrl = resolveUrl(manifestUrl, json.getString("config"))
            val configSha256 = json.optString("configSha256", "")
            val minNativeVersionCode = json.optInt("minNativeVersionCode", 1)
            val assetsJson = json.optJSONArray("assets") ?: JSONArray()
            val assets = buildList {
                for (i in 0 until assetsJson.length()) {
                    val item = assetsJson.optJSONObject(i) ?: continue
                    val name = item.optString("name").trim()
                    val rawUrl = item.optString("url").trim()
                    if (name.isBlank() || rawUrl.isBlank()) continue
                    add(
                        DynamicUiAsset(
                            name = sanitizeAssetName(name),
                            url = resolveUrl(manifestUrl, rawUrl),
                            sha256 = item.optString("sha256", "")
                        )
                    )
                }
            }
            return DynamicUiManifest(
                schema = schema,
                version = version,
                configUrl = configUrl,
                configSha256 = configSha256,
                minNativeVersionCode = minNativeVersionCode,
                assets = assets
            )
        }

        private fun resolveUrl(baseUrl: String, child: String): String {
            val trimmed = child.trim()
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
            return java.net.URL(java.net.URL(baseUrl), trimmed).toString()
        }

        private fun sanitizeAssetName(name: String): String {
            return name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
        }
    }
}
