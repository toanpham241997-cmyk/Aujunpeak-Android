package com.aujunpeak.app.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import com.aujunpeak.app.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dialog bottom-sheet hiển thị toàn bộ lịch sử thông báo từ NotificationStore.
 * Sau khi mở → tự động đánh dấu tất cả là đã đọc.
 */
object NotificationsListDialog {

    fun show(context: Context, onDismiss: () -> Unit = {}) {
        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) return

        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_notifications_list, null)
        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.65f)
        }

        val card          = view.findViewById<View>(R.id.notifListCard)
        val btnClose      = view.findViewById<FrameLayout>(R.id.btnNotifListClose)
        val layoutEmpty   = view.findViewById<View>(R.id.layoutNotifEmpty)
        val listContainer = view.findViewById<LinearLayout>(R.id.notifListContainer)
        val notifScroll   = view.findViewById<ScrollView>(R.id.notifScroll)
        val tvCount       = view.findViewById<TextView>(R.id.tvNotifListCount)

        // ── Load notifications ────────────────────────────────────────────
        val notifs = NotificationStore.getAll(context)

        if (notifs.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            notifScroll.visibility = View.GONE
            listContainer.visibility = View.GONE
            tvCount.text = context.getString(R.string.notif_zero)
        } else {
            layoutEmpty.visibility = View.GONE
            notifScroll.visibility = View.VISIBLE
            listContainer.visibility = View.VISIBLE
            tvCount.text             = "${notifs.size} thông báo"

            val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            notifs.forEachIndexed { index, notif ->
                val item = LayoutInflater.from(context).inflate(
                    R.layout.item_notif_in_list, listContainer, false
                )
                item.findViewById<TextView>(R.id.tvNotifItemTitle).text = notif.title
                item.findViewById<TextView>(R.id.tvNotifItemBody).text  = notif.body
                item.findViewById<TextView>(R.id.tvNotifItemTime).text  =
                    sdf.format(Date(notif.timestamp))
                item.findViewById<View>(R.id.notifItemUnreadDot).visibility =
                    if (notif.isRead) View.INVISIBLE else View.VISIBLE
                item.alpha = 0f
                item.translationY = 24f + index * 2f
                listContainer.addView(item)
            }

            NotificationStore.markAllRead(context)
        }

        card.alpha        = 0f
        card.translationY = 22f
        card.scaleX       = 0.92f
        card.scaleY       = 0.92f

        dialog.setOnShowListener {
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                 .setDuration(320)
                .setInterpolator(OvershootInterpolator(1.08f))
                .withEndAction {
                    for (i in 0 until listContainer.childCount) {
                        val child = listContainer.getChildAt(i)
                        child.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setStartDelay(i * 45L)
                            .setDuration(260L)
                            .start()
                    }
                }
                .start()
        }

        // ── Close button ─────────────────────────────────────────────────
        val dismissDialog: () -> Unit = {
            card.animate().alpha(0f).translationY(18f).scaleX(0.94f).scaleY(0.94f).setDuration(190)
                .withEndAction { dialog.dismiss() }.start()
        }

        btnClose.setOnClickListener {
            it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(120)
                    .withEndAction { dismissDialog() }.start()
            }.start()
        }

        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
    }
}
