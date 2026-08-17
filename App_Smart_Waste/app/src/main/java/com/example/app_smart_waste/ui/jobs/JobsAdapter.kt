package com.example.app_smart_waste.ui.jobs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.databinding.ItemJobCardBinding

class JobsAdapter(
    private var jobs: List<JobDto>,
    private val onItemClick: (JobDto) -> Unit
) : RecyclerView.Adapter<JobsAdapter.JobViewHolder>() {

    fun updateData(newJobs: List<JobDto>) {
        jobs = newJobs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val binding = ItemJobCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return JobViewHolder(binding)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        holder.bind(jobs[position])
    }

    override fun getItemCount(): Int = jobs.size

    inner class JobViewHolder(private val binding: ItemJobCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(job: JobDto) {
            binding.tvJobTitle.text = "Tuyến Thu Gom #${job.id.takeLast(6)}"
            val total = job.totalBins ?: job.targetBinIds?.size ?: (job.items?.size ?: 0)
            val done = job.collectedBins ?: job.completedBinIds?.size ?: (job.items?.count { it.status == "COLLECTED" } ?: 0)
            binding.tvJobDetails.text = "📍 $total điểm thu gom • Đã thu gom $done/$total"

            when (job.status) {
                "IN_PROGRESS", "ACCEPTED" -> {
                    binding.tvJobStatusBadge.text = "Đang làm"
                    binding.tvJobStatusBadge.setBackgroundResource(R.drawable.badge_info)
                    binding.tvJobStatusBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_info_text))
                }
                "PAUSED" -> {
                    binding.tvJobStatusBadge.text = "Tạm dừng"
                    binding.tvJobStatusBadge.setBackgroundResource(R.drawable.badge_warning)
                    binding.tvJobStatusBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_warning_text))
                }
                "COMPLETED" -> {
                    binding.tvJobStatusBadge.text = "Hoàn thành"
                    binding.tvJobStatusBadge.setBackgroundResource(R.drawable.badge_success)
                    binding.tvJobStatusBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.primary_800))
                }
                "CANCELLED", "REJECTED" -> {
                    binding.tvJobStatusBadge.text = "Đã hủy"
                    binding.tvJobStatusBadge.setBackgroundResource(R.drawable.badge_danger)
                    binding.tvJobStatusBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_danger_text))
                }
                else -> {
                    binding.tvJobStatusBadge.text = "Chờ nhận"
                    binding.tvJobStatusBadge.setBackgroundResource(R.drawable.badge_warning)
                    binding.tvJobStatusBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_warning_text))
                }
            }

            binding.root.setOnClickListener {
                it.animate().scaleX(0.98f).scaleY(0.98f).setDuration(100).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    onItemClick(job)
                }.start()
            }
        }
    }
}
