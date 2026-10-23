package com.aujunpeak.app.fragments

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aujunpeak.app.R
import com.aujunpeak.app.activities.MainActivity
import com.aujunpeak.app.databinding.FragmentLoginBinding
import com.aujunpeak.app.utils.KeyManager
import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())

    // ── Google Account Picker (AccountManager — không cần google-services.json) ─
    private val accountPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val email = result.data!!.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.trim() ?: ""
            if (email.isNotEmpty()) {
                onGoogleAccountSelected(email)
            } else {
                try { setStatusBadge("Không lấy được email. Thử lại!", "#FF5252") } catch (_: Exception) {}
            }
        }
        // RESULT_CANCELED = người dùng nhấn Back = không làm gì cả
    }

    private val URL_FACEBOOK = "https://www.facebook.com/huan.ha.748764"
    private val URL_ZALO     = "https://zalo.me/0833091543"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupParticles()
        setupRain()
        animateIn()
        setupButtons()
        updateGoogleButtonVisibility()
    }

    private fun setupParticles() {
        binding.loginHeaderParticles.setColors(intArrayOf(
            Color.parseColor("#57D9FF"),
            Color.parseColor("#937CFF"),
            Color.parseColor("#D7F6FF")
        ))
        binding.loginHeaderParticles.setDensity(9)
        binding.loginHeaderParticles.start()

        binding.loginCardParticles.setColors(intArrayOf(
            Color.parseColor("#937CFF"),
            Color.parseColor("#57D9FF"),
            Color.parseColor("#365CFF")
        ))
        binding.loginCardParticles.setDensity(7)
        binding.loginCardParticles.start()

        binding.fbParticles.setColors(intArrayOf(
            Color.parseColor("#1877F2"),
            Color.parseColor("#4DA3FF"),
            Color.parseColor("#0D5DB5")
        ))
        binding.fbParticles.setDensity(8)
        binding.fbParticles.start()

        binding.zaloParticles.setColors(intArrayOf(
            Color.parseColor("#0068FF"),
            Color.parseColor("#3399FF"),
            Color.parseColor("#004DB3")
        ))
        binding.zaloParticles.setDensity(8)
        binding.zaloParticles.start()
    }

    private fun setupRain() {
        binding.rainView.start()
    }

    private fun animateIn() {
        val container = binding.loginContainer
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            child.alpha = 0f
            child.translationY = 50f
            child.animate()
                .alpha(1f).translationY(0f)
                .setDuration(450).setStartDelay((i * 100).toLong())
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        }
        pulseIconRing()
    }

    private fun pulseIconRing() {
        val pulse  = ObjectAnimator.ofFloat(binding.iconGlowOuter, "scaleX", 1f, 1.08f, 1f).apply {
            duration = 1800; repeatCount = ObjectAnimator.INFINITE
        }
        val pulseY = ObjectAnimator.ofFloat(binding.iconGlowOuter, "scaleY", 1f, 1.08f, 1f).apply {
            duration = 1800; repeatCount = ObjectAnimator.INFINITE
        }
        AnimatorSet().apply { playTogether(pulse, pulseY); start() }
    }

    private fun setupButtons() {
        binding.btnLogin.setOnClickListener {
            hideKeyboard()
            handleLogin()
        }

        binding.tvBackBtn.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.cardFacebook.setOnClickListener {
            animateCardClick(it) { openUrl(URL_FACEBOOK) }
        }

        binding.cardZalo.setOnClickListener {
            animateCardClick(it) { openUrl(URL_ZALO) }
        }

        // Nút GET KEY FREE — gọi API lấy Link4m URL rồi mở thẳng trình duyệt
        binding.btnGetKeyFree.setOnClickListener {
            if (binding.btnGetKeyFree.alpha < 0.6f) return@setOnClickListener
            handleGetKeyFree()
        }

        // Nút đăng nhập Google
        binding.btnLoginGoogle.setOnClickListener {
            animateCardClick(it) { launchGoogleSignIn() }
        }
    }

    private fun handleGetKeyFree() {
        // Hiệu ứng nhấn
        binding.btnGetKeyFree.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90)
            .withEndAction {
                binding.btnGetKeyFree.animate().scaleX(1f).scaleY(1f)
                    .setDuration(200).setInterpolator(OvershootInterpolator(2.8f)).start()
            }.start()

        // Mở trang xác thực thiết bị trực tiếp — API xử lý Link4m server-side
        val deviceId   = KeyManager.getOrCreateDeviceId(requireContext())
        val deviceName = KeyManager.getDeviceName()
        val url = KeyManager.API_BASE_URL + "/keys/get-free-page" +
            "?deviceId=" + java.net.URLEncoder.encode(deviceId, "UTF-8") +
            "&deviceName=" + java.net.URLEncoder.encode(deviceName, "UTF-8")
        openUrl(url)
    }
    private fun animateCardClick(view: View, action: () -> Unit) {
        view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f)
                    .setDuration(200).setInterpolator(OvershootInterpolator(2.5f))
                    .withEndAction(action).start()
            }.start()
    }

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) updateGoogleButtonVisibility()
    }

    // ─────────────────────────────────────────────────────────────
    // GOOGLE SIGN-IN
    // ─────────────────────────────────────────────────────────────

    /** Ẩn/hiện nút Google tùy theo trạng thái đăng nhập */
    private fun updateGoogleButtonVisibility() {
        val isGoogleLoggedIn = KeyManager.isGoogleLoggedIn(requireContext())
        binding.btnLoginGoogle.visibility = if (isGoogleLoggedIn) View.GONE else View.VISIBLE
    }

    /** Mở Android system account picker — không cần google-services.json */
    private fun launchGoogleSignIn() {
        try {
            @Suppress("DEPRECATION")
            val intent = AccountManager.newChooseAccountIntent(
                null,                    // selectedAccount (không pre-select)
                null,                    // allowableAccounts (tất cả accounts)
                arrayOf("com.google"),   // chỉ hiển thị Google accounts
                null,                    // descriptionOverrideText
                null,                    // addAccountAuthTokenType
                null,                    // addAccountRequiredFeatures
                null                     // addAccountOptions
            )
            accountPickerLauncher.launch(intent)
        } catch (e: Exception) {
            // Thiết bị không có tài khoản Google nào
            setStatusBadge("Không tìm thấy tài khoản Google trên thiết bị", "#FF5252")
        }
    }

    /** Được gọi sau khi user chọn tài khoản Google từ system picker */
    private fun onGoogleAccountSelected(email: String) {
        // Tạo display name từ email (vd: "nguyen.van.a@gmail.com" → "Nguyen Van A")
        val namePart = email.substringBefore("@")
        val displayName = namePart
            .replace(".", " ")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
            .ifBlank { "Google User" }

        // Lưu vào SharedPreferences
        KeyManager.saveGoogleAccount(requireContext(), displayName, email, "")

        // Thông báo lên Discord bot (bất đồng bộ)
        viewLifecycleOwner.lifecycleScope.launch {
            KeyManager.sendGoogleLoginNotify(requireContext(), displayName, email)
        }

        // Cập nhật UI — ẩn nút Google ngay
        updateGoogleButtonVisibility()
        setStatusBadge("✓ Đã đăng nhập: $displayName", "#4CAF50")

        // Chuyển sang tab Hồ sơ ngay lập tức (MainActivity sẽ force-refresh InfoFragment)
        try {
            (activity as? com.aujunpeak.app.activities.MainActivity)?.navigateToProfileTab()
        } catch (_: Exception) {
            try { findNavController().navigate(R.id.navigation_info) } catch (_: Exception) {}
        }
    }

    // ──────────────────────────────────────────────
    // LOGIN LOGIC — Kết nối API thật
    // ──────────────────────────────────────────────

    private fun handleLogin() {
        val input = binding.etKeyInput.text?.toString()?.trim() ?: ""
        if (input.isEmpty()) {
            shakeInputFrame()
            binding.tvLoginStatus.text = getString(R.string.login_empty_key)
            return
        }

        setLoadingState(true)
        binding.tvLoginStatus.text = getString(R.string.login_validating)

        // Gọi API bất đồng bộ
        viewLifecycleOwner.lifecycleScope.launch {
            val result = KeyManager.validateKeyOnline(requireContext(), input.uppercase())
            if (_binding == null) return@launch

            handler.post {
                when (result) {
                    is KeyManager.ValidationResult.Ok   -> handleLoginSuccess(result.key)
                    is KeyManager.ValidationResult.Fail -> handleLoginFail(result.message)
                }
            }
        }
    }

    private fun handleLoginSuccess(key: String) {
        setLoadingState(false)
        showRobotIcon()
        binding.tvLoginStatus.text = getString(R.string.login_confirming)
        setStatusBadge("Đang kiểm tra", "#FF9800")
        animateIconBounce(binding.iconCircleFrame, scaleTarget = 1.12f, duration = 400L)

        handler.postDelayed({
            if (_binding == null) return@postDelayed

            showSuccessIcon()
            binding.tvLoginStatus.text = getString(R.string.login_success)
            binding.iconGlowOuter.setBackgroundResource(R.drawable.bg_key_circle_success)
            setStatusBadge("Đang hoạt động", "#69FF47")
            animateIconBounce(binding.iconCircleFrame, scaleTarget = 1.20f, duration = 500L)

            handler.postDelayed({
                if (_binding == null) return@postDelayed
                val mainActivity = activity as? MainActivity
                mainActivity?.onLoginSuccess()
                mainActivity?.navigateToProfileTab()
            }, 1000L)
        }, 1000L)
    }

    private fun handleLoginFail(message: String) {
        setLoadingState(false)
        binding.ivKeyIcon.setImageResource(R.drawable.ic_key_error)
        binding.iconGlowOuter.setBackgroundResource(R.drawable.bg_key_circle_error)
        binding.tvLoginStatus.text = message
        setStatusBadge("Lỗi xác thực", "#FF5252")
        shakeInputFrame()

        handler.postDelayed({
            if (_binding == null) return@postDelayed
            binding.ivKeyIcon.setImageResource(R.drawable.ic_key_lock)
            binding.iconGlowOuter.setBackgroundResource(R.drawable.bg_key_circle)
            binding.tvLoginStatus.text = getString(R.string.login_hint_text)
            setStatusBadge("Chưa kích hoạt", "#FFEE3333")
        }, 2500L)
    }

    private fun showRobotIcon() {
        binding.ivKeyIcon.visibility    = View.INVISIBLE
        binding.ivRobotIcon.visibility  = View.VISIBLE
        binding.ivRobotIcon.alpha       = 0f
        binding.ivRobotIcon.scaleX      = 0.5f
        binding.ivRobotIcon.scaleY      = 0.5f
        binding.ivRobotIcon.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(2f))
            .start()
    }

    private fun showSuccessIcon() {
        binding.ivRobotIcon.visibility  = View.INVISIBLE
        binding.ivKeyIcon.visibility    = View.VISIBLE
        binding.ivKeyIcon.setImageResource(R.drawable.ic_check_green)
        binding.ivKeyIcon.alpha         = 0f
        binding.ivKeyIcon.scaleX        = 0f
        binding.ivKeyIcon.scaleY        = 0f
        binding.ivKeyIcon.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(2.5f))
            .start()
    }

    private fun animateIconBounce(view: View, scaleTarget: Float, duration: Long) {
        val sx = ObjectAnimator.ofFloat(view, "scaleX", 1f, scaleTarget, 1f)
        val sy = ObjectAnimator.ofFloat(view, "scaleY", 1f, scaleTarget, 1f)
        AnimatorSet().apply {
            playTogether(sx, sy)
            this.duration = duration
            interpolator  = OvershootInterpolator(2f)
            start()
        }
    }

    private fun setStatusBadge(text: String, colorHex: String) {
        try {
            val badge  = binding.tvKeyBadge
            val textTv = badge.getChildAt(1) as? android.widget.TextView ?: return
            textTv.text = text
            textTv.setTextColor(Color.parseColor(colorHex))
        } catch (_: Exception) {}
    }

    private fun setLoadingState(loading: Boolean) {
        if (loading) {
            binding.progressLogin.visibility   = View.VISIBLE
            binding.tvLoginBtnText.visibility  = View.INVISIBLE
            binding.ivLoginBtnIcon.visibility  = View.INVISIBLE
            binding.ringLoaderLogin.visibility = View.VISIBLE
            binding.btnLogin.isEnabled         = false
            binding.ivKeyIcon.visibility       = View.INVISIBLE
            binding.ivRobotIcon.visibility     = View.VISIBLE
            binding.ivRobotIcon.alpha          = 1f
            binding.ivRobotIcon.scaleX         = 1f
            binding.ivRobotIcon.scaleY         = 1f
        } else {
            binding.progressLogin.visibility   = View.GONE
            binding.tvLoginBtnText.visibility  = View.VISIBLE
            binding.ivLoginBtnIcon.visibility  = View.VISIBLE
            binding.ringLoaderLogin.visibility = View.GONE
            binding.btnLogin.isEnabled         = true
        }
    }

    private fun shakeInputFrame() {
        ObjectAnimator.ofFloat(binding.inputFrame, "translationX",
            0f, -18f, 18f, -14f, 14f, -8f, 8f, -4f, 4f, 0f
        ).apply { duration = 500; start() }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        try {
            _binding?.rainView?.stop()
            _binding?.loginHeaderParticles?.stop()
            _binding?.loginCardParticles?.stop()
            _binding?.fbParticles?.stop()
            _binding?.zaloParticles?.stop()
        } catch (_: Exception) {}
        super.onDestroyView()
        _binding = null
    }
}
