package com.example.app_smart_waste.ui.incident

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.IncidentReportDto

class IncidentAdapter(
    private val onItemClick: (IncidentReportDto) -> Unit
) : ListAdapter<IncidentReportDto, IncidentAdapter.IncidentViewHolder>(IncidentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IncidentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_incident_card, parent, false)
        return IncidentViewHolder(view)
    }

    override fun onBindViewHolder(holder: IncidentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class IncidentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root: View = itemView.findViewById(R.id.cardIncidentRoot)
        private val tvCode: TextView = itemView.findViewById(R.id.tvIncidentCode)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvIncidentStatusPill)
        private val tvBinName: TextView = itemView.findViewById(R.id.tvIncidentBinName)
        private val tvReason: TextView = itemView.findViewById(R.id.tvIncidentReason)
        private val tvDesc: TextView = itemView.findViewById(R.id.tvIncidentDesc)
        private val tvTime: TextView = itemView.findViewById(R.id.tvIncidentTime)
        private val tvHasPhoto: TextView = itemView.findViewById(R.id.tvIncidentHasPhoto)

        fun bind(item: IncidentReportDto) {
            val code = item.id?.let { "#INC_$it" } ?: "#INC"
            tvCode.text = code

            val binDisplay = item.binName ?: item.deviceId
            tvBinName.text = if (item.binLocation != null) "$binDisplay • ${item.binLocation}" else binDisplay

            tvReason.text = item.reason
            tvDesc.text = if (!item.description.isNullOrBlank()) item.description else "Không có ghi chú thêm."

            // Status Pill (2 trạng thái: Đang xử lý, Đã giải quyết)
            if (item.status.equals("RESOLVED", ignoreCase = true) || item.status.equals("DONE", ignoreCase = true)) {
                tvStatus.text = "ĐÃ GIẢI QUYẾT"
                tvStatus.setTextColor(Color.parseColor("#16A34A"))
                tvStatus.setBackgroundResource(R.drawable.bg_badge_pill_green)
            } else {
                tvStatus.text = "ĐANG XỬ LÝ"
                tvStatus.setTextColor(Color.parseColor("#2563EB"))
                tvStatus.setBackgroundResource(R.drawable.bg_badge_pill_blue)
            }

            // Time Formatted
            val time = formatIsoTime(item.createdAt)
            tvTime.text = "🕒 $time"

            tvHasPhoto.visibility = if (item.hasPhoto || !item.imageUrl.isNullOrBlank()) View.VISIBLE else View.GONE

            root.setOnClickListener {
                it.applyPressEffect { onItemClick(item) }
            }
        }

        private fun formatIsoTime(isoStr: String?): String {
            if (isoStr.isNullOrBlank()) return "Gần đây"
            return try {
                val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val date = inputFormat.parse(isoStr) ?: return isoStr
                val outputFormat = java.text.SimpleDateFormat("HH:mm • dd/MM/yyyy", java.util.Locale.getDefault()).apply {
                    timeZone = java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
                }
                outputFormat.format(date)
            } catch (_: Exception) {
                isoStr
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

    class IncidentDiffCallback : DiffUtil.ItemCallback<IncidentReportDto>() {
        override fun areItemsTheSame(oldItem: IncidentReportDto, newItem: IncidentReportDto): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: IncidentReportDto, newItem: IncidentReportDto): Boolean = oldItem == newItem
    }
}
