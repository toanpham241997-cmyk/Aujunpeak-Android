package com.aujunpeak.app.activities

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.aujunpeak.app.fragments.InfoFragment
import com.aujunpeak.app.R
import com.aujunpeak.app.utils.AppStats
import com.aujunpeak.app.utils.HeartbeatScheduler
import com.aujunpeak.app.utils.KeyManager
import com.aujunpeak.app.utils.KeyRevokedDialog
import com.aujunpeak.app.utils.LocaleHelper
import com.aujunpeak.app.utils.NotificationStore
import com.aujunpeak.app.utils.NotificationsListDialog
import com.aujunpeak.app.utils.ThemeManager
import com.aujunpeak.app.views.RainView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import com.aujunpeak.app.utils.ChatDialog
import com.aujunpeak.app.utils.ChatUnreadStore


class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var typewriterRunning = false
    private val TITLE_TEXT = "Aujunpeak"
    private lateinit var rainOverlay: RainView
    private lateinit var navController: NavController

    // ── Foreground heartbeat ──────────────────────────────────────────────────
    private val FOREGROUND_HEARTBEAT_MS = 2 * 60 * 1000L
    private var heartbeatRunning        = false
    private var isShowingRevokedDialog  = false
    private val HEARTBEAT_TOKEN         = Any()

    // ── Bell icon & badge ─────────────────────────────────────────────────────
    private lateinit var ivHeaderBell: ImageView
    private lateinit var headerBellBadge: View
    private var bellShakeAnim: ObjectAnimator? = null


    // ── Chat icon & badge ─────────────────────────────────────────────────────
    private lateinit var ivHeaderChat: ImageView
    private lateinit var headerChatBadge: TextView
    private var chatIconAnim: ObjectAnimator? = null
    private var chatBadgePollRunnable: Runnable? = null
    private val CHAT_BADGE_POLL_MS = 15_000L

    // ── Toast container ───────────────────────────────────────────────────────
    private lateinit var toastContainer: FrameLayout

    // ── Custom nav item data ──────────────────────────────────────────────────
    private data class NavEntry(
        val frame: FrameLayout,
        val icon: ImageView,
        val glow: View,
        val label: TextView,
        val destId: Int,
        val requiresLogin: Boolean,
        val requiresVip: Boolean = false,
    )

    private lateinit var navEntries: List<NavEntry>
    private var selectedDestId: Int = R.id.navigation_info

    // ── Xin quyền POST_NOTIFICATIONS (Android 13+) ────────────────────────────
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Quyền được cấp → đăng ký token lên server ngay
            MyFirebaseMessagingService.refreshAndRegisterToken(this)
        }
    }


    // ── Image picker for chat ─────────────────────────────────────────────────
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val base64 = ChatDialog.uriToBase64(this, uri)
                if (base64 != null) {
                    pendingChatImageBase64 = base64
                    // Re-open chat dialog with pending image
                    openChatWithPendingImage()
                }
            }
        }
    }
    private var pendingChatImageBase64: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyRedBlackChrome()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        startTypewriterEffect()
        loadAnimatedBackground()

        rainOverlay = findViewById(R.id.rainOverlay)
        rainOverlay.start()

        toastContainer = findViewById(R.id.notifToastContainer)
        ivHeaderBell   = findViewById(R.id.ivHeaderBell)
        headerBellBadge = findViewById(R.id.headerBellBadge)
        ivHeaderChat   = findViewById(R.id.ivHeaderChat)
        headerChatBadge = findViewById<TextView>(R.id.headerChatBadge)

        setupHeaderBell()
        setupHeaderChat()
        setupCustomNav()
        HeartbeatScheduler.schedule(this)

        // Thống kê lần mở app
        AppStats.incrementOpenCount(this)

        // ── Khởi tạo Notification Channel (bắt buộc cho Android 8+) ──────────
        MyFirebaseMessagingService.createChannel(this)

        // ── Xin quyền POST_NOTIFICATIONS (bắt buộc cho Android 13+) ─────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // ── Đăng ký FCM token lên server nếu đã login ────────────────────────
        if (KeyManager.isLoggedIn(this)) {
            MyFirebaseMessagingService.refreshAndRegisterToken(this)
        }
    }


    /** Hard-enforce the compact black/red chrome at runtime so no theme/resource overlay can reintroduce navy/blue. */
    private fun applyRedBlackChrome() {
        val red = Color.parseColor("#FF1744")
        val black = Color.BLACK

        window.statusBarColor = black
        window.navigationBarColor = black

        findViewById<View>(R.id.appHeader)?.let { header ->
            header.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(black)
                setStroke(dpToPx(1), red)
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, dpToPx(11).toFloat(), dpToPx(11).toFloat(), dpToPx(11).toFloat(), dpToPx(11).toFloat())
            }
        }

        findViewById<View>(R.id.customBottomNav)?.let { nav ->
            nav.setPadding(0, 0, 0, 0)
            nav.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(black)
                setStroke(dpToPx(1), red)
                cornerRadii = floatArrayOf(dpToPx(13).toFloat(), dpToPx(13).toFloat(), dpToPx(13).toFloat(), dpToPx(13).toFloat(), 0f, 0f, 0f, 0f)
            }
            nav.elevation = dpToPx(6).toFloat()
        }

        // Header action buttons are always black/red, independent of the saved theme color.
        listOf(R.id.headerChatWrapper, R.id.headerBellWrapper).forEach { id ->
            findViewById<View>(id)?.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(black)
                setStroke(dpToPx(1), red)
                cornerRadius = dpToPx(9).toFloat()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyRedBlackChrome()
        startForegroundHeartbeat()
        refreshNavLockState()
        updateBellBadge(NotificationStore.getUnreadCount(this))
        startChatBadgePolling()
        // Theo dõi thời gian online
        AppStats.startSession(this)
        // Hiển thị cảnh báo hết hạn key nếu cần
        checkExpiryWarning()
    }

    override fun onPause() {
        super.onPause()
        stopForegroundHeartbeat()
        stopChatBadgePolling()
        AppStats.endSession(this)
    }

    // ═══════════════════════════════════════════════════════════════
    // EXPIRY WARNING DIALOG — hiện khi key còn ≤ 3 ngày
    // ═══════════════════════════════════════════════════════════════
    private fun checkExpiryWarning() {
        val daysLeft = KeyManager.getExpiryWarningDaysLeft(this) ?: return

        // Chỉ hiện một lần mỗi ngày (lưu ngày cuối hiển thị)
        val prefs     = getSharedPreferences("aujunpeak_key_prefs", Context.MODE_PRIVATE)
        val todayStr  = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        val lastShown = prefs.getString("expiry_warn_shown_date", "")
        if (lastShown == todayStr) return
        prefs.edit().putString("expiry_warn_shown_date", todayStr).apply()

        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_expiry_warning, null)
        dialogView.findViewById<TextView>(R.id.tvExpiryDialogCountdown)?.text =
            "Còn $daysLeft ngày"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.btnExpiryDismiss)?.setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.btnExpiryRenewZalo)?.setOnClickListener {
            dialog.dismiss()
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://zalo.me/0833091543"))
                )
            } catch (_: Exception) {}
        }

        dialog.show()
    }


    // ═══════════════════════════════════════════════════════════════
    // CHAT ICON — setup, badge, pulse animation
    // ═══════════════════════════════════════════════════════════════

    private fun setupHeaderChat() {
        val wrapper = findViewById<FrameLayout>(R.id.headerChatWrapper)
        wrapper.setOnClickListener { showChatDialog() }
        updateChatBadge(0)  // No unread count initially
    }

    fun updateChatBadge(unreadCount: Int) {
        if (unreadCount > 0) {
            val wasHidden = headerChatBadge.visibility != View.VISIBLE
            headerChatBadge.text = if (unreadCount > 9) "9+" else unreadCount.toString()
            headerChatBadge.visibility = View.VISIBLE
            ivHeaderChat.setColorFilter(android.graphics.Color.parseColor("#FF1744"))
            startChatIconPulse()
            if (wasHidden) {
                headerChatBadge.scaleX = 0f
                headerChatBadge.scaleY = 0f
                headerChatBadge.animate().scaleX(1f).scaleY(1f)
                    .setDuration(280).setInterpolator(OvershootInterpolator(2.2f)).start()
            }
        } else {
            headerChatBadge.visibility = View.GONE
            ivHeaderChat.setColorFilter(android.graphics.Color.parseColor("#CCCCCC"))
            stopChatIconPulse()
        }
    }

    /** Poll định kỳ để phát hiện tin nhắn admin/bot chưa đọc khi khung chat đang đóng. */
    private fun startChatBadgePolling() {
        stopChatBadgePolling()
        val runnable = object : Runnable {
            override fun run() {
                refreshChatUnreadBadge()
                handler.postDelayed(this, CHAT_BADGE_POLL_MS)
            }
        }
        chatBadgePollRunnable = runnable
        handler.post(runnable)
    }

    private fun stopChatBadgePolling() {
        chatBadgePollRunnable?.let { handler.removeCallbacks(it) }
        chatBadgePollRunnable = null
    }

    private fun refreshChatUnreadBadge() {
        if (!KeyManager.isGoogleLoggedIn(this)) {
            updateChatBadge(0)
            return
        }
        val deviceId = KeyManager.getOrCreateDeviceId(this)
        lifecycleScope.launch {
            val count = ChatUnreadStore.checkUnreadCount(this@MainActivity, deviceId)
            updateChatBadge(count)
        }
    }

    private fun startChatIconPulse() {
        if (chatIconAnim?.isRunning == true) return
        chatIconAnim = ObjectAnimator.ofFloat(ivHeaderChat, "alpha", 1f, 0.5f, 1f).apply {
            duration = 900
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopChatIconPulse() {
        chatIconAnim?.cancel()
        chatIconAnim = null
        ivHeaderChat.alpha = 1f
    }

    private fun showChatDialog() {
        ChatDialog.show(
            context = this,
            onImageNeeded = {
                // Launch image picker
                val intent = android.content.Intent(android.content.Intent.ACTION_PICK).apply {
                    type = "image/*"
                }
                try {
                    pickImageLauncher.launch(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, "Không thể mở thư viện ảnh", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun openChatWithPendingImage() {
        val imageBase64 = pendingChatImageBase64 ?: return
        pendingChatImageBase64 = null
        // Gửi ảnh qua API
        val deviceId = KeyManager.getOrCreateDeviceId(this)
        val email    = KeyManager.getGoogleEmail(this)
        if (email.isEmpty()) return
        lifecycleScope.launch {
            val sessionResult = com.aujunpeak.app.utils.ChatApiClient.getSession(deviceId)
            val session = sessionResult.session ?: return@launch
            com.aujunpeak.app.utils.ChatApiClient.sendMessage(
                sessionId = session.id,
                deviceId  = deviceId,
                content   = "",
                type      = "image",
                imageData = imageBase64,
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BELL ICON — setup, badge, shake
    // ═══════════════════════════════════════════════════════════════

    private fun setupHeaderBell() {
        val wrapper = findViewById<FrameLayout>(R.id.headerBellWrapper)
        wrapper.setOnClickListener { showNotificationsPanel() }
        updateBellBadge(NotificationStore.getUnreadCount(this))
    }

    /**
     * Cập nhật badge đỏ + hiệu ứng rung theo số thông báo chưa đọc.
     * Phải gọi trên main thread.
     */
    fun updateBellBadge(unreadCount: Int) {
        if (unreadCount > 0) {
            headerBellBadge.visibility = View.VISIBLE
            ivHeaderBell.setColorFilter(Color.parseColor("#FF9800"))  // cam nổi bật khi có notif
            startBellShake()
        } else {
            headerBellBadge.visibility = View.GONE
            ivHeaderBell.setColorFilter(Color.parseColor("#CCCCCC"))  // xám mờ khi không có
            stopBellShake()
        }
    }

    /** Rung lắc lặp lại cho icon chuông — mỗi chu kỳ 600ms rung, nghỉ 2s, lặp lại */
    private fun startBellShake() {
        if (bellShakeAnim?.isRunning == true) return
        scheduleBellShakeCycle()
    }

    private fun scheduleBellShakeCycle() {
        if (headerBellBadge.visibility != View.VISIBLE) return  // đã tắt badge → dừng
        handler.postDelayed({
            if (headerBellBadge.visibility != View.VISIBLE) return@postDelayed
            bellShakeAnim = ObjectAnimator.ofFloat(
                ivHeaderBell, "translationX",
                0f, -7f, 7f, -5f, 5f, -3f, 3f, 0f
            ).apply {
                duration    = 600
                repeatCount = 0
                start()
            }
            // Sau khi rung xong → lên lịch chu kỳ tiếp (nghỉ 2s)
            handler.postDelayed({ scheduleBellShakeCycle() }, 2600)
        }, 300)
    }

    private fun stopBellShake() {
        bellShakeAnim?.cancel()
        bellShakeAnim = null
        ivHeaderBell.translationX = 0f
    }

    /** Mở dialog xem tất cả thông báo */
    private fun showNotificationsPanel() {
        NotificationsListDialog.show(this) {
            // Sau khi đóng: đánh dấu đã đọc, ẩn badge
            updateBellBadge(NotificationStore.getUnreadCount(this))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FLOATING TOAST — hiện thông báo từ trên xuống, tự mất 3–4s
    // ═══════════════════════════════════════════════════════════════

    /**
     * Hiển thị floating toast ở đầu màn hình với hiệu ứng slide-down.
     * @param delay ms trễ trước khi hiển thị (để xếp hàng nhiều toast)
     */
    private fun showNotifToast(title: String, body: String, delay: Long = 0L) {
        handler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed

            val toast = LayoutInflater.from(this).inflate(R.layout.view_notif_toast, toastContainer, false)
            toast.findViewById<TextView>(R.id.toastTitle).text = title
            toast.findViewById<TextView>(R.id.toastBody).text  = body

            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity     = Gravity.TOP or Gravity.FILL_HORIZONTAL
                marginStart = dpToPx(14)
                marginEnd   = dpToPx(14)
                topMargin   = dpToPx(8)
            }
            toastContainer.addView(toast, lp)

            // Slide down từ trên
            toast.translationY = -dpToPx(120).toFloat()
            toast.alpha        = 0f
            toast.animate()
                .translationY(0f).alpha(1f)
                .setDuration(380).setInterpolator(OvershootInterpolator(1.2f))
                .start()

            // Pulse green dot
            val dotGlow = toast.findViewById<View>(R.id.toastDotGlow)
            val dotCore = toast.findViewById<View>(R.id.toastDotCore)
            pulseGreenDot(dotGlow, dotCore)

            // Tap to open notification list
            toast.setOnClickListener {
                dismissToast(toast)
                showNotificationsPanel()
            }

            // Auto-dismiss sau 3.5s
            handler.postDelayed({ dismissToast(toast) }, 3500)

        }, delay)
    }

    private fun dismissToast(toast: View) {
        if (toast.parent == null) return
        toast.animate()
            .translationY(-dpToPx(100).toFloat())
            .alpha(0f)
            .setDuration(280)
            .withEndAction { toastContainer.removeView(toast) }
            .start()
    }

    private fun pulseGreenDot(glow: View, core: View) {
        ObjectAnimator.ofFloat(glow, "alpha", 0.8f, 0.2f, 0.8f).apply {
            duration    = 900
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
        ObjectAnimator.ofFloat(core, "scaleX", 1f, 1.25f, 1f).apply {
            duration    = 900
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
        ObjectAnimator.ofFloat(core, "scaleY", 1f, 1.25f, 1f).apply {
            duration    = 900
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    /** Chuyển dp → px */
    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()

    // ═══════════════════════════════════════════════════════════════
    // FOREGROUND HEARTBEAT
    // ═══════════════════════════════════════════════════════════════

    private fun startForegroundHeartbeat() {
        if (heartbeatRunning) return
        heartbeatRunning = true
        // Chạy ngay lập tức để phát hiện key bị khóa/xóa tức thì
        performHeartbeat()
    }

    private fun stopForegroundHeartbeat() {
        heartbeatRunning = false
        handler.removeCallbacksAndMessages(HEARTBEAT_TOKEN)
    }

    private fun scheduleNextHeartbeat() {
        if (!heartbeatRunning) return
        handler.postAtTime({
            if (!heartbeatRunning) return@postAtTime
            performHeartbeat()
        }, HEARTBEAT_TOKEN, android.os.SystemClock.uptimeMillis() + FOREGROUND_HEARTBEAT_MS)
    }

    private fun performHeartbeat() {
        if (!KeyManager.isLoggedIn(this)) {
            scheduleNextHeartbeat()
            return
        }
        lifecycleScope.launch {
            val result = KeyManager.sendHeartbeat(this@MainActivity)

            if (!result.ok) {
                if ((result.revoked || result.expired) && !isShowingRevokedDialog) {
                    isShowingRevokedDialog = true
                    runOnUiThread {
                        // Lưu thông báo vào store + cập nhật badge ngay (không show toast
                        // vì dialog revoked là separate window và sẽ che mất toast)
                        if (result.notifications.isNotEmpty()) {
                            NotificationStore.save(this@MainActivity, result.notifications)
                            updateBellBadge(NotificationStore.getUnreadCount(this@MainActivity))
                            lifecycleScope.launch {
                                KeyManager.markNotificationsRead(
                                    this@MainActivity,
                                    result.notifications.map { it.id }
                                )
                            }
                        }

                        // Hiện dialog khoá ngay lập tức
                        if (!isFinishing && !isDestroyed) {
                            KeyRevokedDialog.show(
                                this@MainActivity,
                                isExpired = result.expired
                            ) {
                                // ── Đã ấn "Đã hiểu" ──
                                KeyManager.logout(this@MainActivity)
                                isShowingRevokedDialog = false
                                refreshNavLockState()
                                // Chuyển thẳng sang màn hình đăng nhập key mới
                                navigateToLogin()
                            }
                        }
                    }
                }
            } else {
                // Heartbeat OK
                if (result.newTier != null || result.newMaxDevices != null || result.newDeviceCount != null) {
                    // Admin vừa đổi quyền/hạn mức (vd: /suakey, /nangcap) — cập nhật UI ngay
                    refreshNavLockState()
                }

                // KeyManager.sendHeartbeat() đã tự dọn dẹp thông báo bị xoá (áp dụng cho
                // mọi đường gọi heartbeat, kể cả nền) — ở đây chỉ cần cập nhật lại badge.
                if (result.activeNotificationIds != null) {
                    runOnUiThread {
                        updateBellBadge(NotificationStore.getUnreadCount(this@MainActivity))
                    }
                }

                if (result.notifications.isNotEmpty()) {
                    runOnUiThread {
                        NotificationStore.save(this@MainActivity, result.notifications)
                        val unread = NotificationStore.getUnreadCount(this@MainActivity)
                        updateBellBadge(unread)
                        // Hiện toast cho từng thông báo mới (xếp hàng 500ms)
                        result.notifications.forEachIndexed { i, n ->
                            showNotifToast(n.title, n.body, delay = i * 500L)
                        }
                    }
                    lifecycleScope.launch {
                        KeyManager.markNotificationsRead(
                            this@MainActivity,
                            result.notifications.map { it.id }
                        )
                    }
                }
            }
            scheduleNextHeartbeat()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // NAVIGATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Chuyển thẳng đến màn hình nhập key (LoginFragment).
     * Hiển thị tab Hồ Sơ đang chọn trong bottom nav.
     */
    fun navigateToLogin() {
        // Làm nổi bật tab Hồ Sơ trong bottom nav
        selectedDestId = R.id.navigation_info
        selectNavEntry(R.id.navigation_info)
        // Điều hướng đến LoginFragment
        try {
            navController.navigate(
                R.id.navigation_login, null,
                NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(R.id.nav_graph, false)
                    .build()
            )
        } catch (_: Exception) {
            try { navController.navigate(R.id.navigation_login) } catch (_: Exception) {}
        }
    }

    private fun setupCustomNav() {
        navEntries = listOf(
            NavEntry(
                frame         = findViewById(R.id.navItemHome),
                icon          = findViewById(R.id.navIconHome),
                glow          = findViewById(R.id.navGlowHome),
                label         = findViewById(R.id.navLabelHome),
                destId        = R.id.navigation_home,
                requiresLogin = true,
                requiresVip   = false,
            ),
            NavEntry(
                frame         = findViewById(R.id.navItemGame),
                icon          = findViewById(R.id.navIconGame),
                glow          = findViewById(R.id.navGlowGame),
                label         = findViewById(R.id.navLabelGame),
                destId        = R.id.navigation_game,
                requiresLogin = true,
                requiresVip   = true,
            ),
            NavEntry(
                frame         = findViewById(R.id.navItemFunction),
                icon          = findViewById(R.id.navIconFunction),
                glow          = findViewById(R.id.navGlowFunction),
                label         = findViewById(R.id.navLabelFunction),
                destId        = R.id.navigation_function,
                requiresLogin = true,
                requiresVip   = true,
            ),
            NavEntry(
                frame         = findViewById(R.id.navItemSettings),
                icon          = findViewById(R.id.navIconSettings),
                glow          = findViewById(R.id.navGlowSettings),
                label         = findViewById(R.id.navLabelSettings),
                destId        = R.id.navigation_settings,
                requiresLogin = true,
                requiresVip   = false,   // FREE key được dùng tab Cài đặt
            ),
            NavEntry(
                frame         = findViewById(R.id.navItemInfo),
                icon          = findViewById(R.id.navIconInfo),
                glow          = findViewById(R.id.navGlowInfo),
                label         = findViewById(R.id.navLabelInfo),
                destId        = R.id.navigation_info,
                requiresLogin = false,
                requiresVip   = false,
            ),
        )

        navEntries.forEach { entry ->
            entry.frame.setOnClickListener {
                val isLoggedIn = KeyManager.isLoggedIn(this)
                val isVip      = KeyManager.isVip(this)

                when {
                    entry.requiresLogin && !isLoggedIn -> {
                        showSnackbar("🔑 Vui lòng kích hoạt key trước để sử dụng tính năng này")
                        navigateToTab(R.id.navigation_login)
                        return@setOnClickListener
                    }
                    entry.requiresVip && !isVip -> {
                        showVipRequiredSnackbar()
                        return@setOnClickListener
                    }
                    else -> navigateToTab(entry.destId)
                }
            }
        }

        refreshNavLockState()
        selectNavEntry(R.id.navigation_info)
    }

    /** Cập nhật trạng thái khóa/mở của các tab theo tier hiện tại */
    fun refreshNavLockState() {
        val isLoggedIn = KeyManager.isLoggedIn(this)
        val isVip      = KeyManager.isVip(this)

        navEntries.forEach { entry ->
            val isLocked = when {
                entry.requiresVip && (!isLoggedIn || !isVip) -> true
                entry.requiresLogin && !isLoggedIn           -> true
                else                                         -> false
            }
            entry.icon.alpha  = if (isLocked) 0.35f else 1f
            entry.label.alpha = if (isLocked) 0.35f else 1f
        }
    }

    fun navigateToTab(destId: Int) {
        if (selectedDestId == destId) return
        selectedDestId = destId

        val navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setPopUpTo(R.id.nav_graph, inclusive = false, saveState = true)
            .build()

        try { navController.navigate(destId, null, navOptions) }
        catch (_: Exception) { navController.navigate(destId) }

        selectNavEntry(destId)
    }

    private fun selectNavEntry(destId: Int) {
        navEntries.forEach { entry ->
            val selected    = entry.destId == destId
            val targetColor = if (selected)
                Color.parseColor("#FF1744")
            else
                Color.parseColor("#666666")
            entry.icon.setColorFilter(targetColor)
            entry.label.setTextColor(targetColor)
            entry.glow.visibility = if (selected) View.VISIBLE else View.INVISIBLE

            if (selected) {
                entry.frame.animate().scaleX(1.12f).scaleY(1.12f).setDuration(180)
                    .setInterpolator(OvershootInterpolator(2f)).withEndAction {
                        entry.frame.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    }.start()
            }
        }
    }

    private fun showVipRequiredSnackbar() {
        val rootView = findViewById<View>(android.R.id.content)
        Snackbar.make(rootView, "👑  Tính năng chỉ dành cho key VIP", Snackbar.LENGTH_LONG)
            .setBackgroundTint(Color.BLACK)
            .setTextColor(Color.parseColor("#FFD700"))
            .setActionTextColor(Color.parseColor("#FF1744"))
            .setAction("Hồ sơ") { navigateToTab(R.id.navigation_info) }
            .show()
    }

    private fun showSnackbar(msg: String) {
        val rootView = findViewById<View>(android.R.id.content)
        Snackbar.make(rootView, msg, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(Color.BLACK)
            .setTextColor(Color.WHITE)
            .show()
    }

    // ═══════════════════════════════════════════════════════════════
    // LOGOUT / LOGIN CALLBACKS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Được gọi từ LoginFragment sau khi đăng nhập key hoặc Google thành công.
     * Không dùng navigateToTab() vì hàm đó có guard: if (selectedDestId == destId) return
     * navigateToLogin() set selectedDestId = navigation_info trước khi mở màn Login,
     * nên guard sẽ early-return và không navigate gì cả.
     * → Phải navigate trực tiếp, bỏ qua guard, rồi force-refresh InfoFragment.
     */
    fun navigateToProfileTab() {
        selectedDestId = R.id.navigation_info
        val navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(R.id.nav_graph, inclusive = false, saveState = false)
            .build()
        try { navController.navigate(R.id.navigation_info, null, navOptions) }
        catch (_: Exception) {
            try { navController.navigate(R.id.navigation_info) } catch (_: Exception) {}
        }
        selectNavEntry(R.id.navigation_info)
        refreshNavLockState()
        // Thử refresh ngay lập tức, rồi retry ở 200ms và 500ms để đảm bảo fragment đã sẵn sàng
        forceRefreshInfoFragment()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            forceRefreshInfoFragment()
        }, 200L)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            forceRefreshInfoFragment()
        }, 500L)
    }

    /** Tìm InfoFragment đang active và gọi refresh() để hiển thị data mới ngay */
    private fun forceRefreshInfoFragment() {
        try {
            val navHost = supportFragmentManager.findFragmentById(R.id.navHostFragment)
            val current = navHost?.childFragmentManager?.primaryNavigationFragment
            if (current is InfoFragment) {
                current.refresh()
            } else {
                // Fragment chưa sẵn sàng, thử lại sau 150ms
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        val navHost2 = supportFragmentManager.findFragmentById(R.id.navHostFragment)
                        val frag2 = navHost2?.childFragmentManager?.primaryNavigationFragment
                        if (frag2 is InfoFragment) frag2.refresh()
                    } catch (_: Exception) {}
                }, 150L)
            }
        } catch (_: Exception) {}
    }

    fun onLogout() {
        navigateToTab(R.id.navigation_info)
        refreshNavLockState()
        val rootView = findViewById<View>(android.R.id.content)
        Snackbar.make(rootView, "Đã đăng xuất", Snackbar.LENGTH_SHORT)
            .setBackgroundTint(Color.BLACK)
            .setTextColor(Color.WHITE)
            .show()
    }

    fun onLogin() {
        refreshNavLockState()
    }

    /** Alias được gọi từ LoginFragment */
    fun onLoginSuccess() {
        refreshNavLockState()
        // Sau khi đăng nhập thành công, đăng ký token FCM lên server
        // để server biết thiết bị này cần nhận push notification
        MyFirebaseMessagingService.refreshAndRegisterToken(this)
    }

    /**
     * Gọi từ onResume() của các fragment cần bảo vệ.
     * Nếu key đã bị logout hoặc hết hạn → redirect về tab Hồ Sơ ngay lập tức.
     */
    fun checkKeyOrForceLogout() {
        val isLoggedIn = KeyManager.isLoggedIn(this)
        val isExpired  = KeyManager.isKeyExpired(this)

        if (!isLoggedIn || isExpired) {
            if (isExpired && isLoggedIn) {
                KeyManager.logout(this)
            }
            refreshNavLockState()
            navigateToTab(R.id.navigation_info)
            showSnackbar(if (isExpired) "Key đã hết hạn, vui lòng gia hạn" else "Bạn cần đăng nhập trước")
        } else {
            refreshNavLockState()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ANIMATED BACKGROUND
    // ═══════════════════════════════════════════════════════════════

    private fun loadAnimatedBackground() {
        val bg = findViewById<ImageView>(R.id.appBackgroundImage)
        Glide.with(this)
            .asGif()
            .load(R.raw.bg_app_photo)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(bg)
    }

    // ═══════════════════════════════════════════════════════════════
    // TYPEWRITER EFFECT TIÊU ĐỀ
    // ═══════════════════════════════════════════════════════════════

    private fun startTypewriterEffect() {
        val tv = findViewById<TextView>(R.id.tvHeaderTitle) ?: return
        typewriterRunning = true
        runTypewriterCycle(tv)
    }

    private fun runTypewriterCycle(tv: TextView) {
        if (!typewriterRunning) return
        val full         = TITLE_TEXT
        val charDelay    = 90L
        val holdDelay    = 1600L
        val fadeSteps    = 15
        val fadeInterval = 40L

        for (i in 1..full.length) {
            handler.postDelayed({
                if (!typewriterRunning) return@postDelayed
                tv.text  = full.substring(0, i)
                tv.alpha = 1f
            }, i * charDelay)
        }

        val revealDone = full.length * charDelay + holdDelay
        for (step in 0..fadeSteps) {
            handler.postDelayed({
                if (!typewriterRunning) return@postDelayed
                tv.alpha = 1f - (step.toFloat() / fadeSteps)
            }, revealDone + step * fadeInterval)
        }

        val cycleDuration = revealDone + (fadeSteps * fadeInterval) + 400L
        handler.postDelayed({
            if (typewriterRunning) {
                tv.text  = ""
                tv.alpha = 1f
                runTypewriterCycle(tv)
            }
        }, cycleDuration)
    }

    override fun onDestroy() {
        super.onDestroy()
        typewriterRunning  = false
        heartbeatRunning   = false
        bellShakeAnim?.cancel()
        handler.removeCallbacksAndMessages(null)
        if (::rainOverlay.isInitialized) rainOverlay.stop()
    }
}
