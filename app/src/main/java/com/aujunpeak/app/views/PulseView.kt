package com.aujunpeak.app.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class PulseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pulseRadius = 0f
    private var pulseAlpha = 200f
    private var dotRadius = 8f
    private var maxRadius = 20f

    private val runnable = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            pulseRadius += 0.8f
            pulseAlpha -= 4f
            if (pulseRadius > maxRadius || pulseAlpha <= 0) {
                pulseRadius = dotRadius
                pulseAlpha = 200f
            }
            invalidate()
            postDelayed(this, 16L)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        dotRadius = minOf(w, h) / 6f
        maxRadius = minOf(w, h) / 2f - 2f
        pulseRadius = dotRadius
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(runnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(runnable)
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        val cx = width / 2f
        val cy = height / 2f

        paint.color = Color.parseColor("#FF5C72")
        paint.alpha = pulseAlpha.toInt().coerceIn(0, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawCircle(cx, cy, pulseRadius.coerceAtLeast(1f), paint)

        paint.style = Paint.Style.FILL
        paint.alpha = 255
        canvas.drawCircle(cx, cy, dotRadius, paint)
    }
}
