package com.aujunpeak.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * Hiệu ứng mưa đỏ chết chóc rơi liên tục.
 * Mỗi hạt mưa là một vạch mỏng rơi từ trên xuống với tốc độ và alpha khác nhau.
 */
class RainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Drop(
        var x: Float,
        var y: Float,
        var speed: Float,
        var length: Float,
        var alpha: Int,
        var width: Float,
        var colorR: Int,
        var colorG: Int,
        var colorB: Int,
        var glowAlpha: Int
    )

    private val drops = mutableListOf<Drop>()
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
    }

    init {
        // BlurMaskFilter không hoạt động với hardware acceleration — bắt buộc dùng software
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private val rng = Random.Default
    private var running = false
    private val dropCount = 48

    private val palette = listOf(
        Triple(97, 225, 255),
        Triple(92, 151, 255),
        Triple(161, 137, 255),
        Triple(115, 247, 176),
        Triple(255, 216, 115),
        Triple(136, 205, 255)
    )

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            updateDrops()
            invalidate()
            postDelayed(this, 16L)
        }
    }

    fun start() {
        running = true
        post {
            if (drops.isEmpty()) spawnDrops()
            post(frameRunnable)
        }
    }

    fun stop() {
        running = false
        removeCallbacks(frameRunnable)
        drops.clear()
    }

    private fun spawnDrops() {
        drops.clear()
        val w = width.toFloat().coerceAtLeast(100f)
        val h = height.toFloat().coerceAtLeast(200f)
        repeat(dropCount) { i ->
            drops.add(createDrop(w, h, startY = (i.toFloat() / dropCount) * h))
        }
    }

    private fun createDrop(w: Float, h: Float, startY: Float = -50f): Drop {
        val color = palette[rng.nextInt(palette.size)]
        return Drop(
            x      = rng.nextFloat() * w,
            y      = startY,
            speed  = rng.nextFloat() * 5.5f + 2.8f,
            length = rng.nextFloat() * 16f + 8f,
            alpha  = rng.nextInt(160) + 50,
            width  = rng.nextFloat() * 1.8f + 0.5f,
            colorR = color.first,
            colorG = color.second,
            colorB = color.third,
            glowAlpha = rng.nextInt(60) + 20
        )
    }

    private fun updateDrops() {
        val h = height.toFloat()
        val w = width.toFloat()
        drops.forEachIndexed { i, d ->
            d.y += d.speed
            // Alpha nhấp nháy nhẹ cho cảm giác sống động
            d.alpha = (d.alpha + rng.nextInt(5) - 2).coerceIn(40, 210)
            if (d.y - d.length > h) {
                val nd = createDrop(w, h, startY = -rng.nextFloat() * 80f)
                drops[i] = nd
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) spawnDrops()
    }

    override fun onDraw(canvas: Canvas) {
        drops.forEach { d ->
            val startY = d.y - d.length
            val endY   = d.y

            // Glow xung quanh (mờ rộng hơn)
            glowPaint.color    = Color.argb(d.glowAlpha, d.colorR, d.colorG, d.colorB)
            glowPaint.strokeWidth = d.width + 2.2f
            canvas.drawLine(d.x, startY, d.x, endY, glowPaint)

            // Nhân giữa sáng
            corePaint.color    = Color.argb(d.alpha, d.colorR, d.colorG, d.colorB)
            corePaint.strokeWidth = d.width
            canvas.drawLine(d.x, startY, d.x, endY, corePaint)

            // Đầu giọt sáng nhất (điểm cuối của drop)
            corePaint.color = Color.argb(
                (d.alpha * 1.2f).toInt().coerceAtMost(255),
                220, 245, 255
            )
            corePaint.strokeWidth = d.width * 1.5f
            canvas.drawPoint(d.x, endY, corePaint)
        }
    }
}
