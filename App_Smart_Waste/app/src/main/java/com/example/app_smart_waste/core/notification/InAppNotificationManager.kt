package com.example.app_smart_waste.core.notification

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.utils.ActivityLifecycleTracker
import java.lang.ref.WeakReference

/**
 * Senior Enterprise In-App Top Dropdown Banner Notification Manager
 * Displays clean, animated top notifications (Slide Down from status bar)
 * when the driver is actively using the application (Foreground).
 *
 * Features:
 * - Slide Down Entrance (DecelerateInterpolator 280ms)
 * - Tactile Haptic Vibration Feedback
 * - Touch Swipe-Up Dismiss Gesture
 * - 4-Second Auto Dismiss Timer
 * - Direct Action / Navigation Callbacks
 * - Zero Memory Leak (WeakReference & DecorView safety)
 */
object InAppNotificationManager {

    private var currentViewRef: WeakReference<View>? = null
    private var dismissRunnable: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Display an In-App Top Banner for the given notification type.
     * If no activity is provided, defaults to the currently resumed Activity.
     */
    fun show(
        activity: Activity? = null,
        notification: InAppNotificationType,
        onActionClick: (() -> Unit)? = null
    ) {
        val targetActivity = activity ?: ActivityLifecycleTracker.currentActivity ?: return
        if (targetActivity.isFinishing || targetActivity.isDestroyed) return

        mainHandler.post {
            showInternal(targetActivity, notification, onActionClick)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showInternal(
        activity: Activity,
        notification: InAppNotificationType,
        onActionClick: (() -> Unit)?
    ) {
        mainHandler.removeCallbacksAndMessages(null)

        val decorView = activity.window?.decorView as? ViewGroup ?: return
        var notifView = currentViewRef?.get()

        if (notifView == null || notifView.parent == null) {
            notifView = LayoutInflater.from(activity).inflate(
                R.layout.view_in_app_notification_banner,
                decorView,
                false
            )
            currentViewRef = WeakReference(notifView)

            val statusBarHeight = getStatusBarHeight(activity)
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = statusBarHeight + (activity.resources.displayMetrics.density * 6).toInt()
            }

            decorView.addView(notifView, params)

            // Initial Slide Down Animation
            notifView.translationY = -350f
            notifView.alpha = 0f
            notifView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(280)
                .setInterpolator(DecelerateInterpolator())
                .start()

            // Setup Swipe Up Gesture to Dismiss
            setupSwipeUpGesture(notifView)
        }

        // Trigger subtle Haptic Vibration
        triggerHapticFeedback(activity, notifView)

        // Bind Visual Tokens & Elements
        val card = notifView.findViewById<View>(R.id.layoutInAppBannerCard)
        val containerIcon = notifView.findViewById<FrameLayout>(R.id.containerBannerIcon)
        val ivIcon = notifView.findViewById<ImageView>(R.id.ivBannerIcon)
        val viewDot = notifView.findViewById<View>(R.id.viewBannerDot)
        val tvTitle = notifView.findViewById<TextView>(R.id.tvBannerTitle)
        val tvSubtitle = notifView.findViewById<TextView>(R.id.tvBannerSubtitle)
        val btnAction = notifView.findViewById<AppCompatButton>(R.id.btnBannerAction)

        containerIcon.setBackgroundResource(notification.iconBgRes)
        ivIcon.setImageResource(notification.iconRes)
        viewDot.setBackgroundResource(notification.dotBgRes)

        tvTitle.text = notification.title
        tvSubtitle.text = notification.subtitle

        btnAction.apply {
            text = notification.actionText
            setBackgroundResource(notification.buttonBgRes)
            setTextColor(notification.buttonTextColor)
            setOnClickListener {
                dismiss()
                onActionClick?.invoke()
            }
        }

        // Entire Card Click -> Trigger Action and Dismiss
        card.setOnClickListener {
            dismiss()
            onActionClick?.invoke()
        }

        // Auto Dismiss after 4 seconds
        val r = Runnable { dismiss() }
        dismissRunnable = r
        mainHandler.postDelayed(r, 4000L)
    }

    /**
     * Dismiss the In-App Banner with a smooth slide up + fade out animation.
     */
    fun dismiss() {
        mainHandler.removeCallbacksAndMessages(null)
        val notifView = currentViewRef?.get() ?: return
        val parent = notifView.parent as? ViewGroup ?: return

        notifView.animate()
            .translationY(-350f)
            .alpha(0f)
            .setDuration(240)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                try {
                    parent.removeView(notifView)
                } catch (_: Exception) {}
                currentViewRef?.clear()
            }
            .start()
    }

    /**
     * Check if an in-app banner is currently visible
     */
    fun isShowing(): Boolean {
        val view = currentViewRef?.get()
        return view != null && view.parent != null && view.visibility == View.VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeUpGesture(view: View) {
        var startY = 0f
        var currentTranslationY = 0f

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    currentTranslationY = v.translationY
                    mainHandler.removeCallbacksAndMessages(null)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - startY
                    if (deltaY < 0) {
                        v.translationY = currentTranslationY + deltaY
                        val progress = (-deltaY / 200f).coerceIn(0f, 1f)
                        v.alpha = 1f - (progress * 0.5f)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val deltaY = event.rawY - startY
                    if (deltaY < -60) {
                        // Dismiss on quick swipe up
                        dismiss()
                    } else {
                        // Snap back
                        v.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(150)
                            .start()
                        // Resume auto dismiss
                        dismissRunnable?.let { mainHandler.postDelayed(it, 3000L) }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun triggerHapticFeedback(activity: Activity, view: View) {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(40)
                }
            }
        } catch (_: Exception) {}
    }

    private fun getStatusBarHeight(activity: Activity): Int {
        val resourceId = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            activity.resources.getDimensionPixelSize(resourceId)
        } else {
            (activity.resources.displayMetrics.density * 24).toInt()
        }
    }

    // =========================================================================
    // 🧪 UI TEST SIMULATOR HELPERS (For Rapid Developer / QA Testing)
    // =========================================================================

    /**
     * Test 1: Giao việc mới (Admin Assign) -> #JOB-0258
     */
    fun testJobAssigned(activity: Activity? = null, onAction: (() -> Unit)? = null) {
        show(
            activity = activity,
            notification = InAppNotificationType.JobAssigned(
                jobId = "0258",
                totalBins = 3,
                routeDesc = "Tuyến thu gom trung tâm Quận 1"
            ),
            onActionClick = onAction
        )
    }

    /**
     * Test 2: Cảnh báo thùng quá tải (90%)
     */
    fun testBinOverfull(activity: Activity? = null, onAction: (() -> Unit)? = null) {
        show(
            activity = activity,
            notification = InAppNotificationType.BinOverfull(
                binId = "BIN_01",
                binName = "Thùng rác Bến Bạch Đằng",
                location = "Quận 1",
                levelPercent = 90
            ),
            onActionClick = onAction
        )
    }

    /**
     * Test 3: Nhiệm vụ bị hủy -> #JOB-0245
     */
    fun testJobCancelled(activity: Activity? = null, onAction: (() -> Unit)? = null) {
        show(
            activity = activity,
            notification = InAppNotificationType.JobCancelled(
                jobId = "0245",
                reason = "Điều phối lại tuyến cho tài xế khác."
            ),
            onActionClick = onAction
        )
    }

    /**
     * Test 4: Thông báo khẩn
     */
    fun testBroadcastIncident(activity: Activity? = null, onAction: (() -> Unit)? = null) {
        show(
            activity = activity,
            notification = InAppNotificationType.BroadcastIncident(
                customTitle = "Thông báo khẩn",
                message = "Khu vực Quận 4 đang có mưa lớn, đường trơn trượt."
            ),
            onActionClick = onAction
        )
    }
}
