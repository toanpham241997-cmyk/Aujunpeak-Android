package com.aujunpeak.app.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View

class RingLoaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#33FF1744")
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1744")
    }

    private val oval = RectF()
    private val innerOval = RectF()
    private var rotation = 0f
    private var innerRotation = 0f

    private val animRunnable = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow || visibility != VISIBLE) return
            rotation = (rotation + 3f) % 360f
            innerRotation = (innerRotation - 2f + 360f) % 360f
            invalidate()
            postDelayed(this, 16L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) post(animRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(animRunnable)
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        if (visibility == VISIBLE && isAttachedToWindow) {
            post(animRunnable)
        } else {
            removeCallbacks(animRunnable)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        val pad = 12f
        oval.set(pad, pad, w - pad, h - pad)
        val innerPad = 22f
        innerOval.set(innerPad, innerPad, w - innerPad, h - innerPad)
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        val cx = width / 2f
        val cy = height / 2f

        val shader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.TRANSPARENT,
                Color.parseColor("#FF1744"),
                Color.parseColor("#FF5252"),
                Color.parseColor("#FF1744")
            ),
            floatArrayOf(0f, 0.5f, 0.75f, 1f)
        )
        paint.shader = shader

        canvas.save()
        canvas.rotate(rotation, cx, cy)
        canvas.drawArc(oval, 0f, 300f, false, paint)
        canvas.restore()

        canvas.save()
        canvas.rotate(innerRotation, cx, cy)
        canvas.drawArc(innerOval, 0f, 200f, false, innerPaint)
        canvas.restore()

        dotPaint.alpha = 180
        canvas.drawCircle(cx, cy, 6f, dotPaint)
        dotPaint.alpha = 80
        canvas.drawCircle(cx, cy, 12f, dotPaint)
    }
}
