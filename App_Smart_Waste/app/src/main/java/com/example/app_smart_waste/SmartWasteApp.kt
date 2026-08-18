package com.example.app_smart_waste

import android.app.Application
import com.example.app_smart_waste.core.notification.AppNotificationManager
import com.example.app_smart_waste.core.utils.ActivityLifecycleTracker

/**
 * Senior Application Base Class
 * Initializes System Notification Channels, Activity Lifecycle Trackers, and Core Services.
 */
class SmartWasteApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Register Activity Lifecycle Tracker for In-App Banner & Overlays
        ActivityLifecycleTracker.register(this)

        // 2. Initialize High-Priority Android Notification Channels (API 26+)
        AppNotificationManager.getInstance(this)
    }
}
