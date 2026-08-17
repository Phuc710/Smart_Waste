package com.example.app_smart_waste.ui.jobs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.JobDisplayModel
import com.example.app_smart_waste.databinding.ItemJobBinRowBinding
import com.example.app_smart_waste.databinding.ItemJobFullCardBinding

class JobFullAdapter(
    private val onCardClick: (JobDisplayModel) -> Unit,
    private val onPrimaryActionClick: (JobDisplayModel) -> Unit,
    private val onRejectClick: (JobDisplayModel) -> Unit
) : ListAdapter<JobDisplayModel, JobFullAdapter.JobViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val binding = ItemJobFullCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return JobViewHolder(binding)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class JobViewHolder(
        private val binding: ItemJobFullCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: JobDisplayModel) {
            val context = itemView.context

            // 1. Status Badge & Timestamp
            binding.tvCardStatusBadge.text = item.statusBadgeText
            binding.tvCardTimestamp.text = item.timeLabel

            when (item.statusType) {
                "ASSIGNED" -> {
                    binding.tvCardStatusBadge.setBackgroundResource(R.drawable.bg_tag_moi_giao)
                    binding.tvCardStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_danger_main))
                }
                "IN_PROGRESS" -> {
                    binding.tvCardStatusBadge.setBackgroundResource(R.drawable.bg_tag_dang_thuc_hien)
                    binding.tvCardStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.app_info))
                }
                "COMPLETED" -> {
                    binding.tvCardStatusBadge.setBackgroundResource(R.drawable.bg_tag_hoan_thanh)
                    binding.tvCardStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.app_success))
                }
                else -> {
                    binding.tvCardStatusBadge.setBackgroundResource(R.drawable.bg_tag_moi_giao)
                    binding.tvCardStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_danger_main))
                }
            }

            // 2. Titles & Codes
            binding.tvCardRouteTitle.text = item.displayCode
            binding.tvCardJobNumber.text = "Mã nhiệm vụ: ${item.jobNumber}"
            binding.tvCardLocation.text = item.locationArea

            // 3. Summary Metrics
            binding.tvMetricBinsTotal.text = "${item.totalBins} thùng rác"
            binding.tvMetricBinsCollected.text = "${item.collectedBins}/${item.totalBins} đã gom"
            binding.tvMetricDistance.text = "${String.format("%.1f", item.distanceKm)} km"
            binding.tvMetricDuration.text = "~ ${item.durationMinutes} phút"
            binding.tvMetricPriorityTitle.text = item.priorityText
            binding.tvMetricPrioritySubtitle.text = item.prioritySubtext

            // Progress bar for In-Progress
            if (item.statusType == "IN_PROGRESS") {
                binding.layoutProgressPercentBar.visibility = View.VISIBLE
                binding.pbJobCardProgress.progress = item.progressPercent
                binding.tvJobCardProgressPercent.text = "${item.progressPercent}%"
            } else {
                binding.layoutProgressPercentBar.visibility = View.GONE
            }

            // 4. Expanded Bins List Rows
            binding.llCardBinsContainer.removeAllViews()
            val inflater = LayoutInflater.from(context)

            item.binsList.forEachIndexed { index, bin ->
                val rowBinding = ItemJobBinRowBinding.inflate(inflater, binding.llCardBinsContainer, false)
                rowBinding.tvRowBinId.text = bin.binId
                rowBinding.tvRowBinFill.text = "${bin.fillPercent}%"
                rowBinding.tvRowBinAddress.text = bin.address

                // Color code fill percent
                if (bin.fillPercent >= 90) {
                    rowBinding.tvRowBinFill.setBackgroundResource(R.drawable.bg_bin_percent_red)
                    rowBinding.tvRowBinFill.setTextColor(ContextCompat.getColor(context, R.color.status_danger_main))
                } else if (bin.fillPercent >= 80) {
                    rowBinding.tvRowBinFill.setBackgroundResource(R.drawable.bg_bin_percent_orange)
                    rowBinding.tvRowBinFill.setTextColor(ContextCompat.getColor(context, R.color.app_warning_dark))
                } else {
                    rowBinding.tvRowBinFill.setBackgroundResource(R.drawable.bg_bin_percent_green)
                    rowBinding.tvRowBinFill.setTextColor(ContextCompat.getColor(context, R.color.app_success_dark))
                }

                // Hide divider on last item
                if (index == item.binsList.lastIndex) {
                    rowBinding.rowDivider.visibility = View.GONE
                }

                binding.llCardBinsContainer.addView(rowBinding.root)
            }

            // 5. Action Buttons Config
            when (item.statusType) {
                "ASSIGNED" -> {
                    binding.layoutCardActions.visibility = View.VISIBLE
                    binding.btnCardReject.visibility = View.VISIBLE
                    binding.btnCardReject.text = "✕ Từ chối"
                    binding.btnCardPrimaryAction.text = "✓ Nhận nhiệm vụ"
                }
                "IN_PROGRESS" -> {
                    binding.layoutCardActions.visibility = View.VISIBLE
                    binding.btnCardReject.visibility = View.VISIBLE
                    binding.btnCardReject.text = "⏸ Tạm dừng"
                    binding.btnCardPrimaryAction.text = "▶ Mở bản đồ lộ trình"
                }
                "ACCEPTED" -> {
                    binding.layoutCardActions.visibility = View.VISIBLE
                    binding.btnCardReject.visibility = View.GONE
                    binding.btnCardPrimaryAction.text = "▶ Bắt đầu di chuyển"
                }
                "COMPLETED" -> {
                    binding.layoutCardActions.visibility = View.GONE
                }
                else -> {
                    binding.layoutCardActions.visibility = View.GONE
                }
            }

            // 6. Tactile Click Listeners
            binding.cardRoot.setOnClickListener {
                it.animate().scaleX(0.98f).scaleY(0.98f).setDuration(80).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    onCardClick(item)
                }.start()
            }

            binding.layoutJobDetailChevron.setOnClickListener {
                onCardClick(item)
            }

            binding.btnCardPrimaryAction.setOnClickListener {
                it.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    onPrimaryActionClick(item)
                }.start()
            }

            binding.btnCardReject.setOnClickListener {
                it.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    onRejectClick(item)
                }.start()
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<JobDisplayModel>() {
            override fun areItemsTheSame(oldItem: JobDisplayModel, newItem: JobDisplayModel): Boolean {
                return oldItem.rawJob.id == newItem.rawJob.id
            }

            override fun areContentsTheSame(oldItem: JobDisplayModel, newItem: JobDisplayModel): Boolean {
                return oldItem == newItem
            }
        }
    }
}
