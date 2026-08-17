package com.example.app_smart_waste.ui.history

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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

        fun bind(item: JobDisplayModel) {
            val code = if (item.rawJob.id.startsWith("JOB_") || item.rawJob.id.startsWith("#")) item.rawJob.id else "#JOB_${item.rawJob.id}"
            tvCode.text = code
            tvDateTime.text = item.timeLabel

            val isDone = item.statusType == "COMPLETED"
            if (isDone) {
                tvStatusBadge.text = "✓ Hoàn thành"
                tvStatusBadge.setTextColor(Color.parseColor("#15803D"))
                tvStatusBadge.setBackgroundResource(R.drawable.bg_role_badge_pill)
                tvBinsCount.text = "${item.totalBins}/${item.totalBins} điểm"
            } else {
                tvStatusBadge.text = "⊗ Đã hủy"
                tvStatusBadge.setTextColor(Color.parseColor("#DC2626"))
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_pill_red)
                tvBinsCount.text = "0/${item.totalBins} điểm"
            }

            tvDistance.text = "${item.distanceKm} km"
            tvDuration.text = "${item.durationMinutes} phút"

            val estKg = (item.totalBins * 83).coerceAtLeast(150)
            tvWeight.text = "~$estKg kg"

            root.setOnClickListener {
                it.applyPressEffect {
                    onJobClick(item)
                }
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
