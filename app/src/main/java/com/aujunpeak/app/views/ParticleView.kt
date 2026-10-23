package com.aujunpeak.app.views

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var radius: Float,
        var alpha: Int,
        var alphaSpeed: Int,
        var color: Int,
        var glowRadius: Float = 0f,
        var twinkle: Float = 0f,
        var drift: Float = 0f
    )

    data class EdgeSpark(
        var progress: Float,
        var speed: Float,
        var radius: Float,
        var alpha: Int,
        var color: Int,
        var twinkle: Float,
        var trail: MutableList<Pair<Float, Float>> = mutableListOf()
    )

    private val particles = mutableListOf<Particle>()
    private val edgeSparks = mutableListOf<EdgeSpark>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.2f
    }

    private var running = false
    private var density = 70
    private val rng = Random.Default
    private var tick = 0f

    private var particleColors = listOf(
        Color.parseColor("#61E1FF"),
        Color.parseColor("#5C97FF"),
        Color.parseColor("#A189FF"),
        Color.parseColor("#73F7B0"),
        Color.parseColor("#FFD873")
    )

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            tick += 0.016f
            updateParticles()
            invalidate()
            postDelayed(this, 16L)
        }
    }

    fun setDensity(d: Int) {
        density = d.coerceAtLeast(12)
        if (width > 0 && height > 0) spawnParticles()
    }

    fun setColors(colorArray: IntArray) {
        if (colorArray.isNotEmpty()) particleColors = colorArray.toList()
        if (width > 0 && height > 0) spawnParticles()
    }

    fun start() {
        if (running) return
        running = true
        post {
            if (particles.isEmpty() || edgeSparks.isEmpty()) spawnParticles()
            post(frameRunnable)
        }
    }

    fun stop() {
        running = false
        removeCallbacks(frameRunnable)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun spawnParticles() {
        particles.clear()
        edgeSparks.clear()
        val count = density.coerceIn(16, 120)
        repeat(count) { particles.add(createParticle()) }
        repeat(max(5, count / 10)) { edgeSparks.add(createEdgeSpark()) }
    }

    private fun createParticle(): Particle {
        val w = width.toFloat().coerceAtLeast(100f)
        val h = height.toFloat().coerceAtLeast(100f)
        val edgeSpawn = rng.nextFloat() < 0.62f
        val margin = min(w, h) * 0.12f
        val pos = if (edgeSpawn) {
            when (rng.nextInt(4)) {
                0 -> rng.nextFloat() * w to rng.nextFloat() * margin
                1 -> rng.nextFloat() * w to (h - rng.nextFloat() * margin)
                2 -> rng.nextFloat() * margin to rng.nextFloat() * h
                else -> (w - rng.nextFloat() * margin) to rng.nextFloat() * h
            }
        } else {
            rng.nextFloat() * w to rng.nextFloat() * h
        }
        val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
        val speed = rng.nextFloat() * 0.42f + 0.05f
        return Particle(
            x = pos.first,
            y = pos.second,
            vx = cos(angle) * speed,
            vy = sin(angle) * speed,
            radius = rng.nextFloat() * 1.7f + 0.55f,
            alpha = rng.nextInt(60, 150),
            alphaSpeed = if (rng.nextBoolean()) 1 else -1,
            color = particleColors[rng.nextInt(particleColors.size)],
            glowRadius = rng.nextFloat() * 5f + 1.5f,
            twinkle = rng.nextFloat() * 6.28f,
            drift = rng.nextFloat() * 0.35f + 0.08f
        )
    }

    private fun createEdgeSpark(): EdgeSpark {
        return EdgeSpark(
            progress = rng.nextFloat(),
            speed = rng.nextFloat() * 0.0032f + 0.0012f,
            radius = rng.nextFloat() * 1.4f + 1.1f,
            alpha = rng.nextInt(110, 200),
            color = particleColors[rng.nextInt(particleColors.size)],
            twinkle = rng.nextFloat() * 6.28f
        )
    }

    private fun updateParticles() {
        val w = width.toFloat()
        val h = height.toFloat()
        particles.forEach { p ->
            p.twinkle += 0.04f + p.drift * 0.05f
            p.x += p.vx + sin(p.twinkle) * 0.05f
            p.y += p.vy + cos(p.twinkle * 1.1f) * 0.05f
            p.alpha += p.alphaSpeed
            if (p.alpha >= 155) p.alphaSpeed = -1
            if (p.alpha <= 18) p.alphaSpeed = 1

            if (p.x < -4f) p.x = w + 4f
            if (p.x > w + 4f) p.x = -4f
            if (p.y < -4f) p.y = h + 4f
            if (p.y > h + 4f) p.y = -4f
        }

        edgeSparks.forEach { spark ->
            spark.progress += spark.speed
            if (spark.progress > 1f) spark.progress -= 1f
            val point = pointOnBorder(spark.progress)
            spark.trail.add(point)
            if (spark.trail.size > 8) spark.trail.removeAt(0)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) spawnParticles()
    }

    private fun pointOnBorder(progress: Float): Pair<Float, Float> {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val inset = 4f
        val left = inset
        val top = inset
        val right = w - inset
        val bottom = h - inset
        val perimeter = ((right - left) + (bottom - top)) * 2f
        var d = progress * perimeter
        return when {
            d <= right - left -> (left + d) to top
            d <= (right - left) + (bottom - top) -> {
                d -= (right - left)
                right to (top + d)
            }
            d <= (right - left) * 2 + (bottom - top) -> {
                d -= (right - left) + (bottom - top)
                (right - d) to bottom
            }
            else -> {
                d -= (right - left) * 2 + (bottom - top)
                left to (bottom - d)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val connectionDist = min(width, height) * 0.22f

        for (i in particles.indices) {
            val p1 = particles[i]
            for (j in i + 1 until min(i + 6, particles.size)) {
                val p2 = particles[j]
                val dx = p1.x - p2.x
                val dy = p1.y - p2.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < connectionDist) {
                    val lineAlpha = ((1f - dist / connectionDist) * 18f).toInt().coerceIn(0, 22)
                    linePaint.color = Color.argb(
                        lineAlpha,
                        Color.red(p1.color),
                        Color.green(p1.color),
                        Color.blue(p1.color)
                    )
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint)
                }
            }
        }

        particles.forEachIndexed { index, p ->
            val twinkleAlpha = (p.alpha * (0.65f + 0.35f * (0.5f + 0.5f * sin(p.twinkle)))).toInt().coerceIn(0, 255)
            glowPaint.color = p.color
            glowPaint.alpha = (twinkleAlpha * 0.22f).toInt()
            canvas.drawCircle(p.x, p.y, p.glowRadius, glowPaint)

            paint.color = p.color
            paint.alpha = twinkleAlpha
            canvas.drawCircle(p.x, p.y, p.radius, paint)

            if (index % 7 == 0 || p.radius < 1.05f) {
                starPaint.color = p.color
                starPaint.alpha = (twinkleAlpha * 0.65f).toInt()
                val arm = p.radius * 2.1f
                canvas.drawLine(p.x - arm, p.y, p.x + arm, p.y, starPaint)
                canvas.drawLine(p.x, p.y - arm, p.x, p.y + arm, starPaint)
            }
        }

        edgeSparks.forEach { spark ->
            spark.twinkle += 0.12f
            spark.trail.forEachIndexed { i, (tx, ty) ->
                val tAlpha = ((spark.alpha * (i + 1f) / spark.trail.size) * 0.15f).toInt().coerceIn(8, 60)
                glowPaint.color = spark.color
                glowPaint.alpha = tAlpha
                canvas.drawCircle(tx, ty, spark.radius * (0.65f + i * 0.08f), glowPaint)
            }

            val (x, y) = pointOnBorder(spark.progress)
            val pulse = 0.6f + 0.4f * (0.5f + 0.5f * sin(spark.twinkle))
            glowPaint.color = spark.color
            glowPaint.alpha = (spark.alpha * 0.35f * pulse).toInt().coerceIn(20, 110)
            canvas.drawCircle(x, y, spark.radius * 5.2f * pulse, glowPaint)

            paint.color = spark.color
            paint.alpha = (spark.alpha * pulse).toInt().coerceIn(40, 255)
            canvas.drawCircle(x, y, spark.radius * 1.4f, paint)

            starPaint.color = spark.color
            starPaint.alpha = (spark.alpha * 0.65f * pulse).toInt().coerceIn(40, 200)
            val arm = spark.radius * 3.4f
            canvas.drawLine(x - arm, y, x + arm, y, starPaint)
            canvas.drawLine(x, y - arm, x, y + arm, starPaint)
        }

        drawBorderDust(canvas)
    }

    private fun drawBorderDust(canvas: Canvas) {
        val spacing = 28f
        val inset = 5f
        val left = inset
        val top = inset
        val right = width - inset
        val bottom = height - inset
        if (right <= left || bottom <= top) return

        var x = left
        while (x < right) {
            val alpha = (12 + 10 * abs(sin(tick + x * 0.02f))).toInt()
            paint.color = particleColors[(x.toInt() / 20).mod(particleColors.size)]
            paint.alpha = alpha
            canvas.drawCircle(x, top, 0.8f, paint)
            canvas.drawCircle(x, bottom, 0.8f, paint)
            x += spacing
        }
        var y = top
        while (y < bottom) {
            val alpha = (12 + 10 * abs(cos(tick + y * 0.022f))).toInt()
            paint.color = particleColors[(y.toInt() / 18).mod(particleColors.size)]
            paint.alpha = alpha
            canvas.drawCircle(left, y, 0.8f, paint)
            canvas.drawCircle(right, y, 0.8f, paint)
            y += spacing
        }
    }
}
