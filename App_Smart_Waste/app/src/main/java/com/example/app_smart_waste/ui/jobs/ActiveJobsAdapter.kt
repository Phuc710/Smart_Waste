package com.example.app_smart_waste.ui.jobs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.JobDto

class ActiveJobsAdapter(
    private val onCardClick: (JobDto) -> Unit,
    private val onAcceptClick: (JobDto) -> Unit,
    private val onRejectClick: (JobDto) -> Unit,
    private val onExecuteClick: (JobDto) -> Unit
) : ListAdapter<JobDto, RecyclerView.ViewHolder>(JobDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_ASSIGNED = 1
        private const val VIEW_TYPE_ACCEPTED = 2
        private const val VIEW_TYPE_IN_PROGRESS = 3
        private const val VIEW_TYPE_PAUSED = 4
    }

    override fun getItemViewType(position: Int): Int {
        val job = getItem(position)
        return when (job.status) {
            "ASSIGNED", "PENDING" -> VIEW_TYPE_ASSIGNED
            "ACCEPTED" -> VIEW_TYPE_ACCEPTED
            "PAUSED" -> VIEW_TYPE_PAUSED
            else -> VIEW_TYPE_IN_PROGRESS
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_ASSIGNED -> {
                val v = inflater.inflate(R.layout.item_job_assigned_card, parent, false)
                AssignedViewHolder(v)
            }
            VIEW_TYPE_ACCEPTED -> {
                val v = inflater.inflate(R.layout.item_job_accepted_card, parent, false)
                AcceptedViewHolder(v)
            }
            VIEW_TYPE_PAUSED -> {
                val v = inflater.inflate(R.layout.item_job_paused_card, parent, false)
                PausedViewHolder(v)
            }
            else -> {
                val v = inflater.inflate(R.layout.item_job_in_progress_card, parent, false)
                InProgressViewHolder(v)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val job = getItem(position)
        when (holder) {
            is AssignedViewHolder -> holder.bind(job)
            is AcceptedViewHolder -> holder.bind(job)
            is InProgressViewHolder -> holder.bind(job)
            is PausedViewHolder -> holder.bind(job)
        }
    }

    inner class AssignedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root = itemView.findViewById<View>(R.id.cardJobAssignedRoot)
        private val tvCode = itemView.findViewById<TextView>(R.id.tvAssignedJobCode)
        private val tvDispatcher = itemView.findViewById<TextView>(R.id.tvAssignedDispatcherName)
        private val tvStops = itemView.findViewById<TextView>(R.id.tvAssignedStops)
        private val tvDistance = itemView.findViewById<TextView>(R.id.tvAssignedDistance)
        private val tvDuration = itemView.findViewById<TextView>(R.id.tvAssignedDuration)
        private val tvRouteSummary = itemView.findViewById<TextView>(R.id.tvAssignedRouteSummary)
        private val btnAccept = itemView.findViewById<Button>(R.id.btnAcceptAssigned)
        private val btnReject = itemView.findViewById<Button>(R.id.btnRejectAssigned)

        fun bind(job: JobDto) {
            val code = if (job.id.startsWith("JOB_") || job.id.startsWith("#")) job.id else "#JOB_${job.id}"
            tvCode.text = code
            tvDispatcher.text = job.employeeName ?: "Admin điều phối"

            val stopsCount = job.targetBinIds?.size ?: 3
            tvStops.text = "$stopsCount điểm gom"
            tvDistance.text = "4.8 km"
            tvDuration.text = "~25 phút"
            tvRouteSummary.text = "📍 Chợ Bến Thành → Nguyễn Huệ → Cột Cờ"

            root.setOnClickListener {
                it.applyPressEffect { onCardClick(job) }
            }
            btnAccept.setOnClickListener {
                it.applyPressEffect { onAcceptClick(job) }
            }
            btnReject.setOnClickListener {
                it.applyPressEffect { onRejectClick(job) }
            }
        }
    }

    inner class AcceptedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root = itemView.findViewById<View>(R.id.cardJobAcceptedRoot)
        private val tvCode = itemView.findViewById<TextView>(R.id.tvAcceptedJobCode)
        private val tvDispatcher = itemView.findViewById<TextView>(R.id.tvAcceptedDispatcherName)
        private val tvStops = itemView.findViewById<TextView>(R.id.tvAcceptedStops)
        private val tvDistance = itemView.findViewById<TextView>(R.id.tvAcceptedDistance)
        private val tvDuration = itemView.findViewById<TextView>(R.id.tvAcceptedDuration)
        private val btnStart = itemView.findViewById<Button>(R.id.btnStartExecution)

        fun bind(job: JobDto) {
            val code = if (job.id.startsWith("JOB_") || job.id.startsWith("#")) job.id else "#JOB_${job.id}"
            tvCode.text = code
            tvDispatcher.text = job.employeeName ?: "Admin điều phối"

            val stopsCount = job.targetBinIds?.size ?: 3
            tvStops.text = "$stopsCount điểm gom"
            tvDistance.text = "4.8 km"
            tvDuration.text = "~25 phút"

            root.setOnClickListener {
                it.applyPressEffect { onCardClick(job) }
            }
            btnStart.setOnClickListener {
                it.applyPressEffect { onExecuteClick(job) }
            }
        }
    }

    inner class InProgressViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root = itemView.findViewById<View>(R.id.cardJobInProgressRoot)
        private val tvCode = itemView.findViewById<TextView>(R.id.tvInProgressJobCode)
        private val tvPercent = itemView.findViewById<TextView>(R.id.tvInProgressPercentText)
        private val tvSummary = itemView.findViewById<TextView>(R.id.tvInProgressPointsSummary)
        private val progressBar = itemView.findViewById<ProgressBar>(R.id.pbInProgressCard)
        private val tvCurrentBin = itemView.findViewById<TextView>(R.id.tvInProgressCurrentBin)
        private val btnContinue = itemView.findViewById<Button>(R.id.btnContinueExecution)

        fun bind(job: JobDto) {
            val code = if (job.id.startsWith("JOB_") || job.id.startsWith("#")) job.id else "#JOB_${job.id}"
            tvCode.text = code

            val done = job.completedBinIds?.size ?: (job.collectedBins ?: 2)
            val total = job.targetBinIds?.size ?: (job.totalBins ?: 3)
            val pct = if (total > 0) (done * 100 / total) else 67

            tvPercent.text = "$pct%"
            tvSummary.text = "$done / $total điểm đã hoàn thành"
            progressBar.progress = pct
            tvCurrentBin.text = "Điểm hiện tại: BIN_HCM_04 (Nguyễn Huệ)"

            root.setOnClickListener {
                it.applyPressEffect { onCardClick(job) }
            }
            btnContinue.setOnClickListener {
                it.applyPressEffect { onExecuteClick(job) }
            }
        }
    }

    inner class PausedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root = itemView.findViewById<View>(R.id.cardJobPausedRoot)
        private val tvCode = itemView.findViewById<TextView>(R.id.tvPausedJobCode)
        private val tvTimer = itemView.findViewById<TextView>(R.id.tvPausedDurationTimer)
        private val tvReason = itemView.findViewById<TextView>(R.id.tvPausedReason)
        private val btnResume = itemView.findViewById<Button>(R.id.btnResumeExecution)

        fun bind(job: JobDto) {
            val code = if (job.id.startsWith("JOB_") || job.id.startsWith("#")) job.id else "#JOB_${job.id}"
            tvCode.text = code
            tvTimer.text = "Đã dừng: 00:14:20"
            tvReason.text = if (!job.pauseReason.isNullOrBlank()) "Lý do: ${job.pauseReason}" else "Lý do: Kẹt xe giờ cao điểm"

            root.setOnClickListener {
                it.applyPressEffect { onCardClick(job) }
            }
            btnResume.setOnClickListener {
                it.applyPressEffect { onExecuteClick(job) }
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

    class JobDiffCallback : DiffUtil.ItemCallback<JobDto>() {
        override fun areItemsTheSame(oldItem: JobDto, newItem: JobDto): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: JobDto, newItem: JobDto): Boolean = oldItem == newItem
    }
}
