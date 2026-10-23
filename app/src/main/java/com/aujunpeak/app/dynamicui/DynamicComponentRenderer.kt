package com.aujunpeak.app.dynamicui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.aujunpeak.app.R
import com.aujunpeak.app.views.ParticleView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds new UI components described by ui.json.
 * Supports append/prepend/overlay/replace layouts and nested component trees.
 */
object DynamicComponentRenderer {

    fun render(activity: Activity, root: View, screenKey: String?, config: JSONObject, screen: JSONObject?) {
        if (screen == null) return
        val version = config.optInt("version", DynamicUiStore.activeVersion(activity))
        val fingerprint = "$version:${screenKey.orEmpty()}:${screen.toString().hashCode()}"
        if (root.getTag(R.id.dynamic_ui_render_fingerprint) == fingerprint) return

        cleanup(root)
        root.setTag(R.id.dynamic_ui_render_fingerprint, fingerprint)

        val layouts = when {
            screen.optJSONArray("dynamicLayouts") != null -> screen.optJSONArray("dynamicLayouts")!!
            screen.optJSONObject("dynamic") != null -> JSONArray().put(screen.optJSONObject("dynamic"))
            screen.optJSONArray("components") != null -> JSONArray().put(
                JSONObject()
                    .put("enabled", true)
                    .put("target", "__root__")
                    .put("mode", "append")
                    .put("components", screen.optJSONArray("components"))
            )
            else -> JSONArray()
        }

        for (i in 0 until layouts.length()) {
            val layout = layouts.optJSONObject(i) ?: continue
            if (!layout.optBoolean("enabled", true)) continue
            renderLayout(activity, root, layout)
        }
    }

    private fun renderLayout(activity: Activity, root: View, layout: JSONObject) {
        val targetName = layout.optString("target", "__root__")
        val target = DynamicViewLookup.find(root, targetName) as? ViewGroup ?: return
        val mode = layout.optString("mode", "append").lowercase()
        val components = layout.optJSONArray("components") ?: JSONArray()

        val host = LinearLayout(activity).apply {
            orientation = if (layout.optString("orientation", "vertical").equals("horizontal", true)) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setTag(R.id.dynamic_ui_generated, true)
            setTag(R.id.dynamic_ui_component_id, layout.optString("id", "remote_layout_${System.identityHashCode(this)}"))
        }

        val overlayLike = mode == "overlay" || mode == "replace"
        val hostWidth = if (layout.optString("width", if (overlayLike) "match" else "match").equals("wrap", true)) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
        val hostHeight = if (layout.optString("height", if (overlayLike) "match" else "wrap").equals("match", true)) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        addWithParams(target, host, layout, hostWidth, hostHeight, mode)

        layout.optJSONObject("style")?.let { DynamicUiBinder.applyStyle(activity, root, host, it) }

        if (mode == "replace") {
            for (i in 0 until target.childCount) {
                val child = target.getChildAt(i)
                if (child === host || child.getTag(R.id.dynamic_ui_generated) == true) continue
                if (child.getTag(R.id.dynamic_ui_original_visibility) == null) {
                    child.setTag(R.id.dynamic_ui_original_visibility, child.visibility)
                }
                child.visibility = View.GONE
            }
        }

        for (i in 0 until components.length()) {
            components.optJSONObject(i)?.let { createComponent(activity, root, host, it) }
        }
    }

    private fun addWithParams(target: ViewGroup, child: View, json: JSONObject, defaultWidth: Int, defaultHeight: Int, mode: String) {
        val width = dimension(json, "width", "widthDp", defaultWidth, child)
        val height = dimension(json, "height", "heightDp", defaultHeight, child)
        val lp: ViewGroup.LayoutParams = when (target) {
            is LinearLayout -> LinearLayout.LayoutParams(width, height).apply {
                weight = json.optDouble("weight", 0.0).toFloat()
                gravity = parseGravity(json.optString("layoutGravity"))
            }
            is FrameLayout -> FrameLayout.LayoutParams(width, height).apply {
                gravity = parseGravity(json.optString("layoutGravity", if (mode == "overlay" || mode == "replace") "fill" else "top|start"))
            }
            else -> ViewGroup.LayoutParams(width, height)
        }
        if (lp is ViewGroup.MarginLayoutParams) {
            val all = dp(child, json.optDouble("marginDp", 0.0).toFloat()).toInt()
            lp.setMargins(
                if (json.has("marginStartDp")) dp(child, json.optDouble("marginStartDp").toFloat()).toInt() else all,
                if (json.has("marginTopDp")) dp(child, json.optDouble("marginTopDp").toFloat()).toInt() else all,
                if (json.has("marginEndDp")) dp(child, json.optDouble("marginEndDp").toFloat()).toInt() else all,
                if (json.has("marginBottomDp")) dp(child, json.optDouble("marginBottomDp").toFloat()).toInt() else all
            )
        }
        val index = when (mode) {
            "prepend" -> 0
            else -> json.optInt("insertAt", -1)
        }
        if (index in 0..target.childCount) target.addView(child, index, lp) else target.addView(child, lp)
    }

    private fun createComponent(activity: Activity, root: View, parent: ViewGroup, node: JSONObject): View? {
        if (!node.optBoolean("enabled", true)) return null
        val type = node.optString("type", "text").trim().lowercase()
        val id = node.optString("id").trim()

        val view: View = when (type) {
            "column", "card" -> LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            "row" -> LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = parseGravity(node.optString("gravity", "center_vertical")) }
            "frame", "container" -> FrameLayout(activity)
            "scroll" -> NestedScrollView(activity).apply { isFillViewport = node.optBoolean("fillViewport", false); isVerticalScrollBarEnabled = false }
            "text" -> TextView(activity).apply { text = node.optString("text") }
            "button" -> MaterialButton(activity).apply { text = node.optString("text", node.optString("title")) }
            "switch" -> createSwitch(activity, root, node)
            "image" -> ImageView(activity).apply {
                contentDescription = node.optString("contentDescription", "")
                scaleType = parseScaleType(node.optString("scaleType", "centerCrop"))
            }
            "icon" -> DynamicFaIconView(activity).apply {
                iconName = node.optString("icon", "fa-bolt")
                iconColor = parseColor(node.optString("iconColor", "#FF1744")) ?: 0xFFFF1744.toInt()
                iconStrokeDp = node.optDouble("iconStrokeDp", 2.1).toFloat()
                contentDescription = node.optString("contentDescription", iconName)
            }
            "input" -> EditText(activity).apply {
                setText(node.optString("text"))
                hint = node.optString("hint")
                setTextColor(parseColor(node.optString("textColor")) ?: Color.WHITE)
                setHintTextColor(parseColor(node.optString("hintColor")) ?: 0x88FFFFFF.toInt())
                setSingleLine(node.optBoolean("singleLine", true))
            }
            "progress" -> ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = node.optInt("max", 100).coerceAtLeast(1)
                progress = node.optInt("progress", 0).coerceIn(0, max)
            }
            "particle" -> ParticleView(activity).apply {
                node.optJSONArray("particleColors")?.let { colors ->
                    val ints = IntArray(colors.length()) { i -> parseColor(colors.optString(i)) ?: Color.WHITE }
                    setColors(ints)
                }
                setDensity(node.optInt("particleDensity", 8).coerceIn(1, 40))
                start()
            }
            "divider" -> View(activity).apply { setBackgroundColor(parseColor(node.optString("color", "#33FF1744")) ?: 0x33FF1744) }
            "spacer" -> Space(activity)
            else -> TextView(activity).apply { text = node.optString("text", "Unsupported component: $type") }
        }

        view.setTag(R.id.dynamic_ui_generated, true)
        if (id.isNotBlank()) view.setTag(R.id.dynamic_ui_component_id, id)

        val defaultHeight = when (type) {
            "icon" -> dp(view, node.optDouble("sizeDp", 34.0).toFloat()).toInt()
            "button" -> dp(view, 52f).toInt()
            "divider" -> dp(view, 1f).toInt()
            "spacer" -> dp(view, node.optDouble("heightDp", 8.0).toFloat()).toInt()
            "switch" -> ViewGroup.LayoutParams.WRAP_CONTENT
            else -> ViewGroup.LayoutParams.WRAP_CONTENT
        }
        val defaultWidth = when (type) {
            "icon" -> dp(view, node.optDouble("sizeDp", 34.0).toFloat()).toInt()
            "spacer", "divider", "button", "switch", "column", "card", "row", "frame", "container", "scroll" -> ViewGroup.LayoutParams.MATCH_PARENT
            else -> ViewGroup.LayoutParams.WRAP_CONTENT
        }
        addWithParams(parent, view, node, defaultWidth, defaultHeight, "append")

        node.optJSONObject("style")?.let { DynamicUiBinder.applyStyle(activity, root, view, it) }
        applyDirectProperties(activity, root, view, node)
        if (view is MaterialButton) applyCompactBlackRedButton(view)

        if (view is ViewGroup && type != "switch") {
            val children = node.optJSONArray("children") ?: JSONArray()
            if (type == "scroll") {
                val content = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setTag(R.id.dynamic_ui_generated, true)
                }
                view.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                for (i in 0 until children.length()) children.optJSONObject(i)?.let { createComponent(activity, root, content, it) }
            } else {
                for (i in 0 until children.length()) children.optJSONObject(i)?.let { createComponent(activity, root, view, it) }
            }
        }

        return view
    }


    private fun applyCompactBlackRedButton(button: MaterialButton) {
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.BLACK)
        button.strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFFF1744"))
        button.strokeWidth = dp(button, 1f).toInt().coerceAtLeast(1)
        button.cornerRadius = dp(button, 9f).toInt()
        button.insetTop = 0
        button.insetBottom = 0
        button.setTextColor(Color.WHITE)
        button.elevation = 0f
        button.stateListAnimator = null
    }

    private fun createSwitch(activity: Activity, root: View, node: JSONObject): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(this, 12f).toInt(), dp(this, 9f).toInt(), dp(this, 12f).toInt(), dp(this, 9f).toInt())
        }
        val textCol = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(activity).apply {
            text = node.optString("title", node.optString("text"))
            setTextColor(parseColor(node.optString("titleColor", "#F4F7FF")) ?: Color.WHITE)
            textSize = node.optDouble("titleSizeSp", 13.0).toFloat()
            setTypeface(typeface, Typeface.BOLD)
        }
        val subtitle = TextView(activity).apply {
            text = node.optString("subtitle")
            setTextColor(parseColor(node.optString("subtitleColor", "#8CA0BB")) ?: 0xFF8CA0BB.toInt())
            textSize = node.optDouble("subtitleSizeSp", 9.5).toFloat()
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }
        textCol.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        textCol.addView(subtitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(row, 2f).toInt() })
        row.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val sw = MaterialSwitch(activity)
        val prefKey = node.optString("prefKey", node.optString("id"))
        val prefsName = node.optString("prefs", "dynamic_ui_switches")
        val prefs = activity.getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
        sw.isChecked = if (prefKey.isNotBlank()) prefs.getBoolean(prefKey, node.optBoolean("defaultValue", false)) else node.optBoolean("defaultValue", false)
        row.addView(sw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val id = node.optString("id").trim()
        if (id.isNotBlank()) sw.setTag(R.id.dynamic_ui_component_id, "$id.switch")
        sw.setTag(R.id.dynamic_ui_generated, true)
        sw.setOnCheckedChangeListener { _, checked ->
            if (prefKey.isNotBlank()) prefs.edit().putBoolean(prefKey, checked).apply()
            DynamicActionEngine.execute(activity, root, if (checked) node.opt("onAction") ?: node.opt("onChange") else node.opt("offAction") ?: node.opt("onChange"))
        }
        row.setOnClickListener { if (node.optBoolean("rowToggles", true)) sw.isChecked = !sw.isChecked else DynamicActionEngine.execute(activity, root, node.opt("onClick")) }
        return row
    }

    private fun applyDirectProperties(activity: Activity, root: View, view: View, node: JSONObject) {
        if (view is TextView && node.has("text")) view.text = node.optString("text")
        if (view is MaterialButton && node.has("text")) view.text = node.optString("text")
        if (view is DynamicFaIconView) {
            if (node.has("icon")) view.iconName = node.optString("icon", "fa-bolt")
            if (node.has("iconColor")) parseColor(node.optString("iconColor"))?.let { view.iconColor = it }
            if (node.has("iconStrokeDp")) view.iconStrokeDp = node.optDouble("iconStrokeDp", 2.1).toFloat()
        }
        if (view is ImageView) {
            val asset = node.optString("asset")
            val url = node.optString("url")
            when {
                asset.isNotBlank() -> DynamicUiStore.assetFile(activity, asset)?.let { Glide.with(view).load(it).into(view) }
                url.startsWith("http://") || url.startsWith("https://") -> Glide.with(view).load(url).into(view)
            }
        }
        if ((node.has("onClick") || node.has("action")) && view !is MaterialSwitch) {
            view.isClickable = true
            val clickAction = if (node.has("onClick")) node.opt("onClick") else node.opt("action")
            view.setOnClickListener { DynamicActionEngine.execute(activity, root, clickAction) }
        }
        if (node.has("onLongClick")) {
            view.setOnLongClickListener { DynamicActionEngine.execute(activity, root, node.opt("onLongClick")); true }
        }
        if (view is EditText) {
            val prefKey = node.optString("prefKey")
            if (prefKey.isNotBlank()) {
                val prefs = activity.getSharedPreferences(node.optString("prefs", "dynamic_ui_inputs"), android.content.Context.MODE_PRIVATE)
                view.setText(prefs.getString(prefKey, node.optString("text")))
                view.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) prefs.edit().putString(prefKey, view.text.toString()).apply() }
            }
        }
    }

    fun cleanup(root: View) {
        // restore native views hidden by a previous replace layout
        DynamicViewLookup.walk(root) { view ->
            val original = view.getTag(R.id.dynamic_ui_original_visibility) as? Int
            if (original != null) {
                view.visibility = original
                view.setTag(R.id.dynamic_ui_original_visibility, null)
            }
        }
        // remove generated hosts/components from deepest parents first
        val generated = mutableListOf<View>()
        DynamicViewLookup.walk(root) { if (it.getTag(R.id.dynamic_ui_generated) == true) generated.add(it) }
        generated.asReversed().forEach { view -> (view.parent as? ViewGroup)?.removeView(view) }
    }

    private fun dimension(json: JSONObject, modeKey: String, dpKey: String, defaultValue: Int, view: View): Int {
        if (json.has(dpKey)) return dp(view, json.optDouble(dpKey).toFloat()).toInt()
        return when (json.optString(modeKey).lowercase()) {
            "match", "match_parent" -> ViewGroup.LayoutParams.MATCH_PARENT
            "wrap", "wrap_content" -> ViewGroup.LayoutParams.WRAP_CONTENT
            else -> defaultValue
        }
    }

    private fun parseGravity(raw: String): Int {
        if (raw.isBlank()) return Gravity.NO_GRAVITY
        var result = 0
        raw.lowercase().split('|').forEach {
            result = result or when (it.trim()) {
                "center" -> Gravity.CENTER
                "center_vertical" -> Gravity.CENTER_VERTICAL
                "center_horizontal" -> Gravity.CENTER_HORIZONTAL
                "top" -> Gravity.TOP
                "bottom" -> Gravity.BOTTOM
                "start", "left" -> Gravity.START
                "end", "right" -> Gravity.END
                "fill" -> Gravity.FILL
                else -> 0
            }
        }
        return result
    }

    private fun parseScaleType(raw: String): ImageView.ScaleType = when (raw.lowercase()) {
        "fitcenter", "fit_center" -> ImageView.ScaleType.FIT_CENTER
        "centerinside", "center_inside" -> ImageView.ScaleType.CENTER_INSIDE
        "fitxy", "fit_xy" -> ImageView.ScaleType.FIT_XY
        "center" -> ImageView.ScaleType.CENTER
        else -> ImageView.ScaleType.CENTER_CROP
    }

    private fun parseColor(raw: String?): Int? = runCatching { raw?.takeIf { it.isNotBlank() }?.let(Color::parseColor) }.getOrNull()
    private fun dp(view: View, value: Float): Float = value * view.resources.displayMetrics.density
}
