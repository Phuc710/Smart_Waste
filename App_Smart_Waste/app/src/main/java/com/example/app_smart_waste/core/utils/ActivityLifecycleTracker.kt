package com.example.app_smart_waste.core.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Senior Enterprise Activity Lifecycle Tracker
 * Accurately detects whether the App is currently in the Foreground and retains
 * a WeakReference to the top-most resumed Activity for in-app overlays / banners.
 */
object ActivityLifecycleTracker : Application.ActivityLifecycleCallbacks {

    private var currentActivityRef: WeakReference<Activity>? = null
    private var resumedActivityCount = 0
    private var startedActivityCount = 0

    val currentActivity: Activity?
        get() = currentActivityRef?.get()

    val isAppInForeground: Boolean
        get() = startedActivityCount > 0 && currentActivity != null

    fun register(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivityCount++
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        resumedActivityCount = maxOf(0, resumedActivityCount - 1)
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = maxOf(0, startedActivityCount - 1)
        if (currentActivityRef?.get() == activity) {
            // Keep reference until replaced or cleared if nothing else active
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivityRef?.get() == activity) {
            currentActivityRef?.clear()
        }
    }
}
