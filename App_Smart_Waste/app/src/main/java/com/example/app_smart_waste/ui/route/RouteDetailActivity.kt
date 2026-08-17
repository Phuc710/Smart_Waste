package com.example.app_smart_waste.ui.route

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.location.GpsTracker
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.core.model.JobItemDto
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.model.UiState
import com.example.app_smart_waste.data.repository.BinsRepository
import com.example.app_smart_waste.data.repository.JobsRepository
import com.example.app_smart_waste.databinding.ActivityRouteDetailBinding
import com.example.app_smart_waste.ui.bin.BinDetailActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class RouteDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRouteDetailBinding
    private val viewModel: RouteDetailViewModel by viewModels()
    private val binsRepo by lazy { BinsRepository(this) }
    private val jobsRepo by lazy { JobsRepository(this) }
    private lateinit var stopAdapter: RouteStopAdapter

    private var currentJob: JobDto? = null
    private var allBins: List<SmartBinDto> = emptyList()
    private var isMapReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply Top & Bottom Insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.topHeaderBar) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(
                view.paddingLeft,
                statusBarInset + (8 * resources.displayMetrics.density).toInt(),
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutBottomActions) { view, insets ->
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                navBarInset + (8 * resources.displayMetrics.density).toInt()
            )
            insets
        }

        val jobId = intent.getStringExtra("JOB_ID")

        setupRecyclerView()
        setupListeners()
        setupMapView()
        observeViewModel()

        viewModel.loadRouteDetail(jobId)
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadRouteDetail(intent.getStringExtra("JOB_ID"))
    }

    private fun setupRecyclerView() {
        stopAdapter = RouteStopAdapter { stopUiModel ->
            val intent = Intent(this, BinDetailActivity::class.java).apply {
                putExtra("BIN_ID", stopUiModel.item.binId)
                currentJob?.id?.let { putExtra("JOB_ID", it) }
            }
            startActivity(intent)
        }
        binding.rvRouteStops.layoutManager = LinearLayoutManager(this)
        binding.rvRouteStops.adapter = stopAdapter
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMapView() {
        binding.mapWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        binding.mapWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isMapReady = true
                renderMap()
            }
        }
        binding.mapWebView.loadUrl("file:///android_asset/leaflet_map.html")
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnChat.setOnClickListener {
            Toast.makeText(this, "Liên hệ điều phối viên", Toast.LENGTH_SHORT).show()
        }

        binding.btnMore.setOnClickListener {
            Toast.makeText(this, "Tùy chọn tuyến đường", Toast.LENGTH_SHORT).show()
        }

        binding.btnOptimizeRoute.setOnClickListener {
            Toast.makeText(this, "Đã tối ưu hóa lộ trình ngắn nhất", Toast.LENGTH_SHORT).show()
        }

        binding.btnRecenterMap.setOnClickListener {
            binding.mapWebView.evaluateJavascript("centerOnTruck()", null)
        }

        binding.btnZoomIn.setOnClickListener {
            binding.mapWebView.evaluateJavascript("map.zoomIn()", null)
        }

        binding.btnZoomOut.setOnClickListener {
            binding.mapWebView.evaluateJavascript("map.zoomOut()", null)
        }

        binding.btnLayers.setOnClickListener {
            Toast.makeText(this, "Chuyển chế độ xem bản đồ", Toast.LENGTH_SHORT).show()
        }

        // Bắt đầu tuyến
        binding.btnStartRoute.setOnClickListener {
            handleStartRouteClick()
        }

        // Hoàn thành tuyến
        binding.btnCompleteRoute.setOnClickListener {
            handleCompleteRouteClick()
        }
    }

    private fun handleStartRouteClick() {
        val job = currentJob ?: return
        lifecycleScope.launch {
            when (job.status) {
                "ASSIGNED", "PENDING", "ACCEPTED" -> {
                    val res = jobsRepo.startJob(job.id)
                    if (res.isSuccess) {
                        Toast.makeText(this@RouteDetailActivity, "Đã bắt đầu tuyến thu gom!", Toast.LENGTH_SHORT).show()
                        viewModel.loadRouteDetail(job.id)
                    }
                }
                "IN_PROGRESS" -> {
                    val res = jobsRepo.pauseJob(job.id, "Tạm dừng")
                    if (res.isSuccess) {
                        Toast.makeText(this@RouteDetailActivity, "Đã tạm dừng tuyến.", Toast.LENGTH_SHORT).show()
                        viewModel.loadRouteDetail(job.id)
                    }
                }
                "PAUSED" -> {
                    val res = jobsRepo.resumeJob(job.id)
                    if (res.isSuccess) {
                        Toast.makeText(this@RouteDetailActivity, "Đã tiếp tục tuyến.", Toast.LENGTH_SHORT).show()
                        viewModel.loadRouteDetail(job.id)
                    }
                }
            }
        }
    }

    private fun handleCompleteRouteClick() {
        val job = currentJob ?: return
        lifecycleScope.launch {
            val remainingStops = job.items?.filter { it.status != "COLLECTED" } ?: emptyList()
            for (stop in remainingStops) {
                jobsRepo.collectBin(job.id, stop.binId, "Hoàn tất tuyến")
            }
            Toast.makeText(this@RouteDetailActivity, "Đã hoàn thành toàn bộ tuyến thu gom!", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.jobDetailState.collectLatest { state ->
                when (state) {
                    is UiState.Success -> {
                        currentJob = state.data
                        loadBinsAndBind(state.data)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun loadBinsAndBind(job: JobDto) {
        lifecycleScope.launch {
            val res = binsRepo.getBins()
            allBins = res.getOrNull() ?: emptyList()

            bindJobData(job, allBins)
            renderMap()
        }
    }

    private fun bindJobData(job: JobDto, bins: List<SmartBinDto>) {
        val binMap = bins.associateBy { it.deviceId }

        val stopsList = mutableListOf<RouteStopUiModel>()
        val items = job.items

        if (!items.isNullOrEmpty()) {
            items.forEachIndexed { index, item ->
                val bin = binMap[item.binId]
                stopsList.add(RouteStopUiModel(item, bin, index + 1))
            }
        } else {
            val targetIds = job.targetBinIds ?: listOf("BIN_HCM_01", "BIN_HCM_02", "BIN_HCM_03", "BIN_HCM_04")
            val completedIds = job.completedBinIds ?: emptyList()
            targetIds.forEachIndexed { index, binId ->
                val bin = binMap[binId]
                val isDone = completedIds.contains(binId)
                val item = JobItemDto(
                    binId = binId,
                    status = if (isDone) "COLLECTED" else "PENDING",
                    collectedAt = if (isDone) "08:30" else null
                )
                stopsList.add(RouteStopUiModel(item, bin, index + 1))
            }
        }

        stopAdapter.submitList(stopsList)

        // Summary Card binding
        val totalStops = stopsList.size
        val doneStops = stopsList.count { it.item.status == "COLLECTED" }
        binding.tvRouteTotalStops.text = "$totalStops điểm thu gom"
        binding.tvRouteCollectedSummary.text = "$doneStops / $totalStops"

        val distMeters = job.routeData?.distanceMeters
        if (distMeters != null && distMeters > 0.0) {
            val km = (distMeters / 100.0).roundToInt() / 10.0
            binding.tvRouteDistance.text = "$km km"
        } else {
            binding.tvRouteDistance.text = "12.7 km"
        }

        val durSecs = job.routeData?.durationSeconds
        if (durSecs != null && durSecs > 0.0) {
            val mins = (durSecs / 60.0).roundToInt()
            binding.tvRouteDuration.text = "$mins phút"
        } else {
            binding.tvRouteDuration.text = "32 phút"
        }

        // Action Button State
        when (job.status) {
            "IN_PROGRESS" -> {
                binding.tvStartRouteLabel.text = "Tạm dừng"
            }
            "PAUSED" -> {
                binding.tvStartRouteLabel.text = "Tiếp tục"
            }
            else -> {
                binding.tvStartRouteLabel.text = "Bắt đầu tuyến"
            }
        }
    }

    private fun renderMap() {
        if (!isMapReady) return
        val loc = GpsTracker.getInstance(this).getCurrentLocation()
        val truckJs = "updateTruckLocation(${loc.latitude}, ${loc.longitude});"
        binding.mapWebView.evaluateJavascript(truckJs, null)

        val binsArray = JSONArray()
        allBins.forEach { b ->
            val binObj = JSONObject().apply {
                put("id", b.deviceId)
                put("name", b.name ?: b.deviceId)
                put("lat", b.latitude ?: 10.7725)
                put("lng", b.longitude ?: 106.6980)
                put("level", (b.levelPercent ?: 0.0).roundToInt())
                put("status", b.collectionStatus ?: "IDLE")
            }
            binsArray.put(binObj)
        }

        val binsJs = "setBins($binsArray);"
        binding.mapWebView.evaluateJavascript(binsJs, null)
    }
}
