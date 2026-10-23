package com.aujunpeak.app.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CircularGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 20f
        color = Color.parseColor("#163A4D66")
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 20f
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 28f
    }

    private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#22FFFFFF")
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rect = RectF()
    private var colorStart = Color.parseColor("#57D9FF")
    private var colorEnd = Color.parseColor("#937CFF")
    private var progress = 0f
    private var phase = 0f
    private var orbitAngle = 0f

    private val loop = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow || visibility != VISIBLE) return
            phase = (phase + 3f) % 360f
            orbitAngle = (orbitAngle + 4f) % 360f
            invalidate()
            postDelayed(this, 16L)
        }
    }

    fun setGaugeColors(start: Int, end: Int) {
        colorStart = start
        colorEnd = end
        invalidate()
    }

    fun setProgressCompat(value: Float) {
        val target = value.coerceIn(0f, 100f)
        val start = progress
        ValueAnimator.ofFloat(start, target).apply {
            duration = 520
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(loop)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(loop)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        val pad = size * 0.14f
        rect.set(pad, pad, size - pad, size - pad)
        val stroke = size * 0.11f
        trackPaint.strokeWidth = stroke
        progressPaint.strokeWidth = stroke
        glowPaint.strokeWidth = stroke + 10f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val startAngle = -90f
        val sweep = 360f * (progress / 100f)

        // background disc
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                cx, cy, size * 0.5f,
                intArrayOf(Color.parseColor("#1AFFFFFF"), Color.parseColor("#00000000")),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, size * 0.48f, bgPaint)

        // track
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)

        val shader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.TRANSPARENT,
                colorStart,
                colorEnd,
                colorStart
            ),
            floatArrayOf(0f, 0.28f, 0.72f, 1f)
        )
        val matrix = Matrix()
        matrix.postRotate(phase - 90f, cx, cy)
        shader.setLocalMatrix(matrix)
        progressPaint.shader = shader
        glowPaint.shader = shader
        glowPaint.alpha = 60

        canvas.drawArc(rect, startAngle, sweep, false, glowPaint)
        canvas.drawArc(rect, startAngle, sweep, false, progressPaint)

        // inner decorative ring
        canvas.drawCircle(cx, cy, size * 0.26f, innerRingPaint)
        canvas.drawCircle(cx, cy, size * 0.32f, innerRingPaint)

        // orbiting dot
        val orbitRadius = size * 0.39f
        val rad = Math.toRadians((orbitAngle - 90f).toDouble())
        val dotX = (cx + cos(rad) * orbitRadius).toFloat()
        val dotY = (cy + sin(rad) * orbitRadius).toFloat()
        dotPaint.color = colorStart
        dotPaint.alpha = 200
        canvas.drawCircle(dotX, dotY, size * 0.03f, dotPaint)
        dotPaint.alpha = 70
        canvas.drawCircle(dotX, dotY, size * 0.055f, dotPaint)
    }
}
