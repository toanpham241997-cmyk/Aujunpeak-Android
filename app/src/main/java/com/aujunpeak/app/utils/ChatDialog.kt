package com.aujunpeak.app.utils

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.aujunpeak.app.R
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modal chat dialog — hiển thị chat với bot/admin tương tự NotificationsListDialog.
 *
 * Fix v2:
 *  - Không dùng optimistic bubble → tránh hiển thị tin nhắn 2 lần.
 *    Sau khi send thành công, fetch ngay lập tức (delay 400ms) để lấy message thật.
 *  - Image bubble xử lý cả URL (Glide) và base64 (BitmapFactory) cho cả user/admin.
 *  - imageData null nhưng type=image → hiển thị icon ảnh mờ thay vì text rỗng.
 */
object ChatDialog {

    private val EMOJI_LIST = listOf(
        "😀","😂","🥰","😎","😭","🔥","👍","❤️",
        "🎉","😅","🤔","👋","🙏","💯","✅","❌",
        "😏","🥳","🤩","😤","👀","💪","🎮","⚡",
    )

    fun show(
        context: Context,
        onImageNeeded: (() -> Unit)? = null,
    ) {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) return

        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(false)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_chat, null)
        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        // ── Views ─────────────────────────────────────────────────────────
        val card               = view.findViewById<LinearLayout>(R.id.chatDialogCard)
        val ivAvatar           = view.findViewById<ImageView>(R.id.ivChatAdminAvatar)
        val onlineDot          = view.findViewById<View>(R.id.chatAdminOnlineDot)
        val tvAdminName        = view.findViewById<TextView>(R.id.tvChatAdminName)
        val tvStatus           = view.findViewById<TextView>(R.id.tvChatStatus)
        val btnClose           = view.findViewById<FrameLayout>(R.id.btnChatClose)
        val loginBanner        = view.findViewById<LinearLayout>(R.id.chatLoginRequired)
        val btnLoginNow        = view.findViewById<TextView>(R.id.btnChatLoginNow)
        val scrollView         = view.findViewById<ScrollView>(R.id.chatScrollView)
        val messagesContainer  = view.findViewById<LinearLayout>(R.id.chatMessagesContainer)
        val typingIndicator    = view.findViewById<LinearLayout>(R.id.chatTypingIndicator)
        val tvTypingDots       = view.findViewById<TextView>(R.id.tvTypingDots)
        val etInput            = view.findViewById<EditText>(R.id.etChatInput)
        val btnEmoji           = view.findViewById<FrameLayout>(R.id.btnChatEmoji)
        val btnImage           = view.findViewById<FrameLayout>(R.id.btnChatImage)
        val btnSend            = view.findViewById<FrameLayout>(R.id.btnChatSend)
        // Nền ảnh tĩnh cho khung tin nhắn (đặt trực tiếp trong layout qua android:src)

        val handler  = Handler(Looper.getMainLooper())
        var pollJob: Job? = null
        var emojiPanelVisible = false

        // ── Âm thanh gửi / nhận tin nhắn (to, rõ, kiểu Messenger) ────────────
        val soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        val sendSoundId    = soundPool.load(context, R.raw.chat_send, 1)
        val receiveSoundId = soundPool.load(context, R.raw.chat_receive, 1)
        // Phát ở mức âm lượng tối đa của luồng thông báo để nghe rõ và to hơn
        fun playSound(id: Int) {
            try { soundPool.play(id, 1f, 1f, 1, 0, 1f) } catch (_: Exception) {}
        }

        // ── Đánh dấu đã xem tin nhắn (xoá badge đỏ ở icon chat ngoài màn hình chính) ──
        fun markAllSeen(uptoId: Int) {
            if (uptoId <= 0) return
            ChatUnreadStore.markSeen(context, uptoId)
            (context as? com.aujunpeak.app.activities.MainActivity)?.updateChatBadge(0)
        }
        // Người dùng vừa mở chat → coi như đã xem, tắt badge ngay
        (context as? com.aujunpeak.app.activities.MainActivity)?.updateChatBadge(0)

        // ── Check Google login ────────────────────────────────────────────
        val isGoogleLoggedIn = KeyManager.isGoogleLoggedIn(context)
        val googleEmail      = KeyManager.getGoogleEmail(context)
        val googleName       = KeyManager.getGoogleName(context)
        val deviceId         = KeyManager.getOrCreateDeviceId(context)

        if (!isGoogleLoggedIn) {
            loginBanner.visibility = View.VISIBLE
            etInput.isEnabled      = false
            btnSend.isEnabled      = false
            btnImage.isEnabled     = false
            btnEmoji.isEnabled     = false
            etInput.alpha          = 0.4f
            btnSend.alpha          = 0.4f
        }

        btnLoginNow.setOnClickListener {
            dialog.dismiss()
            (context as? Activity)?.let { act ->
                act.startActivity(Intent(act, act::class.java))
            }
        }

        // ── Session state ─────────────────────────────────────────────────
        var currentSession: ChatApiClient.ChatSession? = null
        var lastMessageId   = 0
        var emojiPanelView: LinearLayout? = null

        // ── Header update ─────────────────────────────────────────────────
        fun updateHeaderForSession(session: ChatApiClient.ChatSession?) {
            if (session == null) return
            currentSession = session
            when (session.status) {
                "accepted" -> {
                    tvAdminName.text = if (session.adminName.isNotEmpty()) session.adminName else "Admin Huấn Hà"
                    tvStatus.text    = if (session.adminOnline) "● Trực Tuyến • Discord" else context.getString(R.string.chat_online_status)
                    tvStatus.setTextColor(
                        if (session.adminOnline) Color.parseColor("#69FF47")
                        else Color.parseColor("#FF9800")
                    )
                    onlineDot.visibility = if (session.adminOnline) View.VISIBLE else View.GONE
                    if (session.adminAvatar.isNotEmpty()) {
                        Glide.with(context)
                            .load(session.adminAvatar)
                            .circleCrop()
                            .placeholder(R.drawable.ic_robot_avatar)
                            .into(ivAvatar)
                    }
                }
                "closed" -> {
                    tvAdminName.text = "Suport Aujunpeak"
                    tvStatus.text    = context.getString(R.string.chat_status_closed)
                    tvStatus.setTextColor(Color.parseColor("#888888"))
                    onlineDot.visibility = View.GONE
                    etInput.isEnabled    = false
                    etInput.alpha        = 0.4f
                    btnSend.alpha        = 0.4f
                    btnSend.isEnabled    = false
                }
                else -> {
                    tvAdminName.text = "Suport Aujunpeak"
                    tvStatus.text    = context.getString(R.string.chat_status_pending)
                    tvStatus.setTextColor(Color.parseColor("#AAAAAA"))
                    onlineDot.visibility = View.GONE
                }
            }
        }

        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        // ── Add message bubble ─────────────────────────────────────────────
        fun addMessageBubble(msg: ChatApiClient.ChatMessage, animate: Boolean = false) {
            val isUser = msg.sender == "user"
            val itemView = LayoutInflater.from(context).inflate(
                if (isUser) R.layout.item_chat_msg_user else R.layout.item_chat_msg_admin,
                messagesContainer, false
            )

            val timeStr = if (msg.createdAt.isNotEmpty()) {
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    val date = sdf.parse(msg.createdAt.take(19))
                    timeFmt.format(date ?: Date())
                } catch (_: Exception) { timeFmt.format(Date()) }
            } else {
                timeFmt.format(Date())
            }

            if (isUser) {
                val tvText = itemView.findViewById<TextView>(R.id.tvMsgUserText)
                val ivImg  = itemView.findViewById<ImageView>(R.id.ivMsgUserImage)
                val tvTime = itemView.findViewById<TextView>(R.id.tvMsgUserTime)

                when {
                    msg.type == "image" -> {
                        // Ưu tiên hiển thị ảnh
                        tvText.visibility = View.GONE
                        ivImg.visibility  = View.VISIBLE
                        renderImageIntoView(context, msg.imageData, ivImg, tvText)
                    }
                    msg.content.isNotEmpty() -> {
                        tvText.visibility = View.VISIBLE
                        ivImg.visibility  = View.GONE
                        tvText.text       = msg.content
                    }
                    else -> {
                        // Không có gì để hiển thị — bỏ qua
                        return
                    }
                }
                tvTime.text = timeStr

            } else {
                // admin hoặc bot
                val tvName = itemView.findViewById<TextView>(R.id.tvMsgAdminName)
                val tvText = itemView.findViewById<TextView>(R.id.tvMsgAdminText)
                val ivImg  = itemView.findViewById<ImageView>(R.id.ivMsgAdminImage)
                val tvTime = itemView.findViewById<TextView>(R.id.tvMsgAdminTime)
                val ivAv   = itemView.findViewById<ImageView>(R.id.ivMsgAdminAvatar)

                tvName.text = when (msg.sender) {
                    "admin" -> currentSession?.adminName?.ifEmpty { "Admin" } ?: "Admin"
                    else    -> "Bot"
                }

                // Avatar của admin
                if (msg.sender == "admin" && currentSession?.adminAvatar?.isNotEmpty() == true) {
                    Glide.with(context)
                        .load(currentSession!!.adminAvatar)
                        .circleCrop()
                        .placeholder(R.drawable.ic_robot_avatar)
                        .into(ivAv)
                }

                when {
                    msg.type == "image" -> {
                        tvText.visibility = View.GONE
                        ivImg.visibility  = View.VISIBLE
                        renderImageIntoView(context, msg.imageData, ivImg, tvText)
                    }
                    msg.content.isNotEmpty() -> {
                        tvText.visibility = View.VISIBLE
                        ivImg.visibility  = View.GONE
                        tvText.text       = msg.content
                    }
                    else -> return
                }
                tvTime.text = timeStr
            }

            messagesContainer.addView(itemView)

            if (animate) {
                itemView.alpha        = 0f
                itemView.translationY = 20f
                itemView.animate()
                    .alpha(1f).translationY(0f).setDuration(220)
                    .setInterpolator(OvershootInterpolator(1.1f)).start()
            }

            handler.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }

        // ── Typing dots animation ─────────────────────────────────────────
        var dotCount = 0
        val dotsRunnable = object : Runnable {
            override fun run() {
                dotCount = (dotCount + 1) % 4
                tvTypingDots.text = ".".repeat(dotCount + 1)
                if (typingIndicator.visibility == View.VISIBLE) handler.postDelayed(this, 400)
            }
        }

        fun showTyping(show: Boolean) {
            typingIndicator.visibility = if (show) View.VISIBLE else View.GONE
            if (show) handler.post(dotsRunnable)
        }

        // ── Load initial messages ─────────────────────────────────────────
        fun loadInitialMessages(session: ChatApiClient.ChatSession) {
            val lifecycle = context as? LifecycleOwner ?: return
            lifecycle.lifecycleScope.launch {
                val result = ChatApiClient.getMessages(session.id, deviceId)
                withContext(Dispatchers.Main) {
                    result.messages.forEach { addMessageBubble(it, false) }
                    if (result.messages.isNotEmpty()) {
                        lastMessageId = result.messages.maxOf { it.id }
                        markAllSeen(lastMessageId)
                    }
                    updateHeaderForSession(result.session ?: session)
                }
            }
        }

        // ── Background poll loop (chỉ fetch messages từ server, KHÔNG add optimistic) ──
        fun startPolling(session: ChatApiClient.ChatSession) {
            pollJob?.cancel()
            val lifecycle = context as? LifecycleOwner ?: return
            pollJob = lifecycle.lifecycleScope.launch(Dispatchers.IO) {
                while (isActive) {
                    delay(3000)
                    val sessId = currentSession?.id ?: session.id
                    val result = ChatApiClient.getMessages(sessId, deviceId, afterId = lastMessageId)

                    withContext(Dispatchers.Main) {
                        if (result.session != null) updateHeaderForSession(result.session)
                        if (result.messages.isNotEmpty()) {
                            showTyping(false)
                            result.messages.forEach { msg ->
                                addMessageBubble(msg, true)
                                if (msg.sender != "user") playSound(receiveSoundId)
                                if (msg.id > lastMessageId) lastMessageId = msg.id
                            }
                            markAllSeen(lastMessageId)
                        }
                    }
                }
            }
        }

        // ── Initialize session ─────────────────────────────────────────────
        val lifecycle = context as? LifecycleOwner
        lifecycle?.lifecycleScope?.launch {
            if (!isGoogleLoggedIn) {
                val dummyMsg = ChatApiClient.ChatMessage(
                    id = -1, sessionId = -1, sender = "bot",
                    content = "Vui lòng đăng nhập Google để bắt đầu chat với admin! 👋" +
                            "Nếu bạn đã đăng nhập key trước đó hãy thoát ra và đăng nhập Google trước rồi mới đăng nhập key",
                    type = "text", imageData = null, createdAt = ""
                )
                withContext(Dispatchers.Main) { addMessageBubble(dummyMsg, true) }
                return@launch
            }

            val sessionResult = ChatApiClient.getOrCreateSession(deviceId, googleEmail, googleName)
            withContext(Dispatchers.Main) {
                if (sessionResult.session != null) {
                    updateHeaderForSession(sessionResult.session)
                    loadInitialMessages(sessionResult.session)
                    startPolling(sessionResult.session)
                } else {
                    Toast.makeText(
                        context,
                        "Không thể kết nối chat: ${sessionResult.error}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // ── Send message ───────────────────────────────────────────────────
        // FIX: KHÔNG dùng optimistic bubble.
        // Sau khi API call thành công, fetch ngay (delay 400ms) để hiển thị message thật.
        // → Tránh hoàn toàn việc message xuất hiện 2 lần.
        fun sendMessage(text: String? = null, imageBase64: String? = null) {
            val session = currentSession ?: return
            if (!isGoogleLoggedIn) {
                Toast.makeText(context, "Vui lòng đăng nhập Google để chat", Toast.LENGTH_SHORT).show()
                return
            }
            if (session.status == "closed") {
                Toast.makeText(context, context.getString(R.string.chat_status_closed), Toast.LENGTH_SHORT).show()
                return
            }

            val msgType = if (imageBase64 != null) "image" else "text"
            val content = text ?: ""

            // Tiếng gửi tin nhắn
            playSound(sendSoundId)

            // Disable send button + clear input ngay
            btnSend.isEnabled  = false
            btnSend.alpha      = 0.5f
            etInput.setText("")

            // Hiện typing indicator nếu đang accepted
            if (session.status == "accepted") showTyping(true)

            lifecycle?.lifecycleScope?.launch {
                val sendResult = ChatApiClient.sendMessage(
                    sessionId  = session.id,
                    deviceId   = deviceId,
                    content    = content,
                    type       = msgType,
                    imageData  = imageBase64,
                )

                withContext(Dispatchers.Main) {
                    btnSend.isEnabled = true
                    btnSend.alpha     = 1f
                }

                if (sendResult.success) {
                    // Đợi 400ms để DB write hoàn tất, rồi fetch message thật từ server
                    delay(400)
                    val result = ChatApiClient.getMessages(
                        sessionId = session.id,
                        deviceId  = deviceId,
                        afterId   = lastMessageId,
                    )
                    withContext(Dispatchers.Main) {
                        showTyping(false)
                        // Chỉ add messages mới, tránh trùng lặp bằng cách so sánh id
                        result.messages.forEach { msg ->
                            if (msg.id > lastMessageId) {
                                addMessageBubble(msg, true)
                                if (msg.sender != "user") playSound(receiveSoundId)
                                lastMessageId = msg.id
                            }
                        }
                        markAllSeen(lastMessageId)
                        if (result.session != null) updateHeaderForSession(result.session)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showTyping(false)
                        Toast.makeText(
                            context,
                            "Gửi thất bại, thử lại",
                            Toast.LENGTH_SHORT
                        ).show()
                        // Khôi phục lại text nếu gửi lỗi
                        if (content.isNotEmpty()) etInput.setText(content)
                    }
                }
            }
        }

        // ── Send button ────────────────────────────────────────────────────
        btnSend.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) sendMessage(text)
        }

        etInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val text = etInput.text.toString().trim()
                if (text.isNotEmpty()) sendMessage(text)
                true
            } else false
        }

        // ── Emoji panel ───────────────────────────────────────────────────
        btnEmoji.setOnClickListener {
            if (emojiPanelVisible) {
                emojiPanelView?.let { panel ->
                    panel.animate().alpha(0f).translationY(20f).setDuration(150)
                        .withEndAction { (panel.parent as? LinearLayout)?.removeView(panel) }.start()
                }
                emojiPanelView  = null
                emojiPanelVisible = false
            } else {
                val inputArea = view.findViewById<LinearLayout>(R.id.chatInputArea)
                val panel = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(12, 8, 12, 8)
                    setBackgroundColor(Color.parseColor("#1A1A1A"))
                    alpha        = 0f
                    translationY = 20f
                }
                val scroll = android.widget.HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                }
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                EMOJI_LIST.forEach { emoji ->
                    val tv = TextView(context).apply {
                        text     = emoji
                        textSize = 24f
                        setPadding(12, 4, 12, 4)
                        setOnClickListener { etInput.append(emoji) }
                    }
                    row.addView(tv)
                }
                scroll.addView(row)
                panel.addView(scroll)
                inputArea.addView(panel, 1)
                panel.animate().alpha(1f).translationY(0f).setDuration(200).start()
                emojiPanelView    = panel
                emojiPanelVisible = true
            }
        }

        // ── Image picker ──────────────────────────────────────────────────
        btnImage.setOnClickListener {
            onImageNeeded?.invoke()
        }

        // ── Close ─────────────────────────────────────────────────────────
        btnClose.setOnClickListener {
            pollJob?.cancel()
            card.animate().alpha(0f).translationY(80f).setDuration(200).start()
            btnClose.animate().alpha(0f).translationY(80f).setDuration(200)
                .withEndAction { dialog.dismiss() }.start()
        }

        dialog.setOnDismissListener {
            pollJob?.cancel()
            handler.removeCallbacksAndMessages(null)
            soundPool.release()
        }

        // ── Entrance animation ─────────────────────────────────────────────
        card.alpha        = 0f
        card.translationY = 120f
        btnClose.alpha        = 0f
        btnClose.translationY = 120f
        dialog.setOnShowListener {
            card.animate().alpha(1f).translationY(0f)
                .setDuration(400).setInterpolator(OvershootInterpolator(1.1f)).start()
            btnClose.animate().alpha(1f).translationY(0f)
                .setDuration(400).setInterpolator(OvershootInterpolator(1.1f)).start()
        }

        dialog.show()
    }

    /**
     * Hiển thị ảnh vào ImageView từ:
     *  - URL (http/https)   → dùng Glide
     *  - Base64 string      → decode thủ công
     *  - null               → hiển thị placeholder icon
     *
     * Nếu không load được, fallback sang tvFallback hiện "[Ảnh không tải được]".
     */
    private fun renderImageIntoView(
        context: Context,
        imageData: String?,
        ivImg: ImageView,
        tvFallback: TextView,
    ) {
        when {
            imageData.isNullOrBlank() -> {
                // Không có dữ liệu ảnh — hiện placeholder
                ivImg.setImageResource(R.drawable.ic_chat_image)
                ivImg.scaleType = ImageView.ScaleType.CENTER
                ivImg.alpha     = 0.4f
            }
            imageData.startsWith("http://") || imageData.startsWith("https://") -> {
                // URL → Glide
                ivImg.scaleType = ImageView.ScaleType.CENTER_CROP
                ivImg.alpha     = 1f
                Glide.with(context)
                    .load(imageData)
                    .centerCrop()
                    .placeholder(R.drawable.ic_chat_image)
                    .error(R.drawable.ic_chat_image)
                    .into(ivImg)
            }
            else -> {
                // Base64 → decode
                try {
                    val bytes = Base64.decode(imageData, Base64.DEFAULT)
                    val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        ivImg.scaleType = ImageView.ScaleType.CENTER_CROP
                        ivImg.alpha     = 1f
                        ivImg.setImageBitmap(bmp)
                    } else {
                        throw IllegalArgumentException("null bitmap")
                    }
                } catch (_: Exception) {
                    ivImg.visibility  = View.GONE
                    tvFallback.visibility = View.VISIBLE
                    tvFallback.text   = "📷 [Ảnh không tải được]"
                }
            }
        }
    }

    /**
     * Chuyển URI ảnh thành Base64 string để gửi lên API.
     * Dùng ImageDecoder trên API 28+, fallback getBitmap cho API 26-27.
     */
    fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val bitmap: Bitmap? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator     = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            if (bitmap == null) return null
            // Scale down nếu quá lớn để tránh payload nặng
            val scaled = if (bitmap.width > 800) {
                val ratio = 800f / bitmap.width
                Bitmap.createScaledBitmap(bitmap, 800, (bitmap.height * ratio).toInt(), true)
            } else bitmap
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }
}
