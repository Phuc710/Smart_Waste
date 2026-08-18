package com.example.app_smart_waste.ui.common

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.example.app_smart_waste.R
import java.lang.ref.WeakReference

/**
 * Top Notification / Snackbar Manager for Command Operations (e.g. Remote Open Lid).
 * Slides down from top under status bar with Loading, Success, Error states and Swipe-to-dismiss.
 */
object TopCommandNotificationManager {

    private var currentViewRef: WeakReference<View>? = null
    private var dismissRunnable: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    enum class State {
        LOADING, SUCCESS, ERROR
    }

    /**
     * Show Loading state: "Đang gửi lệnh mở nắp... / Vui lòng chờ"
     */
    fun showLoading(
        activity: Activity,
        title: String = "Đang gửi lệnh mở nắp...",
        subtitle: String = "Vui lòng chờ"
    ) {
        show(
            activity = activity,
            state = State.LOADING,
            title = title,
            subtitle = subtitle,
            onRetry = null,
            autoDismissMs = 0L // Does not auto-dismiss while loading
        )
    }

    /**
     * Show Success state: "Mở nắp thành công! / Nắp đã mở." (Auto dismiss ~2.5s)
     */
    fun showSuccess(
        activity: Activity,
        title: String = "Mở nắp thành công!",
        subtitle: String = "Nắp đã mở.",
        autoDismissMs: Long = 2500L
    ) {
        show(
            activity = activity,
            state = State.SUCCESS,
            title = title,
            subtitle = subtitle,
            onRetry = null,
            autoDismissMs = autoDismissMs
        )
    }

    /**
     * Show Error state: "Không phản hồi từ thiết bị / Vui lòng kiểm tra kết nối."
     * Includes [ Thử lại ] button and swipe-up to dismiss.
     */
    fun showError(
        activity: Activity,
        title: String = "Không phản hồi từ thiết bị",
        subtitle: String = "Vui lòng kiểm tra kết nối.",
        autoDismissMs: Long = 4500L,
        onRetry: (() -> Unit)? = null
    ) {
        show(
            activity = activity,
            state = State.ERROR,
            title = title,
            subtitle = subtitle,
            onRetry = onRetry,
            autoDismissMs = autoDismissMs
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show(
        activity: Activity,
        state: State,
        title: String,
        subtitle: String,
        onRetry: (() -> Unit)?,
        autoDismissMs: Long
    ) {
        mainHandler.removeCallbacksAndMessages(null)

        val decorView = activity.window?.decorView as? ViewGroup ?: return
        var notifView = currentViewRef?.get()

        if (notifView == null || notifView.parent == null) {
            notifView = LayoutInflater.from(activity).inflate(R.layout.view_top_command_notification, decorView, false)
            currentViewRef = WeakReference(notifView)

            val statusBarHeight = getStatusBarHeight(activity)
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = statusBarHeight + (activity.resources.displayMetrics.density * 8).toInt()
            }

            decorView.addView(notifView, params)

            // Initial Slide Down Animation
            notifView.translationY = -300f
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

        // Bind Views
        val card = notifView.findViewById<View>(R.id.layoutTopNotifCard)
        val pbLoading = notifView.findViewById<ProgressBar>(R.id.pbTopNotifLoading)
        val containerSuccess = notifView.findViewById<View>(R.id.containerTopNotifSuccess)
        val containerError = notifView.findViewById<View>(R.id.containerTopNotifError)
        val tvTitle = notifView.findViewById<TextView>(R.id.tvTopNotifTitle)
        val tvSubtitle = notifView.findViewById<TextView>(R.id.tvTopNotifSubtitle)
        val btnRetry = notifView.findViewById<AppCompatButton>(R.id.btnTopNotifRetry)

        tvTitle.text = title
        tvSubtitle.text = subtitle

        when (state) {
            State.LOADING -> {
                card.setBackgroundResource(R.drawable.bg_notif_loading)
                pbLoading.visibility = View.VISIBLE
                containerSuccess.visibility = View.GONE
                containerError?.visibility = View.GONE
                btnRetry.visibility = View.GONE
                tvTitle.setTextColor(Color.parseColor("#0F172A"))
                tvSubtitle.setTextColor(Color.parseColor("#64748B"))
            }
            State.SUCCESS -> {
                card.setBackgroundResource(R.drawable.bg_notif_success)
                pbLoading.visibility = View.GONE
                containerSuccess.visibility = View.VISIBLE
                containerError?.visibility = View.GONE
                btnRetry.visibility = View.GONE
                tvTitle.setTextColor(Color.parseColor("#0F172A"))
                tvSubtitle.setTextColor(Color.parseColor("#64748B"))
            }
            State.ERROR -> {
                card.setBackgroundResource(R.drawable.bg_notif_error)
                pbLoading.visibility = View.GONE
                containerSuccess.visibility = View.GONE
                containerError?.visibility = View.VISIBLE
                tvTitle.setTextColor(Color.parseColor("#0F172A"))
                tvSubtitle.setTextColor(Color.parseColor("#64748B"))

                if (onRetry != null) {
                    btnRetry.visibility = View.VISIBLE
                    btnRetry.setOnClickListener {
                        onRetry.invoke()
                    }
                } else {
                    btnRetry.visibility = View.GONE
                }
            }
        }

        // Auto dismiss if configured
        if (autoDismissMs > 0) {
            val r = Runnable { dismiss() }
            dismissRunnable = r
            mainHandler.postDelayed(r, autoDismissMs)
        }
    }

    /**
     * Dismiss the notification with smooth slide up + fade out animation.
     */
    fun dismiss() {
        mainHandler.removeCallbacksAndMessages(null)
        val notifView = currentViewRef?.get() ?: return
        val parent = notifView.parent as? ViewGroup ?: return

        notifView.animate()
            .translationY(-300f)
            .alpha(0f)
            .setDuration(240)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                parent.removeView(notifView)
                currentViewRef?.clear()
            }
            .start()
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
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun getStatusBarHeight(activity: Activity): Int {
        val resourceId = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) activity.resources.getDimensionPixelSize(resourceId) else (activity.resources.displayMetrics.density * 24).toInt()
    }
}
