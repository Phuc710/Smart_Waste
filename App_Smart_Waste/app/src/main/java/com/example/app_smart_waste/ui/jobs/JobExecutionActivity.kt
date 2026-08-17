package com.example.app_smart_waste.ui.jobs

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.data.repository.BinsRepository
import com.example.app_smart_waste.data.repository.JobsRepository
import com.example.app_smart_waste.databinding.ActivityJobExecutionBinding
import com.example.app_smart_waste.ui.incident.IncidentReportActivity
import com.example.app_smart_waste.ui.route.RouteDetailActivity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class JobExecutionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJobExecutionBinding
    private val jobsRepo by lazy { JobsRepository(this) }
    private val binsRepo by lazy { BinsRepository(this) }

    private var jobId: String = ""
    private var currentJob: JobDto? = null
    private var currentBinId = ""
    private var nextBinId = ""
    private var currentLat = 10.7769
    private var currentLng = 106.7009
    private var isJobPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJobExecutionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jobId = intent.getStringExtra("JOB_ID").orEmpty()
        if (jobId.isBlank()) {
            Toast.makeText(this, "Không tìm thấy mã nhiệm vụ.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val displayCode = formatJobCode(jobId)

        // 1. Shared Unified Header
        binding.execAppHeader.configure(
            title = "Thực hiện ca thu gom",
            subtitle = displayCode,
            navIconRes = R.drawable.ic_arrow_back,
            onNavClick = { finish() }
        )

        setupListeners()
        loadExecutionData()
        playEntranceAnimation()
    }

    override fun onResume() {
        super.onResume()
        loadExecutionData()
    }

    private fun loadExecutionData() {
        lifecycleScope.launch {
            val jobRes = jobsRepo.getJobDetail(jobId)
            val job = jobRes.getOrNull()
            if (job == null) {
                Toast.makeText(this@JobExecutionActivity, "Không thể tải dữ liệu ca #$jobId", Toast.LENGTH_SHORT).show()
                return@launch
            }
            currentJob = job
            isJobPaused = job.status.uppercase() == "PAUSED"
            updatePauseButtonUi(isJobPaused)

            val binsRes = binsRepo.getBins()
            val binsMap = binsRes.getOrDefault(emptyList()).associateBy { it.deviceId }

            val targets = when {
                !job.targetBinIds.isNullOrEmpty() -> job.targetBinIds!!
                !job.items.isNullOrEmpty() -> job.items!!.map { it.binId }
                else -> emptyList()
            }
            val completed = job.completedBinIds.orEmpty()
            val total = targets.size
            val done = completed.size

            // Check if all bins are completed
            if (total > 0 && done >= total) {
                showCompletedDialog(total)
                return@launch
            }

            // Current Bin: First uncompleted bin in target sequence
            val remaining = targets.filter { !completed.contains(it) }
            val current = remaining.firstOrNull() ?: targets.lastOrNull().orEmpty()
            val next = remaining.getOrNull(1)

            currentBinId = current
            val currentBin = if (current.isNotBlank()) binsMap[current] else null
            currentLat = currentBin?.latitude ?: 10.7769
            currentLng = currentBin?.longitude ?: 106.7009

            // Bind current bin card
            binding.tvExecCurrentBinId.text = currentBin?.deviceId ?: if (current.isNotBlank()) current else "--"
            binding.tvExecCurrentBinAddress.text = currentBin?.location ?: currentBin?.name ?: "Chưa có thông tin vị trí"
            val fill = currentBin?.levelPercent?.toInt() ?: 0
            binding.tvExecCurrentLevelPercent.text = "$fill%"
            binding.pbExecCurrentLevel.progress = fill

            // Upcoming Stop Card
            if (next != null) {
                nextBinId = next
                val nextBin = binsMap[next]
                binding.cardExecUpcoming.visibility = View.VISIBLE
                val nextRow = binding.itemExecNextStop
                val nextTvName = nextRow.findViewWithTag<android.widget.TextView?>(null)
                    ?: nextRow.getChildAt(1)?.let {
                        (it as? android.view.ViewGroup)?.getChildAt(0) as? android.widget.TextView
                    }
                nextTvName?.text = nextBin?.name ?: "Thùng rác $next"
            } else {
                binding.cardExecUpcoming.visibility = View.GONE
            }

            // Progress Bar
            val pct = if (total > 0) (done * 100 / total) else 0
            binding.tvExecStopsCount.text = "$done / $total điểm"
            binding.tvExecPercent.text = "$pct%"
            binding.pbExecProgress.progress = pct
            binding.tvExecSummaryText.text = "Đã hoàn thành $done/$total điểm thu gom"
        }
    }

    private fun setupListeners() {
        // Pause / Resume Job
        binding.btnExecPauseJob.setOnClickListener {
            it.applyPressEffect {
                if (isJobPaused) {
                    resumeActiveJob()
                } else {
                    showPauseReasonDialog()
                }
            }
        }

        // Navigate: Google Maps Turn-by-Turn Intent (Fallback to internal Map)
        binding.btnExecNavigate.setOnClickListener {
            it.applyPressEffect {
                val gmmIntentUri = Uri.parse("google.navigation:q=$currentLat,$currentLng&mode=d")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                if (mapIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapIntent)
                } else {
                    val intent = Intent(this, RouteDetailActivity::class.java).apply {
                        putExtra("JOB_ID", jobId)
                    }
                    startActivity(intent)
                }
            }
        }

        // Remote IoT Lid Unlock
        binding.btnExecUnlockLid.setOnClickListener {
            it.applyPressEffect {
                if (currentBinId.isBlank()) {
                    Toast.makeText(this, "Chưa chọn điểm thu gom", Toast.LENGTH_SHORT).show()
                    return@applyPressEffect
                }
                Toast.makeText(this, "📶 Đang gửi lệnh mở nắp thùng $currentBinId...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    val res = binsRepo.openLid(currentBinId)
                    if (res.isSuccess) {
                        Toast.makeText(this@JobExecutionActivity, "✓ Đã mở nắp thùng $currentBinId thành công!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@JobExecutionActivity, "Thiết bị không phản hồi. Bạn có thể mở tay.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Confirm Bin Collection
        binding.btnExecConfirmCollect.setOnClickListener {
            it.applyPressEffect {
                if (currentBinId.isBlank()) {
                    Toast.makeText(this, "Chưa có thông tin điểm thu gom", Toast.LENGTH_SHORT).show()
                    return@applyPressEffect
                }
                showConfirmCollectDialog(currentBinId)
            }
        }

        // Report Incident
        binding.btnExecReportIncident.setOnClickListener {
            it.applyPressEffect {
                val intent = Intent(this, IncidentReportActivity::class.java).apply {
                    putExtra("JOB_ID", jobId)
                    if (currentBinId.isNotBlank()) {
                        putExtra("BIN_ID", currentBinId)
                    }
                }
                startActivity(intent)
            }
        }

        // Next Stop Click
        binding.itemExecNextStop.setOnClickListener {
            it.applyPressEffect {
                if (nextBinId.isNotBlank()) {
                    Toast.makeText(this, "Điểm tiếp theo: $nextBinId", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showConfirmCollectDialog(binId: String) {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận thu gom")
            .setMessage("Bạn đã thu gom xong rác tại điểm #$binId?")
            .setPositiveButton("Xác nhận") { _, _ ->
                executeCollectBin(binId)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun executeCollectBin(binId: String) {
        lifecycleScope.launch {
            binding.btnExecConfirmCollect.isEnabled = false
            binding.btnExecConfirmCollect.text = "Đang đồng bộ..."

            val res = jobsRepo.collectBin(jobId, binId)
            binding.btnExecConfirmCollect.isEnabled = true
            binding.btnExecConfirmCollect.text = "Xác nhận thu gom điểm này"

            if (res.isSuccess) {
                val collectRes = res.getOrNull()
                Toast.makeText(this@JobExecutionActivity, "✓ Đã thu gom điểm #$binId!", Toast.LENGTH_SHORT).show()

                if (collectRes?.allDone == true) {
                    val total = currentJob?.targetBinIds?.size ?: 1
                    showCompletedDialog(total)
                } else {
                    loadExecutionData()
                }
            } else {
                Toast.makeText(this@JobExecutionActivity, "Lỗi ghi nhận thu gom. Vui lòng thử lại.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPauseReasonDialog() {
        val input = EditText(this).apply {
            hint = "Lý do: Kẹt xe, sự cố xe, nghỉ giải lao..."
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Tạm dừng ca làm")
            .setMessage("Vui lòng nhập lý do tạm dừng để hệ thống ghi nhận:")
            .setView(input)
            .setPositiveButton("Tạm dừng") { _, _ ->
                val reason = input.text.toString().trim().ifBlank { "Tài xế tạm dừng ngoài hiện trường" }
                pauseActiveJob(reason)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun pauseActiveJob(reason: String) {
        lifecycleScope.launch {
            val res = jobsRepo.pauseJob(jobId, reason)
            if (res.isSuccess) {
                isJobPaused = true
                updatePauseButtonUi(true)
                Toast.makeText(this@JobExecutionActivity, "⏸ Đã tạm dừng ca làm.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@JobExecutionActivity, "Không thể tạm dừng ca.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resumeActiveJob() {
        lifecycleScope.launch {
            val res = jobsRepo.resumeJob(jobId)
            if (res.isSuccess) {
                isJobPaused = false
                updatePauseButtonUi(false)
                Toast.makeText(this@JobExecutionActivity, "▶ Đã tiếp tục ca làm!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@JobExecutionActivity, "Không thể tiếp tục ca.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updatePauseButtonUi(paused: Boolean) {
        if (paused) {
            binding.tvExecPauseLabel.text = "Tiếp tục ca"
            binding.tvExecPauseLabel.setTextColor(Color.parseColor("#15803D"))
            binding.ivExecPauseIcon.setImageResource(R.drawable.ic_play_orange)
        } else {
            binding.tvExecPauseLabel.text = "Tạm dừng ca"
            binding.tvExecPauseLabel.setTextColor(Color.parseColor("#0F172A"))
            binding.ivExecPauseIcon.setImageResource(R.drawable.ic_pause_orange)
        }
    }

    private fun showCompletedDialog(totalCount: Int) {
        val distMeters = currentJob?.routeData?.distanceMeters
        val distText = distMeters?.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "%.1f km", it / 1000.0) } ?: "--"

        val durSecs = currentJob?.routeData?.durationSeconds
        val durText = durSecs?.takeIf { it > 0 }?.let { "${(it / 60.0).roundToInt()} phút" } ?: "--"

        AlertDialog.Builder(this)
            .setTitle("🎉 Hoàn thành ca thu gom!")
            .setMessage("Chúc mừng bạn đã thu gom xong toàn bộ $totalCount điểm của ca #$jobId.\n\n• Quãng đường: $distText\n• Thời gian: $durText")
            .setPositiveButton("Xem lịch sử ca") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
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
            binding.execAppHeader,
            binding.cardExecProgress,
            binding.cardExecCurrentBin,
            binding.cardExecUpcoming
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
}
