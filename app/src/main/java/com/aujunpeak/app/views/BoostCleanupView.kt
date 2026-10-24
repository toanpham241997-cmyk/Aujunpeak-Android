package com.aujunpeak.app.views

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.aujunpeak.app.R
import kotlin.math.*

/**
 * BoostCleanupView — màn hình hoạt ảnh full-screen khi nhấn BOOST.
 *
 * Hiệu ứng:
 *  - Icon các ứng dụng xuất hiện từ rìa màn hình, bay theo hình xoắn ốc vào trung tâm
 *  - Thùng rác phát sáng và xung tâm ở giữa
 *  - Khi mỗi icon chạm tâm: bùng nổ tia lửa + vòng sóng năng lượng
 *  - Trails sáng phía sau icon
 *  - Vòng xung quanh tâm xoay liên tục
 *  - Counter đếm số ứng dụng đã dọn
 *  - Kết thúc: nổ to màu xanh lá → callback onComplete
 */
class BoostCleanupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ─── Data classes ─────────────────────────────────────────────────────────

    private inner class IconParticle(val icon: Drawable) {
        var x = 0f;        var y = 0f
        var vx = 0f;       var vy = 0f
        var scale = 1f;    var alphaF = 1f
        var active = false; var done = false
        var spawnFrame = 0
        val trail = ArrayDeque<Triple<Float, Float, Float>>() // x, y, scale
    }

    private data class Spark(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float,
        val radius: Float, val color: Int
    )

    private data class Ring(
        var r: Float, val maxR: Float,
        var alpha: Float, val color: Int,
        val strokeW: Float = 5f
    )

    private data class TrashDebris(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var rot: Float, var rotV: Float,
        var life: Float, val maxLife: Float,
        val size: Float, val color: Int
    )

    // ─── State ────────────────────────────────────────────────────────────────

    private enum class Phase { IDLE, RUNNING, FINALIZING, DONE }
    private var phase = Phase.IDLE
    private var frame  = 0
    private var consumed = 0
    private var total  = 0

    private val icons   = mutableListOf<IconParticle>()
    private val sparks  = mutableListOf<Spark>()
    private val rings   = mutableListOf<Ring>()
    private val debris  = mutableListOf<TrashDebris>()

    private var cx = 0f; private var cy = 0f
    private val dp by lazy { resources.displayMetrics.density }
    private val iconSizePx get() = 52f * dp

    private var vortexPulse   = 0f
    private var vortexGlowF   = 0f    // 0→1
    private var bgAlpha       = 0
    private var haloAngle     = 0f
    private var counterAlpha  = 0f
    private var lidTilt       = 0f

    private var onComplete: (() -> Unit)? = null
    private val rng = java.util.Random()

    // Pool of app icons to cycle through while spawning particles
    private var iconPool: List<Drawable> = emptyList()
    private var totalDurationMs = 10_000L
    private val elapsedMs get() = frame * FRAME_MS

    private val trashBinDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(context, R.drawable.ic_trash_bin_glow)
    }

    // ─── Colors ───────────────────────────────────────────────────────────────

    private val RED     = Color.parseColor("#7E9CFF")
    private val ORANGE  = Color.parseColor("#FF6D00")
    private val YELLOW  = Color.parseColor("#FFD600")
    private val GREEN   = Color.parseColor("#00E676")
    private val CYAN    = Color.parseColor("#00E5FF")
    private val PINK    = Color.parseColor("#FF4081")
    private val sparkPalette = intArrayOf(
        Color.parseColor("#7E9CFF"),
        Color.parseColor("#FF6D00"),
        Color.parseColor("#FFD600"),
        Color.parseColor("#FF4081"),
        Color.WHITE
    )

    // ─── Paints ───────────────────────────────────────────────────────────────

    private val pBg = Paint()
    private val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pSpark = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pTrail = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize  = 14f  // will be set dynamically
        color     = Color.WHITE
        typeface  = Typeface.DEFAULT_BOLD
    }
    private val pGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * 1f
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }
    private val pHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }
    private val pPercent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /** Đặt danh sách drawable icon ứng dụng sẽ được tái sử dụng xuyên suốt vòng quay */
    fun setIcons(drawables: List<Drawable>) {
        iconPool = drawables
    }

    /**
     * Bắt đầu hoạt ảnh dọn rác trong [durationMs] (mặc định 10 giây). Icon sẽ được
     * sinh ra liên tục và xoay vào tâm suốt thời gian này, sau đó nổ kết thúc.
     */
    fun startAnimation(durationMs: Long = 10_000L, onDone: () -> Unit) {
        onComplete = onDone
        totalDurationMs = durationMs.coerceAtLeast(2_000L)
        phase = Phase.RUNNING
        frame = 0; bgAlpha = 0; vortexGlowF = 0f; counterAlpha = 0f; lidTilt = 0f
        sparks.clear(); rings.clear(); debris.clear(); icons.clear()
        consumed = 0

        val spawnWindowMs = (totalDurationMs - 1_200L).coerceAtLeast(800L)
        val spawnIntervalMs = 210L
        val spawnCount = (spawnWindowMs / spawnIntervalMs).toInt().coerceAtLeast(8)
        total = spawnCount
        if (iconPool.isNotEmpty()) {
            for (i in 0 until spawnCount) {
                val drawable = iconPool[i % iconPool.size]
                icons.add(IconParticle(drawable).apply {
                    spawnFrame = ((i * spawnIntervalMs) / FRAME_MS).toInt() + 15
                })
            }
        }
        visibility = VISIBLE
        post(frameLoop)
    }

    fun stopAnimation() {
        phase = Phase.IDLE
        removeCallbacks(frameLoop)
        visibility = GONE
        invalidate()
    }

    // ─── Frame loop ───────────────────────────────────────────────────────────

    private val frameLoop = object : Runnable {
        override fun run() {
            if (phase == Phase.IDLE || phase == Phase.DONE) return
            update()
            invalidate()
            postDelayed(this, FRAME_MS)
        }
    }

    private fun update() {
        frame++
        haloAngle = (haloAngle + 1.8f) % 360f

        // Fade in background
        if (bgAlpha < 210) bgAlpha = (bgAlpha + 7).coerceAtMost(210)

        // Vortex glow build-up
        if (vortexGlowF < 1f) vortexGlowF = (vortexGlowF + 0.025f).coerceAtMost(1f)

        // Counter fade-in
        if (consumed > 0 && counterAlpha < 1f) counterAlpha = (counterAlpha + 0.04f).coerceAtMost(1f)

        // Vortex oscillation
        vortexPulse = sin(frame * 0.12f) * 8f

        // Lid tilt "breathing" — makes the trash bin feel alive while devouring icons
        lidTilt = sin(frame * 0.1f) * 4f

        // Emit ambient pulse rings from vortex
        if (frame % 22 == 0 && phase == Phase.RUNNING) {
            rings.add(Ring(0f, dp * 100f, 0.8f, RED, 4f * dp))
        }

        // Spawn icons (stop spawning once we've entered the finishing window)
        if (phase == Phase.RUNNING) {
            icons.forEach { if (!it.active && !it.done && frame >= it.spawnFrame) spawnIcon(it) }
        }

        // Move icons
        icons.filter { it.active && !it.done }.forEach { moveIcon(it) }

        // Update sparks
        val si = sparks.iterator()
        while (si.hasNext()) {
            val s = si.next()
            s.x += s.vx; s.y += s.vy
            s.vy += 0.35f; s.vx *= 0.96f
            s.life--
            if (s.life <= 0f) si.remove()
        }

        // Update rings
        val ri = rings.iterator()
        while (ri.hasNext()) {
            val r = ri.next()
            r.r += (r.maxR - r.r) * 0.09f
            r.alpha -= 0.018f
            if (r.alpha <= 0f) ri.remove()
        }

        // Update debris
        val di = debris.iterator()
        while (di.hasNext()) {
            val d = di.next()
            d.x += d.vx; d.y += d.vy
            d.vy += 0.25f; d.vx *= 0.98f
            d.rot += d.rotV
            d.life--
            if (d.life <= 0f) di.remove()
        }

        // Check completion — driven by elapsed time so the full boost lasts exactly
        // as long as the caller requested (e.g. ~10s), regardless of icon count.
        if (phase == Phase.RUNNING && elapsedMs >= totalDurationMs) {
            phase = Phase.FINALIZING
            spawnFinalBurst()
            postDelayed({
                phase = Phase.DONE
                removeCallbacks(frameLoop)
                onComplete?.invoke()
            }, 900)
        }
    }

    private fun spawnIcon(p: IconParticle) {
        val margin = -iconSizePx
        val side = rng.nextInt(4)
        when (side) {
            0 -> { p.x = rng.nextFloat() * width;  p.y = margin }
            1 -> { p.x = width - margin;            p.y = rng.nextFloat() * height }
            2 -> { p.x = rng.nextFloat() * width;  p.y = height - margin }
            else -> { p.x = margin;                 p.y = rng.nextFloat() * height }
        }
        val dx = cx - p.x; val dy = cy - p.y
        val dist = hypot(dx, dy).coerceAtLeast(1f)
        val speed = 3.5f + rng.nextFloat() * 2f
        p.vx = dx / dist * speed; p.vy = dy / dist * speed
        p.scale = 1f; p.alphaF = 1f; p.active = true
    }

    private fun moveIcon(p: IconParticle) {
        val dx = cx - p.x; val dy = cy - p.y
        val dist = hypot(dx, dy).coerceAtLeast(1f)

        // Attraction force toward center
        val attract = 0.18f
        p.vx += dx / dist * attract
        p.vy += dy / dist * attract

        // Spiral: add small tangential velocity
        p.vx += (-dy / dist) * 0.6f
        p.vy += (dx  / dist) * 0.6f

        // Speed cap
        val spd = hypot(p.vx, p.vy)
        if (spd > 18f) { p.vx = p.vx / spd * 18f; p.vy = p.vy / spd * 18f }

        p.x += p.vx; p.y += p.vy

        // Scale shrinks near center
        if (dist < 220f) p.scale = (dist / 220f).coerceIn(0.1f, 1f)
        p.alphaF = p.scale

        // Trail
        p.trail.addLast(Triple(p.x, p.y, p.scale))
        if (p.trail.size > 10) p.trail.removeFirst()

        // Consumed?
        if (dist < 45f * dp) consumeIcon(p)
    }

    private fun consumeIcon(p: IconParticle) {
        p.done = true; p.active = false; consumed++
        val burstCount = 24
        repeat(burstCount) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val spd = 5f + rng.nextFloat() * 14f
            sparks.add(Spark(cx, cy, cos(a) * spd, sin(a) * spd - 4f,
                28f + rng.nextFloat() * 32f, 60f,
                (2.5f + rng.nextFloat() * 5f) * dp,
                sparkPalette[rng.nextInt(sparkPalette.size)]))
        }
        // Shockwave rings
        rings.add(Ring(0f, dp * 110f, 1f, ORANGE, 6f * dp))
        rings.add(Ring(0f, dp * 70f,  0.7f, YELLOW, 3f * dp))
        // Debris squares
        repeat(6) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val spd = 3f + rng.nextFloat() * 7f
            debris.add(TrashDebris(cx, cy, cos(a) * spd, sin(a) * spd - 3f,
                rng.nextFloat() * 360f, rng.nextFloat() * 12f - 6f,
                20f + rng.nextFloat() * 20f, 40f,
                (3f + rng.nextFloat() * 5f) * dp,
                sparkPalette[rng.nextInt(sparkPalette.size)]))
        }
    }

    private fun spawnFinalBurst() {
        repeat(80) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val spd = 7f + rng.nextFloat() * 22f
            val col = if (rng.nextBoolean()) GREEN
                      else if (rng.nextBoolean()) CYAN
                      else sparkPalette[rng.nextInt(sparkPalette.size)]
            sparks.add(Spark(cx, cy, cos(a) * spd, sin(a) * spd - 8f,
                60f + rng.nextFloat() * 60f, 120f,
                (3f + rng.nextFloat() * 8f) * dp, col))
        }
        for (i in 0..5) {
            val col = if (i % 2 == 0) GREEN else CYAN
            rings.add(Ring(0f, dp * (80f + i * 45f), 1f, col, (4f + i) * dp))
        }
    }

    // ─── Draw ─────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        cx = w / 2f; cy = h * 0.52f
    }

    override fun onDraw(canvas: Canvas) {
        if (phase == Phase.IDLE) return

        // ── Background overlay ────────────────────────────────────────────────
        pBg.color = Color.argb(bgAlpha, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pBg)

        // ── Expanding rings ───────────────────────────────────────────────────
        rings.forEach { ring ->
            pRing.strokeWidth = ring.strokeW
            pRing.color = ring.color
            pRing.alpha = (ring.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, ring.r, pRing)
        }

        // ── Vortex glow gradient ──────────────────────────────────────────────
        val vortexR = (55f + vortexPulse + consumed * 3.5f).coerceAtMost(130f) * dp
        val shader = RadialGradient(
            cx, cy, vortexR * 2.2f,
            intArrayOf(
                Color.argb((vortexGlowF * 200).toInt(), 255, 60, 0),
                Color.argb((vortexGlowF * 120).toInt(), 255, 100, 0),
                Color.argb((vortexGlowF * 60).toInt(),  200, 50,  0),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.35f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        pFill.shader = shader
        canvas.drawCircle(cx, cy, vortexR * 2.2f, pFill)
        pFill.shader = null

        // ── Vortex core ───────────────────────────────────────────────────────
        pFill.color = Color.argb((vortexGlowF * 230).toInt(), 255, 80, 0)
        canvas.drawCircle(cx, cy, vortexR * 0.9f, pFill)

        // ── Spinning halo ring ────────────────────────────────────────────────
        pHalo.strokeWidth = 3.5f * dp
        pHalo.color = Color.argb((vortexGlowF * 200).toInt(), 255, 150, 0)
        pHalo.pathEffect = DashPathEffect(floatArrayOf(18f * dp, 8f * dp), haloAngle * dp)
        canvas.drawCircle(cx, cy, vortexR * 1.35f, pHalo)

        // Second halo (counter-rotate)
        pHalo.color = Color.argb((vortexGlowF * 140).toInt(), 255, 220, 0)
        pHalo.pathEffect = DashPathEffect(floatArrayOf(12f * dp, 16f * dp), (-haloAngle * 1.3f) * dp)
        canvas.drawCircle(cx, cy, vortexR * 1.65f, pHalo)

        // ── Trash bin icon (XML vector, glowing + breathing) ─────────────────
        trashBinDrawable?.let { bin ->
            val binHalf = ((26f + vortexPulse * 0.35f) * dp).toInt()
            canvas.save()
            canvas.rotate(lidTilt, cx, cy - binHalf * 0.3f)
            bin.setBounds(
                (cx - binHalf).toInt(), (cy - binHalf).toInt(),
                (cx + binHalf).toInt(), (cy + binHalf).toInt()
            )
            bin.alpha = (vortexGlowF * 255).toInt().coerceIn(0, 255)
            bin.draw(canvas)
            canvas.restore()
        }

        // ── Debris squares ────────────────────────────────────────────────────
        debris.forEach { d ->
            val lifeRatio = d.life / d.maxLife
            pFill.color = d.color
            pFill.alpha = (lifeRatio * 220).toInt().coerceIn(0, 220)
            canvas.save()
            canvas.rotate(d.rot, d.x, d.y)
            canvas.drawRect(d.x - d.size / 2f, d.y - d.size / 2f, d.x + d.size / 2f, d.y + d.size / 2f, pFill)
            canvas.restore()
        }

        // ── Icon trails ───────────────────────────────────────────────────────
        icons.filter { it.active }.forEach { p ->
            p.trail.forEachIndexed { i, (tx, ty, ts) ->
                val ratio = i.toFloat() / p.trail.size
                pTrail.color = RED
                pTrail.alpha = (ratio * ratio * 160).toInt()
                canvas.drawCircle(tx, ty, iconSizePx * ts * 0.28f * ratio, pTrail)
            }
        }

        // ── Icon particles ────────────────────────────────────────────────────
        icons.filter { it.active }.forEach { p ->
            val half = (iconSizePx * p.scale / 2f).toInt()
            if (half < 3) return@forEach

            // Outer glow
            pGlow.maskFilter = BlurMaskFilter(half * 0.6f, BlurMaskFilter.Blur.NORMAL)
            pGlow.color = RED
            pGlow.alpha = (p.alphaF * 180).toInt().coerceIn(0, 180)
            canvas.drawCircle(p.x, p.y, half.toFloat() + 4f * dp, pGlow)

            // Icon
            p.icon.setBounds(
                (p.x - half).toInt(), (p.y - half).toInt(),
                (p.x + half).toInt(), (p.y + half).toInt()
            )
            p.icon.alpha = (p.alphaF * 255).toInt().coerceIn(0, 255)
            p.icon.draw(canvas)
        }

        // ── Spark particles ───────────────────────────────────────────────────
        sparks.forEach { s ->
            val lifeR = s.life / s.maxLife
            pSpark.color = s.color
            pSpark.alpha = (lifeR * lifeR * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(s.x, s.y, s.radius * lifeR, pSpark)
        }

        // ── Counter text ──────────────────────────────────────────────────────
        if (counterAlpha > 0f) {
            val counterY = cy + (90f + vortexR * 1.4f)
            pText.textSize = 15f * dp
            pText.color = Color.WHITE
            pText.alpha = (counterAlpha * 230).toInt()
            canvas.drawText("$consumed ứng dụng rác đã bị tiêu diệt", cx, counterY, pText)

            // Sub-text
            pText.textSize = 11f * dp
            pText.color = Color.parseColor("#FF6D00")
            pText.alpha = (counterAlpha * 180).toInt()
            canvas.drawText("Đang giải phóng RAM...", cx, counterY + 22f * dp, pText)

            // Progress percentage — reflects real elapsed time out of the full boost duration
            val percent = ((elapsedMs.toFloat() / totalDurationMs.toFloat()) * 100f)
                .coerceIn(0f, 100f).toInt()
            pPercent.textSize = 13f * dp
            pPercent.color = Color.parseColor("#FFD600")
            pPercent.alpha = (counterAlpha * 220).toInt()
            canvas.drawText("$percent%", cx, counterY + 44f * dp, pPercent)
        }
    }

    companion object {
        private const val FRAME_MS = 16L
    }
}
