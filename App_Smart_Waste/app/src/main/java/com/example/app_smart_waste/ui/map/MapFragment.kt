package com.example.app_smart_waste.ui.map

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.location.GpsTracker
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.network.RealtimeManager
import com.example.app_smart_waste.databinding.FragmentMapBinding
import com.example.app_smart_waste.ui.incident.IncidentReportActivity
import com.example.app_smart_waste.ui.jobs.JobExecutionActivity
import com.example.app_smart_waste.ui.main.MainActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapViewModel by viewModels()

    private var gpsTracker: GpsTracker? = null
    private var isMapReady = false

    // Không dùng tọa độ giả làm vị trí tài xế.
    private var currentDriverLat: Double? = null
    private var currentDriverLng: Double? = null
    private var currentHeading = 0f

    private val realtimeManager by lazy { RealtimeManager(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.topMapContainer) { topContainer, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            topContainer.setPadding(
                topContainer.paddingLeft,
                statusBarTop + (6 * resources.displayMetrics.density).toInt(),
                topContainer.paddingRight,
                topContainer.paddingBottom
            )
            insets
        }

        setupLeafletWebView()
        setupTopBarListeners()
        setupFloatingControls()
        setupBottomCardsListeners()
        observeViewModel()
        setupGpsTracking()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadMapData(arguments?.getString("ARG_TARGET_JOB_ID"))
    }

    override fun onStart() {
        super.onStart()
        realtimeManager.connect(object : RealtimeManager.Listener {
            override fun onJobUpdated() {
                activity?.runOnUiThread {
                    viewModel.loadMapData(arguments?.getString("ARG_TARGET_JOB_ID"))
                }
            }

            override fun onBinOverfullAlert(alert: RealtimeManager.BinOverfullAlert) {
                activity?.runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "🔴 Cảnh báo thùng ${alert.binId} vượt mức ${alert.levelPercent}%!",
                        Toast.LENGTH_LONG
                    ).show()
                    viewModel.loadMapData(arguments?.getString("ARG_TARGET_JOB_ID"))
                }
            }
        })
    }

    override fun onStop() {
        realtimeManager.disconnect()
        super.onStop()
    }

    // =========================================================================
    // 1. LEAFLET WEBVIEW
    // =========================================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupLeafletWebView() {
        binding.mapWebView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)

            addJavascriptInterface(WebAppBridge(), "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isMapReady = true
                    updateDriverLocationOnMapIfAvailable()
                    renderBinsOnMap(viewModel.displayedBins.value)
                }
            }

            loadUrl("file:///android_asset/leaflet_map.html")
        }
    }

    inner class WebAppBridge {

        @JavascriptInterface
        fun onMapReady() {
            activity?.runOnUiThread {
                isMapReady = true
                updateDriverLocationOnMapIfAvailable()
                renderBinsOnMap(viewModel.displayedBins.value)
            }
        }

        @JavascriptInterface
        fun onBinClicked(binId: String) {
            activity?.runOnUiThread {
                viewModel.selectBin(binId)

                val bin = viewModel.allBins.value.find { it.deviceId == binId }
                val lat = bin?.latitude
                val lng = bin?.longitude

                if (lat != null && lng != null) {
                    binding.mapWebView.evaluateJavascript(
                        "zoomToBin($lat, $lng);",
                        null
                    )
                }
            }
        }

        @JavascriptInterface
        fun onMapClicked(lat: Double, lng: Double) {
            activity?.runOnUiThread {
                if (!viewModel.isNavigating.value) {
                    viewModel.clearSelectedBin()
                }
            }
        }
    }

    // =========================================================================
    // 2. TOP BAR & SEARCH
    // =========================================================================

    private fun setupTopBarListeners() {
        binding.btnMapMenu.setOnClickListener {
            it.applyPressEffect {
                (activity as? MainActivity)?.switchTab(R.id.navItemProfile)
            }
        }

        binding.btnMapBell.setOnClickListener {
            it.applyPressEffect {
                (activity as? MainActivity)?.navigateToTab(R.id.navigation_jobs)
            }
        }

        binding.etSearchMapBins.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                val query = s?.toString().orEmpty()
                binding.btnClearSearch.isVisible = query.isNotEmpty()
                viewModel.setSearchQuery(query)
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.btnClearSearch.setOnClickListener {
            it.applyPressEffect {
                binding.etSearchMapBins.setText("")
                viewModel.setSearchQuery("")
            }
        }

        binding.btnMapFilter.setOnClickListener {
            it.applyPressEffect { showFilterBottomSheet() }
        }
    }

    // =========================================================================
    // 3. FLOATING CONTROLS
    // =========================================================================

    private fun setupFloatingControls() {
        binding.btnMyLocation.setOnClickListener {
            it.applyPressEffect {
                if (currentDriverLat == null || currentDriverLng == null) {
                    Toast.makeText(
                        requireContext(),
                        "Chưa xác định được vị trí hiện tại.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@applyPressEffect
                }

                binding.mapWebView.evaluateJavascript("centerOnDriver();", null)
            }
        }

        binding.btnMapLayers.setOnClickListener {
            it.applyPressEffect { showMapLayersMenu() }
        }

        binding.btnToggleSelfPickRadar.setOnClickListener {
            it.applyPressEffect {
                if (currentDriverLat == null || currentDriverLng == null) {
                    Toast.makeText(
                        requireContext(),
                        "Cần vị trí GPS để quét thùng trong bán kính.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@applyPressEffect
                }

                viewModel.toggleRadarMode()
            }
        }
    }

    // =========================================================================
    // 4. BOTTOM CARD LISTENERS
    // =========================================================================

    private fun setupBottomCardsListeners() {
        binding.btnViewAllRecentTasks.setOnClickListener {
            it.applyPressEffect {
                (activity as? MainActivity)?.navigateToTab(R.id.navigation_jobs)
            }
        }

        binding.btnSelectRecentBinPreview.setOnClickListener {
            it.applyPressEffect {
                val firstBin = viewModel.displayedBins.value.firstOrNull()
                    ?: return@applyPressEffect

                viewModel.selectBin(firstBin.deviceId)
                showBinDetailBottomSheet(firstBin.deviceId)
            }
        }

        binding.btnViewBinDetailSheet.setOnClickListener {
            it.applyPressEffect {
                val selected = viewModel.selectedBin.value
                    ?: return@applyPressEffect

                showBinDetailBottomSheet(selected.deviceId)
            }
        }

        binding.btnNavigateToSelectedBin.setOnClickListener {
            it.applyPressEffect {
                val selected = viewModel.selectedBin.value
                    ?: return@applyPressEffect

                withDriverLocation { lat, lng ->
                    viewModel.startNavigationToBin(selected, lat, lng)
                }
            }
        }

        binding.btnViewActiveJobDetail.setOnClickListener {
            it.applyPressEffect {
                val job = viewModel.activeJob.value
                    ?: return@applyPressEffect

                startActivity(
                    Intent(requireContext(), JobExecutionActivity::class.java)
                        .putExtra("JOB_ID", job.id)
                )
            }
        }

        binding.btnExitNavigation.setOnClickListener {
            it.applyPressEffect {
                viewModel.stopNavigation()
                Toast.makeText(
                    requireContext(),
                    "Đã thoát chế độ dẫn đường",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnOpenCreateSelfPickJobSheet.setOnClickListener {
            it.applyPressEffect { showCreateSelfPickJobSheet() }
        }

        binding.btnOpenGpsSettings.setOnClickListener {
            it.applyPressEffect {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }

        binding.btnResetFiltersFromEmpty.setOnClickListener {
            it.applyPressEffect {
                viewModel.resetFilters()
                binding.etSearchMapBins.setText("")
            }
        }
    }

    // =========================================================================
    // 5. OBSERVE VIEWMODEL
    // =========================================================================

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.displayedBins.collectLatest { bins ->
                        renderBinsOnMap(bins)
                        updateBottomCardsState()
                    }
                }

                launch {
                    viewModel.routeCoordinates.collectLatest { coords ->
                        if (coords.isNotEmpty()) {
                            val jsonCoords = JSONArray()

                            coords.forEach { pt ->
                                if (pt.size >= 2) {
                                    val point = JSONArray()
                                    point.put(pt[0])
                                    point.put(pt[1])
                                    jsonCoords.put(point)
                                }
                            }

                            val jsonWaypoints = JSONArray()
                            viewModel.routeWaypoints.value.forEach { waypoint ->
                                val lat = waypoint.latitude
                                val lng = waypoint.longitude

                                if (lat != null && lng != null) {
                                    val item = JSONObject()
                                    item.put("latitude", lat)
                                    item.put("longitude", lng)
                                    item.put("is_collected", waypoint.status == "COLLECTED")
                                    jsonWaypoints.put(item)
                                }
                            }

                            val coordsJs = JSONObject.quote(jsonCoords.toString())
                            val waypointsJs = JSONObject.quote(jsonWaypoints.toString())

                            binding.mapWebView.evaluateJavascript(
                                "drawRoute($coordsJs, $waypointsJs);",
                                null
                            )
                        } else {
                            binding.mapWebView.evaluateJavascript("clearRoute();", null)
                        }
                    }
                }

                launch {
                    viewModel.isRadarMode.collectLatest { enabled ->
                        val lat = currentDriverLat
                        val lng = currentDriverLng

                        if (lat != null && lng != null) {
                            val radius = viewModel.radarRadiusMeters.value
                            binding.mapWebView.evaluateJavascript(
                                "setRadarCircle($lat, $lng, $radius, $enabled);",
                                null
                            )
                        } else if (!enabled) {
                            binding.mapWebView.evaluateJavascript(
                                "setRadarCircle(0, 0, 0, false);",
                                null
                            )
                        }

                        updateBottomCardsState()
                    }
                }

                launch {
                    viewModel.selectedBin.collectLatest { bin ->
                        if (bin != null) {
                            binding.tvSelectedBinName.text = bin.deviceId
                            binding.tvSelectedBinAddress.text =
                                bin.location
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "Chưa có thông tin vị trí"

                            renderSelectedBinFill(bin)
                        }

                        updateBottomCardsState()
                    }
                }

                launch {
                    viewModel.isNavigating.collectLatest { navigating ->
                        binding.bannerTurnByTurnNav.isVisible = navigating
                        binding.headerMapDefault.isVisible = !navigating
                        updateBottomCardsState()
                    }
                }

                launch {
                    viewModel.navDistanceText.collectLatest {
                        binding.tvNavTotalDistance.text = it
                    }
                }

                launch {
                    viewModel.navEtaText.collectLatest {
                        binding.tvNavEtaText.text = it
                    }
                }

                launch {
                    viewModel.isOffline.collectLatest { offline ->
                        binding.bannerOfflineWarning.isVisible = offline
                        binding.tvOnlineStatusPill.text =
                            if (offline) "⚪ Đang ngoại tuyến" else "🟢 Đang online"
                    }
                }

                launch {
                    viewModel.isLoading.collectLatest {
                        updateBottomCardsState()
                    }
                }

                launch {
                    viewModel.toastMessage.collectLatest { msg ->
                        Toast.makeText(
                            requireContext(),
                            msg,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun renderSelectedBinFill(bin: SmartBinDto) {
        val fill = bin.levelPercent
            ?.roundToInt()
            ?.coerceIn(0, 100)

        if (fill == null) {
            binding.tvSelectedBinFillBadge.text = "--"
            binding.tvSelectedBinFillBadge.setBackgroundResource(
                R.drawable.bg_badge_status_inactive
            )
            binding.tvSelectedBinFillBadge.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.profile_text_secondary
                )
            )
            return
        }

        binding.tvSelectedBinFillBadge.text = "$fill%"
        binding.tvSelectedBinFillBadge.setBackgroundResource(
            if (fill >= 85) {
                R.drawable.badge_danger
            } else {
                R.drawable.badge_warning
            }
        )
        binding.tvSelectedBinFillBadge.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (fill >= 85) {
                    R.color.profile_danger
                } else {
                    R.color.profile_warning
                }
            )
        )
    }

    private fun updateBottomCardsState() {
        val isLoading = viewModel.isLoading.value
        val isNav = viewModel.isNavigating.value
        val isRadar = viewModel.isRadarMode.value
        val selected = viewModel.selectedBin.value
        val activeJob = viewModel.activeJob.value
        val bins = viewModel.displayedBins.value

        binding.cardDefaultRecentTasks.isVisible = false
        binding.cardSelectedBinPreview.isVisible = false
        binding.cardActiveJobRoute.isVisible = false
        binding.cardActiveNavigationBottom.isVisible = false
        binding.cardSelfPickRadarBottom.isVisible = false
        binding.cardGpsDisabled.isVisible = false
        binding.cardNoBinsFound.isVisible = false

        if (isLoading) return

        when {
            isNav -> {
                binding.cardActiveNavigationBottom.isVisible = true
            }

            isRadar -> {
                val criticalCount = bins.count {
                    (it.levelPercent ?: 0.0) >= 85.0
                }

                val radiusText = formatRadius(viewModel.radarRadiusMeters.value)

                binding.tvRadarFoundCount.text =
                    "$criticalCount thùng >85% trong bán kính $radiusText"
                binding.btnOpenCreateSelfPickJobSheet.text =
                    "Tạo job ($criticalCount điểm)"
                binding.cardSelfPickRadarBottom.isVisible = true
            }

            selected != null -> {
                binding.cardSelectedBinPreview.isVisible = true
            }

            activeJob != null &&
                activeJob.status in listOf("ASSIGNED", "ACCEPTED", "IN_PROGRESS") -> {

                binding.tvActiveJobCode.text = "#${activeJob.id}"

                val targets = activeJob.targetBinIds.orEmpty().size
                val done = activeJob.completedBinIds.orEmpty().size
                val percent = if (targets > 0) {
                    (done * 100 / targets).coerceIn(0, 100)
                } else {
                    0
                }

                binding.tvActiveJobProgressFraction.text =
                    "$done / $targets điểm • Hoàn thành $percent%"
                binding.cardActiveJobRoute.isVisible = true
            }

            bins.isEmpty() -> {
                binding.cardNoBinsFound.isVisible = true
            }

            else -> {
                val firstBin = bins.firstOrNull()
                    ?: return

                binding.tvRecentBinId.text = firstBin.deviceId
                binding.tvRecentBinDistance.text =
                    firstBin.location
                        ?.takeIf { it.isNotBlank() }
                        ?: "Chưa có thông tin vị trí"

                binding.tvRecentBinBadge.text =
                    firstBin.levelPercent
                        ?.roundToInt()
                        ?.coerceIn(0, 100)
                        ?.let { "$it%" }
                        ?: "--"

                binding.cardDefaultRecentTasks.isVisible = true
            }
        }
    }

    private fun renderBinsOnMap(bins: List<SmartBinDto>) {
        if (!isMapReady) return

        val jsonArray = JSONArray()

        bins.forEach { bin ->
            val lat = bin.latitude
            val lng = bin.longitude

            // Không đặt bin thiếu tọa độ vào một tọa độ giả.
            if (lat == null || lng == null) return@forEach

            val item = JSONObject()
            item.put("id", bin.deviceId)
            item.put("name", bin.name ?: bin.deviceId)
            item.put("latitude", lat)
            item.put("longitude", lng)
            item.put(
                "level",
                bin.levelPercent
                    ?.roundToInt()
                    ?.coerceIn(0, 100)
                    ?: JSONObject.NULL
            )
            item.put("is_online", bin.isOnline ?: JSONObject.NULL)
            item.put("is_collected", bin.status == "COLLECTED")
            jsonArray.put(item)
        }

        val binsJs = JSONObject.quote(jsonArray.toString())
        binding.mapWebView.evaluateJavascript(
            "setBins($binsJs);",
            null
        )
    }

    private fun updateDriverLocationOnMapIfAvailable() {
        val lat = currentDriverLat ?: return
        val lng = currentDriverLng ?: return
        updateDriverLocationOnMap(lat, lng, currentHeading)
    }

    private fun updateDriverLocationOnMap(
        lat: Double,
        lng: Double,
        heading: Float
    ) {
        if (!isMapReady) return

        binding.mapWebView.evaluateJavascript(
            "setDriverLocation($lat, $lng, $heading);",
            null
        )
    }

    // =========================================================================
    // 6. GPS
    // =========================================================================

    private fun setupGpsTracking() {
        val tracker = GpsTracker.getInstance(requireContext())
        gpsTracker = tracker
        tracker.startTracking()

        val location = tracker.getCurrentLocation()

        currentDriverLat = location.latitude
        currentDriverLng = location.longitude
        currentHeading = if (location.hasBearing()) {
            location.bearing
        } else {
            0f
        }

        viewModel.updateDriverLocation(
            location.latitude,
            location.longitude
        )

        updateDriverLocationOnMapIfAvailable()
    }

    private fun withDriverLocation(
        action: (lat: Double, lng: Double) -> Unit
    ) {
        val lat = currentDriverLat
        val lng = currentDriverLng

        if (lat == null || lng == null) {
            Toast.makeText(
                requireContext(),
                "Chưa xác định được vị trí hiện tại.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        action(lat, lng)
    }

    // =========================================================================
    // 7. FILTER
    // =========================================================================

    private fun showFilterBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(
            R.layout.bottom_sheet_map_filter,
            null
        )
        dialog.setContentView(view)

        val btnClose = view.findViewById<ImageView>(R.id.btnCloseFilterSheet)
        val cbCritical = view.findViewById<CheckBox>(R.id.cbFilterCritical)
        val cbWarning = view.findViewById<CheckBox>(R.id.cbFilterWarning)
        val cbNormal = view.findViewById<CheckBox>(R.id.cbFilterNormal)
        val cbOffline = view.findViewById<CheckBox>(R.id.cbFilterOffline)
        val btnReset = view.findViewById<AppCompatButton>(R.id.btnResetFilter)
        val btnApply = view.findViewById<AppCompatButton>(R.id.btnApplyFilter)

        val currentLevels = viewModel.filterLevels.value
        cbCritical.isChecked = currentLevels.contains("CRITICAL")
        cbWarning.isChecked = currentLevels.contains("WARNING")
        cbNormal.isChecked = currentLevels.contains("NORMAL")
        cbOffline.isChecked = currentLevels.contains("OFFLINE")

        btnClose.setOnClickListener { dialog.dismiss() }

        btnReset.setOnClickListener {
            cbCritical.isChecked = true
            cbWarning.isChecked = true
            cbNormal.isChecked = true
            cbOffline.isChecked = false
        }

        btnApply.setOnClickListener {
            val levels = mutableSetOf<String>()

            if (cbCritical.isChecked) levels.add("CRITICAL")
            if (cbWarning.isChecked) levels.add("WARNING")
            if (cbNormal.isChecked) levels.add("NORMAL")
            if (cbOffline.isChecked) levels.add("OFFLINE")

            viewModel.applyFilterSettings(levels)
            dialog.dismiss()
        }

        dialog.show()
    }

    // =========================================================================
    // 8. BIN DETAIL
    // =========================================================================

    private fun showBinDetailBottomSheet(binId: String) {
        val bin = viewModel.allBins.value
            .find { it.deviceId == binId }
            ?: return

        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(
            R.layout.bottom_sheet_bin_detail_map,
            null
        )
        dialog.setContentView(view)

        val tvId = view.findViewById<TextView>(R.id.tvBinDetailId)
        val tvAddress = view.findViewById<TextView>(R.id.tvBinDetailAddress)
        val tvBadge = view.findViewById<TextView>(R.id.tvBinDetailLevelBadge)
        val tvPercent = view.findViewById<TextView>(R.id.tvBinDetailPercentText)
        val pbFill = view.findViewById<ProgressBar>(R.id.pbBinDetailFill)
        val btnClose = view.findViewById<ImageView>(R.id.btnCloseBinDetail)

        val btnNav = view.findViewById<LinearLayout>(R.id.btnNavigateToBin)
        val btnIncident = view.findViewById<LinearLayout>(R.id.btnReportIncidentSheet)
        val btnOpenLid = view.findViewById<LinearLayout>(R.id.btnRemoteOpenLidSheet)

        tvId.text = bin.deviceId
        tvAddress.text =
            bin.location
                ?.takeIf { it.isNotBlank() }
                ?: "Chưa có thông tin vị trí"

        val fill = bin.levelPercent
            ?.roundToInt()
            ?.coerceIn(0, 100)

        tvBadge.text = fill?.let { "$it%" } ?: "--"
        tvPercent.text = fill?.let { "$it%" } ?: "--"
        pbFill.progress = fill ?: 0

        btnClose.setOnClickListener { dialog.dismiss() }

        btnNav.setOnClickListener {
            it.applyPressEffect {
                withDriverLocation { lat, lng ->
                    dialog.dismiss()
                    viewModel.startNavigationToBin(bin, lat, lng)
                }
            }
        }

        btnIncident.setOnClickListener {
            it.applyPressEffect {
                dialog.dismiss()
                showIncidentReportBottomSheet(bin.deviceId)
            }
        }

        btnOpenLid.setOnClickListener {
            it.applyPressEffect {
                dialog.dismiss()
                showRemoteOpenLidBottomSheet(bin.deviceId)
            }
        }

        dialog.show()
    }

    // =========================================================================
    // 9. CREATE SELF-PICK JOB
    // =========================================================================

    private fun showCreateSelfPickJobSheet() {
        val criticalBins = viewModel.displayedBins.value.filter {
            (it.levelPercent ?: 0.0) >= 85.0
        }

        if (criticalBins.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Không có thùng >85% trong phạm vi hiện tại để tạo job",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(
            R.layout.bottom_sheet_create_self_pick_job,
            null
        )
        dialog.setContentView(view)

        val tvStops = view.findViewById<TextView>(R.id.tvSelfPickStopsCount)
        val btnClose = view.findViewById<ImageView>(R.id.btnCloseSelfPickSheet)
        val btnCreate = view.findViewById<AppCompatButton>(
            R.id.btnConfirmCreateSelfPickJob
        )

        tvStops.text = "${criticalBins.size} điểm"
        btnClose.setOnClickListener { dialog.dismiss() }

        btnCreate.setOnClickListener {
            it.applyPressEffect {
                val binIds = criticalBins.map { bin -> bin.deviceId }

                viewModel.createSelfPickJob(binIds) { success ->
                    if (success) {
                        dialog.dismiss()
                        (activity as? MainActivity)
                            ?.navigateToTab(R.id.navigation_jobs)
                    }
                }
            }
        }

        dialog.show()
    }

    // =========================================================================
    // 10. INCIDENT
    // =========================================================================

    private fun showIncidentReportBottomSheet(binId: String?) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(
            R.layout.bottom_sheet_incident_report_map,
            null
        )
        dialog.setContentView(view)

        val tvTitle = view.findViewById<TextView>(R.id.tvIncidentSheetTitle)
        val btnClose = view.findViewById<ImageView>(R.id.btnCloseIncidentSheet)
        val etDesc = view.findViewById<EditText>(R.id.etIncidentDescription)
        val btnSubmit = view.findViewById<AppCompatButton>(
            R.id.btnSubmitIncidentSheet
        )
        val btnAddPhoto = view.findViewById<FrameLayout>(
            R.id.btnAddPhotoIncident
        )

        val chips = listOf(
            view.findViewById<TextView>(R.id.chipIssueBroken),
            view.findViewById<TextView>(R.id.chipIssueLidStuck),
            view.findViewById<TextView>(R.id.chipIssueSensor),
            view.findViewById<TextView>(R.id.chipIssueOverflow),
            view.findViewById<TextView>(R.id.chipIssueOther)
        )

        var selectedIssue = "Thùng hỏng"

        if (!binId.isNullOrBlank()) {
            tvTitle.text = "Báo sự cố: $binId"
        }

        chips.forEach { chip ->
            chip.setOnClickListener {
                it.applyPressEffect {
                    chips.forEach { item ->
                        item.setBackgroundResource(
                            R.drawable.bg_chip_filter_inactive
                        )
                        item.setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.profile_text_primary
                            )
                        )
                    }

                    chip.setBackgroundResource(
                        R.drawable.bg_chip_filter_active
                    )
                    chip.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.profile_green_primary
                        )
                    )
                    selectedIssue = chip.text.toString()
                }
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        btnAddPhoto.setOnClickListener {
            it.applyPressEffect {
                val intent = Intent(
                    requireContext(),
                    IncidentReportActivity::class.java
                ).apply {
                    binId?.let { putExtra("BIN_ID", it) }
                }

                startActivity(intent)
                dialog.dismiss()
            }
        }

        btnSubmit.setOnClickListener {
            it.applyPressEffect {
                val targetBin = binId
                    ?: viewModel.allBins.value.firstOrNull()?.deviceId

                if (targetBin.isNullOrBlank()) {
                    Toast.makeText(
                        requireContext(),
                        "Không xác định được thùng cần báo sự cố.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@applyPressEffect
                }

                val description = etDesc.text.toString().trim()

                viewModel.reportIncident(
                    targetBin,
                    selectedIssue,
                    description
                ) { success ->
                    if (success) {
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.show()
    }

    // =========================================================================
    // 11. REMOTE OPEN LID
    // =========================================================================

    private fun showRemoteOpenLidBottomSheet(binId: String) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(
            R.layout.bottom_sheet_remote_open_lid,
            null
        )
        dialog.setContentView(view)

        val tvId = view.findViewById<TextView>(R.id.tvOpenLidBinId)
        val btnClose = view.findViewById<ImageView>(R.id.btnCloseOpenLidSheet)
        val btnConfirm = view.findViewById<AppCompatButton>(
            R.id.btnConfirmOpenLid
        )
        val btnCancel = view.findViewById<AppCompatButton>(
            R.id.btnCancelOpenLid
        )
        val tvResult = view.findViewById<TextView>(R.id.tvOpenLidResult)

        tvId.text = binId

        btnClose.setOnClickListener { dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            it.applyPressEffect {
                viewModel.remoteOpenLid(binId) { success ->
                    if (success) {
                        tvResult.text = "✓ Đã gửi lệnh mở nắp thùng."
                        tvResult.setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.profile_green_primary
                            )
                        )
                    }
                }
            }
        }

        dialog.show()
    }

    // =========================================================================
    // 12. MAP LAYERS
    // =========================================================================

    private fun showMapLayersMenu() {
        val popup = PopupMenu(
            requireContext(),
            binding.btnMapLayers
        )

        popup.menu.add(0, 1, 0, "🗺️ Mặc định (Vector Clean)")
        popup.menu.add(0, 2, 1, "🛰️ Vệ tinh (Satellite)")
        popup.menu.add(0, 3, 2, "⛰️ Địa hình (Terrain)")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    viewModel.setMapLayer("default")
                    binding.mapWebView.evaluateJavascript(
                        "setMapLayer('default');",
                        null
                    )
                }

                2 -> {
                    viewModel.setMapLayer("satellite")
                    binding.mapWebView.evaluateJavascript(
                        "setMapLayer('satellite');",
                        null
                    )
                }

                3 -> {
                    viewModel.setMapLayer("terrain")
                    binding.mapWebView.evaluateJavascript(
                        "setMapLayer('terrain');",
                        null
                    )
                }
            }
            true
        }

        popup.show()
    }

    private fun formatRadius(radiusMeters: Double): String {
        return if (radiusMeters >= 1000.0) {
            val km = radiusMeters / 1000.0
            if (km % 1.0 == 0.0) {
                "${km.toInt()} km"
            } else {
                String.format(java.util.Locale.US, "%.1f km", km)
            }
        } else {
            "${radiusMeters.roundToInt()} m"
        }
    }

    private fun View.applyPressEffect(onEnd: () -> Unit) {
        animate()
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(85)
            .withEndAction {
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(85)
                    .withEndAction { onEnd() }
                    .start()
            }
            .start()
    }

    override fun onDestroyView() {
        gpsTracker?.stopTracking()
        gpsTracker = null

        binding.mapWebView.removeJavascriptInterface("AndroidBridge")
        binding.mapWebView.stopLoading()

        super.onDestroyView()
        _binding = null
    }
}
