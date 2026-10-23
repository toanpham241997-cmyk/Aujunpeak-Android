package com.aujunpeak.app.activities

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.aujunpeak.app.utils.LocaleHelper
import androidx.core.animation.doOnEnd
import androidx.lifecycle.lifecycleScope
import com.aujunpeak.app.R
import com.aujunpeak.app.views.BoostCleanupView
import com.aujunpeak.app.views.BoostParticleView
import com.aujunpeak.app.views.RingLoaderView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameBoostActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }



    private lateinit var ivGameIcon: ImageView
    private lateinit var ivSuccessIcon: ImageView
    private lateinit var tvGameName: TextView
    private lateinit var tvPackageName: TextView
    private lateinit var tvBoostStatus: TextView
    private lateinit var tvSuccessAppName: TextView
    private lateinit var tvBoostPercent: TextView
    private lateinit var tvBoostStep: TextView
    private lateinit var btnBoost: LinearLayout
    private lateinit var btnBack: LinearLayout
    private lateinit var boostParticles: BoostParticleView
    private lateinit var boostCleanupView: BoostCleanupView
    private lateinit var glowView: View
    private lateinit var boostRing: RingLoaderView
    private lateinit var boostProgressBar: ProgressBar
    private lateinit var layoutBoosting: LinearLayout
    private lateinit var cardSuccess: LinearLayout
    private lateinit var vSuccessDot: View
    private lateinit var ivSuccessBadge: ImageView
    private lateinit var statResultRam: LinearLayout
    private lateinit var statResultCpu: LinearLayout
    private lateinit var statResultFps: LinearLayout
    private lateinit var cardAppInfo: LinearLayout

    private var isBoosting = false
    private var selectedPackage = ""
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        /** Total length of the vortex cleanup animation shown while "boosting" */
        private const val BOOST_DURATION_MS = 5_200L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_boost)

        ivGameIcon       = findViewById(R.id.ivGameIcon)
        ivSuccessIcon    = findViewById(R.id.ivSuccessIcon)
        tvGameName       = findViewById(R.id.tvGameName)
        tvPackageName    = findViewById(R.id.tvPackageName)
        tvBoostStatus    = findViewById(R.id.tvBoostStatus)
        tvSuccessAppName = findViewById(R.id.tvSuccessAppName)
        tvBoostPercent   = findViewById(R.id.tvBoostPercent)
        tvBoostStep      = findViewById(R.id.tvBoostStep)
        btnBoost         = findViewById(R.id.btnBoost)
        btnBack          = findViewById(R.id.btnBack)
        boostParticles   = findViewById(R.id.boostParticles)
        boostCleanupView = findViewById(R.id.boostCleanupView)
        glowView         = findViewById(R.id.glowView)
        boostRing        = findViewById(R.id.boostRing)
        boostProgressBar = findViewById(R.id.boostProgressBar)
        layoutBoosting   = findViewById(R.id.layoutBoosting)
        cardSuccess      = findViewById(R.id.cardSuccess)
        vSuccessDot      = findViewById(R.id.vSuccessDot)
        ivSuccessBadge   = findViewById(R.id.ivSuccessBadge)
        statResultRam    = findViewById(R.id.statResultRam)
        statResultCpu    = findViewById(R.id.statResultCpu)
        statResultFps    = findViewById(R.id.statResultFps)
        cardAppInfo      = findViewById(R.id.cardAppInfo)

        val appName = intent.getStringExtra("app_name") ?: "Unknown App"
        selectedPackage = intent.getStringExtra("package_name") ?: ""

        tvGameName.text       = appName
        tvPackageName.text    = selectedPackage
        tvSuccessAppName.text = appName

        if (selectedPackage.isNotEmpty()) {
            try {
                val icon = packageManager.getApplicationIcon(selectedPackage)
                ivGameIcon.setImageDrawable(icon)
                ivSuccessIcon.setImageDrawable(icon)
            } catch (_: Exception) {
                ivGameIcon.setImageResource(android.R.drawable.sym_def_app_icon)
                ivSuccessIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
        }

        boostParticles.setIdleMode(true)
        boostParticles.start()

        loadGifBackground()
        animateCardIn()

        btnBoost.setOnClickListener { if (!isBoosting) startBoost() }
        btnBack.setOnClickListener { finish() }
    }

    // ─── Card entrance animation ──────────────────────────────────────────────

    private fun animateCardIn() {
        cardAppInfo.alpha       = 0f
        cardAppInfo.translationY = 80f
        cardAppInfo.animate()
            .alpha(1f).translationY(0f)
            .setDuration(500).setStartDelay(150)
            .setInterpolator(OvershootInterpolator(1f)).start()

        btnBoost.alpha  = 0f
        btnBoost.scaleX = 0.8f
        btnBoost.scaleY = 0.8f
        btnBoost.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(450).setStartDelay(350)
            .setInterpolator(OvershootInterpolator(1.5f)).start()
    }

    // ─── Boost start ──────────────────────────────────────────────────────────

    private fun startBoost() {
        isBoosting = true
        btnBoost.isEnabled = false
        handler.removeCallbacksAndMessages(null)
        resetBoostSessionViews()

        // 1) Pulse button on tap
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(btnBoost, "scaleX", 1f, 1.14f, 1f),
                ObjectAnimator.ofFloat(btnBoost, "scaleY", 1f, 1.14f, 1f)
            )
            duration = 260
            start()
        }

        // 2) After short pulse → animate button + card OUT, then start cleanup
        handler.postDelayed({
            animateBoostButtonOut()
        }, 280)
    }

    private fun resetBoostSessionViews() {
        cardSuccess.visibility = View.GONE
        cardSuccess.alpha = 0f
        layoutBoosting.visibility = View.GONE
        layoutBoosting.alpha = 0f
        layoutBoosting.translationY = 40f
        boostProgressBar.progress = 0
        tvBoostPercent.text = "0%"
        tvBoostStep.text = getString(R.string.label_processing)
        tvBoostStatus.text = getString(R.string.game_boost_ready)
        ivSuccessBadge.alpha = 0f
        statResultRam.alpha = 0f
        statResultCpu.alpha = 0f
        statResultFps.alpha = 0f
    }

    private fun animateBoostButtonOut() {
        // Shrink and fade out the boost button
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(btnBoost, "scaleX", 1f, 0f),
                ObjectAnimator.ofFloat(btnBoost, "scaleY", 1f, 0f),
                ObjectAnimator.ofFloat(btnBoost, "alpha", 1f, 0f)
            )
            duration = 350
            interpolator = AccelerateInterpolator(2f)
            doOnEnd { btnBoost.visibility = View.GONE }
            start()
        }

        // Slide card up and fade out
        cardAppInfo.animate()
            .translationY(-60f).alpha(0.3f)
            .setDuration(350).setStartDelay(100)
            .setInterpolator(AccelerateInterpolator(1.5f))
            .withEndAction {
                launchCleanupAnimation()
            }
            .start()
    }

    // ─── Cleanup animation ────────────────────────────────────────────────────

    private fun launchCleanupAnimation() {
        // Load a pool of installed app icons for the animation (exclude selected game).
        // The pool is cycled/reused so the vortex stays populated across the full
        // BOOST_DURATION_MS instead of running out of icons after a couple seconds.
        val icons = getJunkAppIcons(count = 18)

        layoutBoosting.visibility = View.VISIBLE
        layoutBoosting.alpha = 0f
        layoutBoosting.translationY = 40f
        layoutBoosting.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(420)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()
        startBoostProgressUi()

        boostCleanupView.setIcons(icons)
        boostCleanupView.startAnimation(durationMs = BOOST_DURATION_MS) {
            // Animation done → show success
            handler.post { onBoostComplete() }
        }

        // Switch background particles to active mode
        boostParticles.setIdleMode(false)

        // Glow pulse
        glowView.animate().alpha(0.6f).setDuration(600).start()
    }

    private fun startBoostProgressUi() {
        val steps = listOf(
            "Scanning background cache...",
            "Releasing RAM fragments...",
            "Balancing CPU threads...",
            "Applying Free Fire profile..."
        )

        ValueAnimator.ofInt(0, 100).apply {
            duration = BOOST_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val value = anim.animatedValue as Int
                boostProgressBar.progress = value
                tvBoostPercent.text = "$value%"
            }
            start()
        }

        steps.forEachIndexed { index, step ->
            handler.postDelayed({
                tvBoostStep.text = step
                tvBoostStep.alpha = 0.2f
                tvBoostStep.translationY = 14f
                tvBoostStep.animate().alpha(1f).translationY(0f)
                    .setDuration(240)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .start()
            }, index * (BOOST_DURATION_MS / steps.size))
        }

        pulseBoostRingWhileActive()
    }

    private fun pulseBoostRingWhileActive() {
        if (!isBoosting) {
            boostRing.scaleX = 1f
            boostRing.scaleY = 1f
            return
        }
        boostRing.animate().scaleX(1.08f).scaleY(1.08f).setDuration(520L)
            .withEndAction {
                boostRing.animate().scaleX(0.92f).scaleY(0.92f).setDuration(520L)
                    .withEndAction { pulseBoostRingWhileActive() }
                    .start()
            }
            .start()
    }

    private fun getJunkAppIcons(count: Int): List<Drawable> {
        return try {
            packageManager.getInstalledApplications(0)
                .filter { info ->
                    packageManager.getLaunchIntentForPackage(info.packageName) != null &&
                    info.packageName != selectedPackage &&
                    info.packageName != packageName
                }
                .shuffled()
                .take(count)
                .mapNotNull { info ->
                    try { packageManager.getApplicationIcon(info.packageName) }
                    catch (_: Exception) { null }
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ─── Boost complete ───────────────────────────────────────────────────────

    private fun onBoostComplete() {
        boostCleanupView.stopAnimation()
        boostParticles.setIdleMode(true)
        layoutBoosting.animate().alpha(0f).translationY(-18f).setDuration(220).withEndAction {
            layoutBoosting.visibility = View.GONE
            layoutBoosting.translationY = 0f
        }.start()

        // Quick green success flash on the glow layer before it settles back to idle
        glowView.animate().cancel()
        glowView.alpha = 0.85f
        glowView.animate().alpha(0f).setDuration(700).setStartDelay(120).start()

        // Haptic tick — small physical confirmation that the boost finished
        try {
            cardSuccess.isHapticFeedbackEnabled = true
            cardSuccess.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } catch (_: Exception) {}

        // Bring card back into view, slide from below
        cardAppInfo.translationY = 120f
        cardAppInfo.animate()
            .translationY(0f).alpha(1f)
            .setDuration(350)
            .setInterpolator(OvershootInterpolator(1f))
            .start()

        tvBoostStatus.text = getString(R.string.game_boost_success)

        // Show success card with a punchy pop-in (slight overshoot on scale + slide)
        cardSuccess.visibility = View.VISIBLE
        cardSuccess.alpha       = 0f
        cardSuccess.translationY = 120f
        cardSuccess.scaleX = 0.92f
        cardSuccess.scaleY = 0.92f
        cardSuccess.animate()
            .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(500).setStartDelay(200)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()

        // Success badge on the app icon pops in slightly after the card lands
        ivSuccessBadge.scaleX = 0f; ivSuccessBadge.scaleY = 0f; ivSuccessBadge.alpha = 0f
        ivSuccessBadge.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(420).setStartDelay(520)
            .setInterpolator(OvershootInterpolator(2.5f))
            .start()

        // Stat chips (RAM / CPU / FPS) cascade in one after another for a livelier reveal
        staggerStatChip(statResultRam, 650)
        staggerStatChip(statResultCpu, 730)
        staggerStatChip(statResultFps, 810)

        pulseSuccessDot()

        // Reset boost button (in case user wants to boost again)
        lifecycleScope.launch {
            delay(1800)
            btnBoost.visibility = View.VISIBLE
            btnBoost.scaleX = 0f; btnBoost.scaleY = 0f; btnBoost.alpha = 0f
            btnBoost.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(400).setInterpolator(OvershootInterpolator(1.5f))
                .start()
            isBoosting = false
            btnBoost.isEnabled = true
        }
    }

    /** Fades + slides a single result stat chip in with its own start delay, for a staggered reveal. */
    private fun staggerStatChip(chip: LinearLayout, startDelay: Long) {
        chip.alpha = 0f
        chip.translationY = 20f
        chip.animate()
            .alpha(1f).translationY(0f)
            .setDuration(320).setStartDelay(startDelay)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()
    }

    private fun pulseSuccessDot() {
        if (isDestroyed) return
        vSuccessDot.animate().alpha(0.3f).scaleX(1.4f).scaleY(1.4f).setDuration(800)
            .withEndAction {
                if (!isDestroyed)
                    vSuccessDot.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(800)
                        .withEndAction { pulseSuccessDot() }.start()
            }.start()
    }

    // ─── GIF background ───────────────────────────────────────────────────────

    private fun loadGifBackground() {
        try {
            val bgGif = findViewById<ImageView>(R.id.gameBoostBgGif)
            Glide.with(this).asGif().load(R.raw.bg_app_photo)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE).into(bgGif)
        } catch (_: Exception) {}
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        boostParticles.stop()
        boostCleanupView.stopAnimation()
        try {
            Glide.with(this).clear(findViewById<ImageView>(R.id.gameBoostBgGif))
        } catch (_: Exception) {}
    }
}
