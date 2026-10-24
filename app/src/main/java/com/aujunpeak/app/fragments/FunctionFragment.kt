package com.aujunpeak.app.fragments

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aujunpeak.app.R
import com.aujunpeak.app.activities.CrosshairActivity
import com.aujunpeak.app.activities.FloatingBoostService
import com.aujunpeak.app.activities.OptimizerSuperActivity
import com.aujunpeak.app.databinding.FragmentFunctionBinding
import com.aujunpeak.app.databinding.ItemSwitchIconRowBinding
import com.aujunpeak.app.utils.GameModuleInstaller

class FunctionFragment : Fragment() {

    private var _binding: FragmentFunctionBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private val MODAL_TOKEN = "func_modal_token"
    private var pendingModuleInstall: GameModuleInstaller.Module? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFunctionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCrosshairCard()
        setupPerformanceSwitches()
        setupFreeFire()
        setupGameModuleSwitches()
        setupOptimizerSuperCard()
        startOrbitalAnimation()
        animateCardsIn()
    }

    // ──────────────────────────────────────────
    // OPTIMIZER SUPER CARD
    // ──────────────────────────────────────────

    private fun setupOptimizerSuperCard() {
        binding.particlesOptimizerSuper.setColors(intArrayOf(
            Color.parseColor("#FF5C72"),
            Color.parseColor("#FF4D67"),
            Color.parseColor("#FF5C72")
        ))
        binding.particlesOptimizerSuper.setDensity(8)
        binding.particlesOptimizerSuper.start()

        // Rotating orbit rings + dot around the OPTIMIZER SUPER icon (same language as
        // the rocket-apply orbit on the Free Fire card, tuned a bit faster/eye-catching).
        ObjectAnimator.ofFloat(binding.vOptOrbitOuter, "rotation", 0f, 360f).apply {
            duration = 1800; repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART; interpolator = LinearInterpolator(); start()
        }
        ObjectAnimator.ofFloat(binding.vOptOrbitInner, "rotation", 360f, 0f).apply {
            duration = 2600; repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART; interpolator = LinearInterpolator(); start()
        }
        ValueAnimator.ofFloat(0.25f, 1f, 0.25f).apply {
            duration = 900; repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { if (_binding != null) binding.vOptOrbitDot.alpha = it.animatedValue as Float }
            start()
        }

        binding.cardOptimizerSuper.setOnClickListener {
            it.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100).withEndAction {
                it.animate().scaleX(1f).scaleY(1f)
                    .setDuration(200).setInterpolator(OvershootInterpolator(2f))
                    .withEndAction {
                        startActivity(Intent(requireContext(), OptimizerSuperActivity::class.java))
                    }.start()
            }.start()
        }
    }

    // ──────────────────────────────────────────
    // CROSSHAIR CARD
    // ──────────────────────────────────────────

    private fun setupCrosshairCard() {
        binding.crosshairParticles.setIdleMode(true)
        binding.crosshairParticles.start()

        binding.particlesPerformance.setColors(intArrayOf(
            Color.parseColor("#FF5C72"),
            Color.parseColor("#FF4D67"),
            Color.parseColor("#FF5C72")
        ))
        binding.particlesPerformance.setDensity(9)
        binding.particlesPerformance.start()

        binding.cardCrosshair.setOnClickListener {
            it.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100).withEndAction {
                it.animate().scaleX(1f).scaleY(1f)
                    .setDuration(200).setInterpolator(OvershootInterpolator(2f))
                    .withEndAction {
                        startActivity(Intent(requireContext(), CrosshairActivity::class.java))
                    }.start()
            }.start()
        }
    }

    // ──────────────────────────────────────────
    // PERFORMANCE SWITCHES
    // ──────────────────────────────────────────

    private data class PerfRow(
        val rowBinding: ItemSwitchIconRowBinding,
        val title: String,
        val sub: String,
        val iconRes: Int,
        val prefKey: String
    )

    private fun setupPerformanceSwitches() {
        val prefs = requireContext().getSharedPreferences("func_switch_prefs", android.content.Context.MODE_PRIVATE)
        val rows = listOf(
            PerfRow(binding.switchHighPerf, "FPS LOCK",
                "Force  CPU frequency", R.drawable.ic_perf_cpu, "switch_fps_lock"),
            PerfRow(binding.switchGpuTurbo, "FPS STABILIZER",
                "Boost graphics rendering",    R.drawable.ic_perf_gpu, "switch_fps_stabilizer"),
            PerfRow(binding.switchNetBoost,  "PERFORMANCE",
                "Prioritize game packets",     R.drawable.ic_perf_net, "switch_performance")
        )

        rows.forEach { row ->
            row.rowBinding.tvSwitchTitle.text = row.title
            row.rowBinding.tvSwitchSub.text   = row.sub
            row.rowBinding.ivSwitchIcon.setImageResource(row.iconRes)
            startRingRotation(row.rowBinding.vIconRing)

            // Khôi phục trạng thái đã lưu mà không kích hoạt listener
            row.rowBinding.materialSwitch.setOnCheckedChangeListener(null)
            row.rowBinding.materialSwitch.isChecked = prefs.getBoolean(row.prefKey, false)

            row.rowBinding.materialSwitch.setOnCheckedChangeListener { _, isOn ->
                prefs.edit().putBoolean(row.prefKey, isOn).apply()
                iconBounce(row.rowBinding.ivSwitchIcon)
                showSwitchModal(row.title, row.iconRes, isOn)
            }
        }
    }

    // ──────────────────────────────────────────
    // FREE FIRE
    // ──────────────────────────────────────────

    private fun setupFreeFire() {
        binding.particlesFreeFireFunc.setColors(intArrayOf(
            Color.parseColor("#FF5C72"),
            Color.parseColor("#FF6B6B"),
            Color.parseColor("#FF4D67")
        ))
        binding.particlesFreeFireFunc.setDensity(9)
        binding.particlesFreeFireFunc.start()

        startRingRotation(binding.vFFGraphicsRing)
        startRingRotation(binding.vFFAntiLagRing)
        iconPulseEntrance(binding.ivFFGraphicsIcon, 200L)
        iconPulseEntrance(binding.ivFFAntiLagIcon,  350L)

        val ffPrefs = requireContext().getSharedPreferences("func_switch_prefs", android.content.Context.MODE_PRIVATE)

        // Khôi phục Ultra Graphics
        binding.switchFFGraphics.setOnCheckedChangeListener(null)
        binding.switchFFGraphics.isChecked = ffPrefs.getBoolean("switch_ultra_graphics", false)
        binding.switchFFGraphics.setOnCheckedChangeListener { _, isOn ->
            ffPrefs.edit().putBoolean("switch_ultra_graphics", isOn).apply()
            iconBounce(binding.ivFFGraphicsIcon)
            showSwitchModal("Ultra Graphics", R.drawable.ic_ff_graphics, isOn)
        }

        // Khôi phục AntiLag Seed
        binding.switchFFAntiLag.setOnCheckedChangeListener(null)
        binding.switchFFAntiLag.isChecked = ffPrefs.getBoolean("switch_antilag_seed", false)
        binding.switchFFAntiLag.setOnCheckedChangeListener { _, isOn ->
            ffPrefs.edit().putBoolean("switch_antilag_seed", isOn).apply()
            iconBounce(binding.ivFFAntiLagIcon)
            showSwitchModal("AntiLag Seed", R.drawable.ic_ff_antilag, isOn) }

        startApplyParticles()

        binding.btnApplyFF.setOnClickListener {
            it.animate().scaleX(0.88f).scaleY(0.88f).setDuration(100).withEndAction {
                it.animate().scaleX(1f).scaleY(1f)
                    .setDuration(300).setInterpolator(OvershootInterpolator(2.5f))
                    .withEndAction { handleApplyGame() }.start()
            }.start()
        }
    }

    // ──────────────────────────────────────────
    // GAME MODULE SWITCHES
    // ──────────────────────────────────────────

    private fun setupGameModuleSwitches() {
        val prefs = requireContext().getSharedPreferences("game_module_installer", Context.MODE_PRIVATE)

        configureModuleRow(
            binding.switchAimlockPro,
            getString(R.string.func_aimlock_pro),
            getString(R.string.func_aimlock_pro_desc),
            R.drawable.ic_menu_aim,
            GameModuleInstaller.Module.AIMLOCK_PRO,
            prefs.getBoolean("aimlock_pro_enabled", false)
        )
        configureModuleRow(
            binding.switchEspAimlock,
            getString(R.string.func_esp_aimlock),
            getString(R.string.func_esp_aimlock_desc),
            R.drawable.ic_crosshair_target,
            GameModuleInstaller.Module.ESP_AIMLOCK,
            prefs.getBoolean("esp_aimlock_enabled", false)
        )
        configureModuleRow(
            binding.switchAimlockBox,
            getString(R.string.func_aimlock_box),
            getString(R.string.func_aimlock_box_desc),
            R.drawable.ic_aimlock_box,
            GameModuleInstaller.Module.AIMLOCK_BOX,
            prefs.getBoolean("aimlock_box_enabled", false)
        )
    }

    private fun configureModuleRow(
        row: ItemSwitchIconRowBinding,
        title: String,
        sub: String,
        icon: Int,
        module: GameModuleInstaller.Module,
        checked: Boolean
    ) {
        row.tvSwitchTitle.text = title
        row.tvSwitchSub.text = sub
        row.ivSwitchIcon.setImageResource(icon)
        row.ivSwitchIcon.setColorFilter(Color.parseColor("#FF5C72"))
        row.materialSwitch.setOnCheckedChangeListener(null)
        row.materialSwitch.isChecked = checked
        startRingRotation(row.vIconRing)

        row.materialSwitch.setOnCheckedChangeListener { _, enabled ->
            if (enabled) {
                installGameModule(module, row, title, icon)
            } else {
                GameModuleInstaller.remove(requireContext(), module) { result ->
                    if (!isAdded || _binding == null) return@remove
                    requireActivity().runOnUiThread {
                        row.materialSwitch.isEnabled = true
                        row.materialSwitch.setOnCheckedChangeListener(null)
                        row.materialSwitch.isChecked = false
                        row.materialSwitch.setOnCheckedChangeListener { _, on ->
                            if (on) installGameModule(module, row, title, icon)
                        }
                        iconBounce(row.ivSwitchIcon)
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                        showSwitchModal(title, icon, false)
                    }
                }
            }
        }
    }

    private fun installGameModule(
        module: GameModuleInstaller.Module,
        row: ItemSwitchIconRowBinding,
        title: String,
        icon: Int
    ) {
        if (!GameModuleInstaller.hasRequiredStorageAccess()) {
            row.materialSwitch.setOnCheckedChangeListener(null)
            row.materialSwitch.isChecked = false
            row.materialSwitch.setOnCheckedChangeListener { _, on ->
                if (on) installGameModule(module, row, title, icon)
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Storage access required")
                .setMessage("Grant storage access before installing $title.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("OPEN SETTINGS") { _, _ ->
                    runCatching {
                        startActivity(GameModuleInstaller.buildStorageAccessIntent(requireContext()))
                    }
                }
                .show()
            return
        }

        row.materialSwitch.isEnabled = false
        GameModuleInstaller.install(requireContext(), module) { result ->
            if (!isAdded || _binding == null) return@install
            requireActivity().runOnUiThread {
                row.materialSwitch.isEnabled = true
                if (result.ok) {
                    row.materialSwitch.setOnCheckedChangeListener(null)
                    row.materialSwitch.isChecked = true
                    row.materialSwitch.setOnCheckedChangeListener { _, on ->
                        if (on) {
                            installGameModule(module, row, title, icon)
                        } else {
                            GameModuleInstaller.remove(requireContext(), module) { }
                        }
                    }
                    iconBounce(row.ivSwitchIcon)
                    showSwitchModal(title, icon, true)
                } else {
                    row.materialSwitch.setOnCheckedChangeListener(null)
                    row.materialSwitch.isChecked = false
                    row.materialSwitch.setOnCheckedChangeListener { _, on ->
                        if (on) installGameModule(module, row, title, icon)
                    }
                }
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ──────────────────────────────────────────
    // ORBITAL ANIMATION on rocket icon
    // ──────────────────────────────────────────

    private fun startOrbitalAnimation() {
        val b = _binding ?: return

        // Outer ring rotates forward
        ObjectAnimator.ofFloat(b.vRocketOrbitOuter, "rotation", 0f, 360f).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }

        // Inner ring counter-rotates
        ObjectAnimator.ofFloat(b.vRocketOrbitInner, "rotation", 360f, 0f).apply {
            duration = 3500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }

        // Rocket icon gentle pulse
        val rocketPulse = ValueAnimator.ofFloat(1f, 1.15f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val s = anim.animatedValue as Float
                b.ivRocketApply.scaleX = s
                b.ivRocketApply.scaleY = s
            }
        }
        rocketPulse.start()

        // Orbital dot alpha pulse (simulates moving dot)
        ValueAnimator.ofFloat(0.2f, 1f, 0.2f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { b.vOrbitalDot.alpha = it.animatedValue as Float }
            start()
        }
    }

    // ──────────────────────────────────────────
    // HANDLE APPLY → Launch game + Floating service
    // ──────────────────────────────────────────

    private fun handleApplyGame() {
        val ctx = requireContext()

        // Check overlay permission
        if (!Settings.canDrawOverlays(ctx)) {
            Toast.makeText(ctx,
                "Cap quyen hien thi tren man hinh de dung floating menu",
                Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${ctx.packageName}")
                )
                startActivity(intent)
            } catch (_: Exception) {}
            // Still launch game even without overlay permission
        }

        // Start floating boost service
        try {
            val serviceIntent = Intent(ctx, FloatingBoostService::class.java).apply {
                action = FloatingBoostService.ACTION_START_BOOST
            }
            ctx.startService(serviceIntent)
        } catch (_: Exception) {}

        // Launch Free Fire
        launchFreeFire()
    }

    private fun launchFreeFire() {
        val pm = requireContext().packageManager
        for (pkg in listOf("com.dts.freefireth", "com.dts.freefiremax")) {
            pm.getLaunchIntentForPackage(pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(it)
                return
            }
        }
        Toast.makeText(requireContext(),
            "Free Fire chua duoc cai dat tren thiet bi nay", Toast.LENGTH_LONG).show()
    }

    // ──────────────────────────────────────────
    // SWITCH MODAL
    // ──────────────────────────────────────────

    private fun showSwitchModal(title: String, iconRes: Int, isOn: Boolean) {
        val modal        = binding.funcSwitchModal
        val ivIcon       = binding.ivFuncModalIcon
        val tvTitle      = binding.tvFuncModalTitle
        val tvState      = binding.tvFuncModalState
        val ivStatus     = binding.ivFuncModalStatus
        val progressFill = binding.vFuncProgressFill

        ivIcon.setImageResource(iconRes)
        tvTitle.text = title

        if (isOn) {
            tvState.text = "● BAT"
            tvState.setTextColor(Color.parseColor("#69FF47"))
            ivStatus.setImageResource(R.drawable.ic_status_on)
        } else {
            tvState.text = "○ TAT"
            tvState.setTextColor(Color.parseColor("#FF4D67"))
            ivStatus.setImageResource(R.drawable.ic_status_off)
        }

        (progressFill.layoutParams as FrameLayout.LayoutParams).width = 0
        progressFill.requestLayout()

        modal.visibility = View.VISIBLE
        modal.alpha = 0f
        modal.translationY = -80f
        modal.animate()
            .alpha(1f).translationY(0f)
            .setDuration(320).setInterpolator(OvershootInterpolator(1.6f))
            .start()

        ivStatus.scaleX = 0f; ivStatus.scaleY = 0f
        ivStatus.animate().scaleX(1f).scaleY(1f)
            .setDuration(350).setStartDelay(150)
            .setInterpolator(OvershootInterpolator(2f)).start()

        modal.post {
            val parentWidth = (modal.width - modal.paddingLeft - modal.paddingRight).coerceAtLeast(1)
            val targetW = if (isOn) parentWidth else 0
            val startW  = if (isOn) 0 else parentWidth

            (progressFill.layoutParams as FrameLayout.LayoutParams).width = startW
            progressFill.requestLayout()

            ValueAnimator.ofInt(startW, targetW).apply {
                duration = 700; startDelay = 80
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    if (_binding == null) return@addUpdateListener
                    (progressFill.layoutParams as FrameLayout.LayoutParams).width =
                        anim.animatedValue as Int
                    progressFill.requestLayout()
                }
                start()
            }
        }

        handler.removeCallbacksAndMessages(MODAL_TOKEN)
        handler.postAtTime({
            if (_binding != null) {
                modal.animate().alpha(0f).translationY(-40f).setDuration(250).withEndAction {
                    if (_binding != null) { modal.visibility = View.GONE; modal.translationY = 0f }
                }.start()
            }
        }, MODAL_TOKEN, SystemClock.uptimeMillis() + 2200L)
    }

    // ──────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────

    private fun startRingRotation(v: View) {
        ObjectAnimator.ofFloat(v, "rotation", 0f, 360f).apply {
            duration = 4000; repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART; interpolator = LinearInterpolator(); start()
        }
    }

    private fun iconBounce(v: View) {
        v.animate().scaleX(1.4f).scaleY(1.4f).setDuration(120).withEndAction {
            v.animate().scaleX(1f).scaleY(1f)
                .setDuration(250).setInterpolator(OvershootInterpolator(2.5f)).start()
        }.start()
    }

    private fun iconPulseEntrance(v: View, delay: Long) {
        v.scaleX = 0.4f; v.scaleY = 0.4f
        v.animate().scaleX(1f).scaleY(1f)
            .setDuration(500).setStartDelay(delay)
            .setInterpolator(OvershootInterpolator(2f)).start()
    }

    private fun startApplyParticles() {
        val pv = binding.applyBtnParticles
        try {
            pv.javaClass.getMethod("setColors", IntArray::class.java).invoke(pv, intArrayOf(
                Color.parseColor("#FF5C72"), Color.parseColor("#FF6678"),
                Color.parseColor("#FF6B6B"), Color.parseColor("#B71C1C"),
                Color.parseColor("#FF4D67")
            ))
        } catch (_: Exception) {}
        try { pv.javaClass.getMethod("setIdleMode", Boolean::class.java).invoke(pv, true) }
        catch (_: Exception) {}
        try { pv.javaClass.getMethod("start").invoke(pv) } catch (_: Exception) {}
    }

    private fun animateCardsIn() {
        val sv = binding.root.getChildAt(0) as? ViewGroup ?: return
        val container = sv.getChildAt(0) as? ViewGroup ?: return
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            child.alpha = 0f; child.translationY = 50f
            child.animate().alpha(1f).translationY(0f)
                .setDuration(450).setStartDelay((i * 130).toLong())
                .setInterpolator(OvershootInterpolator(1f)).start()
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? com.aujunpeak.app.activities.MainActivity)?.checkKeyOrForceLogout()
        val pending = pendingModuleInstall
        if (pending != null && GameModuleInstaller.hasRequiredStorageAccess()) {
            pendingModuleInstall = null
            when (pending) {
                GameModuleInstaller.Module.AIMLOCK_PRO -> binding.switchAimlockPro.materialSwitch.performClick()
                GameModuleInstaller.Module.ESP_AIMLOCK -> binding.switchEspAimlock.materialSwitch.performClick()
                GameModuleInstaller.Module.AIMLOCK_BOX -> binding.switchAimlockBox.materialSwitch.performClick()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        _binding?.crosshairParticles?.stop()
        _binding?.particlesPerformance?.stop()
        _binding?.particlesFreeFireFunc?.stop()
        _binding?.particlesOptimizerSuper?.stop()
        try { _binding?.applyBtnParticles?.javaClass?.getMethod("stop")?.invoke(_binding?.applyBtnParticles) }
        catch (_: Exception) {}
        _binding = null
    }
}
