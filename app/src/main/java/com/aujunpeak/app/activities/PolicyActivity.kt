package com.aujunpeak.app.activities

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.aujunpeak.app.R
import com.aujunpeak.app.databinding.ActivityPolicyBinding
import com.aujunpeak.app.utils.LocaleHelper

class PolicyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPolicyBinding
    private var activeTab = "privacy"  // privacy | terms | license

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPolicyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupParticles()
        setupBackButton()
        setupTabs()
        animateEntrance()
        startPolicyGlow()
    }

    // ── Particles ─────────────────────────────────────────────────────────────
    private fun setupParticles() {
        binding.policyParticles.setColors(intArrayOf(
            Color.parseColor("#FFD700"),
            Color.parseColor("#FF9800"),
            Color.parseColor("#69FF47")
        ))
        binding.policyParticles.setDensity(14)
        binding.policyParticles.start()

        binding.policyHeroParticles.setColors(intArrayOf(
            Color.parseColor("#FFD700"),
            Color.parseColor("#FFFFFF")
        ))
        binding.policyHeroParticles.setDensity(8)
        binding.policyHeroParticles.start()
    }

    // ── Back button ───────────────────────────────────────────────────────────
    private fun setupBackButton() {
        binding.btnPolicyBack.setOnClickListener {
            it.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f)
                    .setDuration(160).setInterpolator(OvershootInterpolator(2f))
                    .withEndAction { finish() }.start()
            }.start()
        }
    }

    // ── Tab switching ─────────────────────────────────────────────────────────
    private fun setupTabs() {
        selectTab("privacy")  // default

        binding.tabPrivacy.setOnClickListener {
            bounceView(it)
            selectTab("privacy")
        }
        binding.tabTerms.setOnClickListener {
            bounceView(it)
            selectTab("terms")
        }
        binding.tabLicense.setOnClickListener {
            bounceView(it)
            selectTab("license")
        }
    }

    private fun selectTab(tab: String) {
        activeTab = tab
        val tabs = listOf(
            "privacy" to binding.tabPrivacy,
            "terms" to binding.tabTerms,
            "license" to binding.tabLicense,
        )
        tabs.forEach { (id, tv) ->
            if (id == tab) {
                tv.setBackgroundResource(R.drawable.bg_floating_tab_active)
                tv.setTextColor(Color.WHITE)
            } else {
                tv.setBackgroundResource(android.R.color.transparent)
                tv.setTextColor(Color.parseColor("#80FFFFFF"))
            }
        }

        // Animate section transition
        val sections = listOf(
            "privacy" to binding.sectionPrivacy,
            "terms" to binding.sectionTerms,
            "license" to binding.sectionLicense,
        )
        sections.forEach { (id, view) ->
            if (id == tab) {
                view.visibility = View.VISIBLE
                view.alpha = 0f
                view.translationY = 20f
                view.animate().alpha(1f).translationY(0f)
                    .setDuration(280).setInterpolator(OvershootInterpolator(1f)).start()
            } else {
                view.visibility = View.GONE
            }
        }
    }

    // ── Glow pulse ────────────────────────────────────────────────────────────
    private fun startPolicyGlow() {
        ObjectAnimator.ofFloat(binding.policyGlow, "scaleX", 1f, 1.25f, 1f).apply {
            duration = 2600; repeatCount = ValueAnimator.INFINITE; start()
        }
        ObjectAnimator.ofFloat(binding.policyGlow, "scaleY", 1f, 1.25f, 1f).apply {
            duration = 2600; repeatCount = ValueAnimator.INFINITE; start()
        }
        ObjectAnimator.ofFloat(binding.policyGlow, "alpha", 0.5f, 0.85f, 0.5f).apply {
            duration = 2600; repeatCount = ValueAnimator.INFINITE; start()
        }
    }

    // ── Entrance animation ────────────────────────────────────────────────────
    private fun animateEntrance() {
        val container = binding.policyContainer
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            child.alpha = 0f
            child.translationY = 45f
            child.animate()
                .alpha(1f).translationY(0f)
                .setDuration(420).setStartDelay((i * 60).toLong())
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
            binding.policyParticles.stop()
            binding.policyHeroParticles.stop()
        } catch (_: Exception) {}
    }
}
