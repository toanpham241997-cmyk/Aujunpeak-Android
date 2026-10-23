package com.aujunpeak.app.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class MiniChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#FF1744")
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.parseColor("#22FF1744")
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#999999")
        textSize = 24f
    }

    private var data: List<Float> = emptyList()
    private var lineColor: Int = Color.parseColor("#FF1744")

    fun setLineColor(color: Int) {
        lineColor = color
        linePaint.color = color
        invalidate()
    }

    fun setData(values: List<Float>) {
        data = values
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0 || data.size < 2) return

        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 24f

        // Grid lines
        val gridLines = listOf(0f, 25f, 50f, 75f, 100f)
        gridLines.forEach { value ->
            val y = pad + (h - 2 * pad) * (1f - value / 100f)
            canvas.drawLine(0f, y, w, y, gridPaint)
            canvas.drawText("${value.toInt()}", 4f, y - 4f, labelPaint)
        }

        // Build path
        val path = Path()
        val fillPath = Path()
        val step = (w - pad) / (data.size - 1).toFloat()

        data.forEachIndexed { i, value ->
            val x = pad + i * step
            val y = pad + (h - 2 * pad) * (1f - (value.coerceIn(0f, 100f) / 100f))
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                val prevX = pad + (i - 1) * step
                val prevValue = data[i - 1]
                val prevY = pad + (h - 2 * pad) * (1f - (prevValue.coerceIn(0f, 100f) / 100f))
                val cpX = (prevX + x) / 2f
                path.cubicTo(cpX, prevY, cpX, y, x, y)
                fillPath.cubicTo(cpX, prevY, cpX, y, x, y)
            }
        }

        // Fill gradient
        val lastX = pad + (data.size - 1) * step
        fillPath.lineTo(lastX, h)
        fillPath.close()

        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                Color.argb(80, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor)),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)

        // Draw last point dot
        val lastValue = data.last()
        val lastY = pad + (h - 2 * pad) * (1f - (lastValue.coerceIn(0f, 100f) / 100f))
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(lastX, lastY, 6f, dotPaint)
        dotPaint.alpha = 60
        canvas.drawCircle(lastX, lastY, 12f, dotPaint)
    }
}
