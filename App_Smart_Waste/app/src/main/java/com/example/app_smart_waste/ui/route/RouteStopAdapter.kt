package com.example.app_smart_waste.ui.route

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.JobItemDto
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.databinding.ItemRouteStopBinding
import kotlin.math.roundToInt

data class RouteStopUiModel(
    val item: JobItemDto,
    val bin: SmartBinDto?,
    val sequenceNumber: Int
)

class RouteStopAdapter(
    private val onStopClick: (RouteStopUiModel) -> Unit
) : ListAdapter<RouteStopUiModel, RouteStopAdapter.StopViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StopViewHolder {
        val binding = ItemRouteStopBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return StopViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StopViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StopViewHolder(
        private val binding: ItemRouteStopBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(uiModel: RouteStopUiModel) {
            val item = uiModel.item
            val bin = uiModel.bin
            val seq = uiModel.sequenceNumber

            binding.tvStopNumber.text = seq.toString()

            val displayCode = when (bin?.deviceId ?: item.binId) {
                "BIN_HCM_01" -> "BIN-023"
                "BIN_HCM_02" -> "BIN-041"
                "BIN_HCM_03" -> "BIN-055"
                "BIN_HCM_04" -> "BIN-012"
                else -> (bin?.deviceId ?: item.binId).replace("BIN_HCM_", "BIN-0")
            }

            binding.tvStopTitle.text = "Thùng $seq – $displayCode"
            binding.tvStopAddress.text = bin?.location ?: bin?.name ?: "Đường Nguyễn Văn Linh, Quận 7"

            val level = (bin?.levelPercent ?: 80.0).roundToInt()
            binding.tvStopLevel.text = "$level%"

            val isCollected = item.status == "COLLECTED" || bin?.collectionStatus == "COLLECTED"

            if (isCollected) {
                binding.tvStopNumber.setBackgroundResource(R.drawable.bg_stop_num_green)
                binding.tvStopStatusBadge.text = "Đã thu gom"
                binding.tvStopStatusBadge.setBackgroundResource(R.drawable.bg_badge_collected)
                binding.tvStopStatusBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.primary_600))
                binding.tvStopTime.text = "08:30"
            } else {
                if (level >= 85) {
                    binding.tvStopNumber.setBackgroundResource(R.drawable.bg_stop_num_red)
                } else if (level >= 70) {
                    binding.tvStopNumber.setBackgroundResource(R.drawable.bg_stop_num_yellow)
                } else {
                    binding.tvStopNumber.setBackgroundResource(R.drawable.bg_stop_num_green)
                }

                binding.tvStopStatusBadge.text = "Chưa thu gom"
                binding.tvStopStatusBadge.setBackgroundResource(R.drawable.bg_badge_pending)
                binding.tvStopStatusBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.navy_600))
                binding.tvStopTime.text = when (seq) {
                    2 -> "08:45"
                    3 -> "09:05"
                    else -> "09:20"
                }
            }

            binding.root.setOnClickListener {
                onStopClick(uiModel)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<RouteStopUiModel>() {
        override fun areItemsTheSame(oldItem: RouteStopUiModel, newItem: RouteStopUiModel): Boolean {
            return oldItem.item.binId == newItem.item.binId
        }

        override fun areContentsTheSame(oldItem: RouteStopUiModel, newItem: RouteStopUiModel): Boolean {
            return oldItem == newItem
        }
    }
}
