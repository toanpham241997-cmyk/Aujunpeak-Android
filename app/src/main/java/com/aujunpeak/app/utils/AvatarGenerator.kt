package com.aujunpeak.app.utils

import android.graphics.*
import android.widget.ImageView

/**
 * Tự generate avatar hình tròn từ tên người dùng.
 * - Initials: lấy ký tự đầu mỗi từ (tối đa 2)
 * - Màu nền: hash-based từ tên → nhất quán, không đổi
 * - Không cần internet, không cần Glide, chạy instant
 */
object AvatarGenerator {

    // ── Bảng màu gradient (Google Material-inspired + tech theme) ────────────
    private val PALETTE = intArrayOf(
        0xFF1A73E8.toInt(), // Google Blue
        0xFF34A853.toInt(), // Google Green
        0xFFEA4335.toInt(), // Google Red
        0xFFFF6D00.toInt(), // Deep Orange
        0xFF9C27B0.toInt(), // Purple
        0xFF00ACC1.toInt(), // Cyan
        0xFF3949AB.toInt(), // Indigo
        0xFF00897B.toInt(), // Teal
        0xFFE91E63.toInt(), // Pink
        0xFF7CB342.toInt(), // Light Green
        0xFF5E35B1.toInt(), // Deep Purple
        0xFF039BE5.toInt(), // Light Blue
    )

    // ── Màu làm sáng hơn cho gradient (overlay phía trên vòng tròn) ──────────
    private fun lighten(color: Int, factor: Float = 0.3f): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.rgb(
            (r + (255 - r) * factor).toInt().coerceIn(0, 255),
            (g + (255 - g) * factor).toInt().coerceIn(0, 255),
            (b + (255 - b) * factor).toInt().coerceIn(0, 255)
        )
    }

    // ── Chọn màu nhất quán dựa theo hash của tên ─────────────────────────────
    fun colorFor(name: String): Int {
        val idx = Math.abs(name.trim().hashCode()) % PALETTE.size
        return PALETTE[idx]
    }

    // ── Tạo initials từ tên (tối đa 2 ký tự) ─────────────────────────────────
    fun initialsOf(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            words.isEmpty()  -> "?"
            words.size == 1  -> words[0].take(2).uppercase()
            else             -> "${words[0][0]}${words.last()[0]}".uppercase()
        }
    }

    /**
     * Tạo Bitmap avatar hình tròn.
     * @param name  Tên người dùng (dùng để tính initials + màu)
     * @param sizePx Kích thước bitmap (pixels)
     */
    fun create(name: String, sizePx: Int): Bitmap {
        val initials = initialsOf(name)
        val baseColor = colorFor(name)
        val topColor  = lighten(baseColor, 0.28f)

        val bmp    = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx     = sizePx / 2f
        val cy     = sizePx / 2f
        val radius = sizePx / 2f

        // 1. Nền gradient hình tròn
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy * 0.6f,          // tâm gradient lệch lên trên
                radius * 1.1f,
                topColor, baseColor,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // 2. Viền mờ bên trong (inner glow)
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            alpha = 40
            strokeWidth = sizePx * 0.035f
        }
        canvas.drawCircle(cx, cy, radius - sizePx * 0.025f, glowPaint)

        // 3. Text initials
        val textSize = when {
            initials.length == 1 -> sizePx * 0.44f
            else                 -> sizePx * 0.36f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = Color.WHITE
            this.textSize  = textSize
            textAlign = Paint.Align.CENTER
            typeface  = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            // Shadow nhẹ cho text
            setShadowLayer(sizePx * 0.04f, 0f, sizePx * 0.02f, Color.parseColor("#40000000"))
        }

        // Căn giữa chính xác
        val bounds = Rect()
        textPaint.getTextBounds(initials, 0, initials.length, bounds)
        val textY = cy - (bounds.top + bounds.bottom) / 2f
        canvas.drawText(initials, cx, textY, textPaint)

        return bmp
    }

    /**
     * Tạo và set avatar vào ImageView ngay lập tức (main thread safe).
     * @param name   Tên người dùng
     * @param view   ImageView đích
     * @param sizeDp Kích thước mong muốn (dp) — sẽ convert sang px
     */
    fun loadInto(name: String, view: ImageView, sizeDp: Int = 72) {
        val density = view.context.resources.displayMetrics.density
        val sizePx  = (sizeDp * density).toInt().coerceAtLeast(48)
        val bmp     = create(name.ifBlank { "?" }, sizePx)
        view.setImageBitmap(bmp)
    }
}
