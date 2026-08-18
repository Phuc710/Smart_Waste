package com.example.app_smart_waste.ui.jobs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.JobHistoryUiModel
import com.example.app_smart_waste.core.model.JobStatus
import com.example.app_smart_waste.databinding.ItemJobHistoryCardBinding

class JobHistoryAdapter(
    private val onItemClick: (JobHistoryUiModel) -> Unit
) : ListAdapter<JobHistoryUiModel, JobHistoryAdapter.HistoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemJobHistoryCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HistoryViewHolder(
        private val binding: ItemJobHistoryCardBinding,
        private val onItemClick: (JobHistoryUiModel) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: JobHistoryUiModel) {
            binding.tvHistoryJobCode.text = item.displayCode
            binding.tvHistoryDateTime.text = "${item.dateStr} • ${item.timeRangeStr}"
            binding.tvHistoryStatusBadge.text = item.statusBadgeText

            // Status styling & card outline border
            val context = binding.root.context
            when (item.status) {
                JobStatus.COMPLETED -> {
                    binding.cardJobHistoryRoot.setBackgroundResource(R.drawable.bg_history_card_completed)
                    binding.tvHistoryStatusBadge.setBackgroundResource(R.drawable.bg_status_completed_pill)
                    binding.tvHistoryStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.profile_green_primary))
                }
                JobStatus.CANCELLED, JobStatus.REJECTED -> {
                    binding.cardJobHistoryRoot.setBackgroundResource(R.drawable.bg_history_card_cancelled)
                    binding.tvHistoryStatusBadge.setBackgroundResource(R.drawable.bg_tag_danger)
                    binding.tvHistoryStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.profile_danger))
                }
                JobStatus.EXPIRED -> {
                    binding.cardJobHistoryRoot.setBackgroundResource(R.drawable.bg_history_card_expired)
                    binding.tvHistoryStatusBadge.setBackgroundResource(R.drawable.bg_role_badge_pill)
                    binding.tvHistoryStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.profile_warning))
                }
                else -> {
                    binding.cardJobHistoryRoot.setBackgroundResource(R.drawable.bg_history_card_completed)
                    binding.tvHistoryStatusBadge.setBackgroundResource(R.drawable.bg_status_completed_pill)
                    binding.tvHistoryStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.profile_green_primary))
                }
            }

            // Metrics
            binding.tvHistoryBinsCount.text = "${item.totalStops} điểm"
            binding.tvHistoryDistance.text = String.format("%.1f km", item.distanceKm)
            binding.tvHistoryDuration.text = if (item.durationMinutes != null && item.durationMinutes > 0) {
                "${item.durationMinutes} phút"
            } else {
                "--"
            }

            // Route or Reason
            binding.tvHistoryReason.text = item.routeOrReason

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<JobHistoryUiModel>() {
            override fun areItemsTheSame(oldItem: JobHistoryUiModel, newItem: JobHistoryUiModel): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: JobHistoryUiModel, newItem: JobHistoryUiModel): Boolean {
                return oldItem == newItem
            }
        }
    }
}
