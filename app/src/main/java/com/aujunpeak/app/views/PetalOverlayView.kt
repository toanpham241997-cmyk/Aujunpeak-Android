package com.aujunpeak.app.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/**
 * Soft falling peach / cherry blossom (hoa đào) petal overlay.
 * Purely decorative — never intercepts touches, safe to place above
 * any screen content across all tabs.
 */
class PetalOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Petal(
        var x: Float,
        var y: Float,
        var size: Float,
        var fallSpeed: Float,
        var swayAmp: Float,
        var swaySpeed: Float,
        var swayPhase: Float,
        var rotation: Float,
        var rotSpeed: Float,
        var alpha: Int,
        var color: Int
    )

    private val petals = mutableListOf<Petal>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val petalPath = Path()
    private val rng = Random.Default
    private var running = false
    private var density = 16
    private var time = 0f

    private val petalColors = listOf(
        Color.parseColor("#FFC1CC"),
        Color.parseColor("#FFB7C5"),
        Color.parseColor("#FFD1DC"),
        Color.parseColor("#FF9EB5"),
        Color.parseColor("#FFE4EC")
    )

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        // Purely decorative — never consumes touches so taps pass through to content below.
        isClickable = false
        isFocusable = false
    }

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            time += 1f
            updatePetals()
            invalidate()
            postDelayed(this, 16L)
        }
    }

    fun setDensity(d: Int) {
        density = d
    }

    fun start() {
        running = true
        post {
            if (petals.isEmpty() && width > 0 && height > 0) spawnPetals()
            post(frameRunnable)
        }
    }

    fun stop() {
        running = false
        removeCallbacks(frameRunnable)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && petals.isEmpty()) spawnPetals()
    }

    private fun spawnPetals() {
        petals.clear()
        val h = height.toFloat().coerceAtLeast(200f)
        repeat(density) {
            petals.add(createPetal(startY = rng.nextFloat() * h))
        }
    }

    private fun createPetal(startY: Float): Petal {
        val w = width.toFloat().coerceAtLeast(200f)
        return Petal(
            x = rng.nextFloat() * w,
            y = startY,
            size = rng.nextFloat() * 10f + 8f,
            fallSpeed = rng.nextFloat() * 0.6f + 0.35f,
            swayAmp = rng.nextFloat() * 40f + 20f,
            swaySpeed = rng.nextFloat() * 0.02f + 0.01f,
            swayPhase = rng.nextFloat() * 6.28f,
            rotation = rng.nextFloat() * 360f,
            rotSpeed = (rng.nextFloat() - 0.5f) * 3f,
            alpha = rng.nextInt(90) + 110,
            color = petalColors[rng.nextInt(petalColors.size)]
        )
    }

    private fun updatePetals() {
        val w = width.toFloat()
        val h = height.toFloat()
        petals.forEach { p ->
            p.y += p.fallSpeed
            p.x += sin(time * p.swaySpeed + p.swayPhase) * 0.6f
            p.rotation += p.rotSpeed
            if (p.y - p.size > h) {
                p.y = -p.size
                p.x = rng.nextFloat() * w
            }
            if (p.x < -p.size) p.x = w + p.size
            if (p.x > w + p.size) p.x = -p.size
        }
    }

    override fun onDraw(canvas: Canvas) {
        petals.forEach { p ->
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.rotation)

            // Soft glow behind the petal
            paint.style = Paint.Style.FILL
            paint.color = p.color
            paint.alpha = (p.alpha * 0.3f).toInt()
            canvas.drawCircle(0f, 0f, p.size * 1.3f, paint)

            // Petal shape — a simple teardrop/heart-like oval formed via a path
            petalPath.reset()
            val s = p.size
            petalPath.moveTo(0f, -s)
            petalPath.cubicTo(s * 0.9f, -s * 0.6f, s * 0.9f, s * 0.5f, 0f, s)
            petalPath.cubicTo(-s * 0.9f, s * 0.5f, -s * 0.9f, -s * 0.6f, 0f, -s)
            petalPath.close()

            paint.alpha = p.alpha
            canvas.drawPath(petalPath, paint)

            // Center vein highlight
            paint.alpha = (p.alpha * 0.5f).toInt()
            paint.color = Color.WHITE
            canvas.drawLine(0f, -s * 0.7f, 0f, s * 0.7f, paint)
            paint.color = p.color

            canvas.restore()
        }
    }
}
