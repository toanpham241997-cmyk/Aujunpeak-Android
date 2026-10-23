package com.aujunpeak.app.activities

import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aujunpeak.app.BuildConfig
import com.aujunpeak.app.R
import com.aujunpeak.app.utils.KeyManager
import com.aujunpeak.app.views.IntroBrandView
import com.aujunpeak.app.views.ParticleView
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Cinematic entry screen.
 *
 * The old START interface is intentionally skipped: the app opens on a black
 * canvas, draws the Aujunpeak VN wolf/logo intro with sound, then goes directly
 * to MainActivity (or ForceUpdateActivity when required by the server).
 */
class SplashActivity : AppCompatActivity() {

    private var introSound: MediaPlayer? = null
    private var brandVoice: MediaPlayer? = null
    private var isNavigating = false
    private var versionCheck: Deferred<KeyManager.VersionCheckResult>? = null

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.aujunpeak.app.utils.LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyFirebaseMessagingService.createChannel(this)
        prepareCinematicWindow()

        // Start the network check while the intro is playing so navigation is
        // immediate when the final frame finishes.
        versionCheck = lifecycleScope.async {
            KeyManager.checkForceUpdate()
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
        }
        setContentView(root)

        val ambientParticles = ParticleView(this).apply {
            alpha = 0.16f
            setDensity(34)
            setColors(
                intArrayOf(
                    Color.parseColor("#FF2448"),
                    Color.parseColor("#D90031"),
                    Color.parseColor("#FF6A38"),
                    Color.parseColor("#8A0C30")
                )
            )
        }
        root.addView(
            ambientParticles,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val intro = IntroBrandView(this)
        root.addView(
            intro,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        playIntroSound()
        intro.start(
            onVoiceCue = { playBrandVoice() }
        ) {
            if (!isFinishing && !isDestroyed) {
                finishIntroAndNavigate(intro, ambientParticles)
            }
        }
    }

    private fun prepareCinematicWindow() {
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun playIntroSound() {
        releaseIntroSound()
        introSound = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                val afd = resources.openRawResourceFd(R.raw.intro_aujunpeak_cinematic)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setVolume(0.72f, 0.72f)
                setOnCompletionListener { player ->
                    player.release()
                    if (introSound === player) introSound = null
                }
                prepare()
                start()
            }
        } catch (_: Exception) {
            null
        }
    }


    /**
     * Plays the bundled low, slow brand voice exactly when the title begins
     * revealing. The voice is pre-processed and packaged with the app, so it
     * sounds the same on every Android device instead of depending on system TTS.
     */
    private fun playBrandVoice() {
        releaseBrandVoice()
        try {
            // Duck the cinematic layer so the spoken brand remains clear.
            introSound?.setVolume(0.34f, 0.34f)
        } catch (_: Exception) {
        }

        brandVoice = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                val afd = resources.openRawResourceFd(R.raw.intro_aujunpeak_voice)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setVolume(0.96f, 0.96f)
                setOnCompletionListener { player ->
                    try {
                        introSound?.setVolume(0.72f, 0.72f)
                    } catch (_: Exception) {
                    }
                    player.release()
                    if (brandVoice === player) brandVoice = null
                }
                prepare()
                start()
            }
        } catch (_: Exception) {
            try {
                introSound?.setVolume(0.72f, 0.72f)
            } catch (_: Exception) {
            }
            null
        }
    }

    private fun releaseBrandVoice() {
        try {
            brandVoice?.stop()
        } catch (_: Exception) {
        }
        try {
            brandVoice?.release()
        } catch (_: Exception) {
        }
        brandVoice = null
    }

    private fun finishIntroAndNavigate(intro: View, ambientParticles: ParticleView) {
        if (isNavigating) return
        isNavigating = true

        // A short cinematic fade keeps the logo from cutting abruptly while the
        // already-running version check is resolved in the background.
        intro.animate()
            .alpha(0f)
            .scaleX(1.035f)
            .scaleY(1.035f)
            .setDuration(320L)
            .start()
        ambientParticles.animate().alpha(0f).setDuration(320L).start()

        lifecycleScope.launch {
            val result = runCatching {
                if (versionCheck?.isCompleted == true) {
                    versionCheck?.await() ?: KeyManager.VersionCheckResult(false, 0, "")
                } else {
                    // Keep the transition instant; the check has already had the full
                    // intro duration to finish in the background.
                    KeyManager.VersionCheckResult(false, 0, "")
                }
            }.getOrElse {
                KeyManager.VersionCheckResult(false, 0, "")
            }

            intro.postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed
                if (result.forceUpdate && BuildConfig.VERSION_CODE < result.minVersionCode) {
                    val intent = Intent(this@SplashActivity, ForceUpdateActivity::class.java).apply {
                        putExtra(ForceUpdateActivity.EXTRA_DOWNLOAD_URL, result.downloadUrl)
                        putExtra(ForceUpdateActivity.EXTRA_MIN_VERSION_CODE, result.minVersionCode)
                        putExtra(ForceUpdateActivity.EXTRA_CURRENT_VERSION, BuildConfig.VERSION_NAME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                } else {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                }
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }, 260L)
        }
    }

    private fun releaseIntroSound() {
        try {
            introSound?.stop()
        } catch (_: Exception) {
        }
        try {
            introSound?.release()
        } catch (_: Exception) {
        }
        introSound = null
    }

    override fun onDestroy() {
        releaseBrandVoice()
        releaseIntroSound()
        versionCheck?.cancel()
        super.onDestroy()
    }
}
