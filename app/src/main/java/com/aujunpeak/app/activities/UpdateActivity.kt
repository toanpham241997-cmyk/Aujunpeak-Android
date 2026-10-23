package com.aujunpeak.app.activities

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aujunpeak.app.R
import com.aujunpeak.app.dynamicui.DynamicUiManager
import com.aujunpeak.app.dynamicui.DynamicUiStore
import com.aujunpeak.app.databinding.ActivityUpdateBinding
import com.aujunpeak.app.utils.LocaleHelper
import com.aujunpeak.app.utils.ThemeManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.launch

class UpdateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpdateBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadGifBackground()
        setupParticles()
        animateEntrance()
        startGreenDotPulse()
        startAmbientHeroMotion()
        applyThemeColor()
        setupDynamicUiUpdate()

        binding.btnUpdateBack.setOnClickListener {
            it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f)
                    .setDuration(150).setInterpolator(OvershootInterpolator(2f))
                    .withEndAction { finish() }.start()
            }.start()
        }
    }


    private fun setupDynamicUiUpdate() {
        val card = findViewById<android.view.View>(R.id.cardDynamicUi)
        val versionText = findViewById<android.widget.TextView>(R.id.tvDynamicUiVersion)
        val statusText = findViewById<android.widget.TextView>(R.id.tvDynamicUiStatus)
        val button = findViewById<android.view.View>(R.id.btnDynamicUiRefresh)
        val buttonText = findViewById<android.widget.TextView>(R.id.tvDynamicUiRefresh)

        fun renderVersion() {
            val version = DynamicUiStore.activeVersion(this)
            versionText.text = if (version > 0) "Data version: v$version" else "Data version: mặc định trong APK"
        }
        renderVersion()

        button.setOnClickListener {
            if (!button.isEnabled) return@setOnClickListener
            button.isEnabled = false
            buttonText.text = "ĐANG KIỂM TRA..."
            statusText.text = "Đang kết nối máy chủ và kiểm tra manifest data..."
            card.animate().scaleX(0.985f).scaleY(0.985f).setDuration(100).withEndAction {
                card.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
            }.start()

            lifecycleScope.launch {
                val result = DynamicUiManager.refreshIfNeeded(this@UpdateActivity, force = true)
                renderVersion()
                statusText.text = when {
                    result.requiresNativeUpdate -> "Data mới cần chức năng native mới; trường hợp này mới cần APK mới."
                    result.updated -> "Cập nhật data thành công. Giao diện mới đã được áp dụng."
                    result.error != null -> "Không tải được data mới. App vẫn dùng data cache hiện tại."
                    else -> "Bạn đang dùng data giao diện mới nhất."
                }
                buttonText.text = "KIỂM TRA DATA MỚI"
                button.isEnabled = true
            }
        }
    }

    /** Load the same GIF as MainActivity background */
    private fun loadGifBackground() {
        try {
            Glide.with(this)
                .asGif()
                .load(R.raw.bg_app_photo)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .into(binding.updateBgGif)
        } catch (e: Exception) { /* GIF loading is optional visual; never crash */ }
    }

    @Deprecated("Use OnBackPressedDispatcher", ReplaceWith("onBackPressedDispatcher.onBackPressed()"))
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun setupParticles() {
        val pv = binding.updateParticles
        try {
            pv.javaClass.getMethod("setColors", IntArray::class.java).invoke(pv, intArrayOf(
                Color.parseColor("#1E88FF"),
                Color.parseColor("#0D47A1"),
                Color.parseColor("#42A5F5"),
                Color.parseColor("#1565C0"),
                Color.parseColor("#00B0FF")
            ))
        } catch (e: Exception) { }
        try { pv.javaClass.getMethod("setIdleMode", Boolean::class.javaPrimitiveType).invoke(pv, true) }
        catch (e: Exception) { }
        try { pv.javaClass.getMethod("start").invoke(pv) }
        catch (e: Exception) { }
    }

    private fun animateEntrance() {
        // 1. Back button fades in
        binding.btnUpdateBack.alpha = 0f
        binding.btnUpdateBack.animate().alpha(1f).setDuration(350).setStartDelay(80).start()

        // 2. Header slides up
        val headerLayout = try { binding.root.findViewById<LinearLayout>(R.id.layoutUpdateHeader) }
                           catch (e: Exception) { null }
        headerLayout?.let { v ->
            v.alpha = 0f; v.translationY = 40f
            v.animate().alpha(1f).translationY(0f)
                .setDuration(520).setStartDelay(120)
                .setInterpolator(DecelerateInterpolator()).start()
        }

        // 3. Version card slides + bounces in from below
        binding.updateCard.alpha = 0f
        binding.updateCard.translationY = 80f
        binding.updateCard.animate()
            .alpha(1f).translationY(0f)
            .setDuration(560).setStartDelay(220)
            .setInterpolator(OvershootInterpolator(1.3f)).start()

        // 4. Staggered section cards: What's New, Stats, Coming Soon
        val sectionIds = listOf(
            R.id.cardWhatsNew,
            R.id.cardUpdateStats,
            R.id.cardComingSoon
        )
        sectionIds.forEachIndexed { index, id ->
            val delay = 400L + (index * 130L)
            try {
                val card = binding.root.findViewById<android.view.View>(id)
                card.alpha = 0f
                card.translationX = if (index % 2 == 0) -60f else 60f
                card.animate()
                    .alpha(1f).translationX(0f)
                    .setDuration(480).setStartDelay(delay)
                    .setInterpolator(OvershootInterpolator(1.1f)).start()
            } catch (e: Exception) { }
        }

        // 5. Staggered feature item rows inside What's New
        val featureIds = listOf(
            R.id.featureItem1, R.id.featureItem2, R.id.featureItem3, R.id.featureItem4
        )
        featureIds.forEachIndexed { index, id ->
            val delay = 700L + (index * 100L)
            try {
                val row = binding.root.findViewById<android.view.View>(id)
                row.alpha = 0f
                row.translationX = -50f
                row.animate()
                    .alpha(1f).translationX(0f)
                    .setDuration(400).setStartDelay(delay)
                    .setInterpolator(DecelerateInterpolator()).start()
            } catch (e: Exception) { }
        }

        // 6. Stats tiles animate up with delay
        val statIds = listOf(R.id.tvStatRam, R.id.tvStatFps, R.id.tvStatPing)
        statIds.forEachIndexed { index, id ->
            val delay = 900L + (index * 80L)
            try {
                val tv = binding.root.findViewById<android.view.View>(id)
                tv.scaleX = 0.4f; tv.scaleY = 0.4f; tv.alpha = 0f
                tv.animate()
                    .scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(400).setStartDelay(delay)
                    .setInterpolator(OvershootInterpolator(1.5f)).start()
            } catch (e: Exception) { }
        }

        // 7. Animated TITLE typewriter glow after card appears
        try {
            val tvTitle = binding.root.findViewById<android.widget.TextView>(R.id.tvUpdateTitle)
            tvTitle?.let {
                handler.postDelayed({
                    ObjectAnimator.ofFloat(it, "alpha", 0.4f, 1f).apply {
                        duration = 800; repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.REVERSE
                        interpolator = LinearInterpolator()
                        start()
                    }
                }, 300)
            }
        } catch (e: Exception) { }
    }

    private fun startGreenDotPulse() {
        val dot = binding.vUpdateDot
        ValueAnimator.ofFloat(1f, 0.2f, 1f).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { dot.alpha = it.animatedValue as Float }
            start()
        }
        fun pulseDot() {
            dot.animate().scaleX(1.6f).scaleY(1.6f).setDuration(700).withEndAction {
                dot.animate().scaleX(1f).scaleY(1f).setDuration(700)
                    .withEndAction { pulseDot() }.start()
            }.start()
        }
        pulseDot()

        // Pulse the dot feature indicators too
        val dotIds = listOf(R.id.dotFeature1, R.id.dotFeature2, R.id.dotFeature3, R.id.dotFeature4)
        dotIds.forEachIndexed { i, id ->
            try {
                val d = binding.root.findViewById<android.view.View>(id)
                handler.postDelayed({
                    ValueAnimator.ofFloat(1f, 0.3f, 1f).apply {
                        duration = 1200 + (i * 150L)
                        repeatCount = ValueAnimator.INFINITE
                        interpolator = LinearInterpolator()
                        addUpdateListener { d.alpha = it.animatedValue as Float }
                        start()
                    }
                }, i * 200L)
            } catch (e: Exception) { }
        }
    }

    private fun startAmbientHeroMotion() {
        fun pulseCard() {
            if (isDestroyed || isFinishing) return
            binding.updateCard.animate().translationY(-8f).setDuration(1800).withEndAction {
                binding.updateCard.animate().translationY(0f).setDuration(1800).withEndAction {
                    pulseCard()
                }.start()
            }.start()
        }
        fun pulseRing() {
            if (isDestroyed || isFinishing) return
            binding.updateRingLoader.animate().scaleX(1.08f).scaleY(1.08f).setDuration(900).withEndAction {
                binding.updateRingLoader.animate().scaleX(1f).scaleY(1f).setDuration(900).withEndAction {
                    pulseRing()
                }.start()
            }.start()
        }
        pulseCard()
        pulseRing()
    }

    private fun applyThemeColor() {
        val saved = ThemeManager.getSavedColor(this)
        val displayColor = if (saved.equals("#FFFFFF", true)) "#69FF47" else saved
        try { binding.tvVersionBadge.setTextColor(Color.parseColor(displayColor)) }
        catch (e: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { binding.updateParticles.javaClass.getMethod("stop").invoke(binding.updateParticles) }
        catch (e: Exception) { }
        try { Glide.with(this).clear(binding.updateBgGif) }
        catch (e: Exception) { }
    }
}
