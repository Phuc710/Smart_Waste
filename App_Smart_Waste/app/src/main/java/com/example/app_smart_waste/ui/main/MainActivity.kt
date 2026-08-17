package com.example.app_smart_waste.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.location.GpsTracker
import com.example.app_smart_waste.core.utils.applyNavigationBarBottomPadding
import com.example.app_smart_waste.databinding.ActivityMainBinding
import com.example.app_smart_waste.ui.home.HomeFragment
import com.example.app_smart_waste.ui.jobs.JobsFragment
import com.example.app_smart_waste.ui.map.MapFragment
import com.example.app_smart_waste.ui.profile.ProfileFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentTabId: Int = R.id.navItemHome

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            GpsTracker.getInstance(this).startTracking()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Senior reusable Insets extension
        binding.bottomNavContainer.applyNavigationBarBottomPadding()

        setupCustomBottomNav()

        if (savedInstanceState == null) {
            selectTab(R.id.navItemHome)
        }

        if (intent.getBooleanExtra("SHOW_WELCOME_MESSAGE", false)) {
            val userFullName = com.example.app_smart_waste.core.storage.SecureTokenStorage.getInstance(this).getFullName()
            val greeting = if (!userFullName.isNullOrBlank()) "👋 Chào mừng $userFullName!" else "👋 Đăng nhập thành công!"
            android.widget.Toast.makeText(this, greeting, android.widget.Toast.LENGTH_SHORT).show()
        }

        checkAndStartGps()
    }

    private fun checkAndStartGps() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            GpsTracker.getInstance(this).startTracking()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun setupCustomBottomNav() {
        binding.navItemHome.setOnClickListener { selectTab(R.id.navItemHome) }
        binding.navItemJobs.setOnClickListener { selectTab(R.id.navItemJobs) }
        binding.navItemMap.setOnClickListener { selectTab(R.id.navItemMap) }
        binding.navItemProfile.setOnClickListener { selectTab(R.id.navItemProfile) }
    }

    fun selectTab(tabId: Int) {
        currentTabId = tabId

        val activeColor = ContextCompat.getColor(this, R.color.profile_green_primary)
        val inactiveColor = ContextCompat.getColor(this, R.color.profile_text_secondary)

        fun updateItem(icon: ImageView, label: TextView, isSelected: Boolean) {
            val color = if (isSelected) activeColor else inactiveColor
            icon.setColorFilter(color)
            label.setTextColor(color)
            label.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        updateItem(binding.navIconHome, binding.navLabelHome, tabId == R.id.navItemHome || tabId == R.id.navigation_home)
        updateItem(binding.navIconJobs, binding.navLabelJobs, tabId == R.id.navItemJobs || tabId == R.id.navigation_jobs)
        updateItem(binding.navIconMap, binding.navLabelMap, tabId == R.id.navItemMap || tabId == R.id.navigation_map)
        updateItem(binding.navIconProfile, binding.navLabelProfile, tabId == R.id.navItemProfile || tabId == R.id.navigation_profile)

        val fragment: Fragment = when (tabId) {
            R.id.navItemHome, R.id.navigation_home -> HomeFragment()
            R.id.navItemJobs, R.id.navigation_jobs -> JobsFragment()
            R.id.navItemMap, R.id.navigation_map -> MapFragment()
            R.id.navItemProfile, R.id.navigation_profile -> ProfileFragment()
            else -> HomeFragment()
        }
        loadFragment(fragment)
    }

    fun updateJobsBadge(count: Int) {
        if (count > 0) {
            binding.navBadgeJobs.visibility = View.VISIBLE
            binding.navBadgeJobs.text = if (count > 9) "9+" else count.toString()
        } else {
            binding.navBadgeJobs.visibility = View.GONE
        }
    }

    fun switchTab(tabId: Int) {
        selectTab(tabId)
    }

    fun navigateToTab(tabId: Int) {
        selectTab(tabId)
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.navHostContainer, fragment)
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        GpsTracker.getInstance(this).stopTracking()
    }
}
