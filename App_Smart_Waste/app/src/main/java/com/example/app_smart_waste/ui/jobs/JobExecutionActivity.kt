package com.example.app_smart_waste.ui.jobs

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.location.GpsTracker
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.data.repository.BinsRepository
import com.example.app_smart_waste.data.repository.JobsRepository
import com.example.app_smart_waste.databinding.ActivityJobExecutionBinding
import com.example.app_smart_waste.databinding.ItemJobNextStopRowBinding
import com.example.app_smart_waste.ui.common.TopCommandNotificationManager
import com.example.app_smart_waste.ui.incident.IncidentReportActivity
import com.example.app_smart_waste.ui.main.MainActivity
import com.example.app_smart_waste.ui.route.RouteDetailActivity
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class JobExecutionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_JOB_ID = "JOB_ID"
    }

    private lateinit var binding: ActivityJobExecutionBinding
    private val jobsRepo by lazy { JobsRepository(this) }
    private val binsRepo by lazy { BinsRepository(this) }

    private var jobId: String = ""
    private var currentJob: JobDto? = null
    private var currentBinId: String = ""
    private var currentBinDto: SmartBinDto? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJobExecutionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jobId = intent.getStringExtra(EXTRA_JOB_ID).orEmpty()
        if (jobId.isBlank()) {
            Toast.makeText(this, "Không tìm thấy mã nhiệm vụ.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupHeader()
        setupListeners()
        loadExecutionData()

        // Start Foreground High-Accuracy Route Tracking for this Active Job
        GpsTracker.getInstance(this).startRouteTracking(jobId)
    }

    override fun onResume() {
        super.onResume()
        loadExecutionData()
    }

    private fun setupHeader() {
        binding.appHeader.configure(
            title = "Thực hiện nhiệm vụ",
            subtitle = if (jobId.isNotBlank()) "#$jobId" else null,
            navIconRes = R.drawable.ic_arrow_back,
            onNavClick = { finish() },
            actionIconRes = R.drawable.ic_more_vert,
            onActionClick = {
                Toast.makeText(this, "Tùy chọn ca thu gom #$jobId", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun setupListeners() {
        // 1. Action: Chỉ đường tới điểm hiện tại
        binding.btnExecDirections.setOnClickListener {
            openNavigationToCurrentBin()
        }

        // 2. Action: Mở nắp (Remote Command Handshake)
        binding.btnExecOpenLid.setOnClickListener {
            if (currentBinId.isNotBlank()) {
                triggerRemoteOpenLid(currentBinId)
            } else {
                Toast.makeText(this, "Chưa xác định điểm dừng hiện tại.", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Action: Báo sự cố
        binding.btnExecReportIncident.setOnClickListener {
            val intent = Intent(this, IncidentReportActivity::class.java).apply {
                putExtra("BIN_ID", currentBinId)
                putExtra("JOB_ID", jobId)
            }
            startActivity(intent)
        }

        // 4. Primary Action: Xác nhận đã thu gom
        binding.btnExecConfirmCollected.setOnClickListener {
            handleConfirmCollected()
        }
    }

    private fun loadExecutionData() {
        lifecycleScope.launch {
            val jobRes = jobsRepo.getJobDetail(jobId)
            val job = jobRes.getOrNull()
            val binsRes = binsRepo.getBins()
            val binsMap = binsRes.getOrDefault(emptyList()).associateBy { it.deviceId }

            if (job != null) {
                currentJob = job
                bindExecutionView(job, binsMap)
            } else {
                Toast.makeText(this@JobExecutionActivity, "Không thể tải dữ liệu ca thu gom.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindExecutionView(job: JobDto, binsMap: Map<String, SmartBinDto>) {
        val targets = when {
            !job.targetBinIds.isNullOrEmpty() -> job.targetBinIds!!
            !job.items.isNullOrEmpty() -> job.items!!.map { it.binId }
            else -> emptyList()
        }

        val totalStops = targets.size
        val completedCount = (job.completedBinIds?.size ?: 0).coerceAtLeast(job.items?.count { it.status == "COLLECTED" } ?: 0)
        val percent = if (totalStops > 0) ((completedCount * 100) / totalStops) else 0

        // 1. Tiến độ thu gom
        binding.tvExecProgressFraction.text = "$completedCount/$totalStops điểm"
        binding.pbExecProgress.progress = percent

        // 2. Xác định điểm dừng hiện tại (Điểm đầu tiên chưa COLLECTED)
        var activeIndex = -1
        for (i in targets.indices) {
            val binId = targets[i]
            val isDone = (job.completedBinIds?.contains(binId) == true) ||
                         (job.items?.find { it.binId == binId }?.status == "COLLECTED")
            if (!isDone) {
                activeIndex = i
                break
            }
        }

        if (activeIndex == -1) {
            // Tất cả điểm đã hoàn thành
            showJobCompletedCelebration()
            return
        }

        currentBinId = targets[activeIndex]
        currentBinDto = binsMap[currentBinId]

        val bin = currentBinDto
        binding.tvCurrentBinId.text = currentBinId
        binding.tvCurrentBinAddress.text = bin?.location ?: bin?.name ?: "Vị trí thùng #$currentBinId"

        // Mức đầy tô màu
        val fillPercent = (bin?.levelPercent ?: 0.0).toInt()
        val fillText = "Mức đầy: $fillPercent%"
        val span = SpannableString(fillText)
        val startIdx = fillText.indexOf("$fillPercent%")
        if (startIdx >= 0) {
            val colorCode = if (fillPercent >= 80) "#EF4444" else if (fillPercent >= 60) "#F59E0B" else "#16A34A"
            span.setSpan(
                ForegroundColorSpan(Color.parseColor(colorCode)),
                startIdx,
                startIdx + "$fillPercent%".length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        binding.tvCurrentBinFillLevel.text = span

        binding.tvCurrentBinType.text = "Loại thùng: Thùng 240L"
        binding.tvCurrentBinNote.text = "Ghi chú: ${bin?.location?.let { "Đặt tại $it" } ?: "Thùng đặt gần cột điện."}"

        // 3. Render Các điểm dừng tiếp theo
        binding.llExecNextStopsContainer.removeAllViews()
        val nextIndices = (activeIndex + 1 until targets.size)

        if (nextIndices.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Đây là điểm dừng cuối cùng trong ca."
                setTextColor(ContextCompat.getColor(context, R.color.profile_text_secondary))
                textSize = 12.5f
            }
            binding.llExecNextStopsContainer.addView(emptyTv)
        } else {
            for (idx in nextIndices) {
                val nextBinId = targets[idx]
                val nextBin = binsMap[nextBinId]
                val itemBinding = ItemJobNextStopRowBinding.inflate(LayoutInflater.from(this), binding.llExecNextStopsContainer, false)

                itemBinding.tvNextStopNumber.text = "${idx + 1}"
                itemBinding.tvNextStopBinId.text = nextBinId
                itemBinding.tvNextStopAddress.text = nextBin?.location ?: nextBin?.name ?: "Điểm dừng tiếp theo"

                // Tính khoảng cách Haversine thực tế nếu có tọa độ
                val distKm = if (bin?.latitude != null && bin.longitude != null && nextBin?.latitude != null && nextBin.longitude != null) {
                    val lat1 = Math.toRadians(bin.latitude!!)
                    val lon1 = Math.toRadians(bin.longitude!!)
                    val lat2 = Math.toRadians(nextBin.latitude!!)
                    val lon2 = Math.toRadians(nextBin.longitude!!)
                    val dLat = lat2 - lat1
                    val dLon = lon2 - lon1
                    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                            Math.cos(lat1) * Math.cos(lat2) *
                            Math.sin(dLon / 2) * Math.sin(dLon / 2)
                    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
                    val d = 6371.0 * c
                    (d * 10).roundToInt() / 10.0
                } else {
                    0.9 + ((idx - activeIndex - 1) * 0.8)
                }

                itemBinding.tvNextStopDistance.text = String.format(Locale.US, "%.1f km", distKm)

                binding.llExecNextStopsContainer.addView(itemBinding.root)
            }
        }
    }

    private fun handleConfirmCollected() {
        if (currentBinId.isBlank()) return

        binding.btnExecConfirmCollected.isEnabled = false
        binding.btnExecConfirmCollected.text = "⏳ Đang ghi nhận..."

        lifecycleScope.launch {
            val res = jobsRepo.collectBin(jobId, currentBinId)
            binding.btnExecConfirmCollected.isEnabled = true
            binding.btnExecConfirmCollected.text = "Xác nhận đã thu gom"

            if (res.isSuccess) {
                val body = res.getOrNull()
                Toast.makeText(this@JobExecutionActivity, "✓ Đã thu gom điểm $currentBinId!", Toast.LENGTH_SHORT).show()

                if (body?.allDone == true) {
                    showJobCompletedCelebration()
                } else {
                    loadExecutionData()
                }
            } else {
                val err = res.exceptionOrNull()?.message ?: "Lỗi kết nối máy chủ"
                Toast.makeText(this@JobExecutionActivity, "Không thể xác nhận thu gom: $err", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun triggerRemoteOpenLid(binId: String) {
        TopCommandNotificationManager.showLoading(
            activity = this,
            title = "Đang gửi lệnh mở nắp...",
            subtitle = "Vui lòng chờ thiết bị phản hồi"
        )

        lifecycleScope.launch {
            val result = binsRepo.openLid(binId)
            if (result is com.example.app_smart_waste.core.model.BinCommandResult.Executed) {
                TopCommandNotificationManager.showSuccess(
                    activity = this@JobExecutionActivity,
                    title = "Mở nắp thành công!",
                    subtitle = "Thùng $binId đã sẵn sàng thu gom"
                )
            } else {
                val reason = when (result) {
                    is com.example.app_smart_waste.core.model.BinCommandResult.DeviceOffline -> "Thiết bị đang ngoại tuyến"
                    is com.example.app_smart_waste.core.model.BinCommandResult.Timeout -> "Hết thời gian chờ phản hồi"
                    is com.example.app_smart_waste.core.model.BinCommandResult.NetworkError -> "Lỗi kết nối mạng"
                    is com.example.app_smart_waste.core.model.BinCommandResult.ServerError -> result.message
                    is com.example.app_smart_waste.core.model.BinCommandResult.Unauthorized -> "Không có quyền gửi lệnh"
                    else -> "Không phản hồi từ thiết bị"
                }
                TopCommandNotificationManager.showError(
                    activity = this@JobExecutionActivity,
                    title = "Lệnh chưa thực hiện được",
                    subtitle = reason,
                    onRetry = { triggerRemoteOpenLid(binId) }
                )
            }
        }
    }

    private fun openNavigationToCurrentBin() {
        val targetBinId = currentBinId.ifBlank { currentBinDto?.deviceId.orEmpty() }
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_OPEN_TAB", "MAP")
            putExtra("EXTRA_BIN_ID", targetBinId)
            putExtra("EXTRA_JOB_ID", jobId)
            putExtra("EXTRA_START_NAV", true)
        }
        startActivity(mainIntent)
        finish()
    }

    private fun showJobCompletedCelebration() {
        GpsTracker.getInstance(this).stopRouteTracking()

        val dialogView = layoutInflater.inflate(R.layout.dialog_job_completed, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val btnViewHistory = dialogView.findViewById<AppCompatButton>(R.id.btnCompletedViewHistory)
        val btnBackToList = dialogView.findViewById<AppCompatButton>(R.id.btnCompletedBackToList)

        btnViewHistory?.setOnClickListener {
            dialog.dismiss()
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("OPEN_TAB", "HISTORY")
            }
            startActivity(mainIntent)
            finish()
        }

        btnBackToList?.setOnClickListener {
            dialog.dismiss()
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("OPEN_TAB", "JOBS")
            }
            startActivity(mainIntent)
            finish()
        }

        dialog.show()
    }
}
