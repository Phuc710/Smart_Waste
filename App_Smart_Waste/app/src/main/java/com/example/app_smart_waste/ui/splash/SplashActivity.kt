package com.example.app_smart_waste.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.app_smart_waste.data.repository.AuthRepository
import com.example.app_smart_waste.databinding.ActivitySplashBinding
import com.example.app_smart_waste.ui.auth.LoginActivity
import com.example.app_smart_waste.ui.main.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val authRepo by lazy { AuthRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Subtle logo & bottom entrance animation
        binding.logoContainer.alpha = 0f
        binding.logoContainer.scaleX = 0.90f
        binding.logoContainer.scaleY = 0.90f
        binding.logoContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        binding.bottomSection.alpha = 0f
        binding.bottomSection.animate()
            .alpha(1f)
            .setDuration(450)
            .setStartDelay(200)
            .start()

        // Check authentication session in background
        lifecycleScope.launch {
            delay(700) // Minimum time for smooth transition without stalling user
            val result = authRepo.checkSession()
            if (result.isSuccess) {
                val user = result.getOrNull()
                if (user?.isActive == false) {
                    Toast.makeText(this@SplashActivity, "Tài khoản đã bị khóa bởi Quản trị viên", Toast.LENGTH_LONG).show()
                    navigateToLogin()
                } else {
                    navigateToHome()
                }
            } else {
                navigateToLogin()
            }
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(com.example.app_smart_waste.R.anim.anim_fade_in_smooth, com.example.app_smart_waste.R.anim.anim_fade_out_smooth)
        finish()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(com.example.app_smart_waste.R.anim.anim_fade_in_smooth, com.example.app_smart_waste.R.anim.anim_fade_out_smooth)
        finish()
    }
}
