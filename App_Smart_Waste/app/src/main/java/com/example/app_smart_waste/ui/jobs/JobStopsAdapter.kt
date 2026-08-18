package com.example.app_smart_waste.ui.jobs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.JobStopStatus
import com.example.app_smart_waste.core.model.JobStopUiModel
import com.example.app_smart_waste.databinding.ItemJobStopRowBinding

class JobStopsAdapter(
    private val onStopClick: (JobStopUiModel) -> Unit
) : ListAdapter<JobStopUiModel, JobStopsAdapter.StopViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StopViewHolder {
        val binding = ItemJobStopRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StopViewHolder(binding, onStopClick)
    }

    override fun onBindViewHolder(holder: StopViewHolder, position: Int) {
        val prevItem = if (position > 0) getItem(position - 1) else null
        holder.bind(getItem(position), position, itemCount, prevItem)
    }

    class StopViewHolder(
        private val binding: ItemJobStopRowBinding,
        private val onStopClick: (JobStopUiModel) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: JobStopUiModel, position: Int, totalCount: Int, prevItem: JobStopUiModel?) {
            val context = binding.root.context
            binding.tvStopBinId.text = item.binId
            binding.tvStopAddress.text = item.address ?: item.binName ?: "Vị trí thùng"

            // 1. Timeline Connector Line Visibility
            binding.timelineLineTop.visibility = if (position == 0) android.view.View.INVISIBLE else android.view.View.VISIBLE
            binding.timelineLineBottom.visibility = if (position == totalCount - 1) android.view.View.INVISIBLE else android.view.View.VISIBLE

            val isPrevCollected = prevItem?.status == JobStopStatus.COLLECTED
            val isCurrentCollected = item.status == JobStopStatus.COLLECTED

            // Top line color is green if previous was collected
            binding.timelineLineTop.setBackgroundColor(
                if (isPrevCollected) ContextCompat.getColor(context, R.color.profile_green_primary)
                else ContextCompat.getColor(context, R.color.profile_border_light)
            )

            // Bottom line color is green if current is collected
            binding.timelineLineBottom.setBackgroundColor(
                if (isCurrentCollected) ContextCompat.getColor(context, R.color.profile_green_primary)
                else ContextCompat.getColor(context, R.color.profile_border_light)
            )

            // 2. Status Badge & Icon Indicator
            when {
                item.status == JobStopStatus.COLLECTED -> {
                    // 1. Đã thu gom (Green Check ✓)
                    binding.cardJobStopRoot.setBackgroundResource(R.drawable.bg_profile_item_card)
                    binding.containerStopIcon.setBackgroundResource(R.drawable.bg_avatar_circle_green)
                    binding.tvStopIconGlyph.text = "✓"
                    binding.tvStopIconGlyph.setTextColor(ContextCompat.getColor(context, R.color.white))

                    binding.tvStopStatusBadge.text = "Đã thu gom"
                    binding.tvStopStatusBadge.setBackgroundResource(R.drawable.bg_role_badge_pill)
                    binding.tvStopStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.profile_green_primary))
                }
                item.isNext -> {
                    // 2. Đang đến (Blue Pulse Ring ⦿)
                    binding.cardJobStopRoot.setBackgroundResource(R.drawable.bg_item_next_stop_active)
                    binding.containerStopIcon.setBackgroundResource(R.drawable.bg_badge_on_active_tab)
                    binding.tvStopIconGlyph.text = "⦿"
                    binding.tvStopIconGlyph.setTextColor(ContextCompat.getColor(context, R.color.profile_tag_blue_text))

                    binding.tvStopStatusBadge.text = "Đang đến"
                    binding.tvStopStatusBadge.setBackgroundResource(R.drawable.bg_tag_dang_thuc_hien)
                    binding.tvStopStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.profile_tag_blue_text))
                }
                else -> {
                    // 3. Chưa thu gom (Gray Circle ⌄)
                    binding.cardJobStopRoot.setBackgroundResource(R.drawable.bg_profile_item_card)
                    binding.containerStopIcon.setBackgroundResource(R.drawable.bg_dialog_item_field)
                    binding.tvStopIconGlyph.text = "⌄"
                    binding.tvStopIconGlyph.setTextColor(ContextCompat.getColor(context, R.color.profile_text_secondary))

                    binding.tvStopStatusBadge.text = "Chưa thu gom"
                    binding.tvStopStatusBadge.setBackgroundResource(R.drawable.bg_chip_filter_inactive)
                    binding.tvStopStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.profile_text_secondary))
                }
            }

            binding.root.setOnClickListener {
                onStopClick(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<JobStopUiModel>() {
            override fun areItemsTheSame(oldItem: JobStopUiModel, newItem: JobStopUiModel): Boolean {
                return oldItem.binId == newItem.binId
            }

            override fun areContentsTheSame(oldItem: JobStopUiModel, newItem: JobStopUiModel): Boolean {
                return oldItem == newItem
            }
        }
    }
}
