package com.example.app_smart_waste.ui.auth

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.LoginUiState
import com.example.app_smart_waste.core.network.RetrofitClient
import com.example.app_smart_waste.core.storage.AppConfig
import com.example.app_smart_waste.core.storage.SecureTokenStorage
import com.example.app_smart_waste.databinding.ActivityLoginBinding
import com.example.app_smart_waste.ui.main.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()
    private val tokenStorage by lazy { SecureTokenStorage.getInstance(this) }
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initSavedCredentials()
        setupHeader()
        playEntranceMotion()
        setupListeners()
        observeViewModel()
    }

    private fun setupHeader() {
        binding.appHeader.setTransparentBackground()
        binding.appHeader.configure(
            title = "",
            actionIconRes = R.drawable.ic_settings,
            onActionClick = { showServerConfigDialog() }
        )
    }

    private fun initSavedCredentials() {
        val lastUsername = tokenStorage.getUsername()
        if (!lastUsername.isNullOrBlank()) {
            binding.etUsername.setText(lastUsername)
            binding.etPassword.requestFocus()
        }
    }

    private fun Context.isAnimationEnabled(): Boolean {
        return try {
            val durationScale = Settings.Global.getFloat(
                contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            durationScale > 0f
        } catch (_: Exception) {
            true
        }
    }

    private fun playEntranceMotion() {
        if (!isAnimationEnabled()) {
            binding.topBrandingSection.alpha = 1f
            binding.loginCard.alpha = 1f
            return
        }

        // 1. Hero Logo & Branding Entrance Motion
        binding.topBrandingSection.alpha = 0f
        binding.topBrandingSection.scaleX = 0.94f
        binding.topBrandingSection.scaleY = 0.94f
        binding.topBrandingSection.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(260)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // 2. Card Slide Up + Fade
        binding.loginCard.alpha = 0f
        binding.loginCard.translationY = 16f
        binding.loginCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(60)
            .setDuration(280)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // 3. Bottom Info Pill Fade
        binding.bottomInfoCard.alpha = 0f
        binding.bottomInfoCard.animate()
            .alpha(1f)
            .setStartDelay(120)
            .setDuration(300)
            .start()
    }

    private fun setupListeners() {
        // Show / Hide Password Toggle
        binding.btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                binding.etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                binding.btnTogglePassword.setImageResource(R.drawable.ic_eye)
                binding.btnTogglePassword.imageTintList = ContextCompat.getColorStateList(this, R.color.primary_500)
            } else {
                binding.etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                binding.btnTogglePassword.setImageResource(R.drawable.ic_eye_off)
                binding.btnTogglePassword.imageTintList = ContextCompat.getColorStateList(this, R.color.navy_400)
            }
            binding.etPassword.setSelection(binding.etPassword.text.length)
        }

        // Focus Listeners for subtle visual feedback
        binding.etUsername.setOnFocusChangeListener { _, hasFocus ->
            binding.containerUsername.isSelected = hasFocus
            if (hasFocus) {
                binding.tvError.visibility = View.GONE
            }
        }

        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            binding.containerPassword.isSelected = hasFocus
            if (hasFocus) {
                binding.tvError.visibility = View.GONE
            }
        }

        // Login on IME Done
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                triggerLogin()
                true
            } else {
                false
            }
        }

        // Login Button Click with Micro-scale Press Animation (0.98x)
        binding.btnLogin.setOnClickListener {
            it.animate().scaleX(0.98f).scaleY(0.98f).setDuration(100).withEndAction {
                it.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            }.start()

            triggerLogin()
        }
    }

    private fun triggerLogin() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (username.isBlank() || password.isBlank()) {
            binding.tvError.text = "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu."
            binding.tvError.visibility = View.VISIBLE
            return
        }

        tokenStorage.saveUser(
            id = "",
            username = username,
            fullName = "",
            role = "staff",
            isActive = true
        )

        viewModel.login(username, password)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loginState.collectLatest { state ->
                    when (state) {
                        is LoginUiState.Loading -> {
                            setLoadingState(true)
                            binding.tvError.visibility = View.GONE
                        }
                        is LoginUiState.Success -> {
                            handleLoginSuccess()
                        }
                        is LoginUiState.Error -> {
                            setLoadingState(false)
                            if (state.isInactive) {
                                showAccountLockedDialog()
                            } else {
                                binding.tvError.text = state.message
                                binding.tvError.visibility = View.VISIBLE
                            }
                        }
                        is LoginUiState.Idle -> {
                            setLoadingState(false)
                            binding.tvError.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun handleLoginSuccess() {
        // 1. Hide software keyboard immediately to avoid layout jank
        currentFocus?.let { view ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }

        // 2. Button Success State
        binding.loadingLayout.visibility = View.GONE
        binding.tvBtnLoginText.visibility = View.GONE
        binding.successLayout.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false
        binding.btnLogin.alpha = 1.0f

        // 3. Direct transition to MainActivity without white screen flash
        navigateToMainCleanly()
    }

    private fun navigateToMainCleanly() {
        val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("SHOW_WELCOME_MESSAGE", true)
        }
        startActivity(intent)
        finish()
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.successLayout.visibility = View.GONE
        if (isLoading) {
            binding.btnLogin.isEnabled = false
            binding.btnLogin.alpha = 0.85f
            binding.tvBtnLoginText.visibility = View.GONE
            binding.loadingLayout.visibility = View.VISIBLE
        } else {
            binding.btnLogin.isEnabled = true
            binding.btnLogin.alpha = 1.0f
            binding.tvBtnLoginText.visibility = View.VISIBLE
            binding.loadingLayout.visibility = View.GONE
        }
    }

    private fun showAccountLockedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Tài khoản bị khóa")
            .setMessage("Tài khoản đã bị khóa bởi Quản trị viên.")
            .setPositiveButton("Đã hiểu", null)
            .setCancelable(false)
            .show()
    }

    @SuppressLint("SetTextI18n")
    private fun showServerConfigDialog() {
        val currentUrl = AppConfig.getBaseUrl(this)
        val input = EditText(this).apply {
            setText(currentUrl)
            setSelection(currentUrl.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Cấu hình IP Backend Server")
            .setMessage("Nhập địa chỉ máy chủ)")
            .setView(input)
            .setPositiveButton("Lưu") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotBlank()) {
                    AppConfig.setBaseUrl(this, newUrl)
                    RetrofitClient.resetClient()
                    Toast.makeText(this, "Đã cập nhật: $newUrl", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
