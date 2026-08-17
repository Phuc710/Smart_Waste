package com.example.app_smart_waste.ui.history

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.JobDisplayModel

class HistoryAdapter(
    private val onJobClick: (JobDisplayModel) -> Unit
) : ListAdapter<JobDisplayModel, HistoryAdapter.HistoryViewHolder>(JobDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_job_history_card, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root = itemView.findViewById<View>(R.id.cardJobHistoryRoot)
        private val tvCode = itemView.findViewById<TextView>(R.id.tvHistoryJobCode)
        private val tvDateTime = itemView.findViewById<TextView>(R.id.tvHistoryDateTime)
        private val tvStatusBadge = itemView.findViewById<TextView>(R.id.tvHistoryStatusBadge)
        private val tvBinsCount = itemView.findViewById<TextView>(R.id.tvHistoryBinsCount)
        private val tvDistance = itemView.findViewById<TextView>(R.id.tvHistoryDistance)
        private val tvDuration = itemView.findViewById<TextView>(R.id.tvHistoryDuration)
        private val tvWeight = itemView.findViewById<TextView>(R.id.tvHistoryWeight)
        private val tvReason = itemView.findViewById<TextView>(R.id.tvHistoryReason)

        fun bind(item: JobDisplayModel) {
            tvCode.text = formatJobCode(item.rawJob.id)
            tvDateTime.text = item.timeLabel

            val doneCount = item.rawJob.completedBinIds?.size ?: item.collectedBins
            val totalCount = item.totalBins

            when (item.statusType.uppercase()) {
                "COMPLETED" -> {
                    root.setBackgroundResource(R.drawable.bg_history_card_completed)
                    tvStatusBadge.text = "Hoàn thành"
                    tvStatusBadge.setTextColor(Color.parseColor("#166534"))
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_status_completed_pill)
                    tvBinsCount.text = "$doneCount/$totalCount"
                    tvDistance.text = if (item.distanceKm > 0) "${item.distanceKm} km" else "--"
                    tvDuration.text = if (item.durationMinutes > 0) "${item.durationMinutes} phút" else "--"
                    tvWeight.text = "--"
                    tvReason.text = "Hoàn thành tất cả điểm thu gom"
                }
                "CANCELLED", "CANCELED", "REJECTED" -> {
                    root.setBackgroundResource(R.drawable.bg_history_card_cancelled)
                    tvStatusBadge.text = "Đã hủy"
                    tvStatusBadge.setTextColor(Color.parseColor("#991B1B"))
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_status_cancelled_pill)
                    tvBinsCount.text = "$doneCount/$totalCount"
                    tvDistance.text = if (item.distanceKm > 0) "${item.distanceKm} km" else "--"
                    tvDuration.text = if (item.durationMinutes > 0) "${item.durationMinutes} phút" else "--"
                    tvWeight.text = "--"
                    tvReason.text = item.cancelReason ?: "Đã hủy nhiệm vụ"
                }
                "EXPIRED" -> {
                    root.setBackgroundResource(R.drawable.bg_history_card_expired)
                    tvStatusBadge.text = "Hết hạn"
                    tvStatusBadge.setTextColor(Color.parseColor("#9A3412"))
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_status_expired_pill)
                    tvBinsCount.text = "$doneCount/$totalCount"
                    tvDistance.text = if (item.distanceKm > 0) "${item.distanceKm} km" else "--"
                    tvDuration.text = if (item.durationMinutes > 0) "${item.durationMinutes} phút" else "--"
                    tvWeight.text = "--"
                    tvReason.text = "Hết thời gian nhận ca"
                }
                else -> {
                    root.setBackgroundResource(R.drawable.bg_card_profile)
                    tvStatusBadge.text = item.statusBadgeText
                    tvStatusBadge.setTextColor(Color.GRAY)
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_role_badge_pill)
                    tvBinsCount.text = "$doneCount/$totalCount"
                    tvDistance.text = if (item.distanceKm > 0) "${item.distanceKm} km" else "--"
                    tvDuration.text = if (item.durationMinutes > 0) "${item.durationMinutes} phút" else "--"
                    tvWeight.text = "--"
                    tvReason.text = "Trạng thái khác"
                }
            }

            root.setOnClickListener {
                it.applyPressEffect {
                    onJobClick(item)
                }
            }
        }

        private fun formatJobCode(id: String): String {
            val clean = id.removePrefix("#")
            return when {
                clean.startsWith("JOB_") -> "#$clean"
                else -> "#JOB_$clean"
            }
        }

        private fun View.applyPressEffect(onEnd: () -> Unit) {
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
    }

    class JobDiffCallback : DiffUtil.ItemCallback<JobDisplayModel>() {
        override fun areItemsTheSame(oldItem: JobDisplayModel, newItem: JobDisplayModel): Boolean =
            oldItem.rawJob.id == newItem.rawJob.id

        override fun areContentsTheSame(oldItem: JobDisplayModel, newItem: JobDisplayModel): Boolean =
            oldItem == newItem
    }
}
