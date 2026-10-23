package com.aujunpeak.app.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Original Aujunpeak VN intro mark drawn entirely with Android Canvas/Kotlin.
 * No bitmap logo or font asset is required.
 */
class IntroBrandView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private var masterProgress = 0f
    private var onFinished: (() -> Unit)? = null
    private var onVoiceCue: (() -> Unit)? = null
    private var voiceCueTriggered = false
    private var animator: ValueAnimator? = null

    private val wolfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val wolfFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(28, 255, 20, 45)
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f * density
        strokeCap = Paint.Cap.ROUND
        color = Color.rgb(255, 72, 88)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
    }
    private val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f * density
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
        color = Color.argb(120, 255, 70, 80)
    }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.2f * density
    }
    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.1f * density
        color = Color.WHITE
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(175, 210, 216, 228)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun start(
        onVoiceCue: (() -> Unit)? = null,
        onFinished: () -> Unit
    ) {
        this.onVoiceCue = onVoiceCue
        this.onFinished = onFinished
        voiceCueTriggered = false
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3650L
            interpolator = DecelerateInterpolator(1.12f)
            addUpdateListener {
                masterProgress = it.animatedValue as Float
                if (!voiceCueTriggered && masterProgress >= 0.34f) {
                    voiceCueTriggered = true
                    this@IntroBrandView.onVoiceCue?.invoke()
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    postDelayed({ this@IntroBrandView.onFinished?.invoke() }, 140L)
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        onVoiceCue = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        drawAmbientRedGlow(canvas)

        val logoProgress = phase(0.04f, 0.38f)
        val textProgress = phase(0.30f, 0.78f)
        val detailProgress = phase(0.60f, 0.90f)

        val title = "Aujunpeak VN"
        val maxTextSize = 48f * density
        val textSize = min(maxTextSize, width * 0.093f)
        textPaint.textSize = textSize
        textStrokePaint.textSize = textSize
        val textWidth = textPaint.measureText(title)
        val logoSize = min(width * 0.17f, 80f * density)
        val gap = 16f * density
        val totalWidth = logoSize + gap + textWidth
        val groupLeft = ((width - totalWidth) / 2f).coerceAtLeast(18f * density)
        val centerY = height * 0.47f
        val logoCx = groupLeft + logoSize / 2f
        val textX = groupLeft + logoSize + gap
        val baseline = centerY + textSize * 0.30f

        drawWolf(canvas, logoCx, centerY, logoSize, logoProgress, detailProgress)
        drawTitle(canvas, title, textX, baseline, textWidth, textProgress)
        drawSparks(canvas, logoCx, centerY, logoSize, textX, baseline, textWidth)

        if (detailProgress > 0f) {
            subtitlePaint.textSize = 8.5f * density
            subtitlePaint.alpha = (detailProgress * 175f).toInt().coerceIn(0, 175)
            canvas.drawText(
                "SMART CONTROL INTERFACE",
                width / 2f,
                centerY + logoSize * 0.76f,
                subtitlePaint
            )
        }
    }

    private fun drawAmbientRedGlow(canvas: Canvas) {
        val voiceRise = phase(0.32f, 0.46f)
        val voiceFall = 1f - phase(0.58f, 0.76f)
        val voiceEnergy = (voiceRise * voiceFall).coerceIn(0f, 1f)
        val alpha = (18f + 22f * sin(masterProgress * Math.PI.toFloat()) + 34f * voiceEnergy)
            .toInt().coerceIn(0, 78)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alpha, 255, 20, 42)
            maskFilter = BlurMaskFilter((85f + 24f * voiceEnergy) * density, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(width * 0.50f, height * 0.47f, min(width, height) * (0.20f + 0.025f * voiceEnergy), glow)

        if (voiceEnergy > 0f) {
            val pulse = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (0.9f + voiceEnergy * 1.1f) * density
                color = Color.argb((80f * voiceEnergy).toInt(), 255, 48, 60)
                maskFilter = BlurMaskFilter(10f * density, BlurMaskFilter.Blur.NORMAL)
            }
            val radius = min(width, height) * (0.16f + 0.035f * voiceEnergy)
            canvas.drawCircle(width * 0.50f, height * 0.47f, radius, pulse)
        }
    }

    private fun drawWolf(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        progress: Float,
        detailProgress: Float
    ) {
        val s = size
        val p = Path().apply {
            moveTo(cx - 0.42f * s, cy - 0.15f * s)
            lineTo(cx - 0.50f * s, cy - 0.52f * s)
            lineTo(cx - 0.19f * s, cy - 0.39f * s)
            quadTo(cx, cy - 0.51f * s, cx + 0.19f * s, cy - 0.39f * s)
            lineTo(cx + 0.50f * s, cy - 0.52f * s)
            lineTo(cx + 0.42f * s, cy - 0.15f * s)
            quadTo(cx + 0.38f * s, cy + 0.18f * s, cx + 0.18f * s, cy + 0.37f * s)
            lineTo(cx, cy + 0.52f * s)
            lineTo(cx - 0.18f * s, cy + 0.37f * s)
            quadTo(cx - 0.38f * s, cy + 0.18f * s, cx - 0.42f * s, cy - 0.15f * s)
            close()
        }

        wolfFillPaint.alpha = (progress * 36f).toInt().coerceIn(0, 36)
        canvas.drawPath(p, wolfFillPaint)

        wolfPaint.shader = LinearGradient(
            cx - s / 2f, cy - s / 2f,
            cx + s / 2f, cy + s / 2f,
            intArrayOf(
                Color.rgb(255, 40, 45),
                Color.rgb(215, 0, 35),
                Color.rgb(255, 86, 38)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        wolfPaint.setShadowLayer(12f * density, 0f, 0f, Color.argb(170, 255, 15, 35))

        val measure = PathMeasure(p, false)
        val segment = Path()
        measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), segment, true)
        canvas.drawPath(segment, wolfPaint)

        if (detailProgress > 0f) {
            eyePaint.alpha = (detailProgress * 255f).toInt().coerceIn(0, 255)
            eyePaint.setShadowLayer(8f * density, 0f, 0f, Color.RED)
            canvas.drawLine(cx - 0.23f * s, cy - 0.05f * s, cx - 0.07f * s, cy + 0.01f * s, eyePaint)
            canvas.drawLine(cx + 0.23f * s, cy - 0.05f * s, cx + 0.07f * s, cy + 0.01f * s, eyePaint)
            canvas.drawLine(cx - 0.10f * s, cy + 0.17f * s, cx, cy + 0.27f * s, eyePaint)
            canvas.drawLine(cx, cy + 0.27f * s, cx + 0.10f * s, cy + 0.17f * s, eyePaint)
        }
    }

    private fun drawTitle(
        canvas: Canvas,
        title: String,
        x: Float,
        baseline: Float,
        textWidth: Float,
        progress: Float
    ) {
        if (progress <= 0f) return
        val revealWidth = textWidth * progress.coerceIn(0f, 1f)
        val save = canvas.save()
        canvas.clipRect(RectF(x - 8f * density, baseline - textPaint.textSize * 1.25f, x + revealWidth + 8f * density, baseline + 12f * density))

        textPaint.shader = LinearGradient(
            x, baseline - textPaint.textSize,
            x + textWidth, baseline,
            intArrayOf(
                Color.rgb(255, 68, 75),
                Color.rgb(225, 5, 38),
                Color.rgb(255, 106, 48)
            ),
            floatArrayOf(0f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )
        textPaint.setShadowLayer(14f * density, 0f, 0f, Color.argb(190, 255, 20, 45))
        textStrokePaint.alpha = (progress * 170f).toInt().coerceIn(0, 170)
        canvas.drawText(title, x, baseline, textStrokePaint)
        canvas.drawText(title, x, baseline, textPaint)
        canvas.restoreToCount(save)

        // Bright tracing glint follows the text reveal edge. This is intentionally
        // thin and short-lived so it feels like a saber/light-pen drawing the word.
        if (progress in 0.02f..0.995f) {
            val gx = x + revealWidth
            scanPaint.alpha = (210f * (1f - kotlin.math.abs(progress - 0.55f) * 0.45f)).toInt().coerceIn(80, 220)
            scanPaint.setShadowLayer(12f * density, 0f, 0f, Color.rgb(255, 38, 58))
            canvas.drawLine(
                gx,
                baseline - textPaint.textSize * 0.92f,
                gx,
                baseline + 5f * density,
                scanPaint
            )
        }

        val underlineProgress = phase(0.52f, 0.91f)
        if (underlineProgress > 0f) {
            linePaint.shader = LinearGradient(
                x, baseline,
                x + textWidth, baseline,
                Color.TRANSPARENT,
                Color.rgb(255, 45, 65),
                Shader.TileMode.CLAMP
            )
            linePaint.alpha = (underlineProgress * 210f).toInt().coerceIn(0, 210)
            linePaint.setShadowLayer(7f * density, 0f, 0f, Color.RED)
            val y = baseline + 8f * density
            canvas.drawLine(x, y, x + textWidth * underlineProgress, y, linePaint)
        }
    }

    private fun drawSparks(
        canvas: Canvas,
        logoCx: Float,
        cy: Float,
        logoSize: Float,
        textX: Float,
        baseline: Float,
        textWidth: Float
    ) {
        val sparkProgress = phase(0.22f, 1f)
        if (sparkProgress <= 0f) return
        for (i in 0 until 26) {
            val seed = i * 0.731f
            val t = masterProgress * 9f + seed
            val aroundLogo = i < 13
            val x = if (aroundLogo) {
                logoCx + cos(t * 0.72f + seed) * logoSize * (0.47f + (i % 3) * 0.07f)
            } else {
                textX + ((i - 13) / 12f) * textWidth + sin(t + seed) * 6f * density
            }
            val y = if (aroundLogo) {
                cy + sin(t * 0.84f + seed) * logoSize * (0.42f + (i % 2) * 0.08f)
            } else {
                baseline - textPaint.textSize * 0.48f + cos(t * 0.91f + seed) * 18f * density
            }
            val blink = 0.30f + 0.70f * abs(sin(t * 1.7f))
            val alpha = (sparkProgress * blink * 150f).toInt().coerceIn(0, 150)
            sparkPaint.color = if (i % 4 == 0) Color.rgb(255, 160, 95) else Color.rgb(255, 45, 66)
            sparkPaint.alpha = alpha
            sparkPaint.setShadowLayer(5f * density, 0f, 0f, sparkPaint.color)
            canvas.drawCircle(x, y, (0.45f + (i % 3) * 0.23f) * density, sparkPaint)
        }
    }

    private fun phase(start: Float, end: Float): Float {
        if (masterProgress <= start) return 0f
        if (masterProgress >= end) return 1f
        return ((masterProgress - start) / (end - start)).coerceIn(0f, 1f)
    }
}
