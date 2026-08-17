package com.example.app_smart_waste.ui.jobs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.NotificationItemDto
import com.example.app_smart_waste.databinding.ItemNotifDateHeaderBinding
import com.example.app_smart_waste.databinding.ItemNotificationCardBinding

sealed class NotificationListItem {
    data class Header(val dateText: String) : NotificationListItem()
    data class Item(val notification: NotificationItemDto) : NotificationListItem()
}

class NotificationAdapter(
    private val onItemClick: (NotificationItemDto) -> Unit
) : ListAdapter<NotificationListItem, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is NotificationListItem.Header -> VIEW_TYPE_HEADER
            is NotificationListItem.Item -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val binding = ItemNotifDateHeaderBinding.inflate(inflater, parent, false)
            HeaderViewHolder(binding)
        } else {
            val binding = ItemNotificationCardBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is NotificationListItem.Header -> (holder as HeaderViewHolder).bind(item.dateText)
            is NotificationListItem.Item -> (holder as ItemViewHolder).bind(item.notification)
        }
    }

    inner class HeaderViewHolder(
        private val binding: ItemNotifDateHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(dateText: String) {
            binding.tvNotifDateHeader.text = dateText
        }
    }

    inner class ItemViewHolder(
        private val binding: ItemNotificationCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NotificationItemDto) {
            val context = itemView.context

            // Unread Dot
            binding.viewUnreadDot.visibility = if (item.isUnread) View.VISIBLE else View.GONE

            // Title & Subtitle
            binding.tvNotifTitle.text = item.title
            binding.tvNotifSubtitle.text = item.subtitle
            binding.tvNotifTimestamp.text = item.timeStr
            binding.tvNotifCategoryBadge.text = item.categoryText

            // Category & Icon Styling
            when (item.iconType) {
                "TRASH" -> {
                    binding.flNotifIconContainer.setBackgroundResource(R.drawable.bg_circle_notif_red)
                    binding.ivNotifIcon.setImageResource(R.drawable.ic_trash_bin_red)
                    binding.tvNotifCategoryBadge.setBackgroundResource(R.drawable.bg_tag_red)
                    binding.tvNotifCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.status_danger_main))
                }
                "CALENDAR" -> {
                    binding.flNotifIconContainer.setBackgroundResource(R.drawable.bg_circle_notif_green)
                    binding.ivNotifIcon.setImageResource(R.drawable.ic_calendar_task)
                    binding.tvNotifCategoryBadge.setBackgroundResource(R.drawable.bg_tag_green)
                    binding.tvNotifCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.app_success))
                }
                "ROUTE" -> {
                    binding.flNotifIconContainer.setBackgroundResource(R.drawable.bg_circle_notif_blue)
                    binding.ivNotifIcon.setImageResource(R.drawable.ic_route_nodes)
                    binding.tvNotifCategoryBadge.setBackgroundResource(R.drawable.bg_tag_blue)
                    binding.tvNotifCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.app_info))
                }
                "CHECK" -> {
                    binding.flNotifIconContainer.setBackgroundResource(R.drawable.bg_circle_notif_green)
                    binding.ivNotifIcon.setImageResource(R.drawable.ic_check_circle_green)
                    binding.tvNotifCategoryBadge.setBackgroundResource(R.drawable.bg_tag_green)
                    binding.tvNotifCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.app_success))
                }
                "BELL" -> {
                    binding.flNotifIconContainer.setBackgroundResource(R.drawable.bg_circle_notif_yellow)
                    binding.ivNotifIcon.setImageResource(R.drawable.ic_bell_warning)
                    binding.tvNotifCategoryBadge.setBackgroundResource(R.drawable.bg_tag_amber)
                    binding.tvNotifCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.app_warning_dark))
                }
                "TRUCK" -> {
                    binding.flNotifIconContainer.setBackgroundResource(R.drawable.bg_circle_notif_purple)
                    binding.ivNotifIcon.setImageResource(R.drawable.ic_truck_purple)
                    binding.tvNotifCategoryBadge.setBackgroundResource(R.drawable.bg_badge_pill_purple)
                    binding.tvNotifCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.status_purple_dark))
                }
                else -> {
                    binding.flNotifIconContainer.setBackgroundResource(R.drawable.bg_circle_notif_blue)
                    binding.ivNotifIcon.setImageResource(R.drawable.ic_info_circle)
                    binding.tvNotifCategoryBadge.setBackgroundResource(R.drawable.bg_tag_blue)
                    binding.tvNotifCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.app_info))
                }
            }

            binding.notifCardRoot.setOnClickListener {
                it.animate().scaleX(0.98f).scaleY(0.98f).setDuration(80).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    onItemClick(item)
                }.start()
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1

        private val DiffCallback = object : DiffUtil.ItemCallback<NotificationListItem>() {
            override fun areItemsTheSame(oldItem: NotificationListItem, newItem: NotificationListItem): Boolean {
                return when {
                    oldItem is NotificationListItem.Header && newItem is NotificationListItem.Header ->
                        oldItem.dateText == newItem.dateText
                    oldItem is NotificationListItem.Item && newItem is NotificationListItem.Item ->
                        oldItem.notification.id == newItem.notification.id
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: NotificationListItem, newItem: NotificationListItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
