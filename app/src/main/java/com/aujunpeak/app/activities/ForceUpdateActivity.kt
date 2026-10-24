package com.aujunpeak.app.activities

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.aujunpeak.app.R
import com.aujunpeak.app.views.BoostParticleView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.aujunpeak.app.utils.UiModal

/**
 * ForceUpdateActivity — màn hình bắt buộc cập nhật (không thể back).
 *
 * Hiển thị khi server trả về forceUpdate=true và
 * BuildConfig.VERSION_CODE < minVersionCode.
 */
class ForceUpdateActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DOWNLOAD_URL     = "extra_download_url"
        const val EXTRA_MIN_VERSION_CODE = "extra_min_version_code"
        const val EXTRA_CURRENT_VERSION  = "extra_current_version"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var downloadUrl = ""

    // Views
    private lateinit var ivBgGif: ImageView
    private lateinit var particles: BoostParticleView
    private lateinit var tvCurrentVersion: TextView
    private lateinit var tvNewVersionBadge: TextView
    private lateinit var tvWarningMsg: TextView
    private lateinit var tvDownloadUrl: TextView
    private lateinit var btnUpdate: FrameLayout
    private lateinit var btnCopyLink: FrameLayout
    private lateinit var vGlowOuter: View
    private lateinit var vGlowInner: View
    private lateinit var vRotatingRing: View
    private lateinit var vShimmer: View
    private lateinit var vShimmerBtn: View
    private lateinit var vStatusDot: View
    private lateinit var cardMain: View
    private lateinit var cardUrl: View
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var ivIcon: ImageView

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.aujunpeak.app.utils.LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_force_update)

        downloadUrl = intent.getStringExtra(EXTRA_DOWNLOAD_URL) ?: ""
        val minVersion = intent.getIntExtra(EXTRA_MIN_VERSION_CODE, 0)
        val curVersion = intent.getStringExtra(EXTRA_CURRENT_VERSION) ?: ""

        bindViews()
        applyData(minVersion, curVersion)
        loadBg()
        setupParticles()
        animateEntrance()
        startIdleEffects()
        setupButtons()
    }

    private fun bindViews() {
        ivBgGif          = findViewById(R.id.fuBgGif)
        particles         = findViewById(R.id.fuParticles)
        tvCurrentVersion  = findViewById(R.id.fuTvCurrentVersion)
        tvNewVersionBadge = findViewById(R.id.fuTvNewVersionBadge)
        tvWarningMsg      = findViewById(R.id.fuTvWarningMsg)
        tvDownloadUrl     = findViewById(R.id.fuTvDownloadUrl)
        btnUpdate         = findViewById(R.id.fuBtnUpdate)
        btnCopyLink       = findViewById(R.id.fuBtnCopyLink)
        vGlowOuter        = findViewById(R.id.fuVGlowOuter)
        vGlowInner        = findViewById(R.id.fuVGlowInner)
        vRotatingRing     = findViewById(R.id.fuVRotatingRing)
        vShimmer          = findViewById(R.id.fuVShimmer)
        vShimmerBtn       = findViewById(R.id.fuVShimmerBtn)
        vStatusDot        = findViewById(R.id.fuVStatusDot)
        cardMain          = findViewById(R.id.fuCardMain)
        cardUrl           = findViewById(R.id.fuCardUrl)
        tvTitle           = findViewById(R.id.fuTvTitle)
        tvSubtitle        = findViewById(R.id.fuTvSubtitle)
        ivIcon            = findViewById(R.id.fuIvWarningIcon)
    }

    private fun applyData(minVersion: Int, curVersion: String) {
        tvCurrentVersion.text  = "v$curVersion"
        tvNewVersionBadge.text = "v$minVersion"
        if (downloadUrl.isNotEmpty()) {
            tvDownloadUrl.text = downloadUrl
            cardUrl.visibility = View.VISIBLE
        } else {
            cardUrl.visibility = View.GONE
        }
    }

    private fun loadBg() {
        try {
            Glide.with(this).asGif()
                .load(R.raw.bg_app_photo)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .into(ivBgGif)
        } catch (_: Exception) {}
    }

    private fun setupParticles() {
        try {
            particles.setColors(intArrayOf(
                Color.parseColor("#FF4500"),
                Color.parseColor("#FF6D00"),
                Color.parseColor("#FFC107"),
                Color.parseColor("#FF8C00"),
            ))
            particles.setIdleMode(true)
            particles.start()
        } catch (_: Exception) {}
    }

    // ── Entrance animations ─────────────────────────────────────────────────
    private fun animateEntrance() {
        // Icon rings
        vGlowOuter.alpha = 0f
        vGlowInner.alpha = 0f
        vRotatingRing.alpha = 0f

        // Title section
        tvTitle.alpha = 0f; tvTitle.translationY = -28f
        tvSubtitle.alpha = 0f; tvSubtitle.translationY = 10f
        tvTitle.animate().alpha(1f).translationY(0f)
            .setDuration(520).setStartDelay(150).setInterpolator(DecelerateInterpolator()).start()
        tvSubtitle.animate().alpha(1f).translationY(0f)
            .setDuration(400).setStartDelay(380).setInterpolator(DecelerateInterpolator()).start()

        // Icon
        ivIcon.alpha = 0f; ivIcon.scaleX = 0.4f; ivIcon.scaleY = 0.4f
        ivIcon.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(600).setStartDelay(200).setInterpolator(OvershootInterpolator(2f)).start()

        // Main card
        cardMain.alpha = 0f; cardMain.translationY = 50f
        cardMain.animate().alpha(1f).translationY(0f)
            .setDuration(600).setStartDelay(500).setInterpolator(OvershootInterpolator(1.2f)).start()

        // URL card
        if (downloadUrl.isNotEmpty()) {
            cardUrl.alpha = 0f; cardUrl.translationX = -30f
            cardUrl.animate().alpha(1f).translationX(0f)
                .setDuration(450).setStartDelay(750).setInterpolator(OvershootInterpolator(1f)).start()
        }

        // Update button
        btnUpdate.alpha = 0f; btnUpdate.scaleX = 0.5f; btnUpdate.scaleY = 0.5f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(btnUpdate, "alpha", 0f, 1f).setDuration(400),
                ObjectAnimator.ofFloat(btnUpdate, "scaleX", 0.5f, 1f).apply {
                    setDuration(400); interpolator = OvershootInterpolator(2.2f)
                },
                ObjectAnimator.ofFloat(btnUpdate, "scaleY", 0.5f, 1f).apply {
                    setDuration(400); interpolator = OvershootInterpolator(2.2f)
                }
            )
            startDelay = 950
            start()
        }

        // Copy button
        btnCopyLink.alpha = 0f
        btnCopyLink.animate().alpha(1f).setDuration(350).setStartDelay(1150).start()

        // Start idle after entrance
        handler.postDelayed({ if (!isFinishing) startIdleRings() }, 1600)
    }

    // ── Idle effects ────────────────────────────────────────────────────────
    private fun startIdleEffects() {
        // Status dot pulse
        ValueAnimator.ofFloat(1f, 0.15f, 1f).apply {
            duration = 1300; repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { vStatusDot.alpha = it.animatedValue as Float }
            start()
        }

        // Rotating ring
        ObjectAnimator.ofFloat(vRotatingRing, "rotation", 0f, 360f).apply {
            duration = 6000; repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        vRotatingRing.animate().alpha(0.7f).setDuration(600).setStartDelay(1200).start()

        // Shimmer on card
        vShimmer.translationX = -80f
        ObjectAnimator.ofFloat(vShimmer, "translationX", -80f, 400f).apply {
            duration = 1600; startDelay = 1800
            repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
            interpolator = DecelerateInterpolator(); start()
        }

        // Shimmer on button
        handler.postDelayed({
            if (!isFinishing) {
                shimmerLoop()
                pulseButton()
            }
        }, 1800)
    }

    private fun shimmerLoop() {
        if (isFinishing) return
        vShimmerBtn.translationX = -140f
        ObjectAnimator.ofFloat(vShimmerBtn, "translationX", -140f, 220f).apply {
            duration = 1300; startDelay = 1000
            repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
            interpolator = DecelerateInterpolator(); start()
        }
    }

    private fun startIdleRings() {
        startSonarRing(vGlowOuter, 0L)
        startSonarRing(vGlowInner, 800L)
    }

    private fun startSonarRing(ring: View, delay: Long) {
        ring.postDelayed({
            if (isFinishing) return@postDelayed
            ring.scaleX = 0.6f; ring.scaleY = 0.6f; ring.alpha = 0.55f
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(ring, "scaleX", 0.6f, 1.8f),
                    ObjectAnimator.ofFloat(ring, "scaleY", 0.6f, 1.8f),
                    ObjectAnimator.ofFloat(ring, "alpha", 0.55f, 0f)
                )
                duration = 1700; interpolator = DecelerateInterpolator()
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: android.animation.Animator) {
                        if (!isFinishing) startSonarRing(ring, 0)
                    }
                })
                start()
            }
        }, delay)
    }

    private fun pulseButton() {
        if (isFinishing) return
        btnUpdate.animate().scaleX(1.04f).scaleY(1.04f).setDuration(800)
            .withEndAction {
                if (!isFinishing) btnUpdate.animate().scaleX(1f).scaleY(1f).setDuration(800)
                    .withEndAction { if (!isFinishing) pulseButton() }.start()
            }.start()
    }

    // ── Buttons ─────────────────────────────────────────────────────────────
    private fun setupButtons() {
        btnUpdate.setOnClickListener {
            animateClick(btnUpdate) { openUrl() }
        }
        btnCopyLink.setOnClickListener {
            animateClick(btnCopyLink) { copyLink() }
        }
    }

    private fun openUrl() {
        if (downloadUrl.isEmpty()) {
            UiModal.show(this, "Chưa có link tải. Liên hệ hỗ trợ.", title = "Thiếu liên kết", type = UiModal.Type.WARNING)
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
        } catch (_: Exception) {
            UiModal.show(this, "Không thể mở trình duyệt.", title = "Lỗi mở liên kết", type = UiModal.Type.ERROR)
        }
    }

    private fun copyLink() {
        if (downloadUrl.isEmpty()) {
            UiModal.show(this, "Chưa có link để sao chép.", title = "Thiếu dữ liệu", type = UiModal.Type.WARNING)
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("APK Link", downloadUrl))
        UiModal.show(this, "Đã sao chép link tải!", title = "Sao chép thành công", type = UiModal.Type.SUCCESS, autoDismissMillis = 1500L)
    }

    private fun animateClick(v: View, action: () -> Unit) {
        v.animate().scaleX(0.91f).scaleY(0.91f).setDuration(80)
            .withEndAction {
                v.animate().scaleX(1f).scaleY(1f)
                    .setDuration(180).setInterpolator(OvershootInterpolator(2f))
                    .withEndAction { action() }.start()
            }.start()
    }

    // ── Block back ──────────────────────────────────────────────────────────
    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        UiModal.show(this, "Bạn bắt buộc phải cập nhật để tiếp tục.", title = "Bắt buộc cập nhật", type = UiModal.Type.WARNING)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { particles.stop() } catch (_: Exception) {}
        try { Glide.with(this).clear(ivBgGif) } catch (_: Exception) {}
    }
}