package com.aujunpeak.app.fragments

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aujunpeak.app.R
import com.aujunpeak.app.activities.MainActivity
import com.aujunpeak.app.databinding.FragmentInfoBinding
import com.aujunpeak.app.utils.AppStats
import com.aujunpeak.app.utils.AvatarGenerator
import com.aujunpeak.app.utils.KeyManager
import com.aujunpeak.app.utils.LoginHistory
import com.aujunpeak.app.utils.UserProfile
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class InfoFragment : Fragment() {

    private var _binding: FragmentInfoBinding? = null
    private val binding get() = _binding!!

    private var vipRingAnim: ObjectAnimator? = null

    // ── Gallery picker ─────────────────────────────────────────────────────────
    private val galleryPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            // Grant persistent read permission
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            UserProfile.saveAvatarUri(requireContext(), uri.toString())
            loadAvatarIntoHeroView(uri.toString())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshState()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    /** Được gọi từ MainActivity khi navigate về tab này để force-refresh data */
    fun refresh() {
        if (_binding != null) refreshState()
    }

    private fun refreshState() {
        val ctx       = requireContext()
        val hasKey    = KeyManager.isLoggedIn(ctx)
        val hasGoogle = KeyManager.isGoogleLoggedIn(ctx)

        when {
            hasKey               -> showLoggedIn(hasGoogle)
            hasGoogle && !hasKey -> showGoogleOnly()
            else                 -> showNotLoggedIn()
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // STATE 1: NOT LOGGED IN
    // ─────────────────────────────────────────────────────────────────
    private fun showNotLoggedIn() {
        binding.layoutNotLoggedIn.visibility = View.VISIBLE
        binding.layoutLoggedIn.visibility    = View.GONE
        try { binding.layoutGoogleOnly.visibility = View.GONE } catch (_: Exception) {}

        try {
            binding.notLoggedParticles.setColors(intArrayOf(
                Color.parseColor("#57D9FF"),
                Color.parseColor("#937CFF"),
                Color.parseColor("#FF69B4"),
                Color.parseColor("#4B8DFF")
            ))
            binding.notLoggedParticles.setDensity(7)
            binding.notLoggedParticles.start()
        } catch (_: Exception) {}

        binding.btnGoToLogin.setOnClickListener {
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f)
                    .setDuration(200).setInterpolator(OvershootInterpolator(2f))
                    .withEndAction { findNavController().navigate(R.id.navigation_login) }
                    .start()
            }.start()
        }

        animateIn(binding.profileContainer)
    }

    // ─────────────────────────────────────────────────────────────────
    // STATE 2: GOOGLE ONLY
    // ─────────────────────────────────────────────────────────────────
    private fun showGoogleOnly() {
        binding.layoutNotLoggedIn.visibility = View.GONE
        binding.layoutLoggedIn.visibility    = View.GONE
        binding.layoutGoogleOnly.visibility  = View.VISIBLE

        val ctx  = requireContext()
        val name = KeyManager.getGoogleName(ctx)
        val email= KeyManager.getGoogleEmail(ctx)

        val displayName = name.ifEmpty { "Google User" }
        binding.tvGoogleOnlyName.text  = displayName
        binding.tvGoogleOnlyEmail.text = email

        AvatarGenerator.loadInto(displayName, binding.ivGoogleOnlyAvatar, 72)
        binding.ivGoogleOnlyAvatar.visibility            = View.VISIBLE
        binding.ivGoogleOnlyAvatarPlaceholder.visibility = View.GONE

        binding.btnGoogleOnlyGetKey.setOnClickListener {
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f)
                    .setDuration(200).setInterpolator(OvershootInterpolator(2f))
                    .withEndAction { findNavController().navigate(R.id.navigation_login) }
                    .start()
            }.start()
        }

        binding.btnGoogleOnlyLogout.setOnClickListener {
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(180).withEndAction {
                    KeyManager.clearGoogleAccount(requireContext())
                    refreshState()
                }.start()
            }.start()
        }

        animateIn(binding.profileContainer)
    }

    // ─────────────────────────────────────────────────────────────────
    // STATE 3: FULLY LOGGED IN
    // ─────────────────────────────────────────────────────────────────
    private fun showLoggedIn(hasGoogle: Boolean = false) {
        binding.layoutNotLoggedIn.visibility = View.GONE
        binding.layoutLoggedIn.visibility    = View.VISIBLE
        try { binding.layoutGoogleOnly.visibility = View.GONE } catch (_: Exception) {}
        try { binding.notLoggedParticles.stop() } catch (_: Exception) {}

        val ctx  = requireContext()
        val tier = KeyManager.getTier(ctx)
        val isVip = tier == "vip"

        // ── Avatar hero card ────────────────────────────────────────────────
        setupAvatarHero(ctx, isVip)

        // ── Google info card ───────────────────────────────────────────────
        if (hasGoogle) {
            val name  = KeyManager.getGoogleName(ctx)
            val email = KeyManager.getGoogleEmail(ctx)
            val displayName = name.ifEmpty { "Google User" }
            binding.cardGoogleInfo.visibility   = View.VISIBLE
            binding.cardNoGoogleInfo.visibility = View.GONE
            binding.tvGoogleInfoName.text       = displayName
            binding.tvGoogleInfoEmail.text      = email
            AvatarGenerator.loadInto(displayName, binding.ivGoogleInfoAvatar, 50)
            binding.ivGoogleInfoAvatar.visibility            = View.VISIBLE
            binding.ivGoogleInfoAvatarPlaceholder.visibility = View.GONE
        } else {
            binding.cardGoogleInfo.visibility   = View.GONE
            binding.cardNoGoogleInfo.visibility = View.VISIBLE
        }

        // ── Key fields ─────────────────────────────────────────────────────
        binding.etDisplayKey.setText(KeyManager.getKey(ctx))
        binding.etDisplayStatus.setText(KeyManager.getStatusText(ctx))
        binding.etDisplayExpiry.setText(KeyManager.getExpiryLabel(ctx))
        binding.etDisplayExpiryDate.setText(KeyManager.getExpiryDateString(ctx))
        val maxDev   = KeyManager.getMaxDevices(ctx)
        val devCount = KeyManager.getDeviceCount(ctx)
        binding.etDisplayDevices.setText("$devCount / $maxDev thiết bị")

        if (KeyManager.isKeyExpired(ctx)) {
            binding.etDisplayStatus.setTextColor(Color.parseColor("#FF5252"))
        } else {
            binding.etDisplayStatus.setTextColor(Color.parseColor("#69FF47"))
        }

        // ── Copy key button (shortcut below card) ─────────────────────────
        try {
            binding.root.findViewById<View>(R.id.btnCopyKeyShortcut)?.setOnClickListener {
                val key = KeyManager.getKey(ctx)
                copyToClipboard(ctx, "Key kích hoạt", key)
                showToast("Đã sao chép key!")
            }
        } catch (_: Exception) {}

        // ── Copy key button (inline icon inside field) ─────────────────────
        try {
            binding.root.findViewById<View>(R.id.btnCopyKeyInline)?.setOnClickListener { btn ->
                btn.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
                    btn.animate().scaleX(1f).scaleY(1f)
                        .setDuration(180).setInterpolator(OvershootInterpolator(2f)).start()
                }.start()
                val key = KeyManager.getKey(ctx)
                copyToClipboard(ctx, "Key kích hoạt", key)
                showToast("Đã sao chép key!")
            }
        } catch (_: Exception) {}

        // ── Expiry warning banner ──────────────────────────────────────────
        setupExpiryWarningBanner(ctx)

        // ── Usage progress ─────────────────────────────────────────────────
        setupProgressCard(ctx, isVip)

        // ── Stats card ─────────────────────────────────────────────────────
        setupStatsCard(ctx)

        // ── Login history ──────────────────────────────────────────────────
        setupLoginHistory(ctx)

        // ── Tier card ──────────────────────────────────────────────────────
        try {
            binding.cardTierVip.visibility  = if (isVip) View.VISIBLE else View.GONE
            binding.cardTierFree.visibility = if (!isVip) View.VISIBLE else View.GONE
        } catch (_: Exception) {}

        // ── Note card ─────────────────────────────────────────────────────
        val note = KeyManager.getNote(ctx)
        try {
            if (note.isNotBlank()) {
                binding.cardNote.visibility = View.VISIBLE
                binding.tvNoteContent.text  = note
            } else {
                binding.cardNote.visibility = View.GONE
            }
        } catch (_: Exception) {}

        // ── Particles ─────────────────────────────────────────────────────
        try {
            binding.profileCardParticles.setColors(intArrayOf(
                if (isVip) Color.parseColor("#FFD700") else Color.parseColor("#69FF47"),
                Color.parseColor("#00E676"),
                Color.parseColor("#FF1744")
            ))
            binding.profileCardParticles.setDensity(8)
            binding.profileCardParticles.start()
        } catch (_: Exception) {}

        // ── Logout button ─────────────────────────────────────────────────
        binding.btnLogout.setOnClickListener {
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f)
                    .setDuration(200).setInterpolator(OvershootInterpolator(2f)).start()
            }.start()
            viewLifecycleOwner.lifecycleScope.launch {
                KeyManager.sendLogout(ctx)
                KeyManager.logout(ctx)
                activity?.runOnUiThread {
                    (activity as? MainActivity)?.onLogout()
                    refreshState()
                }
            }
        }

        animateIn(binding.profileContainer)
    }

    // ── Avatar hero setup ──────────────────────────────────────────────────────
    private fun setupAvatarHero(ctx: Context, isVip: Boolean) {
        val root = binding.root

        // Nickname
        val displayName = UserProfile.getDisplayName(ctx)
        root.findViewById<TextView>(R.id.tvUserNickname)?.text = displayName

        // Account ID
        val accountId = UserProfile.getOrCreateAccountId(ctx)
        root.findViewById<TextView>(R.id.tvAccountIdValue)?.text = accountId

        // Avatar image
        val avatarView = root.findViewById<ImageView>(R.id.ivUserMainAvatar)
        loadAvatarIntoHeroView(UserProfile.getAvatarUri(ctx))

        // VIP / FREE ring
        val vipRing  = root.findViewById<ImageView>(R.id.ivAvatarVipRing)
        val freeRing = root.findViewById<ImageView>(R.id.ivAvatarFreeRing)
        val hasKey   = KeyManager.isLoggedIn(ctx)

        when {
            isVip && hasKey -> {
                vipRing?.visibility  = View.VISIBLE
                freeRing?.visibility = View.GONE
                startVipRingAnimation(vipRing)
            }
            hasKey -> {
                vipRing?.visibility  = View.GONE
                freeRing?.visibility = View.VISIBLE
                vipRingAnim?.cancel()
            }
            else -> {
                vipRing?.visibility  = View.GONE
                freeRing?.visibility = View.GONE
                vipRingAnim?.cancel()
            }
        }

        // Edit nickname
        root.findViewById<View>(R.id.btnEditNickname)?.setOnClickListener {
            showEditNicknameDialog(ctx)
        }

        // Change avatar
        root.findViewById<View>(R.id.btnChangeAvatar)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                         Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            galleryPicker.launch(intent)
        }

        // Copy account ID
        root.findViewById<View>(R.id.btnCopyAccountId)?.setOnClickListener {
            copyToClipboard(ctx, "Account ID", accountId)
            showToast("Đã sao chép ID!")
        }
    }

    private fun loadAvatarIntoHeroView(uriString: String) {
        val avatarView = _binding?.root?.findViewById<ImageView>(R.id.ivUserMainAvatar) ?: return
        val ctx = context ?: return

        val customUri = uriString.ifEmpty { UserProfile.getAvatarUri(ctx) }

        if (customUri.isNotEmpty()) {
            try {
                Glide.with(ctx)
                    .load(Uri.parse(customUri))
                    .transform(CircleCrop())
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .placeholder(R.drawable.ic_user_active)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            // Fallback to generated avatar
                            val name = UserProfile.getDisplayName(ctx)
                            avatarView.post { AvatarGenerator.loadInto(name, avatarView, 90) }
                            return true
                        }
                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ) = false
                    })
                    .into(avatarView)
                return
            } catch (_: Exception) {}
        }

        // Fallback: Google photo URL or generated avatar
        val googlePhoto = try { KeyManager.getGooglePhotoUrl(ctx) } catch (_: Exception) { "" }
        if (googlePhoto.isNotEmpty()) {
            Glide.with(ctx)
                .load(googlePhoto)
                .transform(CircleCrop())
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .placeholder(R.drawable.ic_user_active)
                .into(avatarView)
        } else {
            val name = UserProfile.getDisplayName(ctx)
            AvatarGenerator.loadInto(name, avatarView, 90)
        }
    }

    private fun startVipRingAnimation(ringView: ImageView?) {
        ringView ?: return
        vipRingAnim?.cancel()
        vipRingAnim = ObjectAnimator.ofFloat(ringView, "rotation", 0f, 360f).apply {
            duration       = 4000
            repeatCount    = ObjectAnimator.INFINITE
            repeatMode     = ObjectAnimator.RESTART
            interpolator   = LinearInterpolator()
            start()
        }
    }

    private fun showEditNicknameDialog(ctx: Context) {
        val current = UserProfile.getNickname(ctx)
        val et = EditText(ctx).apply {
            setText(current)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            hint = "Nhập nickname..."
            setSingleLine()
            background = null
            setPadding(48, 32, 48, 16)
        }
        AlertDialog.Builder(ctx)
            .setTitle("Đổi tên hiển thị")
            .setView(et)
            .setPositiveButton("Lưu") { _, _ ->
                val newName = et.text.toString().trim()
                UserProfile.saveNickname(ctx, newName)
                _binding?.root?.findViewById<TextView>(R.id.tvUserNickname)?.text =
                    UserProfile.getDisplayName(ctx)
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    // ── Expiry warning banner ──────────────────────────────────────────────────
    private fun setupExpiryWarningBanner(ctx: Context) {
        val cardView  = binding.root.findViewById<View>(R.id.cardExpiryWarning) ?: return
        val daysLeft  = KeyManager.getExpiryWarningDaysLeft(ctx)

        if (daysLeft != null) {
            cardView.visibility = View.VISIBLE
            binding.root.findViewById<TextView>(R.id.tvExpiryWarnDaysLeft)?.text =
                "Còn $daysLeft ngày — hãy gia hạn ngay!"
            binding.root.findViewById<View>(R.id.btnExpiryWarnRenew)?.setOnClickListener {
                openZaloRenew()
            }
        } else {
            cardView.visibility = View.GONE
        }
    }

    // ── Progress card ──────────────────────────────────────────────────────────
    private fun setupProgressCard(ctx: Context, isVip: Boolean) {
        val cardView = binding.root.findViewById<View>(R.id.cardKeyProgress) ?: return
        val expiryMs = KeyManager.getExpiryMs(ctx)
        val loginMs  = KeyManager.getLoginTimestampMs(ctx)

        if (expiryMs == 0L || loginMs == 0L) {
            cardView.visibility = View.GONE
            return
        }

        cardView.visibility = View.VISIBLE
        val now      = System.currentTimeMillis()
        val totalMs  = expiryMs - loginMs
        val usedMs   = now - loginMs
        val percent  = ((usedMs.toFloat() / totalMs) * 100).coerceIn(0f, 100f)
        val daysUsed = (usedMs / 86_400_000L).coerceAtLeast(0L)
        val daysLeft = ((expiryMs - now) / 86_400_000L).coerceAtLeast(0L)

        binding.root.findViewById<TextView>(R.id.tvProgressPercent)?.text =
            "${percent.toInt()}%"
        binding.root.findViewById<TextView>(R.id.tvProgressUsed)?.text =
            "Đã dùng: $daysUsed ngày"
        binding.root.findViewById<TextView>(R.id.tvProgressLeft)?.text =
            "Còn lại: $daysLeft ngày"

        // Animate progress bar fill
        val fillView = binding.root.findViewById<View>(R.id.viewProgressFill)
        fillView?.setBackgroundResource(
            if (isVip) R.drawable.bg_progress_key_fill_vip
            else R.drawable.bg_progress_key_fill_free
        )
        fillView?.doOnLayout { v ->
            val parent  = v.parent as? View ?: return@doOnLayout
            val targetW = (parent.width * percent / 100f).toInt()
            ValueAnimator.ofInt(0, targetW).apply {
                duration = 900
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val params = v.layoutParams
                    params.width = it.animatedValue as Int
                    v.layoutParams = params
                }
                start()
            }
        }
    }

    // ── Stats card ─────────────────────────────────────────────────────────────
    private fun setupStatsCard(ctx: Context) {
        binding.root.findViewById<TextView>(R.id.tvStatOpenCount)?.text =
            AppStats.getOpenCount(ctx).toString()
        binding.root.findViewById<TextView>(R.id.tvStatOptimizeCount)?.text =
            AppStats.getOptimizeCount(ctx).toString()
        binding.root.findViewById<TextView>(R.id.tvStatOnlineTime)?.text =
            AppStats.getOnlineTimeFormatted(ctx)
        binding.root.findViewById<TextView>(R.id.tvStatJoinDate)?.text =
            AppStats.getJoinDateFormatted(ctx)
    }

    // ── Login history ──────────────────────────────────────────────────────────
    private fun setupLoginHistory(ctx: Context) {
        val container = binding.root.findViewById<LinearLayout>(R.id.llLoginHistoryContainer) ?: return
        val emptyView = binding.root.findViewById<TextView>(R.id.tvLoginHistoryEmpty)
        container.removeAllViews()

        // Show up to 20 most recent entries; user scrolls inside the fixed-height container
        val history = LoginHistory.getAll(ctx).take(20)
        if (history.isEmpty()) {
            emptyView?.visibility = View.VISIBLE
            return
        }
        emptyView?.visibility = View.GONE

        val inflater = LayoutInflater.from(ctx)
        history.forEachIndexed { index, entry ->
            val itemView = inflater.inflate(R.layout.item_login_history, container, false)
            itemView.findViewById<TextView>(R.id.tvHistoryDevice)?.text =
                entry.deviceName.ifEmpty { "Thiết bị không xác định" }
            itemView.findViewById<TextView>(R.id.tvHistoryTime)?.text = entry.timeFormatted
            itemView.findViewById<TextView>(R.id.tvHistoryIp)?.text =
                entry.ip.ifEmpty { "N/A" }
            itemView.findViewById<TextView>(R.id.tvHistoryCountry)?.text =
                entry.country.ifEmpty { "N/A" }
            itemView.findViewById<TextView>(R.id.tvHistoryIndex)?.text =
                "#${history.size - index}"
            container.addView(itemView)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun copyToClipboard(ctx: Context, label: String, text: String) {
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun openZaloRenew() {
        val url = "https://zalo.me/0833091543"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {}
    }

    private fun animateIn(container: ViewGroup) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            child.alpha = 0f
            child.translationY = 40f
            child.animate()
                .alpha(1f).translationY(0f)
                .setDuration(420).setStartDelay((i * 110).toLong())
                .setInterpolator(OvershootInterpolator(1f))
                .start()
        }
    }

    override fun onDestroyView() {
        vipRingAnim?.cancel()
        try {
            _binding?.profileCardParticles?.stop()
            _binding?.notLoggedParticles?.stop()
        } catch (_: Exception) {}
        super.onDestroyView()
        _binding = null
    }
}
