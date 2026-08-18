package com.example.app_smart_waste.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.utils.applyStatusBarTopPadding
import com.example.app_smart_waste.databinding.LayoutAppHeaderBinding

/**
 * Global Shared AppHeader Component
 * Ensures 100% identical geometry, status bar safe area, title baseline, and icon alignment.
 */
class AppHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: LayoutAppHeaderBinding

    init {
        orientation = HORIZONTAL
        binding = LayoutAppHeaderBinding.inflate(LayoutInflater.from(context), this, true)

        // Automatic Status Bar Inset Handling with extra 6dp breathing room
        applyStatusBarTopPadding(6)
    }

    /**
     * Configure header attributes with a single unified call.
     */
    fun configure(
        title: CharSequence,
        subtitle: CharSequence? = null,
        @DrawableRes navIconRes: Int? = null,
        onNavClick: (() -> Unit)? = null,
        actionText: CharSequence? = null,
        @ColorInt actionTextColor: Int? = null,
        onActionTextClick: (() -> Unit)? = null,
        @DrawableRes actionIconRes: Int? = null,
        actionBadgeCount: Int? = null,
        onActionClick: (() -> Unit)? = null,
        @DrawableRes secondaryActionIconRes: Int? = null,
        onSecondaryActionClick: (() -> Unit)? = null
    ) {
        // 1. Title & Subtitle
        binding.tvHeaderTitle.text = title
        if (!subtitle.isNullOrBlank()) {
            binding.tvHeaderSubtitle.text = subtitle
            binding.tvHeaderSubtitle.visibility = View.VISIBLE
        } else {
            binding.tvHeaderSubtitle.visibility = View.GONE
        }

        // 2. Navigation Icon (Back arrow, Menu)
        if (navIconRes != null) {
            binding.btnHeaderNav.visibility = View.VISIBLE
            binding.ivHeaderNavIcon.setImageResource(navIconRes)
            binding.btnHeaderNav.setOnClickListener {
                it.applyPressEffect { onNavClick?.invoke() }
            }
        } else {
            binding.btnHeaderNav.visibility = View.GONE
        }

        // 3. Text Action Button (e.g. "Đánh dấu đã đọc")
        if (!actionText.isNullOrBlank()) {
            binding.btnHeaderTextAction.visibility = View.VISIBLE
            binding.tvHeaderTextAction.text = actionText
            if (actionTextColor != null) {
                binding.tvHeaderTextAction.setTextColor(actionTextColor)
            }
            binding.btnHeaderTextAction.setOnClickListener {
                it.applyPressEffect { onActionTextClick?.invoke() }
            }
        } else {
            binding.btnHeaderTextAction.visibility = View.GONE
        }

        // 4. Primary Icon Action (e.g. Refresh, Settings, Bell)
        if (actionIconRes != null) {
            binding.btnHeaderAction.visibility = View.VISIBLE
            binding.ivHeaderActionIcon.setImageResource(actionIconRes)
            binding.btnHeaderAction.setOnClickListener {
                it.applyPressEffect { onActionClick?.invoke() }
            }

            // Action Badge
            if (actionBadgeCount != null && actionBadgeCount > 0) {
                binding.tvHeaderActionBadge.visibility = View.VISIBLE
                binding.tvHeaderActionBadge.text = if (actionBadgeCount > 99) "99+" else "$actionBadgeCount"
            } else {
                binding.tvHeaderActionBadge.visibility = View.GONE
            }
        } else {
            binding.btnHeaderAction.visibility = View.GONE
        }

        // 5. Secondary Icon Action (e.g. Filter)
        if (secondaryActionIconRes != null) {
            binding.btnHeaderActionSecondary.visibility = View.VISIBLE
            binding.ivHeaderActionSecondaryIcon.setImageResource(secondaryActionIconRes)
            binding.btnHeaderActionSecondary.setOnClickListener {
                it.applyPressEffect { onSecondaryActionClick?.invoke() }
            }
        } else {
            binding.btnHeaderActionSecondary.visibility = View.GONE
        }
    }

    fun setTitle(title: CharSequence) {
        binding.tvHeaderTitle.text = title
    }

    fun setSubtitle(subtitle: CharSequence?) {
        if (!subtitle.isNullOrBlank()) {
            binding.tvHeaderSubtitle.text = subtitle
            binding.tvHeaderSubtitle.visibility = View.VISIBLE
        } else {
            binding.tvHeaderSubtitle.visibility = View.GONE
        }
    }

    fun setActionText(text: CharSequence?, onClick: (() -> Unit)? = null) {
        if (!text.isNullOrBlank()) {
            binding.btnHeaderTextAction.visibility = View.VISIBLE
            binding.tvHeaderTextAction.text = text
            if (onClick != null) {
                binding.btnHeaderTextAction.setOnClickListener {
                    it.applyPressEffect { onClick.invoke() }
                }
            }
        } else {
            binding.btnHeaderTextAction.visibility = View.GONE
        }
    }

    fun setTransparentBackground() {
        binding.appHeaderRoot.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        elevation = 0f
    }

    fun setActionBadge(count: Int) {
        if (count > 0) {
            binding.tvHeaderActionBadge.visibility = View.VISIBLE
            binding.tvHeaderActionBadge.text = if (count > 99) "99+" else "$count"
        } else {
            binding.tvHeaderActionBadge.visibility = View.GONE
        }
    }

    fun setActionIcon(@DrawableRes iconRes: Int, onClick: (() -> Unit)? = null) {
        binding.btnHeaderAction.visibility = View.VISIBLE
        binding.ivHeaderActionIcon.setImageResource(iconRes)
        if (onClick != null) {
            binding.btnHeaderAction.setOnClickListener {
                it.applyPressEffect { onClick.invoke() }
            }
        }
    }

    fun startActionRotateAnimation() {
        val rotate = RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
            duration = 500
        }
        binding.ivHeaderActionIcon.startAnimation(rotate)
    }

    fun startSecondaryActionRotateAnimation() {
        val rotate = RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
            duration = 500
        }
        binding.ivHeaderActionSecondaryIcon.startAnimation(rotate)
    }

    val actionButton: FrameLayout get() = binding.btnHeaderAction
    val textActionButton: FrameLayout get() = binding.btnHeaderTextAction
    val navButton: FrameLayout get() = binding.btnHeaderNav
    val actionSecondaryButton: FrameLayout get() = binding.btnHeaderActionSecondary
    val titleTextView: TextView get() = binding.tvHeaderTitle
    val subtitleTextView: TextView get() = binding.tvHeaderSubtitle

    private fun View.applyPressEffect(onEnd: () -> Unit = {}) {
        this.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(60)
            .withEndAction {
                this.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(70)
                    .withEndAction { onEnd() }
                    .start()
            }
            .start()
    }
}
