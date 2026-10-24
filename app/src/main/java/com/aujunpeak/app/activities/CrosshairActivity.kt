package com.aujunpeak.app.activities

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.aujunpeak.app.utils.LocaleHelper
import com.aujunpeak.app.R
import com.aujunpeak.app.views.BoostParticleView
import com.bumptech.glide.Glide
import com.aujunpeak.app.views.CrosshairOverlayView
import com.aujunpeak.app.utils.UiModal

class CrosshairActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }



    private lateinit var btnBack            : LinearLayout
    private lateinit var btnApplyCrosshair  : LinearLayout
    private lateinit var btnToggleCrosshair : LinearLayout
    private lateinit var tvToggleLabel      : TextView
    private lateinit var tvToggleHint       : TextView
    private lateinit var cardSettings       : LinearLayout
    private lateinit var sliderX            : SeekBar
    private lateinit var sliderY            : SeekBar
    private lateinit var sliderSize         : SeekBar
    private lateinit var sliderRotation     : SeekBar
    private lateinit var sliderPulse        : SeekBar
    private lateinit var tvSliderX          : TextView
    private lateinit var tvSliderY          : TextView
    private lateinit var tvSliderSize       : TextView
    private lateinit var tvSliderRotation   : TextView
    private lateinit var tvSliderPulse      : TextView
    private lateinit var chParticles        : BoostParticleView
    private lateinit var crosshairBgGif     : ImageView

    private data class ChDef(val type: CrosshairOverlayView.CrosshairType, val icon: Int, val label: String)

    // 6 basic + 6 advanced = 12 crosshair skins in total.
    private val basicDefs = listOf(
        ChDef(CrosshairOverlayView.CrosshairType.DOT, R.drawable.ic_crosshair_dot, "CHẤM"),
        ChDef(CrosshairOverlayView.CrosshairType.CROSS, R.drawable.ic_crosshair_cross, "THẬP"),
        ChDef(CrosshairOverlayView.CrosshairType.CIRCLE, R.drawable.ic_crosshair_circle, "VÒNG"),
        ChDef(CrosshairOverlayView.CrosshairType.DIAMOND, R.drawable.ic_crosshair_diamond, "KIM CƯƠNG"),
        ChDef(CrosshairOverlayView.CrosshairType.TARGET, R.drawable.ic_crosshair_target, "MỤC TIÊU"),
        ChDef(CrosshairOverlayView.CrosshairType.T_SHAPE, R.drawable.ic_crosshair_t, "CHỮ T")
    )
    private val advancedDefs = listOf(
        ChDef(CrosshairOverlayView.CrosshairType.PLUS, R.drawable.ic_crosshair_plus, "DẤU CỘNG"),
        ChDef(CrosshairOverlayView.CrosshairType.HOLLOW_SQUARE, R.drawable.ic_crosshair_square, "Ô VUÔNG"),
        ChDef(CrosshairOverlayView.CrosshairType.TRIPLE_DOT, R.drawable.ic_crosshair_triple, "4 CHẤM"),
        ChDef(CrosshairOverlayView.CrosshairType.ARROW, R.drawable.ic_crosshair_arrow, "MŨI TÊN"),
        ChDef(CrosshairOverlayView.CrosshairType.CHEVRON, R.drawable.ic_crosshair_chevron, "CHEVRON"),
        ChDef(CrosshairOverlayView.CrosshairType.HAIRLINE, R.drawable.ic_crosshair_hairline, "SỢI MẢNH")
    )
    private val allDefs = basicDefs + advancedDefs

    private val typeViews = mutableListOf<LinearLayout>()

    private var selectedType = CrosshairOverlayView.CrosshairType.DOT
    private var isOverlayOn  = false
    private var settingsShown = false

    // Modern permission launcher — replaces deprecated startActivityForResult
    private val overlayPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            isOverlayOn = true
            updateToggleUI()
            startCrosshairService()
            UiModal.show(this, "Đã cấp quyền! Tâm súng đang hiển thị.", title = "Thành công", type = UiModal.Type.SUCCESS, autoDismissMillis = 1500L)
        } else {
            UiModal.show(this, "Chưa cấp quyền, tâm súng không thể hiển thị.", title = "Thiếu quyền", type = UiModal.Type.WARNING)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crosshair)

        btnBack            = findViewById(R.id.btnBack)
        btnApplyCrosshair  = findViewById(R.id.btnApplyCrosshair)
        btnToggleCrosshair = findViewById(R.id.btnToggleCrosshair)
        tvToggleLabel      = findViewById(R.id.tvToggleLabel)
        tvToggleHint       = findViewById(R.id.tvToggleHint)
        cardSettings       = findViewById(R.id.cardSettings)
        sliderX            = findViewById(R.id.sliderX)
        sliderY            = findViewById(R.id.sliderY)
        sliderSize         = findViewById(R.id.sliderSize)
        sliderRotation     = findViewById(R.id.sliderRotation)
        sliderPulse        = findViewById(R.id.sliderPulse)
        tvSliderX          = findViewById(R.id.tvSliderX)
        tvSliderY          = findViewById(R.id.tvSliderY)
        tvSliderSize       = findViewById(R.id.tvSliderSize)
        tvSliderRotation   = findViewById(R.id.tvSliderRotation)
        tvSliderPulse      = findViewById(R.id.tvSliderPulse)
        chParticles        = findViewById(R.id.chParticles)
        crosshairBgGif     = findViewById(R.id.crosshairBgGif)

        loadGifBackground()
        chParticles.setIdleMode(true)
        chParticles.start()

        buildTypeGrid()
        selectType(0)

        btnBack.setOnClickListener            { finish() }
        btnApplyCrosshair.setOnClickListener  { applyType() }
        btnToggleCrosshair.setOnClickListener { toggleOverlay() }

        setupSliders()
        animateEntranceIn()
    }

    /** Builds the 12-skin selector grid (4 columns) with a rotating orbit ring per card. */
    private fun buildTypeGrid() {
        val basicContainer = findViewById<LinearLayout>(R.id.containerChBasic)
        val advancedContainer = findViewById<LinearLayout>(R.id.containerChAdvanced)
        val inflater = LayoutInflater.from(this)
        val columns = 3

        fun buildSection(defs: List<ChDef>, container: LinearLayout, indexOffset: Int) {
            defs.chunked(columns).forEachIndexed { rowIdx, rowDefs ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                rowDefs.forEachIndexed { colIdx, def ->
                    val globalIndex = indexOffset + rowIdx * columns + colIdx
                    val card = inflater.inflate(R.layout.item_crosshair_type_card, row, false) as LinearLayout
                    card.findViewById<ImageView>(R.id.ivChCardIcon).setImageResource(def.icon)
                    card.findViewById<TextView>(R.id.tvChCardLabel).text = def.label
                    spinForever(card.findViewById(R.id.vChCardRing), 3400L + globalIndex * 220L)
                    card.setOnClickListener { selectType(globalIndex) }
                    typeViews.add(card)
                    row.addView(card)
                }
                container.addView(row)
            }
        }

        buildSection(basicDefs, basicContainer, 0)
        buildSection(advancedDefs, advancedContainer, basicDefs.size)
    }

    private fun spinForever(view: View, durationMs: Long) {
        ObjectAnimator.ofFloat(view, "rotation", 0f, 360f).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun animateEntranceIn() {
        typeViews.forEachIndexed { i, v ->
            v.alpha = 0f; v.translationY = 40f
            v.animate().alpha(1f).translationY(0f)
                .setDuration(400).setStartDelay((i * 60 + 200).toLong())
                .setInterpolator(OvershootInterpolator(1f)).start()
        }
        btnApplyCrosshair.alpha = 0f
        btnApplyCrosshair.animate().alpha(1f).setDuration(400).setStartDelay(700).start()
    }

    private fun selectType(index: Int) {
        selectedType = allDefs[index].type
        typeViews.forEachIndexed { i, v ->
            v.setBackgroundResource(
                if (i == index) R.drawable.bg_crosshair_card_glow
                else            R.drawable.bg_crosshair_card_glass
            )
            v.animate()
                .scaleX(if (i == index) 1.06f else 1f)
                .scaleY(if (i == index) 1.06f else 1f)
                .setDuration(200).start()
        }
    }

    private fun applyType() {
        btnApplyCrosshair.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction {
            btnApplyCrosshair.animate().scaleX(1f).scaleY(1f)
                .setDuration(200).setInterpolator(OvershootInterpolator(2f)).start()
        }.start()

        if (!settingsShown) {
            settingsShown = true
            cardSettings.visibility = View.VISIBLE
            cardSettings.alpha = 0f; cardSettings.translationY = 60f
            cardSettings.animate().alpha(1f).translationY(0f)
                .setDuration(500).setInterpolator(OvershootInterpolator(1.2f)).start()

            btnToggleCrosshair.visibility = View.VISIBLE
            btnToggleCrosshair.alpha = 0f
            btnToggleCrosshair.animate().alpha(1f).setDuration(400).setStartDelay(200).start()

            tvToggleHint.visibility = View.VISIBLE
            tvToggleHint.alpha = 0f
            tvToggleHint.animate().alpha(1f).setDuration(400).setStartDelay(300).start()
        }

        UiModal.show(this, "Đã áp dụng: ${selectedType.name}", title = "Crosshair đã cập nhật", type = UiModal.Type.SUCCESS, autoDismissMillis = 1500L)
        updateOverlayIfRunning()
    }

    private fun setupSliders() {
        sliderX.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val offset = progress - 100
                tvSliderX.text = if (offset >= 0) "+$offset" else "$offset"
                updateOverlayIfRunning()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        sliderY.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val offset = progress - 100
                tvSliderY.text = if (offset >= 0) "+$offset" else "$offset"
                updateOverlayIfRunning()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        sliderSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSliderSize.text = "${progress + 20}px"
                updateOverlayIfRunning()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        sliderRotation.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSliderRotation.text = if (progress == 0) "OFF" else "${progress}%"
                updateOverlayIfRunning()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        sliderPulse.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSliderPulse.text = if (progress == 0) "OFF" else "${progress}%"
                updateOverlayIfRunning()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        tvSliderX.text = "0"
        tvSliderY.text = "0"
        tvSliderSize.text = "70px"
        tvSliderRotation.text = "OFF"
        tvSliderPulse.text = "35%"
    }

    private fun toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            UiModal.show(this, "Cần cấp quyền hiển thị trên ứng dụng khác", title = "Cần cấp quyền", type = UiModal.Type.WARNING)
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermLauncher.launch(intent)
            return
        }
        isOverlayOn = !isOverlayOn
        updateToggleUI()
        if (isOverlayOn) startCrosshairService() else stopCrosshairService()
    }

    private fun updateToggleUI() {
        if (isOverlayOn) {
            btnToggleCrosshair.setBackgroundResource(R.drawable.bg_crosshair_toggle_on)
            tvToggleLabel.text = getString(R.string.crosshair_disable)
            btnToggleCrosshair.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).withEndAction {
                btnToggleCrosshair.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
            }.start()
        } else {
            btnToggleCrosshair.setBackgroundResource(R.drawable.bg_crosshair_toggle_off)
            tvToggleLabel.text = getString(R.string.crosshair_enable)
        }
    }

    private fun startCrosshairService() {
        val intent = Intent(this, FloatingCrosshairService::class.java).apply {
            putExtra(FloatingCrosshairService.EXTRA_TYPE,     selectedType.name)
            putExtra(FloatingCrosshairService.EXTRA_SIZE,     (sliderSize.progress + 20).toFloat())
            putExtra(FloatingCrosshairService.EXTRA_OFFSET_X, (sliderX.progress - 100).toFloat())
            putExtra(FloatingCrosshairService.EXTRA_OFFSET_Y, (sliderY.progress - 100).toFloat())
            putExtra(FloatingCrosshairService.EXTRA_ROTATION_SPEED, sliderRotation.progress.toFloat())
            putExtra(FloatingCrosshairService.EXTRA_PULSE, sliderPulse.progress.toFloat())
        }
        startService(intent)
    }

    private fun stopCrosshairService() = stopService(Intent(this, FloatingCrosshairService::class.java))

    private fun updateOverlayIfRunning() { if (isOverlayOn) startCrosshairService() }

    private fun loadGifBackground() {
        try {
            Glide.with(this)
                .asGif()
                .load(R.raw.bg_app_photo)
                .into(crosshairBgGif)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        chParticles.stop()
        try { Glide.with(this).clear(crosshairBgGif) } catch (_: Exception) {}
    }
}