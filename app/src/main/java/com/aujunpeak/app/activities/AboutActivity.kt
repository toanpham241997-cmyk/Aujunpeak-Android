package com.aujunpeak.app.activities

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.aujunpeak.app.databinding.ActivityAboutBinding
import com.aujunpeak.app.utils.LocaleHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding
    private var activeTab = "info"   // info | update

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupVersionInfo()
        setupParticles()
        setupBackButton()
        setupTabs()
        startHeroGlow()
        animateEntrance()
    }

    // ── Đọc thông tin version từ PackageInfo ──────────────────────────────────
    private fun setupVersionInfo() {
        try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            val versionName = pi.versionName ?: "2.0.0"
            @Suppress("DEPRECATION")
            val versionCode = pi.versionCode.toString()

            binding.tvAppVersion.text = versionName
            binding.tvAppBuild.text   = versionCode
            binding.tvAboutVersionBadge.text = "v$versionName"
            binding.tvCurrentVersionUpdate.text = "v$versionName · Build $versionCode"
        } catch (_: PackageManager.NameNotFoundException) {
            binding.tvAppVersion.text = "2.0.0"
            binding.tvAppBuild.text   = "200"
        }

        // Ngày cập nhật gần nhất (lấy từ thời gian cài đặt APK)
        try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            val lastUpdate = Date(pi.lastUpdateTime)
            val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.tvLastUpdate.text = fmt.format(lastUpdate)
        } catch (_: Exception) {
            binding.tvLastUpdate.text = "12/07/2025"
        }
    }

    // ── Particles ─────────────────────────────────────────────────────────────
    private fun setupParticles() {
        // Background particles — violet/purple palette
        binding.aboutParticles.setColors(intArrayOf(
            Color.parseColor("#7C4DFF"),
            Color.parseColor("#CE93D8"),
            Color.parseColor("#FFFFFF")
        ))
        binding.aboutParticles.setDensity(12)
        binding.aboutParticles.start()

        // Hero banner particles
        binding.aboutHeroParticles.setColors(intArrayOf(
            Color.parseColor("#7C4DFF"),
            Color.parseColor("#CE93D8"),
            Color.parseColor("#E040FB")
        ))
        binding.aboutHeroParticles.setDensity(10)
        binding.aboutHeroParticles.start()

        // Update banner particles (started later when tab selected)
        try {
            binding.updateBannerParticles.setColors(intArrayOf(
                Color.parseColor("#00E5FF"),
                Color.parseColor("#69FF47"),
                Color.parseColor("#FFFFFF")
            ))
            binding.updateBannerParticles.setDensity(8)
        } catch (_: Exception) {}
    }

    // ── Back button ───────────────────────────────────────────────────────────
    private fun setupBackButton() {
        binding.btnAboutBack.setOnClickListener {
            it.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f)
                    .setDuration(160).setInterpolator(OvershootInterpolator(2f))
                    .withEndAction { finish() }.start()
            }.start()
        }
    }

    // ── Tab switching ─────────────────────────────────────────────────────────
    private fun setupTabs() {
        selectTab("info")

        binding.tabAboutInfo.setOnClickListener { bounceView(it); selectTab("info") }
        binding.tabAboutUpdate.setOnClickListener { bounceView(it); selectTab("update") }
    }

    private fun selectTab(tab: String) {
        activeTab = tab

        val tabs = listOf(
            "info"   to binding.tabAboutInfo,
            "update" to binding.tabAboutUpdate,
        )
        tabs.forEach { (id, tv) ->
            if (id == tab) {
                tv.setBackgroundResource(com.aujunpeak.app.R.drawable.bg_floating_tab_active)
                tv.setTextColor(Color.WHITE)
            } else {
                tv.setBackgroundResource(android.R.color.transparent)
                tv.setTextColor(Color.parseColor("#80FFFFFF"))
            }
        }

        val sections = listOf(
            "info"   to binding.sectionAboutInfo,
            "update" to binding.sectionAboutUpdate,
        )
        sections.forEach { (id, view) ->
            if (id == tab) {
                view.visibility = View.VISIBLE
                view.alpha = 0f
                view.translationY = 20f
                view.animate().alpha(1f).translationY(0f)
                    .setDuration(280).setInterpolator(OvershootInterpolator(1f)).start()
                // Start update banner particles when update tab selected
                if (id == "update") {
                    try { binding.updateBannerParticles.start() } catch (_: Exception) {}
                }
            } else {
                view.visibility = View.GONE
                if (id == "update") {
                    try { binding.updateBannerParticles.stop() } catch (_: Exception) {}
                }
            }
        }
    }

    // ── Hero glow pulse ───────────────────────────────────────────────────────
    private fun startHeroGlow() {
        ObjectAnimator.ofFloat(binding.aboutHeroGlow, "scaleX", 1f, 1.3f, 1f).apply {
            duration = 2400; repeatCount = ValueAnimator.INFINITE; start()
        }
        ObjectAnimator.ofFloat(binding.aboutHeroGlow, "scaleY", 1f, 1.3f, 1f).apply {
            duration = 2400; repeatCount = ValueAnimator.INFINITE; start()
        }
        ObjectAnimator.ofFloat(binding.aboutHeroGlow, "alpha", 0.55f, 0.9f, 0.55f).apply {
            duration = 2400; repeatCount = ValueAnimator.INFINITE; start()
        }
    }

    // ── Entrance animation ────────────────────────────────────────────────────
    private fun animateEntrance() {
        val container = binding.aboutContainer
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            child.alpha = 0f
            child.translationY = 45f
            child.animate()
                .alpha(1f).translationY(0f)
                .setDuration(420).setStartDelay((i * 65).toLong())
                .setInterpolator(OvershootInterpolator(1f))
                .start()
        }
    }

    private fun bounceView(v: View) {
        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction {
            v.animate().scaleX(1f).scaleY(1f)
                .setDuration(190).setInterpolator(OvershootInterpolator(2f)).start()
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            binding.aboutParticles.stop()
            binding.aboutHeroParticles.stop()
            binding.updateBannerParticles.stop()
        } catch (_: Exception) {}
    }
}
