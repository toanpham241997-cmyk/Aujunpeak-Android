package com.aujunpeak.app.dynamicui

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/** Compact animated modal for server-driven actions. */
object DynamicVfxModal {

    fun show(activity: Activity, action: JSONObject) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(action.optBoolean("cancelable", true))

        val accent = color(action.optString("accentColor", "#61E1FF"), 0xFF61E1FF.toInt())
        val accent2 = color(action.optString("accentColor2", "#A189FF"), 0xFFA189FF.toInt())
        val titleColor = color(action.optString("titleColor", "#FFFFFF"), Color.WHITE)
        val messageColor = color(action.optString("messageColor", "#9EB0CA"), 0xFF9EB0CA.toInt())
        val successText = action.optString("status", "ĐÃ KÍCH HOẠT")

        val root = FrameLayout(activity).apply {
            setPadding(dp(activity, 22), dp(activity, 12), dp(activity, 22), dp(activity, 12))
        }

        val card = VfxGlowCard(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(activity, 18), dp(activity, 17), dp(activity, 18), dp(activity, 15))
            radius = dp(activity, 22).toFloat()
            glowColor = accent
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    color(action.optString("backgroundStart", "#0A1223"), 0xFF0A1223.toInt()),
                    color(action.optString("backgroundEnd", "#121934"), 0xFF121934.toInt())
                )
            ).apply {
                cornerRadius = dp(activity, 22).toFloat()
                setStroke(dp(activity, 1), withAlpha(accent, 150))
            }
        }

        val iconWrap = FrameLayout(activity).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(withAlpha(accent, 85), withAlpha(accent2, 100))
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(activity, 1), withAlpha(accent, 185))
            }
        }
        val iconSize = dp(activity, action.optInt("iconSizeDp", 50).coerceIn(36, 72))
        card.addView(iconWrap, LinearLayout.LayoutParams(iconSize, iconSize).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(activity, 10)
        })

        val icon = DynamicFaIconView(activity).apply {
            iconName = action.optString("icon", "fa-check")
            iconColor = color(action.optString("iconColor", "#FFFFFF"), Color.WHITE)
            iconStrokeDp = action.optDouble("iconStrokeDp", 2.2).toFloat()
            setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12))
        }
        iconWrap.addView(icon, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val title = TextView(activity).apply {
            text = action.optString("title", "Aujunpeak")
            setTextColor(titleColor)
            textSize = action.optDouble("titleSizeSp", 15.5).toFloat()
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0.035f
        }
        card.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val message = TextView(activity).apply {
            text = action.optString("message")
            setTextColor(messageColor)
            textSize = action.optDouble("messageSizeSp", 10.5).toFloat()
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.08f)
        }
        card.addView(message, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(activity, 5)
        })

        if (successText.isNotBlank()) {
            val chip = TextView(activity).apply {
                text = successText
                setTextColor(accent)
                textSize = 9f
                gravity = Gravity.CENTER
                setPadding(dp(activity, 10), dp(activity, 5), dp(activity, 10), dp(activity, 5))
                background = GradientDrawable().apply {
                    cornerRadius = dp(activity, 12).toFloat()
                    setColor(withAlpha(accent, 28))
                    setStroke(dp(activity, 1), withAlpha(accent, 105))
                }
            }
            card.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(activity, 11)
            })
        }

        if (action.optBoolean("showButton", false)) {
            val button = TextView(activity).apply {
                text = action.optString("positive", "HOÀN TẤT")
                setTextColor(Color.WHITE)
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(dp(activity, 18), dp(activity, 9), dp(activity, 18), dp(activity, 9))
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(accent, accent2)
                ).apply { cornerRadius = dp(activity, 14).toFloat() }
                setOnClickListener { dialog.dismiss() }
            }
            card.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(activity, 12)
            })
        }

        root.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        })
        dialog.setContentView(root)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                dimAmount = action.optDouble("dimAmount", 0.56).toFloat().coerceIn(0f, 0.9f)
                gravity = Gravity.CENTER
            }
        }

        dialog.setOnShowListener {
            card.alpha = 0f
            card.scaleX = .86f
            card.scaleY = .86f
            card.translationY = dp(activity, 18).toFloat()
            card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(OvershootInterpolator(0.95f))
                .start()

            iconWrap.scaleX = .72f
            iconWrap.scaleY = .72f
            iconWrap.rotation = -12f
            iconWrap.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(420).setInterpolator(OvershootInterpolator(1.25f)).start()
        }

        dialog.setOnDismissListener { card.stopPulse() }
        dialog.show()

        val autoDismiss = action.optLong("autoDismissMs", 1650L).coerceIn(0L, 15_000L)
        if (autoDismiss > 0) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (dialog.isShowing && !activity.isFinishing) {
                    card.animate().alpha(0f).scaleX(.94f).scaleY(.94f).setDuration(160).withEndAction {
                        if (dialog.isShowing) dialog.dismiss()
                    }.start()
                }
            }, autoDismiss)
        }
    }

    private class VfxGlowCard(context: android.content.Context) : LinearLayout(context) {
        var glowColor: Int = 0xFF61E1FF.toInt()
        var radius: Float = 40f
        private var phase = 0f
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = resources.displayMetrics.density * 1.25f
        }
        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
        }

        init {
            setWillNotDraw(false)
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            animator.start()
        }

        override fun dispatchDraw(canvas: Canvas) {
            val inset = resources.displayMetrics.density * 2.5f
            border.color = withAlpha(glowColor, (95 + phase * 85).toInt())
            border.maskFilter = BlurMaskFilter(resources.displayMetrics.density * (4f + phase * 3f), BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, border)
            border.maskFilter = null
            border.color = withAlpha(glowColor, 135)
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, border)
            super.dispatchDraw(canvas)
        }

        fun stopPulse() {
            animator.cancel()
        }

        override fun onDetachedFromWindow() {
            animator.cancel()
            super.onDetachedFromWindow()
        }
    }

    private fun dp(context: android.content.Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + .5f).toInt()

    private fun color(raw: String?, fallback: Int): Int =
        runCatching { Color.parseColor(raw) }.getOrDefault(fallback)

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
