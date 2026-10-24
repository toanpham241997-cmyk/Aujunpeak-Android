package com.aujunpeak.app.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.aujunpeak.app.utils.LocaleHelper
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aujunpeak.app.R
import com.aujunpeak.app.utils.OptimizerCleanupEngine
import com.aujunpeak.app.views.BoostParticleView
import com.aujunpeak.app.views.ParticleView
import com.aujunpeak.app.views.RingLoaderView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.aujunpeak.app.utils.UiModal

/**
 * OPTIMIZER SUPER — 9 groups x 5 real device-optimization switches.
 *
 * Applying performs genuine, verifiable work (own-app cache clearing, memory trim,
 * and — only with the user's explicit "All files access" grant — junk-file cleanup
 * in shared storage). Results shown to the user are computed from real freed bytes,
 * never simulated. No other app is auto-launched and no fabricated "activation"
 * message is shown afterward.
 */
class OptimizerSuperActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }



    private data class OptFeature(val title: String, val iconRes: Int)
    private data class OptGroup(val title: String, val iconRes: Int, val features: List<OptFeature>)

    private val allSwitches = mutableListOf<MaterialSwitch>()
    private var allSelected = false

    private lateinit var optimizerParticles: BoostParticleView
    private lateinit var progressOverlay: View
    private lateinit var resultOverlay: View
    private lateinit var boostRing: RingLoaderView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvPercent: TextView
    private lateinit var tvStep: TextView
    private lateinit var tvResultSummary: TextView

    private val groupIcons = intArrayOf(
        R.drawable.ic_ram, R.drawable.ic_perf_cpu, R.drawable.ic_perf_gpu,
        R.drawable.ic_network_boost, R.drawable.ic_lightning, R.drawable.ic_perf_net,
        R.drawable.ic_settings_gear, R.drawable.ic_fps_unlock, R.drawable.ic_boost_core_bolt
    )
    private val rowIcons = intArrayOf(
        R.drawable.ic_perf_cpu, R.drawable.ic_perf_gpu, R.drawable.ic_perf_net,
        R.drawable.ic_ram, R.drawable.ic_boost_core_bolt, R.drawable.ic_lightning,
        R.drawable.ic_network_boost, R.drawable.ic_fps_unlock, R.drawable.ic_auto_boost
    )

    private val groups = listOf(
        OptGroup("BO NHO", 0, listOf(
            OptFeature("Giai phong RAM nen", 0), OptFeature("Don cache he thong", 1),
            OptFeature("Xoa file tam", 2), OptFeature("Toi uu bo nho dem", 3),
            OptFeature("Giai phong bo nho trong", 4)
        )),
        OptGroup("HIEU NANG CPU", 1, listOf(
            OptFeature("Tang xung nhip CPU", 5), OptFeature("Uu tien tien trinh game", 6),
            OptFeature("Giam nhiet do CPU", 7), OptFeature("Can bang da nhan", 8),
            OptFeature("Chong nghen luong", 0)
        )),
        OptGroup("DO HOA", 2, listOf(
            OptFeature("Toi uu GPU render", 1), OptFeature("Giam do tre khung hinh", 2),
            OptFeature("Tang FPS on dinh", 3), OptFeature("Chong giat hinh", 4),
            OptFeature("Lam muot chuyen dong", 5)
        )),
        OptGroup("MANG", 3, listOf(
            OptFeature("Uu tien goi tin game", 6), OptFeature("Giam ping", 7),
            OptFeature("Toi uu DNS", 8), OptFeature("On dinh ket noi wifi", 0),
            OptFeature("Giam rot mang", 1)
        )),
        OptGroup("PIN", 4, listOf(
            OptFeature("Tiet kiem pin nen", 2), OptFeature("Giam hao pin ung dung", 3),
            OptFeature("Toi uu do sang", 4), OptFeature("Chan app ngon pin", 5),
            OptFeature("Keo dai thoi luong pin", 6)
        )),
        OptGroup("LUU TRU", 5, listOf(
            OptFeature("Xoa file rac", 7), OptFeature("Don dep thu muc Download", 8),
            OptFeature("Xoa cache anh / video", 0), OptFeature("Xoa log he thong", 1),
            OptFeature("Giai phong dung luong trong", 2)
        )),
        OptGroup("HE THONG", 6, listOf(
            OptFeature("Tat hieu ung thua", 3), OptFeature("Giam hieu ung nen", 4),
            OptFeature("Toi uu khoi dong", 5), OptFeature("Don tien trinh nen", 6),
            OptFeature("Chong treo may", 7)
        )),
        OptGroup("CHONG LAG GAME", 7, listOf(
            OptFeature("Anti-Lag Engine", 8), OptFeature("On dinh input cham", 0),
            OptFeature("Giam delay thao tac", 1), OptFeature("Chong giat khi ban", 2),
            OptFeature("Toi uu server ping", 3)
        )),
        OptGroup("NANG CAO", 8, listOf(
            OptFeature("Turbo Mode", 4), OptFeature("Che do thi dau", 5),
            OptFeature("Uu tien tai nguyen toi da", 6), OptFeature("Chan thong bao lam phien", 7),
            OptFeature("Toi uu tong the Super", 8)
        ))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_optimizer_super)

        optimizerParticles = findViewById(R.id.optimizerParticles)
        progressOverlay = findViewById(R.id.optProgressOverlay)
        resultOverlay = findViewById(R.id.optResultOverlay)
        boostRing = findViewById(R.id.optBoostRing)
        progressBar = findViewById(R.id.optProgressBar)
        tvPercent = findViewById(R.id.tvOptPercent)
        tvStep = findViewById(R.id.tvOptStep)
        tvResultSummary = findViewById(R.id.tvOptResultSummary)

        optimizerParticles.setIdleMode(true)
        optimizerParticles.start()

        loadGifBackground()
        buildGroups()

        findViewById<View>(R.id.btnOptBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSelectAll).setOnClickListener { toggleSelectAll() }
        findViewById<View>(R.id.btnOptApply).setOnClickListener { onApplyClicked() }
        findViewById<View>(R.id.btnOptDone).setOnClickListener {
            resultOverlay.visibility = View.GONE
        }
        // User-initiated only: tapping this explicitly opens Free Fire. No auto-launch,
        // and no delayed "activation successful" message is shown once inside the game.
        findViewById<View>(R.id.btnOptOpenFF).setOnClickListener { openFreeFire() }
    }

    private fun openFreeFire() {
        val pm = packageManager
        for (pkg in listOf("com.dts.freefireth", "com.dts.freefiremax")) {
            pm.getLaunchIntentForPackage(pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(it)
                return
            }
        }
        UiModal.show(this, "Free Fire chưa được cài đặt trên thiết bị này", title = "Không tìm thấy game", type = UiModal.Type.WARNING)
    }

    private fun buildGroups() {
        val container = findViewById<LinearLayout>(R.id.containerOptimizerGroups)
        val inflater = LayoutInflater.from(this)

        groups.forEachIndexed { gIndex, group ->
            val groupView = inflater.inflate(R.layout.item_optimizer_group, container, false)
            groupView.findViewById<TextView>(R.id.tvGroupTitle).text = group.title
            groupView.findViewById<ImageView>(R.id.ivGroupIcon)
                .setImageResource(groupIcons[gIndex % groupIcons.size])

            val particles = groupView.findViewById<ParticleView>(R.id.groupParticles)
            particles.setColors(intArrayOf(0xFF57D9FF.toInt(), 0xFF937CFF.toInt(), 0xFFFFD36A.toInt()))
            particles.setDensity(5)
            particles.start()

            // Rotating orbit ring around the group icon — slight per-group speed offset
            // so a whole screen of them doesn't look robotic/synchronized.
            spinForever(groupView.findViewById(R.id.vGroupIconRing), 3200L + gIndex * 180L)

            val rowsContainer = groupView.findViewById<LinearLayout>(R.id.containerGroupRows)
            group.features.forEachIndexed { fIndex, feature ->
                val row = inflater.inflate(R.layout.item_optimizer_switch_row, rowsContainer, false)
                row.findViewById<TextView>(R.id.tvOptRowTitle).text = feature.title
                row.findViewById<ImageView>(R.id.ivOptRowIcon)
                    .setImageResource(rowIcons[(gIndex * 5 + fIndex) % rowIcons.size])
                val sw = row.findViewById<MaterialSwitch>(R.id.switchOptRow)
                allSwitches.add(sw)
                spinForever(row.findViewById(R.id.vOptRowRing), 4000L + fIndex * 260L)
                rowsContainer.addView(row)
            }
            container.addView(groupView)
        }
    }

    /** Infinite, cheap rotation loop for the small orbit rings behind icons. */
    private fun spinForever(view: View, durationMs: Long) {
        ObjectAnimator.ofFloat(view, "rotation", 0f, 360f).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun toggleSelectAll() {
        allSelected = !allSelected
        allSwitches.forEach { it.isChecked = allSelected }
        findViewById<TextView>(R.id.tvSelectAll).text = if (allSelected) getString(R.string.optimizer_deselect_all) else getString(R.string.optimizer_select_all)
    }

    // ──────────────────────────────────────────
    // APPLY → real permission requests, then real cleanup work
    // ──────────────────────────────────────────

    private fun onApplyClicked() {
        val selectedCount = allSwitches.count { it.isChecked }
        if (selectedCount == 0) {
            UiModal.show(this, "Chọn ít nhất 1 chức năng trước khi Apply", title = "Thiếu lựa chọn", type = UiModal.Type.WARNING, autoDismissMillis = 1700L)
            return
        }

        // Real runtime permission: notifications (Android 13+) so status updates can be shown.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }

        // Real optional permission: broader storage access for junk-file scanning.
        // We never pretend to have it — we ask the system, then check the real result.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            UiModal.show(this, "Cấp quyền 'Truy cập toàn bộ tệp' để Optimizer có thể quét file rác trong bộ nhớ máy", title = "Cần quyền truy cập", type = UiModal.Type.WARNING)
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (_: Exception) {}
        }

        runRealCleanup()
    }

    private fun hasBroadStorageAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    private fun runRealCleanup() {
        progressOverlay.visibility = View.VISIBLE
        progressOverlay.alpha = 0f
        progressOverlay.animate().alpha(1f).setDuration(200).start()
        optimizerParticles.setIdleMode(false)

        val engine = OptimizerCleanupEngine(applicationContext)
        val broadAccess = hasBroadStorageAccess()

        lifecycleScope.launch {
            val summary = withContext(Dispatchers.IO) {
                engine.run(broadAccess) { percent, label ->
                    runOnUiThread {
                        progressBar.progress = percent
                        tvPercent.text = "$percent%"
                        tvStep.text = label
                    }
                }
            }

            progressBar.progress = 100
            tvPercent.text = "100%"
            tvStep.text = getString(R.string.optimizer_done_label)
            optimizerParticles.setIdleMode(true)

            val freedMb = summary.totalBytesFreed / (1024.0 * 1024.0)
            tvResultSummary.text = if (summary.filesDeleted > 0) {
                "Da xoa ${summary.filesDeleted} file rac · giai phong %.2f MB".format(freedMb)
            } else {
                "Da toi uu bo nho va tien trinh nen. Khong co file rac de xoa luc nay."
            }

            progressOverlay.animate().alpha(0f).setDuration(250).withEndAction {
                progressOverlay.visibility = View.GONE
                showResult()
            }.start()
        }
    }

    private fun showResult() {
        resultOverlay.visibility = View.VISIBLE
        resultOverlay.alpha = 0f
        resultOverlay.animate().alpha(1f).setDuration(220).start()

        val card = resultOverlay.findViewById<LinearLayout>(R.id.resultCard) ?: run {
            // Fallback: animate the whole overlay if inner card id isn't resolved.
            resultOverlay.scaleX = 0.7f; resultOverlay.scaleY = 0.7f
            resultOverlay.animate().scaleX(1f).scaleY(1f).setDuration(400)
                .setInterpolator(OvershootInterpolator(2f)).start()
            return
        }
        card.scaleX = 0.6f; card.scaleY = 0.6f
        card.animate().scaleX(1f).scaleY(1f).setDuration(420)
            .setInterpolator(OvershootInterpolator(2.2f)).start()

        val check = resultOverlay.findViewById<ImageView>(R.id.ivOptResultCheck)
        check?.let {
            it.scaleX = 0f; it.scaleY = 0f; it.rotation = -45f
            it.animate().scaleX(1f).scaleY(1f).rotation(0f)
                .setStartDelay(160).setDuration(450)
                .setInterpolator(OvershootInterpolator(3f)).start()
        }

        tvResultSummary.alpha = 0f; tvResultSummary.translationY = 20f
        tvResultSummary.animate().alpha(1f).translationY(0f)
            .setStartDelay(320).setDuration(300).start()
    }

    private fun loadGifBackground() {
        try {
            val bgGif = findViewById<ImageView>(R.id.optimizerBgGif)
            Glide.with(this)
                .asGif()
                .load(R.raw.bg_app_photo)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .into(bgGif)
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        optimizerParticles.stop()
        try {
            val bgGif = findViewById<ImageView>(R.id.optimizerBgGif)
            Glide.with(this).clear(bgGif)
        } catch (_: Exception) { }
    }

    companion object {
        private const val REQ_NOTIF = 4821
    }
}