package com.example.app_smart_waste.ui.history

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.utils.TimeUtils
import com.example.app_smart_waste.data.repository.BinsRepository
import com.example.app_smart_waste.data.repository.JobsRepository
import com.example.app_smart_waste.databinding.ActivityJobHistoryDetailBinding
import com.example.app_smart_waste.ui.route.RouteDetailActivity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class JobHistoryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJobHistoryDetailBinding
    private val jobsRepo by lazy { JobsRepository(this) }
    private val binsRepo by lazy { BinsRepository(this) }

    private var currentJob: JobDto? = null
    private var allBinsMap: Map<String, SmartBinDto> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJobHistoryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val jobId = intent.getStringExtra("JOB_ID") ?: "JOB_17238001"

        // Setup Shared AppHeader
        binding.detailAppHeader.configure(
            title = "Chi tiết lịch sử ca",
            subtitle = "#$jobId",
            navIconRes = R.drawable.ic_arrow_back,
            onNavClick = { finish() }
        )

        setupListeners()
        playEntranceAnimation()
        loadData(jobId)
    }

    private fun setupListeners() {
        binding.btnCloseDetail.setOnClickListener {
            it.applyPressEffect { finish() }
        }

        binding.btnViewRouteMap.setOnClickListener {
            it.applyPressEffect {
                val intent = Intent(this, RouteDetailActivity::class.java).apply {
                    putExtra("JOB_ID", currentJob?.id ?: "JOB_17238001")
                }
                startActivity(intent)
            }
        }
    }

    private fun loadData(jobId: String) {
        lifecycleScope.launch {
            val binsRes = binsRepo.getBins()
            val bins = binsRes.getOrDefault(emptyList())
            allBinsMap = bins.associateBy { it.deviceId }

            val jobRes = jobsRepo.getJobDetail(jobId)
            val job = jobRes.getOrNull() ?: createSampleHistoryJob(jobId)
            currentJob = job

            bindJobData(job)
        }
    }

    private fun bindJobData(job: JobDto) {
        val displayCode = if (job.id.startsWith("JOB_") || job.id.startsWith("#")) job.id else "#JOB_${job.id}"
        binding.tvDetailJobTitle.text = "Nhiệm vụ $displayCode"
        binding.detailAppHeader.setSubtitle("Mã ca: $displayCode")

        // 1. Time Range VN +7
        val timeStr = TimeUtils.formatJobTimeRange(job.startedAt ?: job.assignedAt, job.completedAt)
        binding.tvDetailJobTimeVn.text = timeStr

        // 2. Status Badge
        val isCompleted = job.status == "COMPLETED"
        if (isCompleted) {
            binding.tvDetailStatusBadge.text = "✓ Hoàn thành 100%"
            binding.tvDetailStatusBadge.setTextColor(Color.parseColor("#15803D"))
            binding.tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_role_badge_pill)
        } else {
            binding.tvDetailStatusBadge.text = "⊗ Đã hủy"
            binding.tvDetailStatusBadge.setTextColor(Color.parseColor("#DC2626"))
            binding.tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_badge_pill_red)
        }

        // 3. Vehicle & Dispatcher
        binding.tvDetailVehiclePlate.text = "51C-234.56 (8m³)"
        binding.tvDetailDispatcher.text = job.employeeName ?: "Admin Điều phối"

        // 4. 4 KPI Statistics
        val totalStops = job.targetBinIds?.size ?: (job.items?.size ?: 3)
        val doneStops = if (isCompleted) totalStops else (job.completedBinIds?.size ?: 0)
        binding.tvDetailStatPoints.text = "$doneStops / $totalStops"

        val distKm = if ((job.routeData?.distanceMeters ?: 0.0) > 0.0) {
            (job.routeData!!.distanceMeters!! / 100.0).roundToInt() / 10.0
        } else {
            5.2
        }
        binding.tvDetailStatDistance.text = "$distKm km"

        val durMins = if ((job.routeData?.durationSeconds ?: 0.0) > 0.0) {
            (job.routeData!!.durationSeconds!! / 60.0).roundToInt()
        } else {
            45
        }
        binding.tvDetailStatDuration.text = "$durMins phút"

        val estKg = (doneStops * 83).coerceAtLeast(150)
        binding.tvDetailStatWaste.text = "~$estKg kg"

        // 5. Stops Timeline List
        renderStopsList(job, totalStops)
    }

    private fun renderStopsList(job: JobDto, totalStops: Int) {
        val container = binding.llHistoryStopsContainer
        container.removeAllViews()

        binding.tvDetailStopCountBadge.text = "$totalStops điểm dừng"

        val targetBins = job.targetBinIds ?: listOf("BIN_HCM_01", "BIN_HCM_02", "BIN_HCM_04")

        targetBins.forEachIndexed { index, binId ->
            val bin = allBinsMap[binId]
            val itemView = layoutInflater.inflate(R.layout.item_history_detail_stop, container, false)

            val tvStep = itemView.findViewById<TextView>(R.id.tvStopStepNumber)
            val tvBinId = itemView.findViewById<TextView>(R.id.tvStopBinId)
            val tvAddress = itemView.findViewById<TextView>(R.id.tvStopAddress)
            val tvStatus = itemView.findViewById<TextView>(R.id.tvStopStatusBadge)
            val tvLevel = itemView.findViewById<TextView>(R.id.tvStopInitialLevel)
            val tvTime = itemView.findViewById<TextView>(R.id.tvStopCollectedTime)
            val tvWeight = itemView.findViewById<TextView>(R.id.tvStopWeight)
            val photoContainer = itemView.findViewById<View>(R.id.layoutStopPhotoContainer)
            val ivThumb = itemView.findViewById<ImageView>(R.id.ivStopEvidenceThumb)

            val stepNum = index + 1
            tvStep.text = "$stepNum"
            tvBinId.text = bin?.deviceId ?: binId
            tvAddress.text = bin?.location ?: bin?.name ?: "Đường Nguyễn Huệ, Phường Bến Nghé, Quận 1"

            val isDone = job.status == "COMPLETED" || (job.completedBinIds?.contains(binId) == true)
            if (isDone) {
                tvStatus.text = "✓ Đã thu gom"
                tvStatus.setTextColor(Color.parseColor("#15803D"))
                tvStatus.setBackgroundResource(R.drawable.bg_role_badge_pill)
            } else {
                tvStatus.text = "Bỏ qua"
                tvStatus.setTextColor(Color.parseColor("#94A3B8"))
                tvStatus.setBackgroundResource(R.drawable.bg_dialog_item_field)
            }

            val level = (85 + index * 4).coerceAtMost(98)
            tvLevel.text = "$level% → 0%"

            val baseHour = 8
            val baseMinute = 30 + index * 12
            val collectedTimeStr = String.format("%02d:%02d:15", baseHour, baseMinute)
            tvTime.text = collectedTimeStr

            val binWeight = 75 + index * 15
            tvWeight.text = "~$binWeight kg"

            // Photo Evidence
            photoContainer.setOnClickListener {
                it.applyPressEffect {
                    showPhotoPreviewDialog(bin?.deviceId ?: binId, collectedTimeStr)
                }
            }

            container.addView(itemView)
        }
    }

    private fun showPhotoPreviewDialog(binName: String, timeStr: String) {
        val view = layoutInflater.inflate(R.layout.dialog_image_preview, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.findViewById<TextView>(R.id.tvPhotoBinCaption)?.text = "Minh chứng thu gom: $binName ($timeStr • VN +7)"
        view.findViewById<ImageView>(R.id.btnClosePhotoPreview)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnDonePhotoPreview)?.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun createSampleHistoryJob(jobId: String): JobDto {
        return JobDto(
            id = jobId,
            status = "COMPLETED",
            employeeId = "NV-1024",
            employeeName = "Admin Tổng đài",
            targetBinIds = listOf("BIN_HCM_01", "BIN_HCM_02", "BIN_HCM_04"),
            completedBinIds = listOf("BIN_HCM_01", "BIN_HCM_02", "BIN_HCM_04"),
            assignedAt = "2026-05-17T01:30:00.000Z",
            startedAt = "2026-05-17T01:30:00.000Z",
            completedAt = "2026-05-17T02:15:00.000Z"
        )
    }

    private fun playEntranceAnimation() {
        val views = listOf(
            binding.detailAppHeader,
            binding.cardDetailHero,
            binding.cardDetailStats,
            binding.cardDetailStops,
            binding.cardDetailActions
        )

        views.forEachIndexed { index, v ->
            v.alpha = 0f
            v.translationY = 24f
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((index * 45).toLong())
                .setDuration(280)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun View.applyPressEffect(onEnd: () -> Unit = {}) {
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
