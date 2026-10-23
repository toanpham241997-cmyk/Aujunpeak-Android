package com.aujunpeak.app.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class GlowTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            setShadowLayer(10f, 0f, 0f, Color.parseColor("#88FF1744"))
            startGlowPulse()
        } catch (_: Exception) {}
    }

    private fun startGlowPulse() {
        if (!isAttachedToWindow) return
        animate().alpha(0.7f).setDuration(1200).withEndAction {
            if (isAttachedToWindow) {
                animate().alpha(1f).setDuration(1200).withEndAction {
                    if (isAttachedToWindow) startGlowPulse()
                }.start()
            }
        }.start()
    }
}
