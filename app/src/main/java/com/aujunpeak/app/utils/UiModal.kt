package com.aujunpeak.app.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.aujunpeak.app.R

object UiModal {
    enum class Type { SUCCESS, ERROR, WARNING, INFO }

    @JvmStatic
    fun show(
        context: Context,
        message: String,
        title: String = "Aujunpeak",
        type: Type = Type.INFO,
        positiveText: String = "Đã hiểu",
        autoDismissMillis: Long = 0L,
    ) {
        showConfirm(
            context = context,
            title = title,
            message = message,
            type = type,
            positiveText = positiveText,
            negativeText = null,
            cancelable = true,
            onPositive = null,
            onNegative = null,
            autoDismissMillis = autoDismissMillis,
        )
    }

    @JvmStatic
    fun showConfirm(
        context: Context,
        title: String,
        message: String,
        type: Type = Type.INFO,
        positiveText: String = "Đồng ý",
        negativeText: String? = null,
        cancelable: Boolean = true,
        onPositive: (() -> Unit)? = null,
        onNegative: (() -> Unit)? = null,
        autoDismissMillis: Long = 0L,
    ) {
        runCatching {
            val dialog = Dialog(context)
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_swal_modal, null, false)
            dialog.setContentView(view)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.setCancelable(cancelable)
            dialog.setCanceledOnTouchOutside(cancelable)

            val titleView = view.findViewById<TextView>(R.id.modalTitle)
            val messageView = view.findViewById<TextView>(R.id.modalMessage)
            val iconText = view.findViewById<TextView>(R.id.modalIconText)
            val btnOk = view.findViewById<TextView>(R.id.modalBtnOk)
            val btnCancel = view.findViewById<TextView>(R.id.modalBtnCancel)
            val space = view.findViewById<View>(R.id.modalActionSpace)

            titleView.text = title
            messageView.text = message
            btnOk.text = positiveText

            when (type) {
                Type.SUCCESS -> {
                    iconText.text = "✓"
                    iconText.setBackgroundResource(R.drawable.bg_swal_icon_success)
                }
                Type.ERROR -> {
                    iconText.text = "✕"
                    iconText.setBackgroundResource(R.drawable.bg_swal_icon_error)
                }
                Type.WARNING -> {
                    iconText.text = "!"
                    iconText.setBackgroundResource(R.drawable.bg_swal_icon_warning)
                }
                Type.INFO -> {
                    iconText.text = "i"
                    iconText.setBackgroundResource(R.drawable.bg_swal_icon_info)
                }
            }

            if (negativeText.isNullOrBlank()) {
                btnCancel.visibility = View.GONE
                space.visibility = View.GONE
                val params = btnOk.layoutParams as android.widget.LinearLayout.LayoutParams
                params.width = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
                params.weight = 0f
                btnOk.layoutParams = params
            } else {
                btnCancel.visibility = View.VISIBLE
                space.visibility = View.VISIBLE
                btnCancel.text = negativeText
                btnCancel.setOnClickListener {
                    dialog.dismiss()
                    onNegative?.invoke()
                }
            }

            btnOk.setOnClickListener {
                dialog.dismiss()
                onPositive?.invoke()
            }

            dialog.show()
            dialog.window?.setLayout(
                (context.resources.displayMetrics.widthPixels * 0.90f).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )

            if (autoDismissMillis > 0) {
                view.postDelayed({ if (dialog.isShowing) dialog.dismiss() }, autoDismissMillis)
            }
        }
    }
}
