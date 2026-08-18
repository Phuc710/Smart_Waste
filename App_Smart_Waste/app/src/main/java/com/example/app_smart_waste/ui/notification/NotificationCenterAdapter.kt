package com.example.app_smart_waste.ui.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.app_smart_waste.core.model.NotificationModel
import com.example.app_smart_waste.core.utils.applyPressEffect
import com.example.app_smart_waste.databinding.ItemNotificationCenterCardBinding
import com.example.app_smart_waste.databinding.ItemNotificationCenterHeaderBinding

sealed class NotificationListItem {
    data class Header(val dateTitle: String) : NotificationListItem()
    data class Item(val notification: NotificationModel) : NotificationListItem()
}

class NotificationCenterAdapter(
    private val onItemClick: (NotificationModel) -> Unit
) : ListAdapter<NotificationListItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1

        private val DiffCallback = object : DiffUtil.ItemCallback<NotificationListItem>() {
            override fun areItemsTheSame(oldItem: NotificationListItem, newItem: NotificationListItem): Boolean {
                return when {
                    oldItem is NotificationListItem.Header && newItem is NotificationListItem.Header ->
                        oldItem.dateTitle == newItem.dateTitle
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

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is NotificationListItem.Header -> VIEW_TYPE_HEADER
            is NotificationListItem.Item -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val binding = ItemNotificationCenterHeaderBinding.inflate(inflater, parent, false)
            HeaderViewHolder(binding)
        } else {
            val binding = ItemNotificationCenterCardBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding, onItemClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is NotificationListItem.Header -> (holder as HeaderViewHolder).bind(item.dateTitle)
            is NotificationListItem.Item -> (holder as ItemViewHolder).bind(item.notification)
        }
    }

    class HeaderViewHolder(
        private val binding: ItemNotificationCenterHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.tvDateGroupTitle.text = title
        }
    }

    class ItemViewHolder(
        private val binding: ItemNotificationCenterCardBinding,
        private val onItemClick: (NotificationModel) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NotificationModel) {
            // Icon & Background
            binding.containerCenterNotifIcon.setBackgroundResource(item.iconBgRes)
            binding.ivCenterNotifIcon.setImageResource(item.iconRes)

            // Titles
            binding.tvCenterNotifTitle.text = item.title
            binding.tvCenterNotifSubtitle.text = item.subtitle
            binding.tvCenterNotifTime.text = item.timeStr

            // Unread Dot Indicator
            binding.viewCenterNotifDot.visibility = if (item.isUnread) View.VISIBLE else View.GONE

            // Click listener with tactile micro-press physics
            binding.cardNotificationItem.setOnClickListener { view ->
                view.applyPressEffect {
                    onItemClick(item)
                }
            }
        }
    }
}
