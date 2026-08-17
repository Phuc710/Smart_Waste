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

        // Subtle logo entrance animation (0ms - 400ms)
        binding.logoContainer.alpha = 0f
        binding.logoContainer.scaleX = 0.92f
        binding.logoContainer.scaleY = 0.92f
        binding.logoContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Check authentication session in background
        lifecycleScope.launch {
            delay(500) // Minimum time for smooth transition without stalling user
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
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
