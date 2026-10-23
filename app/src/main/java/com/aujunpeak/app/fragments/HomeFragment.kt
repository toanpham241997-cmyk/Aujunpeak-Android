package com.aujunpeak.app.fragments

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aujunpeak.app.R
import com.aujunpeak.app.databinding.FragmentHomeBinding
import com.aujunpeak.app.databinding.ItemSwitchRowBinding
import com.aujunpeak.app.utils.KeyManager
import com.aujunpeak.app.utils.SystemStats
import com.aujunpeak.app.views.WaterWaveView

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private val ramValues = ArrayDeque<Float>()
    private val cpuValues = ArrayDeque<Float>()
    private val maxPoints = 20

    private var isBoosted = false
    private var isBoostRunning = false

    private val MODAL_TOKEN = "switch_modal_token"

    private val ticker = object : Runnable {
        override fun run() {
            if (_binding != null) updateStats()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repeat(maxPoints) { ramValues.addLast(0f); cpuValues.addLast(0f) }

        binding.ramChart.setLineColor(Color.parseColor("#57D9FF"))
        binding.cpuChart.setLineColor(Color.parseColor("#937CFF"))
        binding.ramWave.setColors(Color.parseColor("#57D9FF"), Color.parseColor("#937CFF"))
        binding.cpuWave.setColors(Color.parseColor("#937CFF"), Color.parseColor("#4B8DFF"))
        binding.ramGauge.setGaugeColors(Color.parseColor("#57D9FF"), Color.parseColor("#80D8FF"))
        binding.cpuGauge.setGaugeColors(Color.parseColor("#937CFF"), Color.parseColor("#B388FF"))

        setupCardParticles()
        setupSwitches()
        animateCardsIn()
        setupBoostButton()
        handler.postDelayed(ticker, 600L)
    }

    private fun setupCardParticles() {
        binding.particlesRam.setColors(intArrayOf(
            Color.parseColor("#57D9FF"),
            Color.parseColor("#937CFF"),
            Color.parseColor("#FF8A80")
        ))
        binding.particlesCpu.setColors(intArrayOf(
            Color.parseColor("#00E5FF"),
            Color.parseColor("#18FFFF"),
            Color.parseColor("#84FFFF")
        ))
        binding.particlesSwitches.setColors(intArrayOf(
            Color.parseColor("#FFC400"),
            Color.parseColor("#FFD740"),
            Color.parseColor("#FFECB3")
        ))
        binding.particlesRam.setDensity(8)
        binding.particlesCpu.setDensity(8)
        binding.particlesSwitches.setDensity(7)
        binding.particlesRam.start()
        binding.particlesCpu.start()
        binding.particlesSwitches.start()

        binding.particlesFreeFire.setColors(intArrayOf(
            Color.parseColor("#57D9FF"),
            Color.parseColor("#FF6D00"),
            Color.parseColor("#FF8A80")
        ))
        binding.particlesFreeFire.setDensity(9)
        binding.particlesFreeFire.start()

        binding.particlesHighlights.setColors(intArrayOf(
            Color.parseColor("#57D9FF"),
            Color.parseColor("#937CFF"),
            Color.parseColor("#FFD36A")
        ))
        binding.particlesHighlights.setDensity(10)
        binding.particlesHighlights.start()

        binding.boostBtnParticles.setIdleMode(true)
        binding.boostBtnParticles.start()
    }

    // ────────────────────────────────────────
    // FIX: Kiểm tra đăng nhập trước khi boost
    // ────────────────────────────────────────
    private fun setupBoostButton() {
        startBoostStartIconPulse()

        binding.btnBoostStart.setOnClickListener {
            it.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(180)
                    .setInterpolator(android.view.animation.OvershootInterpolator(2f))
                    .withEndAction {
                        val ctx = context ?: return@withEndAction
                        // Nếu đã đăng nhập → chạy boost, không cần navigate sang login
                        if (KeyManager.isLoggedIn(ctx)) {
                            showBoostModal()
                        } else {
                            // Chưa đăng nhập → chuyển sang login
                            try {
                                findNavController().navigate(R.id.action_home_to_login)
                            } catch (e: Exception) {
                                try {
                                    findNavController().navigate(R.id.navigation_login)
                                } catch (e2: Exception) {
                                    // ignore
                                }
                            }
                        }
                    }.start()
            }.start()
        }
        binding.btnCloseBoost.setOnClickListener { dismissBoostModal() }
    }

    // ────────────────────────────────────────
    // BOOST START BUTTON — subtle idle icon pulse
    // ────────────────────────────────────────
    private fun startBoostStartIconPulse() {
        val icon = _binding?.ivBoostStartIcon ?: return
        icon.animate().scaleX(1.18f).scaleY(1.18f).setDuration(700)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (_binding == null) return@withEndAction
                _binding?.ivBoostStartIcon?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(700)
                    ?.setInterpolator(DecelerateInterpolator())
                    ?.withEndAction { startBoostStartIconPulse() }?.start()
            }.start()
    }

    // ────────────────────────────────────────
    // BOOST CORE — spinning ring + orbit + pulsing bolt icon
    // ────────────────────────────────────────
    private var orbitAnimator: ObjectAnimator? = null
    private var coreIconAnimator: AnimatorSet? = null

    private fun startBoostCoreAnimation() {
        val b = _binding ?: return

        orbitAnimator?.cancel()
        orbitAnimator = ObjectAnimator.ofFloat(b.boostOrbitRing, "rotation", 0f, -360f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        b.boostCoreGlow.animate().alpha(0.4f).setDuration(1).start()
        pulseCoreGlow()

        coreIconAnimator?.cancel()
        coreIconAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(b.ivBoostCoreIcon, "scaleX", 1f, 1.22f, 1f).apply {
                    duration = 900; repeatCount = ValueAnimator.INFINITE
                },
                ObjectAnimator.ofFloat(b.ivBoostCoreIcon, "scaleY", 1f, 1.22f, 1f).apply {
                    duration = 900; repeatCount = ValueAnimator.INFINITE
                }
            )
            start()
        }
    }

    private fun pulseCoreGlow() {
        val glow = _binding?.boostCoreGlow ?: return
        if (!isBoostRunning) return
        glow.animate().alpha(0.85f).setDuration(650).withEndAction {
            if (_binding != null && isBoostRunning) {
                _binding?.boostCoreGlow?.animate()?.alpha(0.4f)?.setDuration(650)
                    ?.withEndAction { pulseCoreGlow() }?.start()
            }
        }.start()
    }

    private fun stopBoostCoreAnimation() {
        orbitAnimator?.cancel()
        orbitAnimator = null
        coreIconAnimator?.cancel()
        coreIconAnimator = null
        _binding?.boostCoreGlow?.animate()?.alpha(0f)?.setDuration(500)?.start()
    }

    // ────────────────────────────────────────
    // BOOST STEP CHIPS — spinner → check morph
    // ────────────────────────────────────────
    private val stepSpinnerAnimators = mutableMapOf<View, ObjectAnimator>()

    private fun resetStepIndicator(spinner: View, check: ImageView, chip: View) {
        stepSpinnerAnimators.remove(spinner)?.cancel()
        spinner.animate().cancel()
        spinner.rotation = 0f
        spinner.visibility = View.VISIBLE
        spinner.alpha = 1f
        check.animate().cancel()
        check.visibility = View.GONE
        chip.setBackgroundResource(R.drawable.bg_boost_icon_chip)
        val anim = ObjectAnimator.ofFloat(spinner, "rotation", 0f, 360f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
        stepSpinnerAnimators[spinner] = anim
        anim.start()
    }

    private fun completeStepIndicator(spinner: View, check: ImageView, chip: View) {
        if (_binding == null) return
        stepSpinnerAnimators.remove(spinner)?.cancel()
        spinner.animate().alpha(0f).setDuration(150).withEndAction {
            if (_binding == null) return@withEndAction
            spinner.visibility = View.GONE
            chip.setBackgroundResource(R.drawable.bg_boost_icon_chip_done)
            check.visibility = View.VISIBLE
            check.scaleX = 0f; check.scaleY = 0f
            check.animate().scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(OvershootInterpolator(2.5f)).start()
        }.start()
    }

    private fun showBoostModal() {
        isBoostRunning = true
        binding.tvStep1.text = "Clearing RAM cache..."
        binding.tvStep1.setTextColor(Color.parseColor("#88FFFFFF"))
        binding.tvStep2.text = "Optimizing CPU threads..."
        binding.tvStep2.setTextColor(Color.parseColor("#88FFFFFF"))
        binding.tvStep3.text = "Boosting Free Fire priority..."
        binding.tvStep3.setTextColor(Color.parseColor("#88FFFFFF"))

        resetStepIndicator(binding.spinnerStep1, binding.checkStep1, binding.chipStep1)
        resetStepIndicator(binding.spinnerStep2, binding.checkStep2, binding.chipStep2)
        resetStepIndicator(binding.spinnerStep3, binding.checkStep3, binding.chipStep3)

        binding.llSuccess.visibility = View.GONE
        binding.tvBoostStatus.text = "BOOSTING..."
        binding.tvBoostSubtitle.visibility = View.VISIBLE
        binding.llBoostSteps.alpha = 1f
        binding.llBoostSteps.visibility = View.VISIBLE

        val overlay = binding.boostOverlay
        overlay.alpha = 0f
        overlay.visibility = View.VISIBLE
        overlay.animate().alpha(1f).setDuration(300).start()

        binding.boostParticleView.setIdleMode(false)
        binding.boostParticleView.start()
        startBoostCoreAnimation()

        binding.boostCard.scaleX = 0.7f
        binding.boostCard.scaleY = 0.7f
        binding.boostCard.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(400).setInterpolator(OvershootInterpolator(1.5f))
            .start()

        handler.postDelayed({
            completeStepIndicator(binding.spinnerStep1, binding.checkStep1, binding.chipStep1)
            animateStepDone(binding.tvStep1, "Clearing RAM cache...")
        }, 800L)
        handler.postDelayed({
            completeStepIndicator(binding.spinnerStep2, binding.checkStep2, binding.chipStep2)
            animateStepDone(binding.tvStep2, "Optimizing CPU threads...")
        }, 1600L)
        handler.postDelayed({
            completeStepIndicator(binding.spinnerStep3, binding.checkStep3, binding.chipStep3)
            animateStepDone(binding.tvStep3, "Boosting Free Fire priority...")
        }, 2400L)
        handler.postDelayed({ showBoostSuccess() }, 3200L)
    }

    private fun animateStepDone(stepView: TextView, doneText: String) {
        if (_binding == null) return
        stepView.text = doneText
        stepView.setTextColor(Color.parseColor("#69FF47"))
        stepView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(100)
            .withEndAction { stepView.animate().scaleX(1f).scaleY(1f).setDuration(100).start() }
            .start()
    }

    private fun showBoostSuccess() {
        if (_binding == null) return
        isBoosted = true
        binding.tvBoostStatus.text = getString(R.string.optimizer_done_label)
        binding.tvBoostSubtitle.visibility = View.GONE
        binding.llBoostSteps.animate().alpha(0f).setDuration(200).withEndAction {
            if (_binding == null) return@withEndAction
            binding.llBoostSteps.visibility = View.GONE
            binding.llSuccess.alpha = 0f
            binding.llSuccess.scaleX = 0.7f
            binding.llSuccess.scaleY = 0.7f
            binding.llSuccess.visibility = View.VISIBLE
            binding.llSuccess.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(400).setInterpolator(OvershootInterpolator(1.2f)).start()
            binding.ivSuccessCheck.scaleX = 0f; binding.ivSuccessCheck.scaleY = 0f
            binding.ivSuccessCheck.animate().scaleX(1f).scaleY(1f).setDuration(450)
                .setStartDelay(80).setInterpolator(OvershootInterpolator(3f)).start()
        }.start()
        binding.boostParticleView.setIdleMode(true)
        stopBoostCoreAnimation()
        updateFreeFireStatus(true)
        binding.boostRingLoader.animate().scaleX(1.15f).scaleY(1.15f).setDuration(300)
            .withEndAction {
                if (_binding != null)
                    binding.boostRingLoader.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
            }.start()

        handler.postDelayed({
            if (_binding != null && binding.boostOverlay.visibility == View.VISIBLE) {
                dismissBoostModal()
            }
        }, 2000L)
    }

    private fun dismissBoostModal() {
        binding.boostOverlay.animate().alpha(0f).setDuration(250).withEndAction {
            if (_binding == null) return@withEndAction
            binding.boostOverlay.visibility = View.GONE
            binding.boostParticleView.stop()
        }.start()
        stopBoostCoreAnimation()
        isBoostRunning = false
    }

    private fun updateFreeFireStatus(active: Boolean) {
        val dot = _binding?.vFreeFireStatus ?: return
        if (active) {
            dot.setBackgroundResource(R.drawable.ic_status_dot_green)
            dot.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150).withEndAction {
                dot.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }.start()
            pulseGreenDot()
        } else {
            dot.setBackgroundResource(R.drawable.bg_red_dot)
        }
    }

    private fun pulseGreenDot() {
        val dot = _binding?.vFreeFireStatus ?: return
        if (!isBoosted) return
        dot.animate().alpha(0.35f).setDuration(900).withEndAction {
            if (_binding != null && isBoosted)
                dot.animate().alpha(1f).setDuration(900).withEndAction {
                    if (_binding != null && isBoosted) pulseGreenDot()
                }.start()
        }.start()
    }

    // ────────────────────────────────────────
    // SWITCH ROWS — XML icons + ring rotation
    // ────────────────────────────────────────

    private data class SwitchConfig(
        val rowBinding: ItemSwitchRowBinding,
        val title: String,
        val subtitle: String,
        val iconRes: Int,
        val prefKey: String
    )

    private fun setupSwitches() {
        val prefs = requireContext().getSharedPreferences("home_switch_prefs", android.content.Context.MODE_PRIVATE)
        val configs = listOf(
            SwitchConfig(binding.rowGameMode,  "GAME OPTIMIZER", "Optimize CPU for gaming",  R.drawable.ic_game_mode,  "switch_game_optimizer"),
            SwitchConfig(binding.rowRamBoost,  "RAM BOOST",      "Clear ram apps",            R.drawable.ic_ram_boost,  "switch_ram_boost"),
            SwitchConfig(binding.rowAutoBoost, "TOUCH BOOST",    "touch on game launch",      R.drawable.ic_auto_boost, "switch_touch_boost")
        )

        configs.forEach { cfg ->
            cfg.rowBinding.tvSwitchTitle.text = cfg.title
            cfg.rowBinding.tvSwitchSub.text   = cfg.subtitle
            cfg.rowBinding.ivSwitchIcon.setImageResource(cfg.iconRes)

            startRingRotation(cfg.rowBinding.vIconRing)

            cfg.rowBinding.ivSwitchIcon.animate()
                .scaleX(1.15f).scaleY(1.15f).setDuration(600)
                .setInterpolator(OvershootInterpolator(2f))
                .withEndAction {
                    cfg.rowBinding.ivSwitchIcon.animate()
                        .scaleX(1f).scaleY(1f).setDuration(400).start()
                }.start()

            // Khôi phục trạng thái đã lưu mà không kích hoạt listener
            cfg.rowBinding.materialSwitch.setOnCheckedChangeListener(null)
            cfg.rowBinding.materialSwitch.isChecked = prefs.getBoolean(cfg.prefKey, false)

            cfg.rowBinding.materialSwitch.setOnCheckedChangeListener { _, isOn ->
                // Lưu trạng thái vào SharedPreferences
                prefs.edit().putBoolean(cfg.prefKey, isOn).apply()

                cfg.rowBinding.ivSwitchIcon.animate()
                    .scaleX(1.3f).scaleY(1.3f).setDuration(120)
                    .withEndAction {
                        cfg.rowBinding.ivSwitchIcon.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(200).setInterpolator(OvershootInterpolator(2.5f))
                            .start()
                    }.start()

                showSwitchModal(cfg.title, cfg.iconRes, isOn)
            }
        }
    }

    private val ringRotationAnimators = mutableListOf<ObjectAnimator>()

    private fun startRingRotation(ringView: View) {
        val anim = ObjectAnimator.ofFloat(ringView, "rotation", 0f, 360f).apply {
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
        }
        ringRotationAnimators.add(anim)
        anim.start()
    }

    // ────────────────────────────────────────
    // SWITCH MODAL — slides from top, progress bar
    // ────────────────────────────────────────

    private fun showSwitchModal(title: String, iconRes: Int, isOn: Boolean) {
        val modal        = binding.switchFeedbackModal
        val ivIcon       = binding.ivSwitchModalIcon
        val tvTitle      = binding.tvSwitchModalTitle
        val tvState      = binding.tvSwitchModalState
        val ivStatus     = binding.ivSwitchModalStatus
        val progressFill = binding.vModalProgressFill

        ivIcon.setImageResource(iconRes)
        tvTitle.text = title

        if (isOn) {
            tvState.text = getString(R.string.label_on)
            tvState.setTextColor(Color.parseColor("#69FF47"))
            ivStatus.setImageResource(R.drawable.ic_status_on)
        } else {
            tvState.text = getString(R.string.label_off)
            tvState.setTextColor(Color.parseColor("#FF4D67"))
            ivStatus.setImageResource(R.drawable.ic_status_off)
        }

        // Reset progress width
        (progressFill.layoutParams as FrameLayout.LayoutParams).width = 0
        progressFill.requestLayout()

        // Show modal sliding from top
        modal.visibility = View.VISIBLE
        modal.alpha = 0f
        modal.translationY = -80f
        modal.animate()
            .alpha(1f).translationY(0f)
            .setDuration(320).setInterpolator(OvershootInterpolator(1.6f))
            .start()

        // Status icon bounce
        ivStatus.scaleX = 0f
        ivStatus.scaleY = 0f
        ivStatus.animate().scaleX(1f).scaleY(1f)
            .setDuration(350).setStartDelay(150)
            .setInterpolator(OvershootInterpolator(2f))
            .start()

        // Animate progress bar
        modal.post {
            val parentWidth = (modal.width - modal.paddingLeft - modal.paddingRight)
                .coerceAtLeast(1)
            val targetW = if (isOn) parentWidth else 0
            val startW  = if (isOn) 0 else parentWidth

            (progressFill.layoutParams as FrameLayout.LayoutParams).width = startW
            progressFill.requestLayout()

            ValueAnimator.ofInt(startW, targetW).apply {
                duration = 700
                startDelay = 80
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

        // Auto-dismiss after 2 s
        handler.removeCallbacksAndMessages(MODAL_TOKEN)
        handler.postAtTime(
            {
                if (_binding != null) {
                    modal.animate().alpha(0f).translationY(-40f)
                        .setDuration(250).withEndAction {
                            if (_binding != null) {
                                modal.visibility = View.GONE
                                modal.translationY = 0f
                            }
                        }.start()
                }
            },
            MODAL_TOKEN,
            SystemClock.uptimeMillis() + 2000L
        )
    }

    // ────────────────────────────────────────
    // CARDS ENTRANCE ANIMATION
    // ────────────────────────────────────────

    private fun animateCardsIn() {
        listOf(binding.cardFreeFire, binding.cardRam, binding.cardCpu, binding.cardSwitches)
            .forEachIndexed { i, card ->
                card.alpha = 0f
                card.translationY = 70f
                card.animate().alpha(1f).translationY(0f)
                    .setDuration(500).setStartDelay((i * 120).toLong())
                    .setInterpolator(OvershootInterpolator(1f))
                    .start()
            }
        binding.boostStartContainer.alpha = 0f
        binding.boostStartContainer.translationY = 60f
        binding.boostStartContainer.animate()
            .alpha(1f).translationY(0f)
            .setDuration(500).setStartDelay(560L).start()
    }

    // ────────────────────────────────────────
    // STATS UPDATE
    // ────────────────────────────────────────

    private fun updateStats() {
        val ctx = context ?: return
        val b   = _binding  ?: return

        val ram = SystemStats.getRamUsagePercent(ctx)
        val cpu = SystemStats.getCpuUsagePercent()

        if (ramValues.size >= maxPoints) ramValues.removeFirst()
        ramValues.addLast(ram)
        if (cpuValues.size >= maxPoints) cpuValues.removeFirst()
        cpuValues.addLast(cpu)

        b.tvRamPercent.text = "${ram.toInt()}%"
        b.tvRamInfo.text    = SystemStats.getRamInfo(ctx)
        b.tvCpuPercent.text = "${cpu.toInt()}%"
        b.tvCpuInfo.text    = "${Runtime.getRuntime().availableProcessors()} Cores"

        b.ramChart.setData(ramValues.toList())
        b.cpuChart.setData(cpuValues.toList())
        b.ramGauge.setProgressCompat(ram)
        b.cpuGauge.setProgressCompat(cpu)

        animateWave(b.ramWave, ram / 100f)
        animateWave(b.cpuWave, cpu / 100f)
    }

    private fun animateWave(wave: WaterWaveView, targetFrac: Float) {
        ValueAnimator.ofFloat(0f, targetFrac).apply {
            duration = 800
            addUpdateListener { wave.setProgress(it.animatedValue as Float) }
            start()
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? com.aujunpeak.app.activities.MainActivity)?.checkKeyOrForceLogout()
    }

    override fun onDestroyView() {
        _binding?.let {
            it.particlesRam.stop()
            it.particlesCpu.stop()
            it.particlesSwitches.stop()
            it.particlesFreeFire.stop()
            it.boostBtnParticles.stop()
            it.ivBoostStartIcon.animate().cancel()
            it.boostCoreGlow.animate().cancel()
        }
        stopBoostCoreAnimation()
        ringRotationAnimators.forEach { it.cancel() }
        ringRotationAnimators.clear()
        stepSpinnerAnimators.values.forEach { it.cancel() }
        stepSpinnerAnimators.clear()
        isBoostRunning = false
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }
}
