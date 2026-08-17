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

        val jobId = intent.getStringExtra("JOB_ID").orEmpty()
        if (jobId.isBlank()) {
            finish()
            return
        }

        val displayCode = formatJobCode(jobId)

        // Setup Shared AppHeader
        binding.detailAppHeader.configure(
            title = "Chi tiết lịch sử ca",
            subtitle = displayCode,
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
                val job = currentJob ?: return@applyPressEffect
                val intent = Intent(this, RouteDetailActivity::class.java).apply {
                    putExtra("JOB_ID", job.id)
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
            val job = jobRes.getOrNull()
            if (job != null) {
                currentJob = job
                bindJobData(job)
            } else {
                binding.tvDetailJobTitle.text = "Không tìm thấy dữ liệu ca #$jobId"
            }
        }
    }

    private fun bindJobData(job: JobDto) {
        val displayCode = formatJobCode(job.id)
        binding.tvDetailJobTitle.text = "Nhiệm vụ $displayCode"
        binding.detailAppHeader.setSubtitle("Mã ca: $displayCode")

        // 1. Time Range VN +7
        val timeStr = TimeUtils.formatJobTimeRange(job.startedAt ?: job.assignedAt, job.completedAt)
        binding.tvDetailJobTimeVn.text = timeStr

        // 2. Status Badge with when (strictly avoiding blanket "Đã hủy")
        when (job.status.uppercase()) {
            "COMPLETED", "DONE", "SUCCESS", "FINISHED" -> {
                binding.tvDetailStatusBadge.text = "Hoàn thành"
                binding.tvDetailStatusBadge.setTextColor(Color.parseColor("#166534"))
                binding.tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_status_completed_pill)
            }
            "CANCELLED", "CANCELED", "REJECTED" -> {
                binding.tvDetailStatusBadge.text = "Đã hủy"
                binding.tvDetailStatusBadge.setTextColor(Color.parseColor("#991B1B"))
                binding.tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_status_cancelled_pill)
            }
            "EXPIRED" -> {
                binding.tvDetailStatusBadge.text = "Hết hạn"
                binding.tvDetailStatusBadge.setTextColor(Color.parseColor("#9A3412"))
                binding.tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_status_expired_pill)
            }
            "IN_PROGRESS", "PAUSED", "ASSIGNED", "ACCEPTED" -> {
                binding.tvDetailStatusBadge.text = "Đang thực hiện"
                binding.tvDetailStatusBadge.setTextColor(Color.parseColor("#1D4ED8"))
                binding.tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_tag_dang_thuc_hien)
            }
            else -> {
                binding.tvDetailStatusBadge.text = job.status
                binding.tvDetailStatusBadge.setTextColor(Color.GRAY)
                binding.tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_role_badge_pill)
            }
        }

        // 3. Vehicle & Dispatcher
        binding.tvDetailVehiclePlate.text = "--"
        binding.tvDetailDispatcher.text = job.employeeName?.takeIf { it.isNotBlank() } ?: "--"

        // 4. 4 KPI Statistics
        val targetBins = when {
            !job.targetBinIds.isNullOrEmpty() -> job.targetBinIds!!
            !job.items.isNullOrEmpty() -> job.items!!.map { it.binId }
            else -> emptyList()
        }
        val totalStops = targetBins.size
        val completedIds = job.completedBinIds.orEmpty()
        val isJobCompleted = job.status.uppercase() in listOf("COMPLETED", "DONE", "SUCCESS", "FINISHED")
        val doneStops = if (completedIds.isNotEmpty()) {
            completedIds.size
        } else if (isJobCompleted) {
            totalStops
        } else {
            0
        }
        binding.tvDetailStatPoints.text = "$doneStops / $totalStops"

        binding.tvDetailStatDistance.text =
            job.routeData?.distanceMeters
                ?.takeIf { it >= 0.0 }
                ?.let {
                    String.format(
                        java.util.Locale.US,
                        "%.1f km",
                        it / 1000.0
                    )
                }
                ?: "--"

        binding.tvDetailStatDuration.text =
            job.routeData?.durationSeconds
                ?.takeIf { it >= 0.0 }
                ?.let {
                    "${(it / 60.0).roundToInt()} phút"
                }
                ?: "--"

        binding.tvDetailStatWaste.text = "--"

        // 5. Stops Timeline List
        renderStopsList(job, targetBins)
    }

    private fun renderStopsList(job: JobDto, targetBins: List<String>) {
        val container = binding.llHistoryStopsContainer
        container.removeAllViews()

        binding.tvDetailStopCountBadge.text = "${targetBins.size} điểm dừng"

        val itemsByBin = job.items?.associateBy { it.binId }.orEmpty()
        val completedIds = job.completedBinIds.orEmpty()
        val isJobCompleted = job.status.uppercase() in listOf("COMPLETED", "DONE", "SUCCESS", "FINISHED")

        targetBins.forEachIndexed { index, binId ->
            val bin = allBinsMap[binId]
            val itemDto = itemsByBin[binId]
            val itemView = layoutInflater.inflate(R.layout.item_history_detail_stop, container, false)

            val tvStep = itemView.findViewById<TextView>(R.id.tvStopStepNumber)
            val tvBinId = itemView.findViewById<TextView>(R.id.tvStopBinId)
            val tvAddress = itemView.findViewById<TextView>(R.id.tvStopAddress)
            val tvStatus = itemView.findViewById<TextView>(R.id.tvStopStatusBadge)
            val tvLevel = itemView.findViewById<TextView>(R.id.tvStopInitialLevel)
            val tvTime = itemView.findViewById<TextView>(R.id.tvStopCollectedTime)
            val tvWeight = itemView.findViewById<TextView>(R.id.tvStopWeight)
            val photoContainer = itemView.findViewById<View>(R.id.layoutStopPhotoContainer)

            val stepNum = index + 1
            tvStep.text = "$stepNum"
            tvBinId.text = bin?.deviceId ?: binId
            tvAddress.text = bin?.location ?: bin?.name ?: "Chưa có thông tin vị trí"

            val isDone = when {
                itemDto?.status == "COLLECTED" -> true
                completedIds.isNotEmpty() -> completedIds.contains(binId)
                isJobCompleted -> true
                else -> false
            }

            if (isDone) {
                tvStatus.text = "✓ Đã thu gom"
                tvStatus.setTextColor(Color.parseColor("#15803D"))
                tvStatus.setBackgroundResource(R.drawable.bg_role_badge_pill)
            } else {
                tvStatus.text = "Bỏ qua"
                tvStatus.setTextColor(Color.parseColor("#94A3B8"))
                tvStatus.setBackgroundResource(R.drawable.bg_dialog_item_field)
            }

            tvLevel.text = "--"
            tvTime.text = itemDto?.collectedAt ?: "--"
            tvWeight.text = "--"

            val photoUrl = itemDto?.photoUrl
            if (!photoUrl.isNullOrBlank()) {
                photoContainer.visibility = View.VISIBLE
                photoContainer.setOnClickListener {
                    it.applyPressEffect {
                        showPhotoPreviewDialog(bin?.deviceId ?: binId, itemDto.collectedAt ?: "--")
                    }
                }
            } else {
                photoContainer.visibility = View.GONE
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

    private fun formatJobCode(id: String): String {
        val clean = id.removePrefix("#")
        return when {
            clean.startsWith("JOB_") -> "#$clean"
            else -> "#JOB_$clean"
        }
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
