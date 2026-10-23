package com.aujunpeak.app.activities

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import com.aujunpeak.app.views.CrosshairOverlayView

class FloatingCrosshairService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var crosshairView: CrosshairOverlayView
    private var isAdded = false

    companion object {
        const val EXTRA_TYPE    = "crosshair_type"
        const val EXTRA_SIZE    = "crosshair_size"
        const val EXTRA_OFFSET_X = "crosshair_offset_x"
        const val EXTRA_OFFSET_Y = "crosshair_offset_y"
        const val EXTRA_ROTATION_SPEED = "crosshair_rotation_speed"
        const val EXTRA_PULSE = "crosshair_pulse"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        crosshairView = CrosshairOverlayView(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val typeName = it.getStringExtra(EXTRA_TYPE) ?: "DOT"
            crosshairView.crosshairType = try {
                CrosshairOverlayView.CrosshairType.valueOf(typeName)
            } catch (e: Exception) {
                CrosshairOverlayView.CrosshairType.DOT
            }
            crosshairView.crosshairSize   = it.getFloatExtra(EXTRA_SIZE, 40f)
            crosshairView.offsetX         = it.getFloatExtra(EXTRA_OFFSET_X, 0f)
            crosshairView.offsetY         = it.getFloatExtra(EXTRA_OFFSET_Y, 0f)
            crosshairView.rotationSpeed   = it.getFloatExtra(EXTRA_ROTATION_SPEED, 0f)
            crosshairView.pulseStrength   = it.getFloatExtra(EXTRA_PULSE, 35f)
        }

        if (!isAdded) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            windowManager.addView(crosshairView, params)
            isAdded = true
        } else {
            crosshairView.invalidate()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isAdded) {
            try { windowManager.removeView(crosshairView) } catch (e: Exception) { /* ignore */ }
            isAdded = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
