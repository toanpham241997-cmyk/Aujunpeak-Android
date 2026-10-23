package com.aujunpeak.app.utils

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import com.aujunpeak.app.R

object KeyRevokedDialog {

    fun show(context: Context, isExpired: Boolean = false, onDismiss: () -> Unit) {
        // Tránh BadTokenException khi activity đang bị destroy
        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) return

        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_key_revoked, null)
        dialog.setContentView(view)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.85f)
        }

        val tvTitle   = view.findViewById<TextView>(R.id.tvRevokedTitle)
        val tvMsg     = view.findViewById<TextView>(R.id.tvRevokedMsg)
        val tvSub     = view.findViewById<TextView>(R.id.tvRevokedSub)
        val btnOk     = view.findViewById<FrameLayout>(R.id.btnRevokedOk)
        val iconFrame = view.findViewById<View>(R.id.iconLockFrame)
        val glowOuter = view.findViewById<View>(R.id.revokedGlowOuter)
        val glowInner = view.findViewById<View>(R.id.revokedGlowInner)
        val card      = view.findViewById<View>(R.id.revokedCard)

        if (isExpired) {
            tvTitle.text = context.getString(R.string.key_expired_title)
            tvMsg.text   = context.getString(R.string.key_expired_msg)
            tvSub.text   = context.getString(R.string.key_expired_sub)
        } else {
            tvTitle.text = context.getString(R.string.key_revoked_title)
            tvMsg.text   = context.getString(R.string.key_revoked_msg)
            tvSub.text   = context.getString(R.string.key_revoked_sub)
        }

        // Entrance animation
        card.alpha        = 0f
        card.scaleX       = 0.7f
        card.scaleY       = 0.7f
        card.translationY = 80f

        dialog.setOnShowListener {
            // Card pop-in
            card.animate()
                .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(500).setInterpolator(OvershootInterpolator(1.5f))
                .start()

            // Glow pulse loop
            startGlowPulse(glowOuter, glowInner)

            // Lock icon shake
            Handler(Looper.getMainLooper()).postDelayed({
                ObjectAnimator.ofFloat(iconFrame, "translationX",
                    0f, -14f, 14f, -10f, 10f, -6f, 6f, 0f
                ).apply { duration = 500; start() }
            }, 600)
        }

        btnOk.setOnClickListener {
            it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(150)
                    .setInterpolator(OvershootInterpolator(2f))
                    .withEndAction {
                        card.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f)
                            .setDuration(250).withEndAction {
                                dialog.dismiss()
                                onDismiss()
                            }.start()
                    }.start()
            }.start()
        }

        dialog.show()
    }

    private fun startGlowPulse(outer: View, inner: View) {
        val scaleOutX = ObjectAnimator.ofFloat(outer, "scaleX", 1f, 1.15f, 1f).apply {
            duration = 1200; repeatCount = ObjectAnimator.INFINITE
        }
        val scaleOutY = ObjectAnimator.ofFloat(outer, "scaleY", 1f, 1.15f, 1f).apply {
            duration = 1200; repeatCount = ObjectAnimator.INFINITE
        }
        val alphaOut = ObjectAnimator.ofFloat(outer, "alpha", 0.6f, 1f, 0.6f).apply {
            duration = 1200; repeatCount = ObjectAnimator.INFINITE
        }
        val scaleInX = ObjectAnimator.ofFloat(inner, "scaleX", 1f, 1.08f, 1f).apply {
            duration = 900; repeatCount = ObjectAnimator.INFINITE; startDelay = 150
        }
        val scaleInY = ObjectAnimator.ofFloat(inner, "scaleY", 1f, 1.08f, 1f).apply {
            duration = 900; repeatCount = ObjectAnimator.INFINITE; startDelay = 150
        }
        AnimatorSet().apply {
            playTogether(scaleOutX, scaleOutY, alphaOut, scaleInX, scaleInY)
            start()
        }
    }
}
