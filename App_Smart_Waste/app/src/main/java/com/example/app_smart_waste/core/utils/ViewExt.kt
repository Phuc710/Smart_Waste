package com.example.app_smart_waste.core.utils

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun View.applyStatusBarTopPadding(extraDp: Int = 0) {
    val density = resources.displayMetrics.density
    val fallbackTop = (28 * density).toInt()

    val applyInsets: (Int) -> Unit = { statusBarTop ->
        val safeTop = if (statusBarTop > 0) statusBarTop else fallbackTop
        val extraPx = (extraDp * density).toInt()
        val targetPadding = safeTop + extraPx
        if (paddingTop != targetPadding) {
            setPadding(paddingLeft, targetPadding, paddingRight, paddingBottom)
        }
    }

    // 1. Immediately compute status bar + display cutout height if insets already available
    val rootInsets = ViewCompat.getRootWindowInsets(this)
    val immediateTop = rootInsets?.getInsets(
        WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
    )?.top ?: 0

    if (immediateTop > 0) {
        applyInsets(immediateTop)
    }

    // 2. Register listener for dynamic insets changes (cutouts, status bars)
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val statusBarInset = insets.getInsets(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
        ).top
        applyInsets(statusBarInset)
        insets
    }

    if (isAttachedToWindow) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                ViewCompat.requestApplyInsets(v)
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })
    }
}

fun View.applyNavigationBarBottomPadding(extraDp: Int = 0) {
    val density = resources.displayMetrics.density

    val applyInsets: (Int) -> Unit = { navBarBottom ->
        val extraPx = (extraDp * density).toInt()
        val targetPadding = navBarBottom + extraPx
        if (paddingBottom != targetPadding) {
            setPadding(paddingLeft, paddingTop, paddingRight, targetPadding)
        }
    }

    val rootInsets = ViewCompat.getRootWindowInsets(this)
    val immediateBottom = rootInsets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    if (immediateBottom > 0) {
        applyInsets(immediateBottom)
    }

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        if (navBarInset > 0) {
            applyInsets(navBarInset)
        }
        insets
    }

    if (isAttachedToWindow) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                ViewCompat.requestApplyInsets(v)
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })
    }
}

fun View.applyPressEffect(onEnd: () -> Unit = {}) {
    this.animate()
        .scaleX(0.97f)
        .scaleY(0.97f)
        .setDuration(80)
        .withEndAction {
            this.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(90)
                .withEndAction { onEnd() }
                .start()
        }
        .start()
}
