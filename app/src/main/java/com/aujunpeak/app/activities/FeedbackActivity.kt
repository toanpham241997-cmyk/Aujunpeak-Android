package com.aujunpeak.app.activities

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aujunpeak.app.databinding.ActivityFeedbackBinding
import com.aujunpeak.app.utils.KeyManager
import com.aujunpeak.app.utils.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class FeedbackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeedbackBinding
    private val handler = Handler(Looper.getMainLooper())
    private var selectedType = "bug"   // bug | feedback | contact
    private var selectedStars = 0
    private val stars: List<ImageView> by lazy {
        listOf(binding.star1, binding.star2, binding.star3, binding.star4, binding.star5)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupParticles()
        setupBackButton()
        setupTypChips()
        setupStars()
        setupSubmitButton()
        setupSuccessClose()
        animateEntrance()
        startHeroGlow()
    }

    // ── Particles ────────────────────────────────────────────────────────────
    private fun setupParticles() {
        binding.feedbackParticles.setColors(intArrayOf(
            Color.parseColor("#00E5FF"),
            Color.parseColor("#69FF47"),
            Color.parseColor("#AA00FF")
        ))
        binding.feedbackParticles.setDensity(18)
        binding.feedbackParticles.start()

        binding.heroParticles.setColors(intArrayOf(
            Color.parseColor("#00E5FF"),
            Color.parseColor("#FFFFFF")
        ))
        binding.heroParticles.setDensity(10)
        binding.heroParticles.start()
    }

    // ── Back button ──────────────────────────────────────────────────────────
    private fun setupBackButton() {
        binding.btnFeedbackBack.setOnClickListener {
            it.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f)
                    .setDuration(160).setInterpolator(OvershootInterpolator(2f))
                    .withEndAction { finish() }.start()
            }.start()
        }
    }

    // ── Type chips ───────────────────────────────────────────────────────────
    private fun setupTypChips() {
        selectChip("bug") // default

        binding.chipBug.setOnClickListener {
            bounceView(it)
            selectChip("bug")
        }
        binding.chipFeedback.setOnClickListener {
            bounceView(it)
            selectChip("feedback")
        }
        binding.chipContact.setOnClickListener {
            bounceView(it)
            selectChip("contact")
        }
    }

    private fun selectChip(type: String) {
        selectedType = type
        val chips = listOf(
            Triple(binding.chipBug, Triple(binding.chipBugText, binding.chipBugIcon, binding.chipBugCheck), "bug"),
            Triple(binding.chipFeedback, Triple(binding.chipFeedbackText, binding.chipFeedbackIcon, binding.chipFeedbackCheck), "feedback"),
            Triple(binding.chipContact, Triple(binding.chipContactText, binding.chipContactIcon, binding.chipContactCheck), "contact")
        )
        chips.forEach { (chip, views, chipType) ->
            val (label, icon, check) = views
            if (chipType == type) {
                chip.setBackgroundResource(com.aujunpeak.app.R.drawable.bg_chip_type_selected)
                label.setTextColor(Color.WHITE)
                icon.alpha = 1f
                check.visibility = View.VISIBLE
                check.scaleX = 0f; check.scaleY = 0f
                check.animate().scaleX(1f).scaleY(1f)
                    .setDuration(260).setInterpolator(OvershootInterpolator(3f)).start()
            } else {
                chip.setBackgroundResource(com.aujunpeak.app.R.drawable.bg_chip_type)
                label.setTextColor(Color.parseColor("#AAFFFFFF"))
                icon.alpha = 0.7f
                check.visibility = View.GONE
            }
        }
    }

    // ── Star rating ──────────────────────────────────────────────────────────
    private fun setupStars() {
        stars.forEachIndexed { idx, star ->
            star.setOnClickListener {
                selectedStars = idx + 1
                updateStars()
                bounceView(star)
            }
        }
    }

    private fun updateStars() {
        stars.forEachIndexed { idx, star ->
            val filled = idx < selectedStars
            star.setImageResource(
                if (filled) com.aujunpeak.app.R.drawable.ic_star_glow
                else com.aujunpeak.app.R.drawable.ic_star_glow_outline
            )
            star.alpha = 1f
            if (filled) {
                star.scaleX = 0.6f; star.scaleY = 0.6f
                star.animate().scaleX(1f).scaleY(1f)
                    .setDuration(280).setInterpolator(OvershootInterpolator(4f)).start()
            }
        }
        binding.tvStarsHint.text = when (selectedStars) {
            0 -> "Chạm vào ngôi sao để đánh giá"
            1 -> "Rất tệ 😞"
            2 -> "Chưa hài lòng 😐"
            3 -> "Bình thường 🙂"
            4 -> "Hài lòng 😄"
            else -> "Tuyệt vời! 🤩"
        }
        // Glow pulse behind the star row whenever a rating is picked
        if (selectedStars > 0) {
            binding.starsGlow.animate().cancel()
            binding.starsGlow.alpha = 0.9f
            binding.starsGlow.animate().alpha(0f).setDuration(600).start()
        }
    }

    // ── Submit button ────────────────────────────────────────────────────────
    private fun setupSubmitButton() {
        binding.btnSubmitFeedback.setOnClickListener {
            bounceView(it)
            val title = binding.etFeedbackTitle.text.toString().trim()
            val message = binding.etFeedbackMessage.text.toString().trim()
            val contact = binding.etFeedbackContact.text.toString().trim()

            if (title.isEmpty()) {
                showToast("Vui lòng nhập tiêu đề!")
                binding.etFeedbackTitle.requestFocus()
                return@setOnClickListener
            }
            if (message.isEmpty()) {
                showToast("Vui lòng nhập nội dung phản hồi!")
                binding.etFeedbackMessage.requestFocus()
                return@setOnClickListener
            }

            binding.btnSendIcon.visibility = View.GONE
            binding.btnSendText.visibility = View.GONE
            binding.progressSubmit.visibility = View.VISIBLE
            binding.btnSubmitFeedback.isClickable = false

            val deviceKey = KeyManager.getKey(this)
            lifecycleScope.launch {
                val success = sendFeedback(
                    type = selectedType,
                    title = title,
                    message = message,
                    contact = contact,
                    stars = selectedStars,
                    deviceKey = deviceKey
                )
                withContext(Dispatchers.Main) {
                    binding.progressSubmit.visibility = View.GONE
                    binding.btnSendIcon.visibility = View.VISIBLE
                    binding.btnSendText.visibility = View.VISIBLE
                    binding.btnSubmitFeedback.isClickable = true

                    if (success) {
                        showSuccessOverlay()
                    } else {
                        showToast("Gửi thất bại. Kiểm tra kết nối mạng!")
                    }
                }
            }
        }
    }

    private suspend fun sendFeedback(
        type: String,
        title: String,
        message: String,
        contact: String,
        stars: Int,
        deviceKey: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val apiBase = "https://aujunpeak-api.onrender.com"
            val url = URL("$apiBase/api/feedback")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            val body = JSONObject().apply {
                put("type", type)
                put("title", title)
                put("message", message)
                put("contact", contact)
                put("stars", stars)
                put("deviceKey", deviceKey)
            }.toString()

            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }

    // ── Success modal ─────────────────────────────────────────────────────────
    private fun showSuccessOverlay() {
        try {
            binding.successModalCard.isHapticFeedbackEnabled = true
            binding.successModalCard.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        } catch (_: Exception) {}

        binding.layoutSuccess.alpha = 0f
        binding.layoutSuccess.visibility = View.VISIBLE
        binding.layoutSuccess.animate().alpha(1f).setDuration(220).start()

        // Compact card pops in with a bouncy scale
        binding.successModalCard.scaleX = 0.72f
        binding.successModalCard.scaleY = 0.72f
        binding.successModalCard.alpha = 0f
        binding.successModalCard.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(420).setStartDelay(60)
            .setInterpolator(OvershootInterpolator(1.6f))
            .start()

        // Tick badge pops slightly after the card lands
        binding.successTickIcon.scaleX = 0f
        binding.successTickIcon.scaleY = 0f
        binding.successTickIcon.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(380).setStartDelay(280)
            .setInterpolator(OvershootInterpolator(3f))
            .start()

        // Ambient glow breathing behind the tick
        ObjectAnimator.ofFloat(binding.successIconGlow, "scaleX", 1f, 1.25f, 1f).apply {
            duration = 1600; repeatCount = ValueAnimator.INFINITE; start()
        }
        ObjectAnimator.ofFloat(binding.successIconGlow, "scaleY", 1f, 1.25f, 1f).apply {
            duration = 1600; repeatCount = ValueAnimator.INFINITE; start()
        }
    }

    private fun setupSuccessClose() {
        binding.btnSuccessClose.setOnClickListener {
            bounceView(it)
            binding.successModalCard.animate()
                .scaleX(0.85f).scaleY(0.85f).alpha(0f)
                .setDuration(180).start()
            binding.layoutSuccess.animate()
                .alpha(0f).setDuration(220).setStartDelay(60)
                .withEndAction { finish() }
                .start()
        }
    }

    // ── Hero glow pulse ──────────────────────────────────────────────────────
    private fun startHeroGlow() {
        ObjectAnimator.ofFloat(binding.heroIconGlow, "scaleX", 1f, 1.3f, 1f).apply {
            duration = 2200; repeatCount = ValueAnimator.INFINITE; start()
        }
        ObjectAnimator.ofFloat(binding.heroIconGlow, "scaleY", 1f, 1.3f, 1f).apply {
            duration = 2200; repeatCount = ValueAnimator.INFINITE; start()
        }
        ObjectAnimator.ofFloat(binding.heroIconGlow, "alpha", 0.5f, 0.9f, 0.5f).apply {
            duration = 2200; repeatCount = ValueAnimator.INFINITE; start()
        }
    }

    // ── Entrance animation ───────────────────────────────────────────────────
    private fun animateEntrance() {
        val container = binding.feedbackContainer
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            child.alpha = 0f
            child.translationY = 50f
            child.animate()
                .alpha(1f).translationY(0f)
                .setDuration(440).setStartDelay((i * 80).toLong())
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        }
    }

    private fun bounceView(v: View) {
        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction {
            v.animate().scaleX(1f).scaleY(1f)
                .setDuration(200).setInterpolator(OvershootInterpolator(2.5f)).start()
        }.start()
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            binding.feedbackParticles.stop()
            binding.heroParticles.stop()
        } catch (_: Exception) {}
    }
}
