package com.aujunpeak.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class WaterWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var progress = 0f
    private var waveOffset1 = 0f
    private var waveOffset2 = 0f

    private val wavePaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val wavePaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#44FF1744")
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private data class Bubble(var x: Float, var y: Float, var r: Float, var speed: Float, var alpha: Int)
    private val bubbles = mutableListOf<Bubble>()

    private var primaryColor = Color.parseColor("#FF5C72")
    private var secondaryColor = Color.parseColor("#FF5252")

    private val animRunnable = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            waveOffset1 = (waveOffset1 + 0.05f) % (2 * Math.PI.toFloat())
            waveOffset2 = (waveOffset2 + 0.03f) % (2 * Math.PI.toFloat())
            updateBubbles()
            invalidate()
            postDelayed(this, 16L)
        }
    }

    fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        invalidate()
    }

    fun setColors(primary: Int, secondary: Int) {
        primaryColor = primary
        secondaryColor = secondary
    }

    private fun updateBubbles() {
        val h = height.toFloat()
        val w = width.toFloat()
        val fillY = h * (1f - progress)

        bubbles.removeAll { it.y < fillY - it.r }
        if (bubbles.size < 8 && progress > 0.05f) {
            bubbles.add(Bubble(
                x = (Math.random() * w).toFloat(),
                y = h,
                r = (Math.random() * 3f + 1f).toFloat(),
                speed = (Math.random() * 1.5f + 0.5f).toFloat(),
                alpha = (Math.random() * 120 + 40).toInt()
            ))
        }
        bubbles.forEach { b -> b.y -= b.speed }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        repeat(5) {
            bubbles.add(Bubble(
                x = (Math.random() * 300f).toFloat(),
                y = height * (0.5f + Math.random().toFloat() * 0.5f),
                r = (Math.random() * 3f + 1f).toFloat(),
                speed = (Math.random() * 1.5f + 0.5f).toFloat(),
                alpha = (Math.random() * 120 + 40).toInt()
            ))
        }
        post(animRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(animRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val radius = h / 2f
        val rect = RectF(0f, 0f, w, h)

        val fillY = h * (1f - progress)
        val amplitude = h * 0.18f
        val waveH = h * 0.08f

        // Background track
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1A1A")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, radius, radius, trackPaint)

        if (progress < 0.01f) return

        // Clip to rounded rect
        val path = Path()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(path)

        // Wave 1 path (primary)
        val wavePath1 = Path()
        wavePath1.moveTo(0f, h)
        for (x in 0..w.toInt() step 2) {
            val y = fillY + amplitude * sin((x * 0.025f + waveOffset1).toDouble()).toFloat()
            wavePath1.lineTo(x.toFloat(), y)
        }
        wavePath1.lineTo(w, h)
        wavePath1.close()

        wavePaint1.shader = LinearGradient(
            0f, fillY, 0f, h,
            intArrayOf(
                Color.argb(200, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
                Color.argb(120, Color.red(secondaryColor), Color.green(secondaryColor), Color.blue(secondaryColor))
            ),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(wavePath1, wavePaint1)

        // Wave 2 path (lighter, offset)
        val wavePath2 = Path()
        wavePath2.moveTo(0f, h)
        for (x in 0..w.toInt() step 2) {
            val y = fillY + waveH + amplitude * 0.7f * sin((x * 0.02f + waveOffset2 + 1f).toDouble()).toFloat()
            wavePath2.lineTo(x.toFloat(), y)
        }
        wavePath2.lineTo(w, h)
        wavePath2.close()

        wavePaint2.color = Color.argb(80, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
        canvas.drawPath(wavePath2, wavePaint2)

        // Glow at wave surface
        glowPaint.shader = LinearGradient(
            0f, fillY - 4f, 0f, fillY + 6f,
            intArrayOf(
                Color.argb(0, 255, 255, 255),
                Color.argb(100, 255, 200, 200),
                Color.argb(0, 255, 255, 255)
            ),
            null, Shader.TileMode.CLAMP
        )

        // Bubbles
        bubbles.forEach { b ->
            if (b.y > fillY) {
                bubblePaint.color = Color.argb(b.alpha, 255, 255, 255)
                canvas.drawCircle(b.x, b.y, b.r, bubblePaint)
            }
        }

        canvas.restore()

        // Border glow
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
    }
}
