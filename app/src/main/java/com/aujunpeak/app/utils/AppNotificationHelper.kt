package com.aujunpeak.app.utils

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.aujunpeak.app.R

object AppNotificationHelper {

    /**
     * Hiển thị danh sách thông báo tuần tự (mỗi cái xong thì hiện cái tiếp).
     * Sau khi tất cả được đọc, gọi markNotificationsRead trên server.
     */
    fun showNotifications(
        context: Context,
        notifications: List<KeyManager.AppNotification>,
        onAllRead: (List<Int>) -> Unit,
    ) {
        if (notifications.isEmpty()) return
        showNext(context, notifications, 0, mutableListOf(), onAllRead)
    }

    private fun showNext(
        context: Context,
        list: List<KeyManager.AppNotification>,
        index: Int,
        readIds: MutableList<Int>,
        onAllRead: (List<Int>) -> Unit,
    ) {
        if (index >= list.size) {
            onAllRead(readIds)
            return
        }
        val notif = list[index]
        showSingle(context, notif) {
            readIds.add(notif.id)
            showNext(context, list, index + 1, readIds, onAllRead)
        }
    }

    private fun showSingle(
        context: Context,
        notif: KeyManager.AppNotification,
        onDismiss: () -> Unit,
    ) {
        // Tránh BadTokenException khi activity đang bị destroy
        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) {
            onDismiss()
            return
        }
        val dialog = android.app.Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_app_notification, null)
        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.75f)
        }

        val tvTitle = view.findViewById<TextView>(R.id.tvNotifTitle)
        val tvBody  = view.findViewById<TextView>(R.id.tvNotifBody)
        val btnOk   = view.findViewById<FrameLayout>(R.id.btnNotifOk)
        val card    = view.findViewById<View>(R.id.notifCard)
        val icon    = view.findViewById<View>(R.id.notifIconBell)

        tvTitle.text = notif.title
        tvBody.text  = notif.body

        // Entrance
        card.alpha        = 0f
        card.scaleX       = 0.75f
        card.scaleY       = 0.75f
        card.translationY = 60f

        dialog.setOnShowListener {
            card.animate()
                .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(450).setInterpolator(OvershootInterpolator(1.4f))
                .start()

            // Bell shake
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                ObjectAnimator.ofFloat(icon, "rotation", 0f, -20f, 20f, -15f, 15f, -8f, 8f, 0f)
                    .apply { duration = 600; start() }
            }, 400)
        }

        btnOk.setOnClickListener {
            it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(150)
                    .setInterpolator(OvershootInterpolator(2f))
                    .withEndAction {
                        card.animate().alpha(0f).translationY(-40f).setDuration(220)
                            .withEndAction { dialog.dismiss(); onDismiss() }.start()
                    }.start()
            }.start()
        }

        dialog.show()
    }
}
