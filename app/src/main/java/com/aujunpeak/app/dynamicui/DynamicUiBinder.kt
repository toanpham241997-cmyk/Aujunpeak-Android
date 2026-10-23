package com.aujunpeak.app.dynamicui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import com.aujunpeak.app.views.ParticleView
import com.bumptech.glide.Glide
import org.json.JSONArray
import org.json.JSONObject

object DynamicUiBinder {

    fun apply(activity: Activity, root: View, screenKey: String?) {
        val config = DynamicUiStore.loadConfig(activity)
        applyWindow(activity, config)

        val global = config.optJSONObject("global") ?: JSONObject()
        val screen = screenKey?.let { config.optJSONObject("screens")?.optJSONObject(it) }

        screen?.optJSONObject("rootBackground")?.let { root.background = drawable(root, it) }
        applyParticleDefaults(root, global)

        val globalViews = global.optJSONObject("views") ?: JSONObject()
        val screenViews = screen?.optJSONObject("views") ?: JSONObject()
        applyTree(activity, root, globalViews, screenViews)
        reorderTree(root, globalViews, screenViews)
        DynamicComponentRenderer.render(activity, root, screenKey, config, screen)
        enforceRedBlackChrome(root)
    }

    private fun enforceRedBlackChrome(root: View) {
        val red = android.graphics.Color.parseColor("#FFFF1744")
        val black = android.graphics.Color.BLACK

        fun chrome(view: View, topOnlyRadius: Boolean) {
            view.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(black)
                setStroke(dp(view, 1f).toInt().coerceAtLeast(1), red)
                if (topOnlyRadius) {
                    cornerRadii = floatArrayOf(
                        dp(view, 10f), dp(view, 10f), dp(view, 10f), dp(view, 10f),
                        0f, 0f, 0f, 0f
                    )
                } else {
                    cornerRadius = dp(view, 10f)
                }
            }
        }

        root.findViewById<View>(com.aujunpeak.app.R.id.appHeader)?.let { chrome(it, true) }
        root.findViewById<View>(com.aujunpeak.app.R.id.customBottomNav)?.let { chrome(it, true) }

        walk(root) { view ->
            if (view.id == View.NO_ID) return@walk
            val name = runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull() ?: return@walk
            if (name.startsWith("navGlow")) {
                view.background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(0x16000000)
                    setStroke(dp(view, 1f).toInt().coerceAtLeast(1), red)
                    cornerRadius = dp(view, 9f)
                }
            }
        }
    }

    private fun applyWindow(activity: Activity, config: JSONObject) {
        val global = config.optJSONObject("global") ?: return
        parseColor(global.optString("statusBarColor"))?.let { activity.window.statusBarColor = it }
        parseColor(global.optString("navigationBarColor"))?.let { activity.window.navigationBarColor = it }
    }

    private fun applyParticleDefaults(root: View, global: JSONObject) {
        val colors = global.optJSONArray("particleColors")?.toIntArrayColors()
        val density = global.optInt("particleDensity", -1)
        walk(root) { view ->
            if (view is ParticleView) {
                if (colors != null && colors.isNotEmpty()) view.setColors(colors)
                if (density > 0) view.setDensity(density)
                view.start()
            }
        }
    }

    private fun applyTree(activity: Activity, root: View, globalViews: JSONObject, screenViews: JSONObject) {
        walk(root) { view ->
            if (view.id == View.NO_ID) return@walk
            val idName = runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull() ?: return@walk
            val style = merge(globalViews.optJSONObject(idName), screenViews.optJSONObject(idName)) ?: return@walk
            applyStyle(activity, root, view, style)
        }
    }

    private fun reorderTree(root: View, globalViews: JSONObject, screenViews: JSONObject) {
        walk(root) { view ->
            val group = view as? LinearLayout ?: return@walk
            if (group.childCount < 2) return@walk
            val children = (0 until group.childCount).map { group.getChildAt(it) }
            val styled = children.mapIndexed { index, child ->
                val idName = if (child.id != View.NO_ID) runCatching { child.resources.getResourceEntryName(child.id) }.getOrNull() else null
                val style = idName?.let { merge(globalViews.optJSONObject(it), screenViews.optJSONObject(it)) }
                Triple(child, style?.optInt("order", Int.MAX_VALUE) ?: Int.MAX_VALUE, index)
            }
            if (styled.none { it.second != Int.MAX_VALUE }) return@walk
            val sorted = styled.sortedWith(compareBy<Triple<View, Int, Int>> { it.second }.thenBy { it.third })
            if (sorted.map { it.first } == children) return@walk
            val params = children.associateWith { it.layoutParams }
            group.removeAllViews()
            sorted.forEach { triple -> group.addView(triple.first, params[triple.first]) }
        }
    }

    internal fun applyStyle(activity: Activity, root: View, view: View, style: JSONObject) {
        if (style.has("alpha")) view.alpha = style.optDouble("alpha", 1.0).toFloat().coerceIn(0f, 1f)
        if (style.has("enabled")) view.isEnabled = style.optBoolean("enabled", true)
        if (style.has("visibility")) {
            view.visibility = when (style.optString("visibility").lowercase()) {
                "gone" -> View.GONE
                "invisible" -> View.INVISIBLE
                else -> View.VISIBLE
            }
        }
        if (style.has("rotation")) view.rotation = style.optDouble("rotation", 0.0).toFloat()
        if (style.has("scaleX")) view.scaleX = style.optDouble("scaleX", 1.0).toFloat()
        if (style.has("scaleY")) view.scaleY = style.optDouble("scaleY", 1.0).toFloat()
        if (style.has("elevationDp")) view.elevation = dp(view, style.optDouble("elevationDp", 0.0).toFloat())

        style.optJSONObject("background")?.let { view.background = drawable(view, it) }

        applySizeAndSpacing(view, style)

        if (view is TextView) {
            if (style.has("text")) view.text = style.optString("text")
            if (style.has("hint")) view.hint = style.optString("hint")
            parseColor(style.optString("textColor"))?.let { view.setTextColor(it) }
            parseColor(style.optString("hintColor"))?.let { view.setHintTextColor(it) }
            if (style.has("textSizeSp")) view.textSize = style.optDouble("textSizeSp").toFloat()
            if (style.has("letterSpacing")) view.letterSpacing = style.optDouble("letterSpacing").toFloat()
            val bold = style.optBoolean("bold", false)
            val italic = style.optBoolean("italic", false)
            if (bold || italic) {
                val flag = when {
                    bold && italic -> Typeface.BOLD_ITALIC
                    bold -> Typeface.BOLD
                    else -> Typeface.ITALIC
                }
                view.setTypeface(view.typeface, flag)
            }
        }

        if (view is ImageView) {
            parseColor(style.optString("tint"))?.let { view.setColorFilter(it) }
            style.optString("scaleType").takeIf { it.isNotBlank() }?.let {
                view.scaleType = when (it.lowercase()) {
                    "centercrop" -> ImageView.ScaleType.CENTER_CROP
                    "fitcenter" -> ImageView.ScaleType.FIT_CENTER
                    "centerinside" -> ImageView.ScaleType.CENTER_INSIDE
                    "fitxy" -> ImageView.ScaleType.FIT_XY
                    else -> view.scaleType
                }
            }
            val asset = style.optString("asset")
            if (asset.isNotBlank()) {
                DynamicUiStore.assetFile(activity, asset)?.let { file ->
                    Glide.with(view).load(file).into(view)
                }
            }
        }

        if (view is CompoundButton && style.has("checked")) {
            view.isChecked = style.optBoolean("checked", false)
        }

        if (style.has("onClick")) {
            view.isClickable = true
            view.setOnClickListener { DynamicActionEngine.execute(activity, root, style.opt("onClick")) }
        }
        if (style.has("onLongClick")) {
            view.setOnLongClickListener {
                DynamicActionEngine.execute(activity, root, style.opt("onLongClick"))
                true
            }
        }

        if (view is ParticleView) {
            style.optJSONArray("particleColors")?.toIntArrayColors()?.let {
                if (it.isNotEmpty()) view.setColors(it)
            }
            val density = style.optInt("particleDensity", -1)
            if (density > 0) view.setDensity(density)
            if (style.optBoolean("particlesEnabled", true)) view.start() else view.stop()
        }
    }

    private fun applySizeAndSpacing(view: View, style: JSONObject) {
        val lp = view.layoutParams ?: return
        if (style.has("widthDp")) lp.width = dp(view, style.optDouble("widthDp").toFloat()).toInt()
        if (style.has("heightDp")) lp.height = dp(view, style.optDouble("heightDp").toFloat()).toInt()
        if (style.optString("width").equals("match", true)) lp.width = ViewGroup.LayoutParams.MATCH_PARENT
        if (style.optString("width").equals("wrap", true)) lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
        if (style.optString("height").equals("match", true)) lp.height = ViewGroup.LayoutParams.MATCH_PARENT
        if (style.optString("height").equals("wrap", true)) lp.height = ViewGroup.LayoutParams.WRAP_CONTENT

        val marginLp = lp as? ViewGroup.MarginLayoutParams
        if (marginLp != null) {
            fun m(key: String, current: Int): Int = if (style.has(key)) dp(view, style.optDouble(key).toFloat()).toInt() else current
            marginLp.setMargins(
                m("marginStartDp", marginLp.leftMargin),
                m("marginTopDp", marginLp.topMargin),
                m("marginEndDp", marginLp.rightMargin),
                m("marginBottomDp", marginLp.bottomMargin)
            )
        }
        view.layoutParams = lp

        if (style.has("paddingDp")) {
            val p = dp(view, style.optDouble("paddingDp").toFloat()).toInt()
            view.setPadding(p, p, p, p)
        } else {
            val start = if (style.has("paddingStartDp")) dp(view, style.optDouble("paddingStartDp").toFloat()).toInt() else view.paddingLeft
            val top = if (style.has("paddingTopDp")) dp(view, style.optDouble("paddingTopDp").toFloat()).toInt() else view.paddingTop
            val end = if (style.has("paddingEndDp")) dp(view, style.optDouble("paddingEndDp").toFloat()).toInt() else view.paddingRight
            val bottom = if (style.has("paddingBottomDp")) dp(view, style.optDouble("paddingBottomDp").toFloat()).toInt() else view.paddingBottom
            view.setPadding(start, top, end, bottom)
        }
    }

    private fun drawable(view: View, json: JSONObject): GradientDrawable {
        val colorsJson = json.optJSONArray("colors")
        val colors = if (colorsJson != null && colorsJson.length() > 0) {
            IntArray(colorsJson.length()) { i -> parseColor(colorsJson.optString(i)) ?: Color.TRANSPARENT }
        } else {
            intArrayOf(parseColor(json.optString("color")) ?: Color.TRANSPARENT)
        }
        val orientation = when (json.optString("orientation", "LEFT_RIGHT").uppercase()) {
            "RIGHT_LEFT" -> GradientDrawable.Orientation.RIGHT_LEFT
            "TOP_BOTTOM" -> GradientDrawable.Orientation.TOP_BOTTOM
            "BOTTOM_TOP" -> GradientDrawable.Orientation.BOTTOM_TOP
            "TL_BR" -> GradientDrawable.Orientation.TL_BR
            "TR_BL" -> GradientDrawable.Orientation.TR_BL
            "BL_TR" -> GradientDrawable.Orientation.BL_TR
            "BR_TL" -> GradientDrawable.Orientation.BR_TL
            else -> GradientDrawable.Orientation.LEFT_RIGHT
        }
        return GradientDrawable(orientation, if (colors.size == 1) intArrayOf(colors[0], colors[0]) else colors).apply {
            cornerRadius = dp(view, json.optDouble("radiusDp", 0.0).toFloat())
            val strokeColor = parseColor(json.optString("strokeColor"))
            val strokeWidth = json.optDouble("strokeWidthDp", 0.0).toFloat()
            if (strokeColor != null && strokeWidth > 0f) setStroke(dp(view, strokeWidth).toInt().coerceAtLeast(1), strokeColor)
        }
    }

    private fun merge(a: JSONObject?, b: JSONObject?): JSONObject? {
        if (a == null && b == null) return null
        val out = JSONObject()
        a?.keys()?.forEach { key -> out.put(key, a.get(key)) }
        b?.keys()?.forEach { key -> out.put(key, b.get(key)) }
        return out
    }

    private fun walk(root: View, block: (View) -> Unit) {
        block(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) walk(root.getChildAt(i), block)
    }

    private fun JSONArray.toIntArrayColors(): IntArray {
        val result = mutableListOf<Int>()
        for (i in 0 until length()) parseColor(optString(i))?.let { result.add(it) }
        return result.toIntArray()
    }

    private fun parseColor(raw: String?): Int? = runCatching {
        raw?.takeIf { it.isNotBlank() }?.let { Color.parseColor(it) }
    }.getOrNull()

    private fun dp(view: View, value: Float): Float = value * view.resources.displayMetrics.density
}
