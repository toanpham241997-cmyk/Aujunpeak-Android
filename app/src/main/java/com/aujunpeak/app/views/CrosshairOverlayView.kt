package com.aujunpeak.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class CrosshairOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class CrosshairType {
        DOT, CROSS, CIRCLE, DIAMOND, TARGET, T_SHAPE,
        PLUS, HOLLOW_SQUARE, TRIPLE_DOT, ARROW, CHEVRON, HAIRLINE
    }

    var crosshairType: CrosshairType = CrosshairType.DOT
    var crosshairSize: Float = 40f
    var offsetX: Float = 0f
    var offsetY: Float = 0f
    var rotationSpeed: Float = 0f
    var pulseStrength: Float = 35f

    private var rotationAngle = 0f
    private var pulsePhase = 0f

    private val paintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 3f
    }
    private val paintRed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1744")
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 3f
    }
    private val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val paintGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f + offsetX
        val cy = height / 2f + offsetY
        val baseSize = crosshairSize
        val pulse = (pulseStrength / 100f).coerceIn(0f, 1f)
        val pulseFactor = 1f + (sin(Math.toRadians(pulsePhase.toDouble())).toFloat() * 0.16f * pulse)

        canvas.save()
        canvas.translate(cx, cy)
        if (rotationSpeed > 0f) canvas.rotate(rotationAngle)
        if (pulse > 0f) canvas.scale(pulseFactor, pulseFactor)

        paintGlow.alpha = (55 + (120 * pulse)).toInt().coerceIn(0, 180)
        paintGlow.strokeWidth = baseSize * 0.05f
        canvas.drawCircle(0f, 0f, baseSize * (0.82f + 0.12f * pulse), paintGlow)

        when (crosshairType) {
            CrosshairType.DOT    -> drawDot(canvas, 0f, 0f, baseSize)
            CrosshairType.CROSS  -> drawCross(canvas, 0f, 0f, baseSize)
            CrosshairType.CIRCLE -> drawCircle(canvas, 0f, 0f, baseSize)
            CrosshairType.DIAMOND -> drawDiamond(canvas, 0f, 0f, baseSize)
            CrosshairType.TARGET -> drawTarget(canvas, 0f, 0f, baseSize)
            CrosshairType.T_SHAPE -> drawTShape(canvas, 0f, 0f, baseSize)
            CrosshairType.PLUS -> drawPlus(canvas, 0f, 0f, baseSize)
            CrosshairType.HOLLOW_SQUARE -> drawHollowSquare(canvas, 0f, 0f, baseSize)
            CrosshairType.TRIPLE_DOT -> drawTripleDot(canvas, 0f, 0f, baseSize)
            CrosshairType.ARROW -> drawArrow(canvas, 0f, 0f, baseSize)
            CrosshairType.CHEVRON -> drawChevron(canvas, 0f, 0f, baseSize)
            CrosshairType.HAIRLINE -> drawHairline(canvas, 0f, 0f, baseSize)
        }
        canvas.restore()

        if (rotationSpeed > 0f || pulseStrength > 0f) {
            rotationAngle = (rotationAngle + rotationSpeed / 18f) % 360f
            pulsePhase = (pulsePhase + 6f) % 360f
            postInvalidateOnAnimation()
        }
    }

    private fun drawDot(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val arm = s * 0.8f; val gap = s * 0.3f; val dot = s * 0.18f
        paintStroke.strokeWidth = s * 0.07f
        canvas.drawCircle(cx, cy, s * 0.55f, paintStroke)
        canvas.drawCircle(cx, cy, dot, paintWhite)
        paintWhite.strokeWidth = s * 0.09f; paintWhite.style = Paint.Style.STROKE
        canvas.drawLine(cx, cy - gap, cx, cy - arm, paintWhite)
        canvas.drawLine(cx, cy + gap, cx, cy + arm, paintWhite)
        canvas.drawLine(cx - gap, cy, cx - arm, cy, paintWhite)
        canvas.drawLine(cx + gap, cy, cx + arm, cy, paintWhite)
        paintWhite.style = Paint.Style.FILL_AND_STROKE
    }

    private fun drawCross(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val arm = s * 0.9f; val gap = s * 0.25f; val thick = s * 0.1f
        paintWhite.strokeWidth = thick; paintWhite.style = Paint.Style.STROKE
        canvas.drawLine(cx, cy - gap, cx, cy - arm, paintWhite)
        canvas.drawLine(cx, cy + gap, cx, cy + arm, paintWhite)
        canvas.drawLine(cx - gap, cy, cx - arm, cy, paintWhite)
        canvas.drawLine(cx + gap, cy, cx + arm, cy, paintWhite)
        paintRed.style = Paint.Style.FILL
        canvas.drawRect(cx - gap * 0.7f, cy - gap * 0.7f, cx + gap * 0.7f, cy + gap * 0.7f, paintRed)
        paintWhite.style = Paint.Style.FILL_AND_STROKE
    }

    private fun drawCircle(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        paintStroke.strokeWidth = s * 0.08f
        canvas.drawCircle(cx, cy, s * 0.65f, paintStroke)
        paintRed.style = Paint.Style.STROKE; paintRed.strokeWidth = s * 0.05f
        canvas.drawCircle(cx, cy, s * 0.25f, paintRed)
        paintWhite.style = Paint.Style.FILL
        canvas.drawRect(cx - s * 0.06f, cy - s * 0.06f, cx + s * 0.06f, cy + s * 0.06f, paintWhite)
        val arm = s * 0.9f; val gap = s * 0.7f; val thick = s * 0.07f
        paintWhite.strokeWidth = thick; paintWhite.style = Paint.Style.STROKE
        canvas.drawLine(cx, cy - gap, cx, cy - arm, paintWhite)
        canvas.drawLine(cx, cy + gap, cx, cy + arm, paintWhite)
        canvas.drawLine(cx - gap, cy, cx - arm, cy, paintWhite)
        canvas.drawLine(cx + gap, cy, cx + arm, cy, paintWhite)
        paintWhite.style = Paint.Style.FILL_AND_STROKE
    }

    private fun drawDiamond(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val path = Path()
        path.moveTo(cx, cy - s * 0.85f)
        path.lineTo(cx + s * 0.65f, cy)
        path.lineTo(cx, cy + s * 0.85f)
        path.lineTo(cx - s * 0.65f, cy)
        path.close()
        paintStroke.strokeWidth = s * 0.07f
        canvas.drawPath(path, paintStroke)
        canvas.drawCircle(cx, cy, s * 0.12f, paintRed)
        val corner = s * 0.08f
        paintWhite.style = Paint.Style.FILL
        canvas.drawRect(cx - corner, cy - s * 0.95f, cx + corner, cy - s * 0.75f, paintWhite)
        canvas.drawRect(cx - corner, cy + s * 0.75f, cx + corner, cy + s * 0.95f, paintWhite)
        canvas.drawRect(cx - s * 0.95f, cy - corner, cx - s * 0.75f, cy + corner, paintWhite)
        canvas.drawRect(cx + s * 0.75f, cy - corner, cx + s * 0.95f, cy + corner, paintWhite)
        paintWhite.style = Paint.Style.FILL_AND_STROKE
    }

    private fun drawTarget(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        paintStroke.strokeWidth = s * 0.06f
        canvas.drawCircle(cx, cy, s * 0.9f, paintStroke)
        paintRed.style = Paint.Style.STROKE; paintRed.strokeWidth = s * 0.06f
        canvas.drawCircle(cx, cy, s * 0.45f, paintRed)
        canvas.drawCircle(cx, cy, s * 0.12f, paintWhite)
        val corner = s * 0.07f; val arm1 = s * 0.96f; val arm2 = s * 0.82f
        paintWhite.strokeWidth = corner; paintWhite.style = Paint.Style.STROKE
        canvas.drawLine(cx, cy - arm2, cx, cy - arm1, paintWhite)
        canvas.drawLine(cx, cy + arm2, cx, cy + arm1, paintWhite)
        canvas.drawLine(cx - arm2, cy, cx - arm1, cy, paintWhite)
        canvas.drawLine(cx + arm2, cy, cx + arm1, cy, paintWhite)
        paintWhite.style = Paint.Style.FILL_AND_STROKE
    }

    private fun drawTShape(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val thick = s * 0.1f
        paintWhite.strokeWidth = thick; paintWhite.style = Paint.Style.STROKE
        canvas.drawLine(cx, cy - s * 0.9f, cx, cy, paintWhite)
        canvas.drawLine(cx - s * 0.9f, cy, cx + s * 0.9f, cy, paintWhite)
        canvas.drawLine(cx, cy + s * 0.3f, cx, cy + s * 0.55f, paintWhite)
        paintRed.style = Paint.Style.FILL
        canvas.drawRect(cx - thick, cy - thick, cx + thick, cy + thick, paintRed)
        paintWhite.style = Paint.Style.FILL_AND_STROKE
    }

    private fun drawPlus(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val arm = s * 0.85f; val thick = s * 0.16f
        paintWhite.style = Paint.Style.STROKE; paintWhite.strokeWidth = thick
        canvas.drawLine(cx, cy - arm, cx, cy + arm, paintWhite)
        canvas.drawLine(cx - arm, cy, cx + arm, cy, paintWhite)
        canvas.drawCircle(cx, cy, s * 0.1f, paintRed)
        paintWhite.style = Paint.Style.FILL_AND_STROKE
    }

    private fun drawHollowSquare(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val half = s * 0.6f
        paintStroke.strokeWidth = s * 0.08f
        canvas.drawRect(cx - half, cy - half, cx + half, cy + half, paintStroke)
        paintRed.style = Paint.Style.FILL
        canvas.drawRect(cx - s * 0.06f, cy - s * 0.06f, cx + s * 0.06f, cy + s * 0.06f, paintRed)
        val arm = s * 0.95f
        paintWhite.strokeWidth = s * 0.07f; paintWhite.style = Paint.Style.STROKE
        canvas.drawLine(cx, cy - arm, cx, cy - half - s * 0.08f, paintWhite)
        canvas.drawLine(cx, cy + half + s * 0.08f, cx, cy + arm, paintWhite)
        canvas.drawLine(cx - arm, cy, cx - half - s * 0.08f, cy, paintWhite)
        canvas.drawLine(cx + half + s * 0.08f, cy, cx + arm, cy, paintWhite)
        paintWhite.style = Paint.Style.FILL_AND_STROKE
    }

    private fun drawTripleDot(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        canvas.drawCircle(cx, cy, s * 0.14f, paintWhite)
        paintRed.style = Paint.Style.FILL
        canvas.drawCircle(cx - s * 0.55f, cy, s * 0.09f, paintRed)
        canvas.drawCircle(cx + s * 0.55f, cy, s * 0.09f, paintRed)
        canvas.drawCircle(cx, cy - s * 0.55f, s * 0.09f, paintRed)
        canvas.drawCircle(cx, cy + s * 0.55f, s * 0.09f, paintRed)
    }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val path = Path()
        path.moveTo(cx, cy - s * 0.9f)
        path.lineTo(cx + s * 0.42f, cy - s * 0.15f)
        path.lineTo(cx + s * 0.12f, cy - s * 0.15f)
        path.lineTo(cx + s * 0.12f, cy + s * 0.75f)
        path.lineTo(cx - s * 0.12f, cy + s * 0.75f)
        path.lineTo(cx - s * 0.12f, cy - s * 0.15f)
        path.lineTo(cx - s * 0.42f, cy - s * 0.15f)
        path.close()
        paintWhite.style = Paint.Style.FILL
        canvas.drawPath(path, paintWhite)
        canvas.drawCircle(cx, cy + s * 0.75f, s * 0.09f, paintRed)
        paintWhite.style = Paint.Style.FILL_AND_STROKE
    }

    private fun drawChevron(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val thick = s * 0.14f
        paintWhite.style = Paint.Style.STROKE; paintWhite.strokeWidth = thick
        paintWhite.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(cx - s * 0.6f, cy - s * 0.75f, cx, cy - s * 0.05f, paintWhite)
        canvas.drawLine(cx, cy - s * 0.05f, cx + s * 0.6f, cy - s * 0.75f, paintWhite)
        paintRed.style = Paint.Style.STROKE; paintRed.strokeWidth = thick
        canvas.drawLine(cx - s * 0.4f, cy + s * 0.15f, cx, cy + s * 0.75f, paintRed)
        canvas.drawLine(cx, cy + s * 0.75f, cx + s * 0.4f, cy + s * 0.15f, paintRed)
        paintWhite.strokeCap = Paint.Cap.BUTT
        paintWhite.style = Paint.Style.FILL_AND_STROKE
    }

    private fun drawHairline(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        paintWhite.strokeWidth = s * 0.04f
        paintWhite.style = Paint.Style.STROKE
        canvas.drawLine(cx - s, cy, cx + s, cy, paintWhite)
        canvas.drawLine(cx, cy - s, cx, cy + s, paintWhite)
        paintRed.strokeWidth = s * 0.06f
        canvas.drawCircle(cx, cy, s * 0.12f, paintRed)
        paintWhite.style = Paint.Style.FILL_AND_STROKE
    }
}
