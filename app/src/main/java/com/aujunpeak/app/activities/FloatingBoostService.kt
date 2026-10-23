package com.aujunpeak.app.activities

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.aujunpeak.app.R
import com.google.android.material.materialswitch.MaterialSwitch

@Suppress("DEPRECATION")
class FloatingBoostService : Service() {

    companion object {
        const val ACTION_START_BOOST = "start_boost"
        const val ACTION_STOP        = "stop"
        const val PREF_NAME          = "menu_prefs"
        const val PREF_COLOR         = "menu_color"
        const val DEFAULT_COLOR      = "#FF1744"
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var iconView: View? = null
    private var iconParams: WindowManager.LayoutParams? = null
    private var boostView: View? = null
    private var menuView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var menuVisible = false

    // Icon drag state
    private var iDragX0 = 0f; private var iDragY0 = 0f
    private var iParamX0 = 0; private var iParamY0 = 0
    private var iconMoved = false

    // Menu drag state
    private var mDragX0 = 0f; private var mDragY0 = 0f
    private var mParamX0 = 0; private var mParamY0 = 0
    private var menuMoved = false

    private var selectedTab = 0
    private var resetInProgress = false
    private var resetDone = false
    private var accentColor = Color.parseColor(DEFAULT_COLOR)

    private var modalView: View? = null
    private val MODAL_TOKEN = "float_modal"
    private var infoDotAlive = false

    // ── PERSISTED SWITCH STATES ──────────────────────────────────────────────
    // Survives close/reopen of the floating menu within the same service session.
    private val switchStates = mutableMapOf<Int, Boolean>()

    /** Strongly typed tab entry avoids View vs TextView confusion */
    private data class TabEntry(
        val container : View?,
        val content   : View?,
        val icon      : ImageView?,
        val label     : TextView?
    )

    private fun themedInflater(): LayoutInflater =
        LayoutInflater.from(ContextThemeWrapper(this, R.style.Theme_AujunpeakApp))

    // ─────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val hex = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(PREF_COLOR, DEFAULT_COLOR) ?: DEFAULT_COLOR
        accentColor = runCatching { Color.parseColor(hex) }.getOrDefault(Color.RED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BOOST -> {
                showFloatingIcon()
                handler.postDelayed({ showBoostNotification() }, 5_000L)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        infoDotAlive = false
        handler.removeCallbacksAndMessages(null)
        safeRemove(iconView); safeRemove(boostView)
        safeRemove(menuView); safeRemove(modalView)
        iconView = null; boostView = null; menuView = null; modalView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────────────────────────────────────────
    // FLOATING ICON
    // ─────────────────────────────────────────────

    private fun showFloatingIcon() {
        if (iconView != null) return
        val iv = runCatching { themedInflater().inflate(R.layout.layout_floating_icon, null) }
            .getOrNull() ?: return

        val p = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        ).apply { x = 24; y = 280 }
        iconParams = p

        iv.findViewById<View?>(R.id.vIconGlowRing)?.let { glow ->
            ValueAnimator.ofFloat(0.3f, 1f, 0.3f).apply {
                duration = 1400L; repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { glow.alpha = it.animatedValue as Float }
                start()
            }
        }

        iv.setOnTouchListener { _, e -> handleIconTouch(e, p); true }

        runCatching { wm.addView(iv, p) }.onSuccess {
            iconView = iv
            iv.scaleX = 0f; iv.scaleY = 0f
            iv.animate().scaleX(1f).scaleY(1f)
                .setDuration(400L).setInterpolator(OvershootInterpolator(2f)).start()
        }
    }

    private fun handleIconTouch(e: MotionEvent, p: WindowManager.LayoutParams) {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                iDragX0 = e.rawX; iDragY0 = e.rawY
                iParamX0 = p.x; iParamY0 = p.y
                iconMoved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - iDragX0; val dy = e.rawY - iDragY0
                if (!iconMoved && dx * dx + dy * dy > 64f) iconMoved = true
                if (iconMoved) {
                    p.x = (iParamX0 + dx).toInt(); p.y = (iParamY0 + dy).toInt()
                    runCatching { wm.updateViewLayout(iconView, p) }
                }
            }
            MotionEvent.ACTION_UP -> if (!iconMoved) toggleMenu()
        }
    }

    // ─────────────────────────────────────────────
    // MENU TOGGLE
    // ─────────────────────────────────────────────

    private fun toggleMenu() { if (menuVisible) hideMenu() else showMenu() }

    private fun showMenu() {
        if (menuVisible || menuView != null) return
        val mv = runCatching { themedInflater().inflate(R.layout.layout_floating_menu, null) }
            .getOrNull() ?: return
        val iP = iconParams ?: return

        val mp = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        ).apply { x = iP.x + 66; y = iP.y }
        menuParams = mp

        applyAccentToMenu(mv)
        wireMenu(mv, mp)        // restores switch states internally
        applySwatchColors(mv)
        selectTab(mv, selectedTab)

        runCatching { wm.addView(mv, mp) }.onSuccess {
            menuView = mv; menuVisible = true
            mv.scaleX = 0.85f; mv.scaleY = 0.85f; mv.alpha = 0f
            mv.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(300L).setInterpolator(OvershootInterpolator(1.5f)).start()
        }.onFailure { menuParams = null }
    }

    private fun hideMenu() {
        val mv = menuView ?: return
        infoDotAlive = false
        mv.animate().scaleX(0.85f).scaleY(0.85f).alpha(0f).setDuration(200L)
            .withEndAction {
                safeRemove(mv); menuView = null; menuVisible = false; menuParams = null
            }.start()
    }

    // ─────────────────────────────────────────────
    // WIRE MENU
    // ─────────────────────────────────────────────

    private fun wireMenu(mv: View, mp: WindowManager.LayoutParams) {
        mv.findViewById<ImageView>(R.id.btnMenuClose)?.setOnClickListener { hideMenu() }

        mv.findViewById<View>(R.id.menuDragHandle)?.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    mDragX0 = e.rawX; mDragY0 = e.rawY
                    mParamX0 = mp.x; mParamY0 = mp.y; menuMoved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - mDragX0; val dy = e.rawY - mDragY0
                    if (!menuMoved && dx * dx + dy * dy > 16f) menuMoved = true
                    if (menuMoved) {
                        mp.x = (mParamX0 + dx).toInt(); mp.y = (mParamY0 + dy).toInt()
                        runCatching { wm.updateViewLayout(mv, mp) }
                    }
                }
            }
            true
        }

        // Tab clicks
        mv.findViewById<View>(R.id.tabHome)?.setOnClickListener     { onTabClick(mv, 0) }
        mv.findViewById<View>(R.id.tabAim)?.setOnClickListener      { onTabClick(mv, 1) }
        mv.findViewById<View>(R.id.tabInfo)?.setOnClickListener     { onTabClick(mv, 2) }
        mv.findViewById<View>(R.id.tabSettings)?.setOnClickListener { onTabClick(mv, 3) }

        // Home switches — state restored silently, listener shows modal only on user toggle
        wireSwitch(mv, R.id.switchHomeGameMode, "Game Mode",  R.drawable.ic_game_mode)
        wireSwitch(mv, R.id.switchHomeRam,      "RAM Boost",  R.drawable.ic_ram_boost)
        wireSwitch(mv, R.id.switchHomeFps,      "FPS Unlock", R.drawable.ic_fps_unlock)
        wireSwitch(mv, R.id.switchHomeNet,      "Network",    R.drawable.ic_network_boost)
        wireSwitch(mv, R.id.switchHomeSmooth,   "Smooth",     R.drawable.ic_smooth_render)

        // Asetyaim reset
        val btnReset  = mv.findViewById<View>(R.id.btnResetData)
        val vProgress = mv.findViewById<View>(R.id.vResetProgress)
        val tvReset   = mv.findViewById<TextView>(R.id.tvResetBtnText)
        val aimCont   = mv.findViewById<View>(R.id.aimSwitchesContainer)

        // ── Restore reset visual state ───────────────────────────────────────
        if (resetDone) {
            // Show completed state immediately without animation
            vProgress?.visibility = View.VISIBLE
            tvReset?.text = getString(R.string.float_reset_done)
            aimCont?.visibility = View.VISIBLE
            aimCont?.alpha = 1f
            btnReset?.isClickable = false
            // Fill the progress bar fully after layout pass
            vProgress?.post {
                runCatching {
                    (vProgress.layoutParams as? FrameLayout.LayoutParams)?.width = btnReset?.width ?: 0
                    vProgress.requestLayout()
                }
            }
        } else if (resetInProgress) {
            // Reset was in progress when menu was closed — mark as done to keep UI consistent
            resetInProgress = false; resetDone = true
            vProgress?.visibility = View.VISIBLE
            tvReset?.text = getString(R.string.float_reset_done)
            aimCont?.visibility = View.VISIBLE
            aimCont?.alpha = 1f
            btnReset?.isClickable = false
        }

        btnReset?.setOnClickListener {
            if (!resetInProgress && !resetDone) startReset(btnReset, vProgress, tvReset, aimCont)
        }

        // Aim switches (also state-restored)
        wireSwitch(mv, R.id.switchAimAuto,        "Auto Aim",    R.drawable.ic_crosshair_target)
        wireSwitch(mv, R.id.switchAimSensitivity, "Sensitivity", R.drawable.ic_sensitivity)

        wireSwatches(mv)
    }

    // ─────────────────────────────────────────────
    // TAB LOGIC
    // ─────────────────────────────────────────────

    private fun onTabClick(mv: View, tab: Int) {
        selectedTab = tab
        val tabBtn = tabBtnFor(mv, tab)
        tabBtn?.animate()?.scaleX(0.88f)?.scaleY(0.88f)?.setDuration(80L)?.withEndAction {
            tabBtn.animate().scaleX(1f).scaleY(1f)
                .setDuration(150L).setInterpolator(OvershootInterpolator(2f)).start()
        }?.start()
        selectTab(mv, tab)
    }

    private fun tabBtnFor(mv: View, tab: Int): View? = when (tab) {
        0 -> mv.findViewById(R.id.tabHome)
        1 -> mv.findViewById(R.id.tabAim)
        2 -> mv.findViewById(R.id.tabInfo)
        3 -> mv.findViewById(R.id.tabSettings)
        else -> null
    }

    private fun buildEntries(mv: View): List<TabEntry> = listOf(
        TabEntry(mv.findViewById(R.id.tabHome),     mv.findViewById(R.id.contentHome),
                 mv.findViewById(R.id.icTabHome),   mv.findViewById(R.id.tvTabHome)),
        TabEntry(mv.findViewById(R.id.tabAim),      mv.findViewById(R.id.contentAim),
                 mv.findViewById(R.id.icTabAim),    mv.findViewById(R.id.tvTabAim)),
        TabEntry(mv.findViewById(R.id.tabInfo),     mv.findViewById(R.id.contentInfo),
                 mv.findViewById(R.id.icTabInfo),   mv.findViewById(R.id.tvTabInfo)),
        TabEntry(mv.findViewById(R.id.tabSettings), mv.findViewById(R.id.contentSettings),
                 mv.findViewById(R.id.icTabSettings), mv.findViewById(R.id.tvTabSettings))
    )

    private fun selectTab(mv: View, tab: Int) {
        buildEntries(mv).forEachIndexed { i, e ->
            val active = (i == tab)
            e.container?.setBackgroundResource(
                if (active) R.drawable.bg_left_tab_selected else R.drawable.bg_left_tab_normal
            )
            if (active) tintTabBg(e.container)
            e.icon?.alpha = if (active) 1f else 0.4f
            e.label?.setTextColor(if (active) Color.WHITE else Color.parseColor("#66FFFFFF"))

            if (active) {
                if (e.content?.visibility != View.VISIBLE) {
                    e.content?.alpha = 0f
                    e.content?.visibility = View.VISIBLE
                    e.content?.animate()?.alpha(1f)?.setDuration(200L)?.start()
                }
            } else {
                e.content?.visibility = View.GONE
            }
        }
        if (tab == 2) { infoDotAlive = true; pulseInfoDot(mv) } else infoDotAlive = false
    }

    private fun tintTabBg(v: View?) {
        runCatching {
            val bg = v?.background?.mutate()
            (bg as? GradientDrawable)?.setColor(
                Color.argb(60, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            )
            (bg as? GradientDrawable)?.setStroke(
                2, Color.argb(110, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            )
        }
    }

    // ─────────────────────────────────────────────
    // SWITCH + MODAL
    // ─────────────────────────────────────────────

    /**
     * Wires a MaterialSwitch with persistent state:
     * 1. Sets isChecked from [switchStates] BEFORE attaching the listener
     *    → restoring state does NOT trigger the modal.
     * 2. Listener fires only on genuine user interaction, saves state and shows modal.
     */
    private fun wireSwitch(mv: View, switchId: Int, label: String, iconRes: Int) {
        val sw = mv.findViewById<MaterialSwitch>(switchId) ?: return

        // Restore silently — no listener attached yet
        sw.isChecked = switchStates[switchId] ?: false

        // Now attach listener; only real user toggles reach here
        sw.setOnCheckedChangeListener { _, on ->
            switchStates[switchId] = on
            showModal(label, iconRes, on)
        }
    }

    private fun showModal(label: String, iconRes: Int, isOn: Boolean) {
        safeRemove(modalView); modalView = null

        val modal: View = runCatching {
            themedInflater().inflate(R.layout.layout_floating_switch_modal, null)
        }.getOrNull() ?: return

        modal.findViewById<ImageView>(R.id.ivFloatModalIcon)?.setImageResource(iconRes)
        modal.findViewById<TextView>(R.id.tvFloatModalTitle)?.text = label

        val tvState:  TextView?  = modal.findViewById(R.id.tvFloatModalState)
        val ivStatus: ImageView? = modal.findViewById(R.id.ivFloatModalStatus)
        val progFill: View?      = modal.findViewById(R.id.vFloatModalProgress)

        if (isOn) {
            tvState?.text = getString(R.string.float_on)
            tvState?.setTextColor(Color.parseColor("#69FF47"))
            ivStatus?.setImageResource(R.drawable.ic_status_on)
        } else {
            tvState?.text = getString(R.string.float_off)
            tvState?.setTextColor(Color.parseColor("#FF5252"))
            ivStatus?.setImageResource(R.drawable.ic_status_off)
        }

        runCatching {
            (modal.background?.mutate() as? GradientDrawable)?.setStroke(2, accentColor)
        }

        val mp = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 60 }

        runCatching { wm.addView(modal, mp) }.onFailure { return }
        modalView = modal

        modal.alpha = 0f; modal.translationY = -60f
        modal.animate().alpha(1f).translationY(0f)
            .setDuration(320L).setInterpolator(OvershootInterpolator(1.6f)).start()

        ivStatus?.also { it.scaleX = 0f; it.scaleY = 0f }
        ivStatus?.animate()?.scaleX(1f)?.scaleY(1f)
            ?.setDuration(350L)?.setStartDelay(150L)
            ?.setInterpolator(OvershootInterpolator(2f))?.start()

        modal.post {
            val pw = (modal.width - modal.paddingLeft - modal.paddingRight).coerceAtLeast(1)
            val s = if (isOn) 0 else pw; val t = if (isOn) pw else 0
            (progFill?.layoutParams as? FrameLayout.LayoutParams)?.width = s
            progFill?.requestLayout()
            ValueAnimator.ofInt(s, t).apply {
                duration = 700L; startDelay = 80L; interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    (progFill?.layoutParams as? FrameLayout.LayoutParams)?.width = anim.animatedValue as Int
                    progFill?.requestLayout()
                }
                start()
            }
        }

        handler.removeCallbacksAndMessages(MODAL_TOKEN)
        handler.postAtTime({
            val cur = modalView ?: return@postAtTime
            cur.animate().alpha(0f).translationY(-30f).setDuration(250L).withEndAction {
                safeRemove(cur); if (modalView == cur) modalView = null
            }.start()
        }, MODAL_TOKEN, SystemClock.uptimeMillis() + 2200L)
    }

    // ─────────────────────────────────────────────
    // ASETYAIM RESET ANIMATION
    // ─────────────────────────────────────────────

    private fun startReset(btn: View, vProg: View?, tvText: TextView?, aimCont: View?) {
        resetInProgress = true; btn.isClickable = false
        vProg?.visibility = View.VISIBLE
        tvText?.text = getString(R.string.float_resetting)

        runCatching {
            (vProg?.background?.mutate() as? GradientDrawable)?.setColor(accentColor)
        }

        val duration = 3500L
        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration; interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val lp = vProg?.layoutParams as? FrameLayout.LayoutParams
                lp?.width = (btn.width * (anim.animatedValue as Float)).toInt()
                vProg?.requestLayout()
            }
            start()
        }

        handler.postDelayed({
            resetInProgress = false; resetDone = true
            (vProg?.layoutParams as? FrameLayout.LayoutParams)?.width = btn.width
            vProg?.requestLayout()
            tvText?.text = getString(R.string.float_reset_done)
            aimCont?.alpha = 0f; aimCont?.visibility = View.VISIBLE
            aimCont?.animate()?.alpha(1f)?.setDuration(400L)
                ?.setInterpolator(OvershootInterpolator(1.2f))?.start()
            showModal("Data Reset", R.drawable.ic_autoaim_reset, true)
        }, duration + 200L)
    }

    // ─────────────────────────────────────────────
    // INFO DOT PULSE
    // ─────────────────────────────────────────────

    private fun pulseInfoDot(mv: View) {
        if (!infoDotAlive) return
        val dot = mv.findViewById<View>(R.id.vInfoActiveDot) ?: return
        dot.animate().alpha(0.2f).setDuration(800L).withEndAction {
            if (!infoDotAlive) return@withEndAction
            dot.animate().alpha(1f).setDuration(800L).withEndAction {
                if (infoDotAlive) pulseInfoDot(mv)
            }.start()
        }.start()
    }

    // ─────────────────────────────────────────────
    // COLOR SWATCHES
    // ─────────────────────────────────────────────

    private val colorDefs = listOf(
        "#FF1744" to "Đỏ",     "#2196F3" to "Xanh Dương",
        "#69FF47" to "Xanh Lá","#AA00FF" to "Tím",
        "#FF6D00" to "Cam",    "#FFD700" to "Vàng",
        "#FF4081" to "Hồng",   "#00E5FF" to "Xanh Nhạt",
        "#FFFFFF"  to "Trắng", "#00BFA5" to "Bạc Hà"
    )

    private val swatchResIds = listOf(
        R.id.swatchRed,  R.id.swatchBlue,   R.id.swatchGreen,  R.id.swatchPurple, R.id.swatchOrange,
        R.id.swatchGold, R.id.swatchPink,   R.id.swatchCyan,   R.id.swatchWhite,  R.id.swatchMint
    )
    private val ringResIds = listOf(
        R.id.ringRed,  R.id.ringBlue,  R.id.ringGreen,  R.id.ringPurple, R.id.ringOrange,
        R.id.ringGold, R.id.ringPink,  R.id.ringCyan,   R.id.ringWhite,  R.id.ringMint
    )

    private fun applySwatchColors(mv: View) {
        swatchResIds.forEachIndexed { i, id ->
            val hex = colorDefs.getOrNull(i)?.first ?: return@forEachIndexed
            val inner = (mv.findViewById<FrameLayout?>(id))?.getChildAt(0) ?: return@forEachIndexed
            runCatching {
                val bg = inner.background?.mutate()
                (bg as? GradientDrawable)?.setColor(Color.parseColor(hex))
                inner.background = bg
            }
        }
    }

    private fun wireSwatches(mv: View) {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(PREF_COLOR, DEFAULT_COLOR) ?: DEFAULT_COLOR

        ringResIds.forEachIndexed { i, rId ->
            val hex = colorDefs.getOrNull(i)?.first ?: return@forEachIndexed
            mv.findViewById<View>(rId)?.visibility =
                if (hex.equals(saved, true)) View.VISIBLE else View.GONE
        }

        val name = colorDefs.firstOrNull { it.first.equals(saved, true) }?.second ?: "Đỏ"
        mv.findViewById<TextView>(R.id.tvSelectedColorName)?.text = "Màu hiện tại: $name"

        swatchResIds.forEachIndexed { i, sId ->
            val container = mv.findViewById<FrameLayout?>(sId) ?: return@forEachIndexed
            val (hex, colorName) = colorDefs.getOrNull(i) ?: return@forEachIndexed
            container.setOnClickListener {
                container.animate().scaleX(1.35f).scaleY(1.35f).setDuration(90L).withEndAction {
                    container.animate().scaleX(1f).scaleY(1f)
                        .setDuration(200L).setInterpolator(OvershootInterpolator(2.5f)).start()
                }.start()

                prefs.edit().putString(PREF_COLOR, hex).apply()
                accentColor = runCatching { Color.parseColor(hex) }.getOrDefault(Color.RED)

                mv.findViewById<TextView>(R.id.tvSelectedColorName)?.text = "Màu hiện tại: $colorName"

                ringResIds.forEachIndexed { j, rId ->
                    val rHex = colorDefs.getOrNull(j)?.first ?: return@forEachIndexed
                    mv.findViewById<View>(rId)?.visibility =
                        if (rHex.equals(hex, true)) View.VISIBLE else View.GONE
                }

                applyAccentToMenu(mv)
                selectTab(mv, selectedTab)
            }
        }
    }

    // ─────────────────────────────────────────────
    // ACCENT COLOR APPLICATION
    // ─────────────────────────────────────────────

    private fun applyAccentToMenu(mv: View) {
        val r = Color.red(accentColor); val g = Color.green(accentColor); val b = Color.blue(accentColor)
        runCatching {
            (mv.background?.mutate() as? GradientDrawable)?.setStroke(1, Color.argb(80, r, g, b))
        }
        runCatching {
            val hBg = mv.findViewById<View>(R.id.menuDragHandle)?.background?.mutate()
            (hBg as? GradientDrawable)?.setStroke(1, Color.argb(120, r, g, b))
        }
        runCatching {
            val dotBg = mv.findViewById<View>(R.id.vInfoActiveDot)?.background?.mutate()
            (dotBg as? GradientDrawable)?.setColor(accentColor)
        }
        runCatching {
            val btnBg = mv.findViewById<View>(R.id.btnResetData)?.background?.mutate()
            (btnBg as? GradientDrawable)?.setColor(accentColor)
        }
    }

    // ─────────────────────────────────────────────
    // BOOST NOTIFICATION
    // ─────────────────────────────────────────────

    private fun showBoostNotification() {
        val bv: View = runCatching {
            themedInflater().inflate(R.layout.layout_floating_boost, null)
        }.getOrNull() ?: return

        // Must pass explicit px width — WindowManager.WRAP_CONTENT measures with UNSPECIFIED,
        // which ignores the root view's layout_width and collapses match_parent / weight children to 0.
        val density = resources.displayMetrics.density
        val boostWidthPx = (272 * density + 0.5f).toInt()

        val bp = overlayParams(
            boostWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 60 }

        runCatching { wm.addView(bv, bp) }.onFailure { return }
        boostView = bv

        bv.alpha = 0f; bv.translationY = -30f
        bv.animate().alpha(1f).translationY(0f)
            .setDuration(380L).setInterpolator(OvershootInterpolator(1.2f)).start()

        bv.findViewById<View?>(R.id.tvBoostIcon)?.let { icon ->
            icon.scaleX = 0.7f; icon.scaleY = 0.7f; icon.alpha = 0.4f
            icon.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(420L).setInterpolator(OvershootInterpolator(2f)).start()
            icon.animate().rotationBy(18f).setDuration(220L).withEndAction {
                icon.animate().rotation(0f).setDuration(240L).start()
            }.start()
        }

        listOf(R.id.tvBoostStep1, R.id.tvBoostStep2, R.id.tvBoostStep3).forEachIndexed { index, id ->
            (bv.findViewById<View?>(id)?.parent as? View)?.let { row ->
                row.alpha = 0f
                row.translationX = 24f
                row.animate().alpha(1f).translationX(0f)
                    .setStartDelay(150L + index * 120L)
                    .setDuration(320L)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .start()
            }
        }

        val progressBar: View? = bv.findViewById(R.id.vBoostProgress)
        bv.post {
            val tw = (progressBar?.parent as? View)?.width ?: 280
            ValueAnimator.ofInt(0, tw).apply {
                duration = 2_200L; interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    (progressBar?.layoutParams as? FrameLayout.LayoutParams)?.width = anim.animatedValue as Int
                    progressBar?.requestLayout()
                }
                start()
            }
        }

        handler.postDelayed({
            bv.animate().alpha(0f).translationY(-20f).setDuration(320L).withEndAction {
                safeRemove(bv); boostView = null
            }.start()
        }, 4_500L)
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private fun overlayParams(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun safeRemove(v: View?) {
        v ?: return
        runCatching { wm.removeView(v) }
    }
}
