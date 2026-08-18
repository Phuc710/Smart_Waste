package com.example.app_smart_waste.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
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
import com.example.app_smart_waste.core.notification.AppNotificationManager
import com.example.app_smart_waste.core.notification.InAppNotificationManager
import com.example.app_smart_waste.databinding.ActivityMainBinding
import com.example.app_smart_waste.ui.home.HomeFragment
import com.example.app_smart_waste.ui.jobs.JobsFragment
import com.example.app_smart_waste.ui.map.MapFragment
import com.example.app_smart_waste.ui.profile.ProfileFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentTabId: Int = R.id.navItemHome

    private var realtimeManager: com.example.app_smart_waste.core.network.RealtimeManager? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled
    }

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

        // Initialize Notifications Channels
        com.example.app_smart_waste.core.notification.AppNotificationManager.getInstance(this)

        // Senior reusable Insets extension
        binding.bottomNavContainer.applyNavigationBarBottomPadding()

        setupCustomBottomNav()

        if (savedInstanceState == null) {
            val openTab = intent.getStringExtra("EXTRA_OPEN_TAB") ?: intent.getStringExtra("OPEN_TAB")
            val initialTab = when (openTab) {
                "JOBS", "HISTORY" -> R.id.navItemJobs
                "MAP" -> R.id.navItemMap
                "PROFILE" -> R.id.navItemProfile
                else -> R.id.navItemHome
            }
            selectTab(initialTab)
            if (openTab == "HISTORY") {
                (supportFragmentManager.findFragmentByTag("tab_jobs") as? JobsFragment)?.selectTab(1)
            }
            handleMapIntent(intent)
        }

        if (intent.getBooleanExtra("SHOW_WELCOME_MESSAGE", false)) {
            val userFullName = com.example.app_smart_waste.core.storage.SecureTokenStorage.getInstance(this).getFullName()
            val greeting = if (!userFullName.isNullOrBlank()) "👋 Chào mừng $userFullName!" else "👋 Đăng nhập thành công!"
            android.widget.Toast.makeText(this, greeting, android.widget.Toast.LENGTH_SHORT).show()
        }

        checkAndStartGps()
        checkNotificationPermission()
        setupGlobalRealtime()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val openTab = intent.getStringExtra("EXTRA_OPEN_TAB") ?: intent.getStringExtra("OPEN_TAB")
        when (openTab) {
            "JOBS" -> selectTab(R.id.navItemJobs)
            "HISTORY" -> {
                selectTab(R.id.navItemJobs)
                (supportFragmentManager.findFragmentByTag("tab_jobs") as? JobsFragment)?.selectTab(1)
            }
            "MAP" -> selectTab(R.id.navItemMap)
            "PROFILE" -> selectTab(R.id.navItemProfile)
            "HOME" -> selectTab(R.id.navItemHome)
        }
        handleMapIntent(intent)
    }

    private fun handleMapIntent(targetIntent: Intent) {
        val openTab = targetIntent.getStringExtra("EXTRA_OPEN_TAB") ?: targetIntent.getStringExtra("OPEN_TAB")
        if (openTab == "MAP") {
            val binId = targetIntent.getStringExtra("EXTRA_BIN_ID")
            val startNav = targetIntent.getBooleanExtra("EXTRA_START_NAV", false)
            if (!binId.isNullOrBlank()) {
                val mapFrag = supportFragmentManager.findFragmentByTag("tab_map") as? MapFragment
                mapFrag?.selectAndNavigateBin(binId, startNav)
            }
        }
    }

    private fun checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupGlobalRealtime() {
        realtimeManager = com.example.app_smart_waste.core.network.RealtimeManager(this).apply {
            connect(object : com.example.app_smart_waste.core.network.RealtimeManager.Listener {
                override fun onJobUpdated(jobId: String?) {
                    // Update tab badge dynamically
                }
            })
        }
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

        val tag = when (tabId) {
            R.id.navItemHome, R.id.navigation_home -> "tab_home"
            R.id.navItemJobs, R.id.navigation_jobs -> "tab_jobs"
            R.id.navItemMap, R.id.navigation_map -> "tab_map"
            R.id.navItemProfile, R.id.navigation_profile -> "tab_profile"
            else -> "tab_home"
        }

        val transaction = supportFragmentManager.beginTransaction()
        supportFragmentManager.fragments.forEach { frag ->
            transaction.hide(frag)
        }

        var targetFrag = supportFragmentManager.findFragmentByTag(tag)
        if (targetFrag == null) {
            targetFrag = when (tag) {
                "tab_home" -> HomeFragment()
                "tab_jobs" -> JobsFragment()
                "tab_map" -> MapFragment()
                "tab_profile" -> ProfileFragment()
                else -> HomeFragment()
            }
            transaction.add(R.id.navHostContainer, targetFrag, tag)
        } else {
            transaction.show(targetFrag)
        }
        transaction.commit()
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

    // =========================================================================
    // 🧪 UI TEST SIMULATOR (Dành cho Developer / QA Test trực tiếp trên UI)
    // =========================================================================

    /**
     * Kích hoạt thử nghiệm In-App Top Dropdown Banner (Trượt từ trên đỉnh xuống)
     * @param type 1: Nhiệm vụ mới, 2: Thùng quá tải, 3: Ca bị hủy, 4: Thông báo khẩn
     */
    fun testTriggerInAppNotification(type: Int) {
        when (type) {
            1 -> InAppNotificationManager.testJobAssigned(this) { selectTab(R.id.navItemJobs) }
            2 -> InAppNotificationManager.testBinOverfull(this) { selectTab(R.id.navItemMap) }
            3 -> InAppNotificationManager.testJobCancelled(this) { selectTab(R.id.navItemJobs) }
            4 -> InAppNotificationManager.testBroadcastIncident(this) { selectTab(R.id.navItemHome) }
        }
    }

    /**
     * Kích hoạt thử nghiệm System Heads-Up Push Notification (Ngoài màn hình)
     * @param type 1: Nhiệm vụ mới, 2: Thùng quá tải, 3: Ca bị hủy, 4: Thông báo khẩn
     */
    fun testTriggerSystemPush(type: Int) {
        val appNotif = AppNotificationManager.getInstance(this)
        when (type) {
            1 -> appNotif.testJobAssignedPush()
            2 -> appNotif.testBinOverfullPush()
            3 -> appNotif.testJobCancelledPush()
            4 -> appNotif.testBroadcastIncidentPush()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        InAppNotificationManager.dismiss()
        realtimeManager?.disconnect()
        realtimeManager = null
        GpsTracker.getInstance(this).stopTracking()
    }
}
