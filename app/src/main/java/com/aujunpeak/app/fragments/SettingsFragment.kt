package com.aujunpeak.app.fragments

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.fragment.app.Fragment
import com.aujunpeak.app.R
import com.aujunpeak.app.activities.AboutActivity
import com.aujunpeak.app.activities.FeedbackActivity
import com.aujunpeak.app.activities.PolicyActivity
import com.aujunpeak.app.activities.SocialLinksActivity
import com.aujunpeak.app.databinding.FragmentSettingsBinding
import com.aujunpeak.app.utils.LocaleHelper
import com.aujunpeak.app.utils.SystemStats

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())
    private val statsLoop = object : Runnable {
        override fun run() {
            if (!isAdded || _binding == null) return
            updatePerformancePanel()
            handler.postDelayed(this, 1400L)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCardParticles()
        setupPerformancePanel()
        setupLanguage()
        setupAboutCard()
        setupFeedbackCard()
        setupPolicyCard()
        setupAuthorCard()
        animateCardsIn()
    }

    // ─────────────────────────────────────
    // CARD PARTICLE BACKGROUNDS
    // ─────────────────────────────────────

    private fun setupCardParticles() {
        binding.particlesHero.setColors(intArrayOf(
            Color.parseColor("#57D9FF"),
            Color.parseColor("#69FF47"),
            Color.parseColor("#FFFFFF")
        ))
        binding.particlesHero.setDensity(8)
        binding.particlesHero.start()

        binding.particlesLanguage.setColors(intArrayOf(
            Color.parseColor("#00E5FF"),
            Color.parseColor("#69FF47"),
            Color.parseColor("#FFFFFF")
        ))
        binding.particlesLanguage.setDensity(8)
        binding.particlesLanguage.start()

        binding.particlesStorage.setColors(intArrayOf(
            Color.parseColor("#FFB74D"),
            Color.parseColor("#FFE082"),
            Color.parseColor("#FFFFFF")
        ))
        binding.particlesStorage.setDensity(6)
        binding.particlesStorage.start()

        binding.particlesSettingsCpu.setColors(intArrayOf(
            Color.parseColor("#937CFF"),
            Color.parseColor("#57D9FF"),
            Color.parseColor("#FFFFFF")
        ))
        binding.particlesSettingsCpu.setDensity(6)
        binding.particlesSettingsCpu.start()

        binding.particlesSettingsRam.setColors(intArrayOf(
            Color.parseColor("#57D9FF"),
            Color.parseColor("#69FF47"),
            Color.parseColor("#FFFFFF")
        ))
        binding.particlesSettingsRam.setDensity(6)
        binding.particlesSettingsRam.start()

        // About card — violet/purple particles
        binding.particlesAbout.setColors(intArrayOf(
            Color.parseColor("#7C4DFF"),
            Color.parseColor("#CE93D8"),
            Color.parseColor("#FFFFFF")
        ))
        binding.particlesAbout.setDensity(8)
        binding.particlesAbout.start()

        // Feedback card — cyan-teal particles
        binding.particlesFeedback.setColors(intArrayOf(
            Color.parseColor("#00E5FF"),
            Color.parseColor("#00BCD4"),
            Color.parseColor("#69FF47")
        ))
        binding.particlesFeedback.setDensity(8)
        binding.particlesFeedback.start()

        // Policy card — gold particles
        binding.particlesPolicy.setColors(intArrayOf(
            Color.parseColor("#FFD700"),
            Color.parseColor("#FF9800"),
            Color.parseColor("#FFFFFF")
        ))
        binding.particlesPolicy.setDensity(8)
        binding.particlesPolicy.start()
    }

    private fun setupPerformancePanel() {
        binding.storageGauge.setGaugeColors(Color.parseColor("#FFB74D"), Color.parseColor("#FFE082"))
        binding.settingsCpuGauge.setGaugeColors(Color.parseColor("#937CFF"), Color.parseColor("#57D9FF"))
        binding.settingsRamGauge.setGaugeColors(Color.parseColor("#57D9FF"), Color.parseColor("#69FF47"))
        updatePerformancePanel()
        handler.postDelayed(statsLoop, 1200L)
    }

    private fun updatePerformancePanel() {
        val ram = SystemStats.getRamUsagePercent(requireContext())
        val cpu = SystemStats.getCpuUsagePercent()
        val (storagePercent, storageInfo) = getStorageUsageInfo()

        binding.settingsRamGauge.setProgressCompat(ram)
        binding.settingsCpuGauge.setProgressCompat(cpu)
        binding.storageGauge.setProgressCompat(storagePercent)

        binding.tvSettingsRamPercent.text = "${ram.toInt()}%"
        binding.tvSettingsCpuPercent.text = "${cpu.toInt()}%"
        binding.tvStoragePercent.text = "${storagePercent.toInt()}%"

        binding.tvSettingsRamInfo.text = SystemStats.getRamInfo(requireContext())
        binding.tvSettingsCpuInfo.text = if (cpu >= 75f) "High load" else if (cpu >= 45f) "Balanced load" else "Smooth load"
        binding.tvStorageInfo.text = storageInfo
    }

    private fun getStorageUsageInfo(): Pair<Float, String> {
        return try {
            val statFs = StatFs(requireContext().filesDir.absolutePath)
            val totalBytes = statFs.totalBytes.toFloat().coerceAtLeast(1f)
            val availableBytes = statFs.availableBytes.toFloat()
            val usedBytes = totalBytes - availableBytes
            val usedGb = usedBytes / (1024f * 1024f * 1024f)
            val totalGb = totalBytes / (1024f * 1024f * 1024f)
            val percent = (usedBytes / totalBytes) * 100f
            percent to String.format(java.util.Locale.US, "%.1f / %.1f GB", usedGb, totalGb)
        } catch (_: Exception) {
            48f to "-- / -- GB"
        }
    }

    // ─────────────────────────────────────
    // LANGUAGE CHIPS
    // ─────────────────────────────────────

    private fun setupLanguage() {
        val saved = LocaleHelper.getSavedLocale(requireContext())
        updateLangChips(saved)

        val chipMap = mapOf(
            "vi" to binding.chipVI,
            "en" to binding.chipEN,
            "zh" to binding.chipZH,
            "ja" to binding.chipJA,
            "ko" to binding.chipKO
        )

        chipMap.forEach { (code, chip) ->
            chip.setOnClickListener {
                if (LocaleHelper.getSavedLocale(requireContext()) == code) return@setOnClickListener

                // Update visual immediately
                updateLangChips(code)

                // Bounce animation
                chip.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).withEndAction {
                    chip.animate().scaleX(1f).scaleY(1f)
                        .setDuration(180).setInterpolator(OvershootInterpolator(2f)).start()
                }.start()

                // Save locale, then restart the whole activity stack so every
                // Activity picks up the new locale via attachBaseContext.
                LocaleHelper.setLocale(requireContext(), code)

                handler.postDelayed({
                    if (activity == null || !isAdded) return@postDelayed
                    val ctx = requireContext().applicationContext
                    val restartIntent = Intent(ctx,
                        com.aujunpeak.app.activities.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    requireActivity().overridePendingTransition(
                        android.R.anim.fade_in, android.R.anim.fade_out
                    )
                    ctx.startActivity(restartIntent)
                }, 600L)
            }
        }
    }

    private fun updateLangChips(activeCode: String) {
        val b = _binding ?: return
        val chipMap = mapOf(
            "vi" to b.chipVI,
            "en" to b.chipEN,
            "zh" to b.chipZH,
            "ja" to b.chipJA,
            "ko" to b.chipKO
        )
        chipMap.forEach { (code, chip) ->
            if (code == activeCode) {
                chip.setBackgroundResource(R.drawable.bg_lang_chip_selected)
                chip.setTextColor(Color.WHITE)
            } else {
                chip.setBackgroundResource(R.drawable.bg_lang_chip)
                chip.setTextColor(Color.parseColor("#999999"))
            }
        }
        b.tvCurrentLang.text = activeCode.uppercase()
    }

    // ─────────────────────────────────────
    // ABOUT CARD — opens AboutActivity
    // ─────────────────────────────────────

    private fun setupAboutCard() {
        // Version label live update
        try {
            val pi = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.tvAboutCardVersion.text = "v${pi.versionName} · Build ${pi.versionCode}"
        } catch (_: Exception) {}

        // Glow pulse — violet
        val glow = binding.aboutGlow
        ObjectAnimator.ofFloat(glow, "scaleX", 1f, 1.28f, 1f).apply {
            duration = 2200; repeatCount = ValueAnimator.INFINITE; start()
        }
        ObjectAnimator.ofFloat(glow, "scaleY", 1f, 1.28f, 1f).apply {
            duration = 2200; repeatCount = ValueAnimator.INFINITE; start()
        }
        ObjectAnimator.ofFloat(glow, "alpha", 0.55f, 0.88f, 0.55f).apply {
            duration = 2200; repeatCount = ValueAnimator.INFINITE; start()
        }

        binding.cardAbout.setOnClickListener {
            it.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f)
                    .setDuration(220).setInterpolator(OvershootInterpolator(2f))
                    .withEndAction {
                        startActivity(Intent(requireContext(), AboutActivity::class.java))
                    }.start()
            }.start()
        }
    }

    // ─────────────────────────────────────
    // FEEDBACK CARD — opens FeedbackActivity
    // ─────────────────────────────────────

    private fun setupFeedbackCard() {
        // Glow pulse animation
        val glow = binding.feedbackGlow
        ObjectAnimator.ofFloat(glow, "scaleX", 1f, 1.3f, 1f).apply {
            duration = 2000; repeatCount = ValueAnimator.INFINITE; start()
        }
        ObjectAnimator.ofFloat(glow, "scaleY", 1f, 1.3f, 1f).apply {
            duration = 2000; repeatCount = ValueAnimator.INFINITE; start()
        }
        ObjectAnimator.ofFloat(glow, "alpha", 0.55f, 0.9f, 0.55f).apply {
            duration = 2000; repeatCount = ValueAnimator.INFINITE; start()
        }

        binding.cardFeedback.setOnClickListener {
            it.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f)
                    .setDuration(220).setInterpolator(OvershootInterpolator(2f))
                    .withEndAction {
                        startActivity(Intent(requireContext(), FeedbackActivity::class.java))
                    }.start()
            }.start()
        }
    }

    // ─────────────────────────────────────
    // POLICY CARD — opens PolicyActivity
    // ─────────────────────────────────────

    private fun setupPolicyCard() {
        // Glow pulse animation
        val glow = binding.policyGlowCard
        ObjectAnimator.ofFloat(glow, "scaleX", 1f, 1.25f, 1f).apply {
            duration = 2400; repeatCount = ValueAnimator.INFINITE; start()
        }
        ObjectAnimator.ofFloat(glow, "scaleY", 1f, 1.25f, 1f).apply {
            duration = 2400; repeatCount = ValueAnimator.INFINITE; start()
        }
        ObjectAnimator.ofFloat(glow, "alpha", 0.5f, 0.85f, 0.5f).apply {
            duration = 2400; repeatCount = ValueAnimator.INFINITE; start()
        }

        binding.cardPolicy.setOnClickListener {
            it.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f)
                    .setDuration(220).setInterpolator(OvershootInterpolator(2f))
                    .withEndAction {
                        startActivity(Intent(requireContext(), PolicyActivity::class.java))
                    }.start()
            }.start()
        }
    }

    // ─────────────────────────────────────
    // AUTHOR CARD — opens SocialLinksActivity
    // ─────────────────────────────────────

    private fun setupAuthorCard() {
        binding.cardAuthor.setOnClickListener {
            it.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f)
                    .setDuration(200).setInterpolator(OvershootInterpolator(2f))
                    .withEndAction {
                        startActivity(Intent(requireContext(), SocialLinksActivity::class.java))
                    }.start()
            }.start()
        }
    }

    // ─────────────────────────────────────
    // ENTRANCE ANIMATION
    // ─────────────────────────────────────

    private fun animateCardsIn() {
        val container = binding.settingsContainer
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            child.alpha = 0f
            child.translationY = 60f
            child.animate()
                .alpha(1f).translationY(0f)
                .setDuration(480).setStartDelay((i * 120).toLong())
                .setInterpolator(OvershootInterpolator(1f))
                .start()
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? com.aujunpeak.app.activities.MainActivity)?.checkKeyOrForceLogout()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        _binding?.particlesHero?.stop()
        _binding?.particlesStorage?.stop()
        _binding?.particlesSettingsCpu?.stop()
        _binding?.particlesSettingsRam?.stop()
        _binding?.particlesLanguage?.stop()
        _binding?.particlesAbout?.stop()
        _binding?.particlesFeedback?.stop()
        _binding?.particlesPolicy?.stop()
        _binding = null
    }
}
