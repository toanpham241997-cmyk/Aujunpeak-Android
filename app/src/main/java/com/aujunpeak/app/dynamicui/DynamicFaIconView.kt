package com.aujunpeak.app.dynamicui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Lightweight vector icon view controlled by server aliases such as:
 * fa-bolt, fa-gauge-high, fa-rocket, fa-shield-halved, fa-check, fa-power-off.
 *
 * No downloaded font/dex is required; aliases are rendered with local vector paths.
 */
class DynamicFaIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    var iconName: String = "fa-bolt"
        set(value) {
            field = value.trim().lowercase()
            invalidate()
        }

    var iconColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    var iconStrokeDp: Float = 2.1f
        set(value) {
            field = value.coerceIn(1f, 5f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat() - paddingLeft - paddingRight
        val h = height.toFloat() - paddingTop - paddingBottom
        if (w <= 0f || h <= 0f) return

        val size = min(w, h)
        val left = paddingLeft + (w - size) / 2f
        val top = paddingTop + (h - size) / 2f
        val box = RectF(left, top, left + size, top + size)

        paint.color = iconColor
        paint.strokeWidth = iconStrokeDp * resources.displayMetrics.density
        fill.color = iconColor

        when (iconName.removePrefix("fa-solid ").removePrefix("fa ")) {
            "fa-bolt", "bolt" -> drawBolt(canvas, box)
            "fa-gauge-high", "fa-gauge", "gauge" -> drawGauge(canvas, box)
            "fa-rocket", "rocket" -> drawRocket(canvas, box)
            "fa-shield-halved", "fa-shield", "shield" -> drawShield(canvas, box)
            "fa-check", "fa-circle-check", "check" -> drawCheck(canvas, box)
            "fa-power-off", "power" -> drawPower(canvas, box)
            "fa-microchip", "microchip" -> drawChip(canvas, box)
            "fa-gamepad", "gamepad" -> drawGamepad(canvas, box)
            else -> drawSpark(canvas, box)
        }
    }

    private fun drawBolt(canvas: Canvas, r: RectF) {
        val p = Path().apply {
            moveTo(r.left + r.width() * .56f, r.top + r.height() * .06f)
            lineTo(r.left + r.width() * .22f, r.top + r.height() * .54f)
            lineTo(r.left + r.width() * .47f, r.top + r.height() * .54f)
            lineTo(r.left + r.width() * .39f, r.top + r.height() * .94f)
            lineTo(r.left + r.width() * .80f, r.top + r.height() * .43f)
            lineTo(r.left + r.width() * .55f, r.top + r.height() * .43f)
            close()
        }
        canvas.drawPath(p, fill)
    }

    private fun drawGauge(canvas: Canvas, r: RectF) {
        val inset = r.width() * .12f
        val arc = RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset)
        canvas.drawArc(arc, 200f, 140f, false, paint)
        val cx = r.centerX()
        val cy = r.top + r.height() * .62f
        canvas.drawLine(cx, cy, r.left + r.width() * .72f, r.top + r.height() * .34f, paint)
        canvas.drawCircle(cx, cy, r.width() * .055f, fill)
        canvas.drawLine(r.left + r.width() * .20f, r.top + r.height() * .70f, r.left + r.width() * .80f, r.top + r.height() * .70f, paint)
    }

    private fun drawRocket(canvas: Canvas, r: RectF) {
        val p = Path().apply {
            moveTo(r.centerX(), r.top + r.height() * .08f)
            cubicTo(r.left + r.width() * .76f, r.top + r.height() * .20f, r.left + r.width() * .78f, r.top + r.height() * .55f, r.centerX(), r.top + r.height() * .74f)
            cubicTo(r.left + r.width() * .22f, r.top + r.height() * .55f, r.left + r.width() * .24f, r.top + r.height() * .20f, r.centerX(), r.top + r.height() * .08f)
            close()
        }
        canvas.drawPath(p, paint)
        canvas.drawCircle(r.centerX(), r.top + r.height() * .37f, r.width() * .08f, paint)
        canvas.drawLine(r.left + r.width() * .38f, r.top + r.height() * .70f, r.left + r.width() * .27f, r.top + r.height() * .88f, paint)
        canvas.drawLine(r.left + r.width() * .62f, r.top + r.height() * .70f, r.left + r.width() * .73f, r.top + r.height() * .88f, paint)
        canvas.drawLine(r.centerX(), r.top + r.height() * .76f, r.centerX(), r.top + r.height() * .94f, paint)
    }

    private fun drawShield(canvas: Canvas, r: RectF) {
        val p = Path().apply {
            moveTo(r.centerX(), r.top + r.height() * .08f)
            lineTo(r.left + r.width() * .80f, r.top + r.height() * .22f)
            lineTo(r.left + r.width() * .74f, r.top + r.height() * .63f)
            cubicTo(r.left + r.width() * .69f, r.top + r.height() * .78f, r.left + r.width() * .58f, r.top + r.height() * .88f, r.centerX(), r.top + r.height() * .94f)
            cubicTo(r.left + r.width() * .42f, r.top + r.height() * .88f, r.left + r.width() * .31f, r.top + r.height() * .78f, r.left + r.width() * .26f, r.top + r.height() * .63f)
            lineTo(r.left + r.width() * .20f, r.top + r.height() * .22f)
            close()
        }
        canvas.drawPath(p, paint)
        canvas.drawLine(r.centerX(), r.top + r.height() * .13f, r.centerX(), r.top + r.height() * .87f, paint)
    }

    private fun drawCheck(canvas: Canvas, r: RectF) {
        val cx = r.centerX()
        val cy = r.centerY()
        canvas.drawCircle(cx, cy, r.width() * .40f, paint)
        val p = Path().apply {
            moveTo(r.left + r.width() * .29f, r.top + r.height() * .53f)
            lineTo(r.left + r.width() * .44f, r.top + r.height() * .67f)
            lineTo(r.left + r.width() * .72f, r.top + r.height() * .35f)
        }
        canvas.drawPath(p, paint)
    }

    private fun drawPower(canvas: Canvas, r: RectF) {
        val arc = RectF(r.left + r.width() * .15f, r.top + r.height() * .15f, r.right - r.width() * .15f, r.bottom - r.height() * .15f)
        canvas.drawArc(arc, -45f, 270f, false, paint)
        canvas.drawLine(r.centerX(), r.top + r.height() * .08f, r.centerX(), r.top + r.height() * .48f, paint)
    }

    private fun drawChip(canvas: Canvas, r: RectF) {
        val chip = RectF(r.left + r.width() * .24f, r.top + r.height() * .24f, r.right - r.width() * .24f, r.bottom - r.height() * .24f)
        canvas.drawRoundRect(chip, r.width() * .08f, r.width() * .08f, paint)
        for (i in 0..3) {
            val f = .30f + i * .13f
            canvas.drawLine(r.left + r.width() * f, r.top + r.height() * .12f, r.left + r.width() * f, r.top + r.height() * .24f, paint)
            canvas.drawLine(r.left + r.width() * f, r.bottom - r.height() * .24f, r.left + r.width() * f, r.bottom - r.height() * .12f, paint)
            canvas.drawLine(r.left + r.width() * .12f, r.top + r.height() * f, r.left + r.width() * .24f, r.top + r.height() * f, paint)
            canvas.drawLine(r.right - r.width() * .24f, r.top + r.height() * f, r.right - r.width() * .12f, r.top + r.height() * f, paint)
        }
    }

    private fun drawGamepad(canvas: Canvas, r: RectF) {
        val body = RectF(r.left + r.width() * .10f, r.top + r.height() * .27f, r.right - r.width() * .10f, r.bottom - r.height() * .22f)
        canvas.drawRoundRect(body, r.width() * .20f, r.width() * .20f, paint)
        val cx = r.left + r.width() * .32f
        val cy = r.centerY()
        canvas.drawLine(cx - r.width() * .09f, cy, cx + r.width() * .09f, cy, paint)
        canvas.drawLine(cx, cy - r.width() * .09f, cx, cy + r.width() * .09f, paint)
        canvas.drawCircle(r.left + r.width() * .68f, r.top + r.height() * .43f, r.width() * .035f, fill)
        canvas.drawCircle(r.left + r.width() * .78f, r.top + r.height() * .55f, r.width() * .035f, fill)
    }

    private fun drawSpark(canvas: Canvas, r: RectF) {
        val cx = r.centerX()
        val cy = r.centerY()
        canvas.drawLine(cx, r.top + r.height() * .12f, cx, r.bottom - r.height() * .12f, paint)
        canvas.drawLine(r.left + r.width() * .12f, cy, r.right - r.width() * .12f, cy, paint)
        canvas.drawLine(r.left + r.width() * .25f, r.top + r.height() * .25f, r.right - r.width() * .25f, r.bottom - r.height() * .25f, paint)
        canvas.drawLine(r.right - r.width() * .25f, r.top + r.height() * .25f, r.left + r.width() * .25f, r.bottom - r.height() * .25f, paint)
    }
}
