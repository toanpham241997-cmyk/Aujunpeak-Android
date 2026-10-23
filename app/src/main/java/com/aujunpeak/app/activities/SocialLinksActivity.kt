package com.aujunpeak.app.activities

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.aujunpeak.app.R
import com.aujunpeak.app.databinding.ActivitySocialLinksBinding
import com.aujunpeak.app.utils.LocaleHelper
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class SocialLinksActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySocialLinksBinding
    private val handler = Handler(Looper.getMainLooper())

    private val TIKTOK_URL   = "https://www.tiktok.com/@havanhuan_khabanhmuaquat"
    private val FACEBOOK_URL = "https://www.facebook.com/huan.ha.748764"

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySocialLinksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadGifBackground()
        animateEntrance()
        startContinuousAnimations()
        setupClicks()
    }

    /** Load the same GIF as MainActivity */
    private fun loadGifBackground() {
        try {
            Glide.with(this)
                .asGif()
                .load(R.raw.bg_app_photo)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .into(binding.socialBgGif)
        } catch (e: Exception) { /* GIF is optional visual; never crash */ }
    }

    private fun animateEntrance() {
        // 1. Avatar section slides down from top
        val avatarLayout = try { binding.root.findViewById<android.view.View>(R.id.layoutAvatar) }
                           catch (e: Exception) { null }
        avatarLayout?.let {
            it.alpha = 0f; it.translationY = -50f
            it.animate().alpha(1f).translationY(0f)
                .setDuration(600).setStartDelay(150)
                .setInterpolator(OvershootInterpolator(1.0f)).start()
        }

        // 2. Author name fades in separately
        try {
            val tvName = binding.root.findViewById<android.view.View>(R.id.tvAuthorName)
            tvName.alpha = 0f; tvName.scaleX = 0.8f; tvName.scaleY = 0.8f
            tvName.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(500).setStartDelay(350)
                .setInterpolator(OvershootInterpolator(1.5f)).start()
        } catch (e: Exception) { }

        // 3. Tagline slides in from right
        try {
            val tvTagline = binding.root.findViewById<android.view.View>(R.id.tvAuthorTagline)
            tvTagline.alpha = 0f; tvTagline.translationX = 60f
            tvTagline.animate().alpha(1f).translationX(0f)
                .setDuration(450).setStartDelay(450)
                .setInterpolator(OvershootInterpolator(1.2f)).start()
        } catch (e: Exception) { }

        // 4. Follow label fades up
        try {
            val tvFollow = binding.root.findViewById<android.view.View>(R.id.tvFollowLabel)
            tvFollow.alpha = 0f; tvFollow.translationY = 20f
            tvFollow.animate().alpha(1f).translationY(0f)
                .setDuration(400).setStartDelay(550).start()
        } catch (e: Exception) { }

        // 5. TikTok card slides in from left
        binding.cardTikTok.alpha = 0f
        binding.cardTikTok.translationX = -80f
        binding.cardTikTok.animate()
            .alpha(1f).translationX(0f)
            .setDuration(520).setStartDelay(650)
            .setInterpolator(OvershootInterpolator(1.2f)).start()

        // 6. Facebook card slides in from right
        binding.cardFacebook.alpha = 0f
        binding.cardFacebook.translationX = 80f
        binding.cardFacebook.animate()
            .alpha(1f).translationX(0f)
            .setDuration(520).setStartDelay(780)
            .setInterpolator(OvershootInterpolator(1.2f)).start()
    }

    private fun startContinuousAnimations() {
        // Gentle float animation on avatar
        try {
            val avatarLayout = binding.root.findViewById<android.view.View>(R.id.layoutAvatar)
            handler.postDelayed({
                ObjectAnimator.ofFloat(avatarLayout, "translationY", 0f, -8f, 0f).apply {
                    duration = 3000
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }, 800)
        } catch (e: Exception) { }

        // Tagline pulse
        try {
            val tvTagline = binding.root.findViewById<android.widget.TextView>(R.id.tvAuthorTagline)
            handler.postDelayed({
                ObjectAnimator.ofFloat(tvTagline, "alpha", 0.5f, 1f).apply {
                    duration = 2000
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = LinearInterpolator()
                    start()
                }
            }, 1200)
        } catch (e: Exception) { }

        // Cards subtle breathing scale
        listOf(binding.cardTikTok, binding.cardFacebook).forEachIndexed { i, card ->
            handler.postDelayed({
                ObjectAnimator.ofFloat(card, "scaleX", 1f, 1.02f, 1f).apply {
                    duration = 3500 + (i * 400L)
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
                ObjectAnimator.ofFloat(card, "scaleY", 1f, 1.02f, 1f).apply {
                    duration = 3500 + (i * 400L)
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }, (1000L + i * 500L))
        }
    }

    private fun setupClicks() {
        binding.btnSocialBack.setOnClickListener {
            it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f)
                    .setDuration(150).setInterpolator(OvershootInterpolator(2f))
                    .withEndAction { finish() }.start()
            }.start()
        }
        binding.cardTikTok.setOnClickListener   { bounceCard(it) { openUrl(TIKTOK_URL) } }
        binding.cardFacebook.setOnClickListener { bounceCard(it) { openUrl(FACEBOOK_URL) } }
    }

    private fun bounceCard(view: android.view.View, onEnd: () -> Unit) {
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).withEndAction {
            view.animate().scaleX(1f).scaleY(1f)
                .setDuration(220).setInterpolator(OvershootInterpolator(2.5f))
                .withEndAction { onEnd() }.start()
        }.start()
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    @Deprecated("Use OnBackPressedDispatcher", ReplaceWith("onBackPressedDispatcher.onBackPressed()"))
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { Glide.with(this).clear(binding.socialBgGif) }
        catch (e: Exception) { }
    }
}
