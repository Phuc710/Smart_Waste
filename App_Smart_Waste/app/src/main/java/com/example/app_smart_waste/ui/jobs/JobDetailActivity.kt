package com.example.app_smart_waste.ui.jobs

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.data.repository.BinsRepository
import com.example.app_smart_waste.data.repository.JobsRepository
import com.example.app_smart_waste.databinding.ActivityJobDetailBinding
import com.example.app_smart_waste.ui.route.RouteDetailActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class JobDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJobDetailBinding
    private val jobsRepo by lazy { JobsRepository(this) }
    private val binsRepo by lazy { BinsRepository(this) }

    private var jobId: String = ""
    private var currentJob: JobDto? = null
    private var countdownJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJobDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jobId = intent.getStringExtra("JOB_ID").orEmpty()
        if (jobId.isBlank()) {
            Toast.makeText(this, "Không tìm thấy mã nhiệm vụ.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val displayCode = formatJobCode(jobId)

        // 1. Shared Unified Header
        binding.detailAppHeader.configure(
            title = "Chi tiết nhiệm vụ",
            subtitle = displayCode,
            navIconRes = R.drawable.ic_arrow_back,
            onNavClick = { finish() }
        )

        setupListeners()
        playEntranceAnimation()
        loadJobData()
    }

    override fun onResume() {
        super.onResume()
        loadJobData()
    }

    private fun loadJobData() {
        lifecycleScope.launch {
            val jobRes = jobsRepo.getJobDetail(jobId)
            val job = jobRes.getOrNull()
            if (job != null) {
                currentJob = job
                bindJobDetails(job)
            } else {
                binding.tvJobDetailCode.text = "Không tìm thấy ca #$jobId"
            }

            val binsRes = binsRepo.getBins()
            val binsMap = binsRes.getOrDefault(emptyList()).associateBy { it.deviceId }
            populateBinsList(job, binsMap)
        }
    }

    private fun bindJobDetails(job: JobDto) {
        val code = formatJobCode(job.id)
        binding.tvJobDetailCode.text = code
        binding.detailAppHeader.setSubtitle("Mã ca: $code")

        // Dispatcher source
        binding.tvJobDetailDispatcher.text = when {
            job.employeeName?.isNotBlank() == true -> "Nhiệm vụ: ${job.employeeName}"
            else -> "Nguồn nhiệm vụ: Điều phối tự động"
        }

        // Metrics
        val targetBins = when {
            !job.targetBinIds.isNullOrEmpty() -> job.targetBinIds!!
            !job.items.isNullOrEmpty() -> job.items!!.map { it.binId }
            else -> emptyList()
        }
        binding.tvJobDetailStops.text = "${targetBins.size}"

        val distMeters = job.routeData?.distanceMeters
        binding.tvJobDetailDistance.text = distMeters?.takeIf { it > 0.0 }
            ?.let { String.format(java.util.Locale.US, "%.1f km", it / 1000.0) }
            ?: "--"

        val durSecs = job.routeData?.durationSeconds
        binding.tvJobDetailDuration.text = durSecs?.takeIf { it > 0.0 }
            ?.let { "${(it / 60.0).roundToInt()} phút" }
            ?: "--"

        // Status-driven UI
        when (job.status.uppercase()) {
            "ASSIGNED", "PENDING" -> {
                val timeoutMinutes = com.example.app_smart_waste.core.storage.AppConfig.getAssignTimeoutMinutes(this)
                binding.tvJobDetailStatusPill.setTextColor(Color.parseColor("#D97706"))
                binding.tvJobDetailStatusPill.setBackgroundResource(R.drawable.bg_badge_pill_yellow)
                binding.btnDetailAcceptOrStartJob.text = "Nhận nhiệm vụ"
                binding.btnDetailRejectJob.visibility = View.VISIBLE
                binding.layoutDetailBottomActions.visibility = View.VISIBLE

                countdownJob?.cancel()
                countdownJob = lifecycleScope.launch {
                    while (isActive) {
                        val remSec = com.example.app_smart_waste.core.utils.TimeUtils.calculateJobCountdownSeconds(job.assignedAt, timeoutMinutes)
                        binding.tvJobDetailStatusPill.text = com.example.app_smart_waste.core.utils.TimeUtils.formatJobCountdownText(remSec)
                        delay(1000)
                    }
                }
            }
            "ACCEPTED" -> {
                binding.tvJobDetailStatusPill.text = "Đã nhận ca"
                binding.tvJobDetailStatusPill.setTextColor(Color.parseColor("#15803D"))
                binding.tvJobDetailStatusPill.setBackgroundResource(R.drawable.bg_role_badge_pill)
                binding.btnDetailAcceptOrStartJob.text = "Bắt đầu thu gom"
                binding.btnDetailRejectJob.visibility = View.GONE
                binding.layoutDetailBottomActions.visibility = View.VISIBLE
            }
            "IN_PROGRESS", "PAUSED" -> {
                binding.tvJobDetailStatusPill.text = if (job.status.uppercase() == "PAUSED") "Tạm dừng" else "Đang thực hiện"
                binding.tvJobDetailStatusPill.setTextColor(Color.parseColor("#1D4ED8"))
                binding.tvJobDetailStatusPill.setBackgroundResource(R.drawable.bg_tag_dang_thuc_hien)
                binding.btnDetailAcceptOrStartJob.text = "Tiếp tục thu gom"
                binding.btnDetailRejectJob.visibility = View.GONE
                binding.layoutDetailBottomActions.visibility = View.VISIBLE
            }
            else -> {
                // Completed, Cancelled, Rejected, Expired -> Read-only
                binding.tvJobDetailStatusPill.text = job.status
                binding.tvJobDetailStatusPill.setTextColor(Color.GRAY)
                binding.tvJobDetailStatusPill.setBackgroundResource(R.drawable.bg_role_badge_pill)
                binding.layoutDetailBottomActions.visibility = View.GONE
            }
        }
    }

    private fun populateBinsList(job: JobDto?, binsMap: Map<String, com.example.app_smart_waste.core.model.SmartBinDto>) {
        val targetBinIds = when {
            job?.targetBinIds?.isNotEmpty() == true -> job.targetBinIds!!
            job?.items?.isNotEmpty() == true -> job.items!!.map { it.binId }
            else -> emptyList()
        }

        val container = binding.llBinsDetailContainer
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        if (targetBinIds.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Không có danh sách điểm thu gom."
                setTextColor(Color.parseColor("#64748B"))
                textSize = 14f
                setPadding(0, 32, 0, 32)
            }
            container.addView(emptyTv)
            return
        }

        targetBinIds.forEach { binId ->
            val bin = binsMap[binId]
            val v = inflater.inflate(R.layout.item_bin_detail_preview, container, false)
            v.findViewById<TextView>(R.id.tvBinPreviewId).text = bin?.deviceId ?: binId
            v.findViewById<TextView>(R.id.tvBinPreviewName).text = bin?.name ?: "Thùng rác $binId"
            v.findViewById<TextView>(R.id.tvBinPreviewAddress).text = bin?.location ?: "Chưa có thông tin vị trí"

            val lat = bin?.latitude
            val lng = bin?.longitude
            v.findViewById<TextView>(R.id.tvBinPreviewCoords).text = if (lat != null && lng != null) "Tọa độ: $lat, $lng" else "Tọa độ: --"

            val level = bin?.levelPercent?.toInt() ?: 0
            v.findViewById<TextView>(R.id.tvBinPreviewLevelBadge).text = "$level% Đầy"

            val lid = if (bin?.lidStatus?.contains("OPEN", ignoreCase = true) == true) "🔓 Đang mở" else "🔒 Đã đóng"
            v.findViewById<TextView>(R.id.tvBinPreviewLidStatus).text = lid
            v.findViewById<TextView>(R.id.tvBinPreviewLastSeen).text = bin?.lastSeen ?: "--"
            container.addView(v)
        }
    }

    private fun setupListeners() {
        // Map Route Button
        binding.btnDetailViewRouteMap.setOnClickListener {
            it.applyPressEffect {
                val job = currentJob ?: return@applyPressEffect
                val intent = Intent(this, RouteDetailActivity::class.java).apply {
                    putExtra("JOB_ID", job.id)
                }
                startActivity(intent)
            }
        }

        // Reject Job
        binding.btnDetailRejectJob.setOnClickListener {
            it.applyPressEffect {
                com.example.app_smart_waste.ui.common.AppConfirmDialog.showCancelJobWithReason(
                    context = this,
                    jobId = jobId,
                    onConfirm = { reason ->
                        lifecycleScope.launch {
                            val res = jobsRepo.rejectJob(jobId, reason)
                            if (res.isSuccess) {
                                Toast.makeText(this@JobDetailActivity, "✓ Đã hủy nhiệm vụ", Toast.LENGTH_SHORT).show()
                                finish()
                            } else {
                                Toast.makeText(this@JobDetailActivity, "Không thể hủy nhiệm vụ.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }

        // Accept / Start / Continue Job
        binding.btnDetailAcceptOrStartJob.setOnClickListener {
            it.applyPressEffect {
                val status = currentJob?.status?.uppercase() ?: "ASSIGNED"
                when (status) {
                    "ASSIGNED", "PENDING" -> {
                        lifecycleScope.launch {
                            val res = jobsRepo.acceptJob(jobId)
                            if (res.isSuccess) {
                                Toast.makeText(this@JobDetailActivity, "✓ Đã nhận ca thu gom!", Toast.LENGTH_SHORT).show()
                                loadJobData()
                            } else {
                                Toast.makeText(this@JobDetailActivity, "Không thể nhận ca.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    "ACCEPTED" -> {
                        lifecycleScope.launch {
                            val res = jobsRepo.startJob(jobId)
                            if (res.isSuccess) {
                                val intent = Intent(this@JobDetailActivity, JobExecutionActivity::class.java).apply {
                                    putExtra("JOB_ID", jobId)
                                }
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this@JobDetailActivity, "Không thể bắt đầu ca.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    else -> {
                        // IN_PROGRESS or PAUSED -> Continue
                        val intent = Intent(this@JobDetailActivity, JobExecutionActivity::class.java).apply {
                            putExtra("JOB_ID", jobId)
                        }
                        startActivity(intent)
                        finish()
                    }
                }
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

    private fun playEntranceAnimation() {
        val views = listOf(
            binding.detailAppHeader,
            binding.llBinsDetailContainer,
            binding.layoutDetailBottomActions
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

    override fun onDestroy() {
        countdownJob?.cancel()
        countdownJob = null
        super.onDestroy()
    }
}
