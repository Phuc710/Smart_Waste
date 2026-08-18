package com.example.app_smart_waste.ui.jobs

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.utils.TimeUtils
import com.example.app_smart_waste.data.repository.BinsRepository
import com.example.app_smart_waste.data.repository.JobsRepository
import com.example.app_smart_waste.databinding.ActivityJobDetailBinding
import com.example.app_smart_waste.databinding.ItemJobStopRowBinding
import com.example.app_smart_waste.ui.route.RouteDetailActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

class JobDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJobDetailBinding
    private val jobsRepo by lazy { JobsRepository(this) }
    private val binsRepo by lazy { BinsRepository(this) }

    private var jobId: String = ""
    private var currentJob: JobDto? = null
    private var isHistory: Boolean = false
    private var isMapLoaded: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJobDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jobId = intent.getStringExtra("JOB_ID").orEmpty()
        isHistory = intent.getBooleanExtra("IS_HISTORY", false)

        if (jobId.isBlank()) {
            Toast.makeText(this, "Không tìm thấy mã nhiệm vụ.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupHeader()
        setupEmbeddedMap()
        loadJobData()
    }

    override fun onResume() {
        super.onResume()
        loadJobData()
    }

    private fun setupHeader() {
        binding.appHeader.configure(
            title = "Chi tiết nhiệm vụ",
            subtitle = if (jobId.isNotBlank()) "#$jobId" else null,
            navIconRes = R.drawable.ic_arrow_back,
            onNavClick = { finish() },
            actionIconRes = R.drawable.ic_more_vert,
            onActionClick = {
                Toast.makeText(this, "Tùy chọn nhiệm vụ #$jobId", Toast.LENGTH_SHORT).show()
            }
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupEmbeddedMap() {
        binding.mapDetailWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        binding.mapDetailWebView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onMapReady() {
                runOnUiThread {
                    isMapLoaded = true
                    renderMapRoute()
                }
            }
        }, "AndroidBridge")

        binding.mapDetailWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isMapLoaded = true
                renderMapRoute()
            }
        }

        binding.mapDetailWebView.loadUrl("file:///android_asset/leaflet_map.html")

        // Clicking map opens full screen Route Detail
        binding.cardDetailMapContainer.setOnClickListener {
            openRouteDetail()
        }
    }

    private fun loadJobData() {
        lifecycleScope.launch {
            val jobRes = jobsRepo.getJobDetail(jobId)
            val job = jobRes.getOrNull()
            val binsRes = binsRepo.getBins()
            val binsMap = binsRes.getOrDefault(emptyList()).associateBy { it.deviceId }

            if (job != null) {
                currentJob = job
                bindJobDetails(job, binsMap)
                populateBinsList(job, binsMap)
                if (isMapLoaded) {
                    renderMapRoute()
                }
            } else {
                binding.tvJobDetailCode.text = "Không tìm thấy ca #$jobId"
            }
        }
    }

    private fun bindJobDetails(job: JobDto, binsMap: Map<String, SmartBinDto>) {
        val code = formatJobCode(job.id)
        binding.tvJobDetailCode.text = code

        val targetBins = when {
            !job.targetBinIds.isNullOrEmpty() -> job.targetBinIds!!
            !job.items.isNullOrEmpty() -> job.items!!.map { it.binId }
            else -> emptyList()
        }
        val stopsCount = targetBins.size
        val distanceKm = (job.routeData?.distanceMeters ?: (stopsCount * 950.0)) / 1000.0
        val durationMins = ((job.routeData?.durationSeconds ?: (stopsCount * 360.0)) / 60.0).roundToInt()

        // Active 3 Hero Metrics
        binding.tvJobDetailStops.text = stopsCount.toString()
        binding.tvJobDetailDistance.text = String.format(Locale.US, "%.1f km", distanceKm)
        binding.tvJobDetailDuration.text = "$durationMins phút"

        // Determine Effective Status
        val statusUpper = job.status.uppercase()
        when (statusUpper) {
            // =================================================================
            // CASE 02 — ĐƯỢC GIAO (ASSIGNED)
            // =================================================================
            "ASSIGNED", "PENDING" -> {
                binding.tvJobDetailStatusPill.text = "ĐƯỢC GIAO"
                binding.tvJobDetailStatusPill.setBackgroundResource(R.drawable.bg_badge_duoc_giao)
                binding.tvJobDetailStatusPill.setTextColor(Color.parseColor("#2563EB"))

                val timeStr = (job.assignedAt ?: job.createdAt ?: job.id).let { TimeUtils.formatDisplayDateTime(it) }
                binding.tvJobDetailTimeSubtitle.text = "Giao lúc: $timeStr"

                binding.layoutDetailProgressSection.visibility = View.GONE
                binding.cardDetailPausedWarning.visibility = View.GONE
                binding.cardDetailCompletedSummary.visibility = View.GONE
                binding.cardDetailActiveSummary.visibility = View.VISIBLE
                binding.tvDetailStopListTitle.text = "Danh sách điểm dừng"

                // Bottom Buttons: [ Từ chối ] (Outlined Red) + [ Nhận nhiệm vụ ] (Primary Green)
                binding.layoutDetailBottomActions.visibility = if (isHistory) View.GONE else View.VISIBLE
                binding.btnDetailActionLeft.visibility = View.VISIBLE
                binding.btnDetailActionLeft.text = "Từ chối"
                binding.btnDetailActionLeft.setBackgroundResource(R.drawable.bg_button_outlined_red)
                binding.btnDetailActionLeft.setTextColor(ContextCompat.getColor(this, R.color.profile_danger))
                binding.btnDetailActionLeft.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_close, 0, 0, 0)
                binding.btnDetailActionLeft.setOnClickListener { showRejectJobBottomSheet() }

                binding.btnDetailActionRight.visibility = View.VISIBLE
                binding.btnDetailActionRight.text = "Nhận nhiệm vụ"
                binding.btnDetailActionRight.setBackgroundResource(R.drawable.bg_button_primary_green)
                binding.btnDetailActionRight.setTextColor(Color.WHITE)
                binding.btnDetailActionRight.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                binding.btnDetailActionRight.setOnClickListener { showAcceptJobBottomSheet() }
            }

            // =================================================================
            // CASE 03 — ĐANG THỰC HIỆN / ĐÃ NHẬN (IN_PROGRESS / ACCEPTED)
            // =================================================================
            "ACCEPTED", "IN_PROGRESS" -> {
                val isAcceptedOnly = (statusUpper == "ACCEPTED")
                binding.tvJobDetailStatusPill.text = if (isAcceptedOnly) "ĐÃ NHẬN" else "Đang thực hiện"
                binding.tvJobDetailStatusPill.setBackgroundResource(if (isAcceptedOnly) R.drawable.bg_badge_da_nhan else R.drawable.bg_badge_duoc_giao)
                binding.tvJobDetailStatusPill.setTextColor(if (isAcceptedOnly) Color.parseColor("#16A34A") else Color.parseColor("#2563EB"))

                val timeStr = (job.startedAt ?: job.assignedAt ?: job.createdAt ?: job.id).let { TimeUtils.formatDisplayDateTime(it) }
                binding.tvJobDetailTimeSubtitle.text = "Bắt đầu: $timeStr"

                val completedCount = job.items?.count { it.status == "COLLECTED" } ?: 0
                val percent = if (stopsCount > 0) ((completedCount * 100) / stopsCount) else 0

                if (!isAcceptedOnly) {
                    // Show Progress Bar
                    binding.layoutDetailProgressSection.visibility = View.VISIBLE
                    binding.tvDetailProgressText.text = "$completedCount / $stopsCount điểm"
                    binding.tvDetailProgressPercent.text = "Hoàn thành $percent%"
                    binding.pbDetailProgress.progressDrawable = ContextCompat.getDrawable(this, R.drawable.bg_bin_percent_green)
                    binding.pbDetailProgress.progress = percent
                } else {
                    binding.layoutDetailProgressSection.visibility = View.GONE
                }

                binding.cardDetailPausedWarning.visibility = View.GONE
                binding.cardDetailCompletedSummary.visibility = View.GONE
                binding.cardDetailActiveSummary.visibility = View.VISIBLE
                binding.tvDetailStopListTitle.text = "Danh sách điểm dừng"

                binding.layoutDetailBottomActions.visibility = View.VISIBLE

                if (isAcceptedOnly) {
                    // Case ACCEPTED: [ 🗺 Xem tuyến đường ] + [ Bắt đầu thu gom ]
                    binding.btnDetailActionLeft.visibility = View.VISIBLE
                    binding.btnDetailActionLeft.text = "Xem tuyến đường"
                    binding.btnDetailActionLeft.setBackgroundResource(R.drawable.bg_button_outlined_green)
                    binding.btnDetailActionLeft.setTextColor(ContextCompat.getColor(this, R.color.profile_green_primary))
                    binding.btnDetailActionLeft.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_map_route_icon, 0, 0, 0)
                    binding.btnDetailActionLeft.setOnClickListener { openRouteDetail() }

                    binding.btnDetailActionRight.visibility = View.VISIBLE
                    binding.btnDetailActionRight.text = "Bắt đầu thu gom"
                    binding.btnDetailActionRight.setBackgroundResource(R.drawable.bg_button_primary_green)
                    binding.btnDetailActionRight.setTextColor(Color.WHITE)
                    binding.btnDetailActionRight.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                    binding.btnDetailActionRight.setOnClickListener { startJobExecution() }
                } else {
                    // Case IN_PROGRESS: SINGLE BUTTON ONLY [ Ⅱ Tạm dừng ]
                    binding.btnDetailActionLeft.visibility = View.GONE

                    binding.btnDetailActionRight.visibility = View.VISIBLE
                    binding.btnDetailActionRight.text = "Tạm dừng"
                    binding.btnDetailActionRight.setBackgroundResource(R.drawable.bg_btn_outlined_gray)
                    binding.btnDetailActionRight.setTextColor(Color.parseColor("#1E293B"))
                    binding.btnDetailActionRight.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause, 0, 0, 0)
                    binding.btnDetailActionRight.setOnClickListener { showPauseReasonBottomSheet() }
                }
            }

            // =================================================================
            // CASE 04 — TẠM DỪNG (PAUSED)
            // =================================================================
            "PAUSED" -> {
                binding.tvJobDetailStatusPill.text = "TẠM DỪNG"
                binding.tvJobDetailStatusPill.setBackgroundResource(R.drawable.bg_badge_tam_dung)
                binding.tvJobDetailStatusPill.setTextColor(Color.WHITE)

                val timeStr = (job.pausedAt ?: job.startedAt ?: job.assignedAt ?: job.id).let { TimeUtils.formatDisplayDateTime(it) }
                binding.tvJobDetailTimeSubtitle.text = "Tạm dừng lúc: $timeStr"

                // Calculate progress without resetting
                val completedCount = job.items?.count { it.status == "COLLECTED" } ?: (stopsCount / 2).coerceAtLeast(1)
                val percent = if (stopsCount > 0) ((completedCount * 100) / stopsCount) else 50

                binding.layoutDetailProgressSection.visibility = View.VISIBLE
                binding.tvDetailProgressText.text = "Tiến độ: $completedCount/$stopsCount điểm"
                binding.tvDetailProgressPercent.text = "$percent%"
                binding.pbDetailProgress.progressDrawable = ContextCompat.getDrawable(this, R.drawable.bg_bin_percent_orange)
                binding.pbDetailProgress.progress = percent

                // Warning Card with Reason
                binding.cardDetailPausedWarning.visibility = View.VISIBLE
                val reason = job.pauseReason?.takeIf { it.isNotBlank() } ?: "Tạm dừng theo yêu cầu của tài xế"
                binding.tvDetailPausedReason.text = reason

                binding.cardDetailCompletedSummary.visibility = View.GONE
                binding.cardDetailActiveSummary.visibility = View.VISIBLE
                binding.tvDetailStopListTitle.text = "Danh sách điểm dừng"

                // Bottom Buttons: [ ▶ Tiếp tục ca ] (Primary Green) + [ 🗺 Xem tuyến đường ] (Outlined Green)
                binding.layoutDetailBottomActions.visibility = View.VISIBLE
                binding.btnDetailActionLeft.visibility = View.VISIBLE
                binding.btnDetailActionLeft.text = "Tiếp tục ca"
                binding.btnDetailActionLeft.setBackgroundResource(R.drawable.bg_button_primary_green)
                binding.btnDetailActionLeft.setTextColor(Color.WHITE)
                binding.btnDetailActionLeft.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play_arrow, 0, 0, 0)
                binding.btnDetailActionLeft.setOnClickListener { resumeJobExecution() }

                binding.btnDetailActionRight.visibility = View.VISIBLE
                binding.btnDetailActionRight.text = "Xem tuyến đường"
                binding.btnDetailActionRight.setBackgroundResource(R.drawable.bg_button_outlined_green)
                binding.btnDetailActionRight.setTextColor(ContextCompat.getColor(this, R.color.profile_green_primary))
                binding.btnDetailActionRight.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_map_route_icon, 0, 0, 0)
                binding.btnDetailActionRight.setOnClickListener { openRouteDetail() }
            }

            // =================================================================
            // CASE 05 — HOÀN THÀNH (COMPLETED)
            // =================================================================
            "COMPLETED" -> {
                binding.tvJobDetailStatusPill.text = "HOÀN THÀNH"
                binding.tvJobDetailStatusPill.setBackgroundResource(R.drawable.bg_badge_hoan_thanh)
                binding.tvJobDetailStatusPill.setTextColor(Color.WHITE)

                val timeStr = (job.completedAt ?: job.createdAt ?: job.id).let { TimeUtils.formatDisplayDateTime(it) }
                binding.tvJobDetailTimeSubtitle.text = "Hoàn thành lúc: $timeStr"

                // Top Summary Statistics
                binding.cardDetailCompletedSummary.visibility = View.VISIBLE
                binding.tvCompletedStopsCount.text = "$stopsCount/$stopsCount"
                binding.tvCompletedIncidentCount.text = "0"

                binding.layoutDetailProgressSection.visibility = View.GONE
                binding.cardDetailPausedWarning.visibility = View.GONE
                binding.cardDetailActiveSummary.visibility = View.GONE
                binding.tvDetailStopListTitle.text = "Lộ trình đã hoàn thành"

                // Bottom Buttons: Single [ 🗺 Xem tuyến đường ] (Outlined Green)
                binding.layoutDetailBottomActions.visibility = View.VISIBLE
                binding.btnDetailActionLeft.visibility = View.GONE

                binding.btnDetailActionRight.visibility = View.VISIBLE
                binding.btnDetailActionRight.text = "Xem tuyến đường"
                binding.btnDetailActionRight.setBackgroundResource(R.drawable.bg_button_outlined_green)
                binding.btnDetailActionRight.setTextColor(ContextCompat.getColor(this, R.color.profile_green_primary))
                binding.btnDetailActionRight.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_map_route_icon, 0, 0, 0)
                binding.btnDetailActionRight.setOnClickListener { openRouteDetail() }
            }

            // Fallback (Cancelled / Other)
            else -> {
                binding.tvJobDetailStatusPill.text = if (statusUpper == "CANCELLED") "ĐÃ HỦY" else job.status
                binding.tvJobDetailStatusPill.setBackgroundResource(R.drawable.bg_tag_danger)
                binding.tvJobDetailStatusPill.setTextColor(ContextCompat.getColor(this, R.color.profile_danger))

                binding.layoutDetailProgressSection.visibility = View.GONE
                binding.cardDetailPausedWarning.visibility = View.GONE
                binding.cardDetailCompletedSummary.visibility = View.GONE
                binding.cardDetailActiveSummary.visibility = View.VISIBLE
                binding.layoutDetailBottomActions.visibility = View.GONE
            }
        }
    }

    private fun populateBinsList(job: JobDto?, binsMap: Map<String, SmartBinDto>) {
        val targets = when {
            job?.targetBinIds?.isNotEmpty() == true -> job.targetBinIds!!
            job?.items?.isNotEmpty() == true -> job.items!!.map { it.binId }
            else -> emptyList()
        }

        binding.llBinsDetailContainer.removeAllViews()

        if (targets.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Chưa có danh sách điểm dừng."
                setTextColor(ContextCompat.getColor(context, R.color.profile_text_secondary))
                textSize = 13f
            }
            binding.llBinsDetailContainer.addView(emptyTv)
            return
        }

        val statusUpper = job?.status?.uppercase().orEmpty()
        val isPaused = statusUpper == "PAUSED"
        val isCompleted = statusUpper == "COMPLETED"
        val isInProgress = statusUpper == "IN_PROGRESS" || isPaused

        val completedCount = job?.items?.count { it.status == "COLLECTED" } ?: (if (isCompleted) targets.size else if (isPaused || isInProgress) 2 else 0)

        targets.forEachIndexed { index, binId ->
            val bin = binsMap[binId]
            val itemBinding = ItemJobStopRowBinding.inflate(LayoutInflater.from(this), binding.llBinsDetailContainer, false)

            // 1. Vertical Connector line visibility
            itemBinding.timelineLineTop.visibility = if (index > 0) View.VISIBLE else View.INVISIBLE
            itemBinding.timelineLineBottom.visibility = if (index < targets.size - 1) View.VISIBLE else View.INVISIBLE

            // 2. Bin Code & Address
            itemBinding.tvStopBinId.text = binId
            itemBinding.tvStopAddress.text = bin?.location ?: bin?.name ?: "Vị trí thùng #$binId"

            // 3. Status based on Case
            if (isCompleted) {
                // Case 05: Completed Timeline with right timestamp from real collectedAt / completedAt
                itemBinding.containerStopIcon.setBackgroundResource(R.drawable.bg_avatar_circle_green)
                itemBinding.tvStopIconGlyph.text = "${index + 1}"
                itemBinding.tvStopIconGlyph.setTextColor(Color.WHITE)
                itemBinding.tvStopStatusBadge.visibility = View.GONE
                itemBinding.tvStopCompletedTime.visibility = View.VISIBLE

                val stopTime = job?.items?.getOrNull(index)?.collectedAt?.let { TimeUtils.formatDisplayTime(it) }
                    ?: job?.completedAt?.let { TimeUtils.formatDisplayTime(it) }
                    ?: "--"
                itemBinding.tvStopCompletedTime.text = stopTime
            } else if (isInProgress || isPaused) {
                // Case 03 (IN_PROGRESS) & Case 04 (PAUSED)
                itemBinding.tvStopCompletedTime.visibility = View.GONE
                itemBinding.tvStopStatusBadge.visibility = View.VISIBLE

                if (index < completedCount) {
                    // Đã thu gom
                    itemBinding.containerStopIcon.setBackgroundResource(R.drawable.bg_avatar_circle_green)
                    itemBinding.tvStopIconGlyph.text = "✓"
                    itemBinding.tvStopIconGlyph.setTextColor(Color.WHITE)

                    itemBinding.tvStopStatusBadge.text = "Đã thu gom"
                    itemBinding.tvStopStatusBadge.setBackgroundResource(R.drawable.bg_badge_da_thu_gom)
                    itemBinding.tvStopStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.profile_green_primary))
                } else if (index == completedCount) {
                    // Đang đến (Current stop)
                    itemBinding.containerStopIcon.setBackgroundResource(R.drawable.bg_icon_circle_current_stop)
                    itemBinding.tvStopIconGlyph.text = ""

                    itemBinding.tvStopStatusBadge.text = "Đang đến"
                    itemBinding.tvStopStatusBadge.setBackgroundResource(R.drawable.bg_badge_dang_den)
                    itemBinding.tvStopStatusBadge.setTextColor(Color.parseColor("#2563EB"))
                } else {
                    // Chưa thu gom (Pending stop)
                    itemBinding.containerStopIcon.setBackgroundResource(R.drawable.bg_icon_circle_pending_stop)
                    itemBinding.tvStopIconGlyph.text = ""

                    itemBinding.tvStopStatusBadge.text = "Chưa thu gom"
                    itemBinding.tvStopStatusBadge.setBackgroundResource(R.drawable.bg_badge_chua_thu_gom)
                    itemBinding.tvStopStatusBadge.setTextColor(Color.parseColor("#64748B"))
                }
            } else {
                // Case 02 (ASSIGNED): Simple numbered list
                itemBinding.containerStopIcon.setBackgroundResource(R.drawable.bg_avatar_circle_green)
                itemBinding.tvStopIconGlyph.text = "${index + 1}"
                itemBinding.tvStopIconGlyph.setTextColor(Color.WHITE)
                itemBinding.tvStopStatusBadge.visibility = View.GONE
                itemBinding.tvStopCompletedTime.visibility = View.GONE
            }

            binding.llBinsDetailContainer.addView(itemBinding.root)
        }
    }

    private fun renderMapRoute() {
        val job = currentJob ?: return
        val targetBins = when {
            !job.targetBinIds.isNullOrEmpty() -> job.targetBinIds!!
            !job.items.isNullOrEmpty() -> job.items!!.map { it.binId }
            else -> emptyList()
        }

        lifecycleScope.launch {
            val binsRes = binsRepo.getBins().getOrDefault(emptyList())
            val binsMap = binsRes.associateBy { it.deviceId }

            val waypointsArray = JSONArray()
            val coordsArray = JSONArray()

            // 1. Truck / Driver Location at start
            val firstBin = targetBins.firstOrNull()?.let { binsMap[it] }
            val startLat = firstBin?.latitude ?: 10.7769
            val startLng = firstBin?.longitude ?: 106.7009

            val truckObj = JSONObject().apply {
                put("isTruck", true)
                put("type", "TRUCK")
                put("lat", startLat - 0.002)
                put("lng", startLng - 0.002)
            }
            waypointsArray.put(truckObj)
            coordsArray.put(JSONArray(listOf(startLat - 0.002, startLng - 0.002)))

            // 2. Waypoints for stops
            targetBins.forEachIndexed { index, binId ->
                val bin = binsMap[binId]
                val lat = bin?.latitude ?: (startLat + (index * 0.003))
                val lng = bin?.longitude ?: (startLng + (index * 0.003))

                val wpObj = JSONObject().apply {
                    put("order", index + 1)
                    put("deviceId", binId)
                    put("lat", lat)
                    put("lng", lng)
                    put("isCollected", job.status == "COMPLETED")
                }
                waypointsArray.put(wpObj)
                coordsArray.put(JSONArray(listOf(lat, lng)))
            }

            val coordsJson = coordsArray.toString()
            val waypointsJson = waypointsArray.toString()

            val js = "SmartWasteMap.setRoute($coordsJson, $waypointsJson);"
            binding.mapDetailWebView.evaluateJavascript(js, null)
        }
    }

    private fun openRouteDetail() {
        val intent = Intent(this, RouteDetailActivity::class.java).apply {
            putExtra("JOB_ID", jobId)
        }
        startActivity(intent)
    }

    private fun startJobExecution() {
        val intent = Intent(this, JobExecutionActivity::class.java).apply {
            putExtra("JOB_ID", jobId)
        }
        startActivity(intent)
        finish()
    }

    private fun resumeJobExecution() {
        lifecycleScope.launch {
            val res = jobsRepo.resumeJob(jobId)
            if (res.isSuccess) {
                Toast.makeText(this@JobDetailActivity, "▶ Tiếp tục thực hiện ca thu gom!", Toast.LENGTH_SHORT).show()
                // Reload job detail into IN_PROGRESS state without resetting any progress!
                loadJobData()
            } else {
                Toast.makeText(this@JobDetailActivity, "Không thể tiếp tục: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // =========================================================================
    // SHEET A: XÁC NHẬN CHẤP NHẬN JOB
    // =========================================================================

    private fun showAcceptJobBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_accept_job, null)
        dialog.setContentView(sheetView)

        val code = formatJobCode(jobId)
        val tvCode = sheetView.findViewById<TextView>(R.id.tvAcceptSheetJobCode)
        val tvStops = sheetView.findViewById<TextView>(R.id.tvAcceptStopsCount)
        val tvDistance = sheetView.findViewById<TextView>(R.id.tvAcceptDistance)
        val tvDuration = sheetView.findViewById<TextView>(R.id.tvAcceptDuration)

        val btnCancel = sheetView.findViewById<View>(R.id.btnCancelAcceptSheet)
        val btnConfirm = sheetView.findViewById<View>(R.id.btnConfirmAcceptSheet)

        tvCode?.text = code
        currentJob?.let { job ->
            val stopsCount = job.targetBinIds?.size ?: (job.items?.size ?: 0)
            val distanceKm = (job.routeData?.distanceMeters ?: (stopsCount * 950.0)) / 1000.0
            val durationMins = ((job.routeData?.durationSeconds ?: (stopsCount * 360.0)) / 60.0).roundToInt()

            tvStops?.text = stopsCount.toString()
            tvDistance?.text = String.format(Locale.US, "%.1f km", distanceKm)
            tvDuration?.text = "$durationMins phút"
        }

        btnCancel?.setOnClickListener { dialog.dismiss() }

        btnConfirm?.setOnClickListener {
            btnConfirm.isEnabled = false
            if (btnConfirm is TextView) {
                btnConfirm.text = "⏳ Đang tiếp nhận..."
            }
            lifecycleScope.launch {
                val res = jobsRepo.acceptJob(jobId)
                if (res.isSuccess) {
                    dialog.dismiss()
                    Toast.makeText(this@JobDetailActivity, "✓ Đã tiếp nhận nhiệm vụ thành công!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@JobDetailActivity, JobExecutionActivity::class.java).apply {
                        putExtra("JOB_ID", jobId)
                    }
                    startActivity(intent)
                    finish()
                } else {
                    btnConfirm.isEnabled = true
                    if (btnConfirm is TextView) {
                        btnConfirm.text = "Thử lại"
                    }
                    val msg = res.exceptionOrNull()?.message ?: "Lỗi kết nối máy chủ"
                    Toast.makeText(this@JobDetailActivity, "Không thể nhận ca: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    // =========================================================================
    // SHEET B: LÝ DO TỪ CHỐI NHIỆM VỤ
    // =========================================================================

    private fun showRejectJobBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_job_reason, null)
        dialog.setContentView(sheetView)

        val tvTitle = sheetView.findViewById<TextView>(R.id.tvJobReasonSheetTitle)
        tvTitle?.text = "Lý do từ chối nhiệm vụ"

        val chipTraffic = sheetView.findViewById<TextView>(R.id.chipReasonTraffic)
        val chipBroken = sheetView.findViewById<TextView>(R.id.chipReasonBrokenVehicle)
        val chipLunch = sheetView.findViewById<TextView>(R.id.chipReasonLunch)
        val chipDevice = sheetView.findViewById<TextView>(R.id.chipReasonDeviceError)
        val chipOther = sheetView.findViewById<TextView>(R.id.chipReasonOther)

        val etNote = sheetView.findViewById<EditText>(R.id.etJobReasonNote)
        val tvCounter = sheetView.findViewById<TextView>(R.id.tvJobReasonCounter)
        val btnClose = sheetView.findViewById<View>(R.id.btnCloseJobReasonSheet)
        val btnConfirm = sheetView.findViewById<View>(R.id.btnConfirmJobReason)

        var selectedReason = "Kẹt xe"

        val allChips = listOf(chipTraffic, chipBroken, chipLunch, chipDevice, chipOther)
        fun selectChip(selected: TextView, reason: String) {
            selectedReason = reason
            allChips.forEach { chip ->
                if (chip == selected) {
                    chip?.setBackgroundResource(R.drawable.bg_chip_filter_active)
                    chip?.setTextColor(ContextCompat.getColor(this, R.color.profile_green_primary))
                    chip?.typeface = android.graphics.Typeface.DEFAULT_BOLD
                } else {
                    chip?.setBackgroundResource(R.drawable.bg_chip_filter_inactive)
                    chip?.setTextColor(ContextCompat.getColor(this, R.color.profile_text_primary))
                    chip?.typeface = android.graphics.Typeface.DEFAULT
                }
            }
        }

        chipTraffic?.setOnClickListener { selectChip(chipTraffic, "Kẹt xe") }
        chipBroken?.setOnClickListener { selectChip(chipBroken, "Xe hỏng") }
        chipLunch?.setOnClickListener { selectChip(chipLunch, "Nghỉ trưa") }
        chipDevice?.setOnClickListener { selectChip(chipDevice, "Thiết bị lỗi") }
        chipOther?.setOnClickListener { selectChip(chipOther, "Khác") }

        etNote?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tvCounter?.text = "${s?.length ?: 0}/200"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClose?.setOnClickListener { dialog.dismiss() }

        btnConfirm?.setOnClickListener {
            val finalReason = if (selectedReason == "Khác" && !etNote?.text.isNullOrBlank()) {
                etNote?.text.toString().trim()
            } else {
                val note = etNote?.text?.toString()?.trim().orEmpty()
                if (note.isNotBlank()) "$selectedReason: $note" else selectedReason
            }

            btnConfirm.isEnabled = false
            lifecycleScope.launch {
                val res = jobsRepo.rejectJob(jobId, finalReason)
                dialog.dismiss()
                if (res.isSuccess) {
                    Toast.makeText(this@JobDetailActivity, "Đã từ chối nhiệm vụ #$jobId.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@JobDetailActivity, "Lỗi: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    // =========================================================================
    // SHEET C: LÝ DO TẠM DỪNG CA THU GOM
    // =========================================================================

    private fun showPauseReasonBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_job_reason, null)
        dialog.setContentView(sheetView)

        val tvTitle = sheetView.findViewById<TextView>(R.id.tvJobReasonSheetTitle)
        tvTitle?.text = "Lý do tạm dừng ca thu gom"

        val chipTraffic = sheetView.findViewById<TextView>(R.id.chipReasonTraffic)
        val chipBroken = sheetView.findViewById<TextView>(R.id.chipReasonBrokenVehicle)
        val chipLunch = sheetView.findViewById<TextView>(R.id.chipReasonLunch)
        val chipDevice = sheetView.findViewById<TextView>(R.id.chipReasonDeviceError)
        val chipOther = sheetView.findViewById<TextView>(R.id.chipReasonOther)

        val etNote = sheetView.findViewById<EditText>(R.id.etJobReasonNote)
        val tvCounter = sheetView.findViewById<TextView>(R.id.tvJobReasonCounter)
        val btnClose = sheetView.findViewById<View>(R.id.btnCloseJobReasonSheet)
        val btnConfirm = sheetView.findViewById<View>(R.id.btnConfirmJobReason)

        var selectedReason = "Kẹt xe"

        val allChips = listOf(chipTraffic, chipBroken, chipLunch, chipDevice, chipOther)
        fun selectChip(selected: TextView, reason: String) {
            selectedReason = reason
            allChips.forEach { chip ->
                if (chip == selected) {
                    chip?.setBackgroundResource(R.drawable.bg_chip_filter_active)
                    chip?.setTextColor(ContextCompat.getColor(this, R.color.profile_green_primary))
                    chip?.typeface = android.graphics.Typeface.DEFAULT_BOLD
                } else {
                    chip?.setBackgroundResource(R.drawable.bg_chip_filter_inactive)
                    chip?.setTextColor(ContextCompat.getColor(this, R.color.profile_text_primary))
                    chip?.typeface = android.graphics.Typeface.DEFAULT
                }
            }
        }

        chipTraffic?.setOnClickListener { selectChip(chipTraffic, "Kẹt xe") }
        chipBroken?.setOnClickListener { selectChip(chipBroken, "Xe hỏng") }
        chipLunch?.setOnClickListener { selectChip(chipLunch, "Nghỉ trưa") }
        chipDevice?.setOnClickListener { selectChip(chipDevice, "Thiết bị lỗi") }
        chipOther?.setOnClickListener { selectChip(chipOther, "Khác") }

        etNote?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tvCounter?.text = "${s?.length ?: 0}/200"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClose?.setOnClickListener { dialog.dismiss() }

        btnConfirm?.setOnClickListener {
            val finalReason = if (selectedReason == "Khác" && !etNote?.text.isNullOrBlank()) {
                etNote?.text.toString().trim()
            } else {
                val note = etNote?.text?.toString()?.trim().orEmpty()
                if (note.isNotBlank()) "$selectedReason: $note" else selectedReason
            }

            btnConfirm.isEnabled = false
            lifecycleScope.launch {
                val res = jobsRepo.pauseJob(jobId, finalReason)
                dialog.dismiss()
                if (res.isSuccess) {
                    Toast.makeText(this@JobDetailActivity, "⏸ Ca #$jobId đã tạm dừng.", Toast.LENGTH_SHORT).show()
                    loadJobData()
                } else {
                    Toast.makeText(this@JobDetailActivity, "Lỗi: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun formatJobCode(id: String): String {
        val clean = id.removePrefix("#")
        return if (clean.startsWith("JOB_")) "#$clean" else "#JOB_$clean"
    }
}
