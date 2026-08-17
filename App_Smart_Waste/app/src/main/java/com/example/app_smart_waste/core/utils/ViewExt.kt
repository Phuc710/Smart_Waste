package com.example.app_smart_waste.core.utils

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun View.applyStatusBarTopPadding(extraDp: Int = 16) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        val extraPx = (extraDp * view.resources.displayMetrics.density).toInt()
        view.setPadding(
            view.paddingLeft,
            statusBarInset + extraPx,
            view.paddingRight,
            view.paddingBottom
        )
        insets
    }
}

fun View.applyNavigationBarBottomPadding(extraDp: Int = 0) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        val extraPx = (extraDp * view.resources.displayMetrics.density).toInt()
        view.setPadding(
            view.paddingLeft,
            view.paddingTop,
            view.paddingRight,
            navBarInset + extraPx
        )
        insets
    }
}
