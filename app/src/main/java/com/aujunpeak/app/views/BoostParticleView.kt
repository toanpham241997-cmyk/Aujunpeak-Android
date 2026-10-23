package com.aujunpeak.app.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class BoostParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class BoostParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        var maxLife: Float,
        var radius: Float,
        var color: Int,
        var trail: MutableList<Pair<Float, Float>> = mutableListOf()
    )

    private val particles = mutableListOf<BoostParticle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rng = Random.Default
    private var running = false
    private var idleMode = true

    private var colors = listOf(
        Color.parseColor("#61E1FF"),
        Color.parseColor("#5C97FF"),
        Color.parseColor("#A189FF"),
        Color.parseColor("#73F7B0"),
        Color.parseColor("#FFD873")
    )

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            if (idleMode) spawnIdleParticle() else repeat(6) { spawnBoostParticle() }
            updateParticles()
            invalidate()
            postDelayed(this, 16L)
        }
    }

    /** Called via reflection from FunctionFragment / UpdateActivity */
    fun setColors(colorArray: IntArray) {
        if (colorArray.isNotEmpty()) {
            colors = colorArray.toList()
        }
    }

    fun setIdleMode(idle: Boolean) { idleMode = idle }

    fun start() { if (running) return; running = true; post(frameRunnable) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    fun stop() { running = false; removeCallbacks(frameRunnable) }

    private fun spawnIdleParticle() {
        if (particles.size >= 40) return
        val cx = width / 2f; val cy = height / 2f
        val angle = rng.nextFloat() * 2 * Math.PI.toFloat()
        val r = rng.nextFloat() * 80f + 60f
        particles.add(BoostParticle(
            x = cx + cos(angle) * r,
            y = cy + sin(angle) * r,
            vx = cos(angle) * 0.5f,
            vy = sin(angle) * 0.5f,
            life = 60f + rng.nextFloat() * 60f,
            maxLife = 120f,
            radius = rng.nextFloat() * 4f + 1f,
            color = colors[rng.nextInt(colors.size)]
        ))
    }

    private fun spawnBoostParticle() {
        val cx = width / 2f; val cy = height * 0.65f
        val angle = rng.nextFloat() * 2 * Math.PI.toFloat()
        val speed = rng.nextFloat() * 12f + 4f
        particles.add(BoostParticle(
            x = cx, y = cy,
            vx = cos(angle) * speed,
            vy = sin(angle) * speed - 6f,
            life = 40f + rng.nextFloat() * 40f,
            maxLife = 80f,
            radius = rng.nextFloat() * 8f + 2f,
            color = colors[rng.nextInt(colors.size)]
        ))
    }

    private fun updateParticles() {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.trail.add(p.x to p.y)
            if (p.trail.size > 5) p.trail.removeAt(0)
            p.x += p.vx; p.y += p.vy
            p.vy += if (idleMode) 0f else 0.2f
            p.life--
            if (p.life <= 0) it.remove()
        }
    }

    override fun onDraw(canvas: Canvas) {
        particles.forEach { p ->
            val alpha = ((p.life / p.maxLife) * 255f).toInt().coerceIn(0, 255)
            p.trail.forEachIndexed { i, (tx, ty) ->
                paint.color = p.color
                paint.alpha = (alpha * (i + 1) / p.trail.size * 0.4f).toInt()
                canvas.drawCircle(tx, ty, p.radius * 0.5f, paint)
            }
            paint.color = p.color; paint.alpha = alpha
            canvas.drawCircle(p.x, p.y, p.radius, paint)
            if (p.radius > 2.5f) {
                paint.alpha = (alpha * 0.5f).toInt().coerceIn(0,255)
                canvas.drawCircle(p.x, p.y, p.radius * 2.4f, paint)
            }
        }
    }
}
