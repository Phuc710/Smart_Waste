package com.example.app_smart_waste.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.app_smart_waste.core.model.IncidentAttachmentState
import com.example.app_smart_waste.core.model.IncidentReason
import com.example.app_smart_waste.core.model.IncidentSubmissionState
import com.example.app_smart_waste.core.model.JobStopStatus
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.network.RealtimeManager
import com.example.app_smart_waste.core.utils.TimeUtils
import com.example.app_smart_waste.databinding.FragmentMapBinding
import com.example.app_smart_waste.databinding.ItemMapFilterChipBinding
import com.example.app_smart_waste.ui.incident.IncidentReportActivity
import com.example.app_smart_waste.ui.jobs.JobExecutionActivity
import com.example.app_smart_waste.ui.main.MainActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

data class MapBinRenderModel(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val levelPercent: Int,
    val levelCategory: String,
    val isOnline: Boolean,
    val isCollected: Boolean
)

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapViewModel by viewModels()

    private var gpsTracker: GpsTracker? = null
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isMapReady = false

    // Realtime Manager
    private val realtimeManager by lazy { RealtimeManager(requireContext()) }

    // Render Cache Deduplication
    private var lastRenderedBins: List<MapBinRenderModel> = emptyList()
    private var lastSelectedBinId: String? = null
    private var lastRouteHash: Int = 0
    private var lastRadarState: RadarState = RadarState.Disabled
    private var lastRenderedLayer: MapLayer = MapLayer.DEFAULT

    // Active Dialog References for live updates
    private var activeBinDetailDialog: BottomSheetDialog? = null
    private var activeOpenLidDialog: BottomSheetDialog? = null
    private var activeSelfPickDialog: BottomSheetDialog? = null
    private var activeIncidentDialog: BottomSheetDialog? = null
    private var activeMapLayersDialog: BottomSheetDialog? = null

    // Image Picker for Incident Report Sheet D
    private var incidentPhotoTargetBinId: String? = null
    private val photoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && isAdded) {
            processSelectedImage(uri)
        }
    }

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

        // Reset map ready state and render caches on view creation/recreation
        isMapReady = false
        lastRenderedBins = emptyList()
        lastSelectedBinId = null
        lastRouteHash = 0
        lastRadarState = RadarState.Disabled
        lastRenderedLayer = MapLayer.DEFAULT

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

        setupViews()
        setupListeners()
        setupCollectors()
        setupGpsTracking()
        setupNetworkMonitoring()
    }

    override fun onResume() {
        super.onResume()
        val targetJobId = arguments?.getString("ARG_TARGET_JOB_ID")
        viewModel.handleAction(MapAction.LoadData(targetJobId))
    }

    override fun onStart() {
        super.onStart()
        realtimeManager.connect(object : RealtimeManager.Listener {
            override fun onJobUpdated(jobId: String?) {
                activity?.runOnUiThread {
                    if (_binding != null && isAdded) {
                        viewModel.handleAction(MapAction.RefreshActiveJob)
                    }
                }
            }

            override fun onBinUpdated(binId: String, levelPercent: Double?, isOnline: Boolean?) {
                activity?.runOnUiThread {
                    if (_binding != null && isAdded) {
                        viewModel.handleAction(MapAction.RefreshActiveJob)
                    }
                }
            }

            override fun onBinOverfullAlert(alert: RealtimeManager.BinOverfullAlert) {
                activity?.runOnUiThread {
                    if (_binding != null && isAdded) {
                        Toast.makeText(
                            requireContext(),
                            "🔴 Cảnh báo thùng ${alert.binId} vượt mức ${alert.levelPercent}%!",
                            Toast.LENGTH_LONG
                        ).show()
                        viewModel.handleAction(MapAction.RefreshActiveJob)
                    }
                }
            }

            override fun onConnectionStateChanged(connected: Boolean) {
                activity?.runOnUiThread {
                    if (_binding != null && isAdded) {
                        if (connected) {
                            viewModel.handleAction(MapAction.SetNetworkState(NetworkState.Online))
                        } else if (viewModel.uiState.value.networkState is NetworkState.Online) {
                            viewModel.handleAction(MapAction.SetNetworkState(NetworkState.Reconnecting))
                        }
                    }
                }
            }
        })
    }

    override fun onStop() {
        realtimeManager.disconnect()
        super.onStop()
    }

    // =========================================================================
    // 1. SETUP VIEWS & WEBVIEW
    // =========================================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupViews() {
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
                }
            }

            loadUrl("file:///android_asset/leaflet_map.html")
        }
    }

    inner class WebAppBridge {
        @JavascriptInterface
        fun onMapReady() {
            activity?.runOnUiThread {
                if (_binding != null && isAdded && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    isMapReady = true
                    renderMap(viewModel.uiState.value)
                }
            }
        }

        @JavascriptInterface
        fun onBinClicked(binId: String) {
            activity?.runOnUiThread {
                if (_binding != null && isAdded) {
                    val sanitizedId = binId.trim()
                    viewModel.handleAction(MapAction.SelectBin(sanitizedId))
                    val bin = viewModel.uiState.value.allBins.find { it.deviceId == sanitizedId }
                    val lat = bin?.latitude
                    val lng = bin?.longitude
                    if (lat != null && lng != null && MapStatePolicy.isValidCoordinate(lat, lng)) {
                        binding.mapWebView.evaluateJavascript("SmartWasteMap.focus($lat, $lng, 17);", null)
                    }
                }
            }
        }

        @JavascriptInterface
        fun onMapClicked(lat: Double, lng: Double) {
            activity?.runOnUiThread {
                if (_binding != null && isAdded) {
                    if (viewModel.uiState.value.navigationState !is NavigationState.Active) {
                        viewModel.handleAction(MapAction.ClearSelection)
                    }
                }
            }
        }
    }

    // =========================================================================
    // 2. SETUP LISTENERS & ACTIONS
    // =========================================================================

    private fun setupListeners() {
        // Top Header Navigation
        binding.btnMapMenu.setOnClickListener {
            it.applyPressEffect { (activity as? MainActivity)?.switchTab(R.id.navItemProfile) }
        }

        binding.btnMapBell.setOnClickListener {
            it.applyPressEffect { (activity as? MainActivity)?.navigateToTab(R.id.navigation_jobs) }
        }

        // Search Input Box
        binding.etSearchMapBins.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString().orEmpty()
                viewModel.handleAction(MapAction.SearchInputChanged(input))
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.btnClearSearch.setOnClickListener {
            it.applyPressEffect {
                viewModel.handleAction(MapAction.ClearSearch)
            }
        }

        binding.btnMapFilter.setOnClickListener {
            it.applyPressEffect { showFilterBottomSheet() }
        }

        // Floating Controls
        binding.btnMyLocation.setOnClickListener {
            it.applyPressEffect {
                val driver = viewModel.uiState.value.driverLocation
                if (driver != null && driver.isValid) {
                    binding.mapWebView.evaluateJavascript("SmartWasteMap.focus(${driver.latitude}, ${driver.longitude}, 16);", null)
                } else {
                    Toast.makeText(requireContext(), "Chưa xác định được vị trí GPS.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnMapLayers.setOnClickListener {
            it.applyPressEffect { showMapLayersBottomSheet() }
        }

        binding.btnToggleSelfPickRadar.setOnClickListener {
            it.applyPressEffect {
                val driver = viewModel.uiState.value.driverLocation
                if (driver == null || !driver.isValid) {
                    Toast.makeText(requireContext(), "Cần vị trí GPS để quét radar 500m.", Toast.LENGTH_SHORT).show()
                    return@applyPressEffect
                }
                viewModel.handleAction(MapAction.ToggleRadar)
            }
        }

        // Bottom Cards Actions
        binding.btnViewAllRecentTasks.setOnClickListener {
            it.applyPressEffect { (activity as? MainActivity)?.navigateToTab(R.id.navigation_jobs) }
        }

        binding.btnSelectRecentBinPreview.setOnClickListener {
            it.applyPressEffect {
                val firstBin = viewModel.uiState.value.displayedBins.firstOrNull() ?: return@applyPressEffect
                viewModel.handleAction(MapAction.SelectBin(firstBin.deviceId))
                viewModel.handleAction(MapAction.OpenBinDetail(firstBin.deviceId))
                showBinDetailBottomSheet(firstBin.deviceId)
            }
        }

        binding.btnViewBinDetailSheet.setOnClickListener {
            it.applyPressEffect {
                val selected = viewModel.uiState.value.selectedBin ?: return@applyPressEffect
                viewModel.handleAction(MapAction.OpenBinDetail(selected.deviceId))
                showBinDetailBottomSheet(selected.deviceId)
            }
        }

        binding.btnNavigateToSelectedBin.setOnClickListener {
            it.applyPressEffect {
                val selected = viewModel.uiState.value.selectedBin ?: return@applyPressEffect
                viewModel.handleAction(MapAction.StartNavigationToBin(selected.deviceId))
            }
        }

        binding.btnViewActiveJobDetail.setOnClickListener {
            it.applyPressEffect {
                val job = viewModel.uiState.value.activeJob ?: return@applyPressEffect
                startActivity(Intent(requireContext(), JobExecutionActivity::class.java).putExtra("JOB_ID", job.id))
            }
        }

        binding.btnExitNavigation.setOnClickListener {
            it.applyPressEffect {
                viewModel.handleAction(MapAction.StopNavigation)
                Toast.makeText(requireContext(), "Đã thoát chế độ dẫn đường.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOpenCreateSelfPickJobSheet.setOnClickListener {
            it.applyPressEffect { showCreateSelfPickJobSheet() }
        }

        binding.btnOpenGpsSettings.setOnClickListener {
            it.applyPressEffect { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        }

        binding.btnResetFiltersFromEmpty.setOnClickListener {
            it.applyPressEffect {
                viewModel.handleAction(MapAction.ResetFilter)
            }
        }
    }

    // =========================================================================
    // 3. SETUP COLLECTORS
    // =========================================================================

    private fun setupCollectors() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Collect MapUiState
                launch {
                    viewModel.uiState.collectLatest { state ->
                        render(state)
                    }
                }

                // Collect MapEffect
                launch {
                    viewModel.effects.collectLatest { effect ->
                        handleEffect(effect)
                    }
                }
            }
        }
    }

    // =========================================================================
    // 4. DETERMINISTIC RENDER PIPELINE
    // =========================================================================

    private fun render(state: MapUiState) {
        renderHeader(state)
        renderSearch(state)
        renderActiveFilterChips(state)
        renderStatusOverlays(state)
        renderFloatingControls(state)
        renderEmptyState(state)
        renderBottomPreview(state)
        renderMap(state)
    }

    private fun renderHeader(state: MapUiState) {
        when (state.networkState) {
            is NetworkState.Online -> {
                binding.tvOnlineStatusPill.text = "🟢 Đang online"
                binding.tvOnlineStatusPill.setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_green_primary))
            }
            is NetworkState.Reconnecting -> {
                binding.tvOnlineStatusPill.text = "🟡 Đang kết nối..."
                binding.tvOnlineStatusPill.setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_warning))
            }
            is NetworkState.BackendUnavailable -> {
                binding.tvOnlineStatusPill.text = "🟠 Máy chủ gián đoạn"
                binding.tvOnlineStatusPill.setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_warning))
            }
            is NetworkState.NoInternet -> {
                binding.tvOnlineStatusPill.text = "⚫ Đang ngoại tuyến"
                binding.tvOnlineStatusPill.setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_text_secondary))
            }
        }
    }

    private fun renderSearch(state: MapUiState) {
        if (binding.etSearchMapBins.text.toString() != state.searchInput) {
            val selectionStart = binding.etSearchMapBins.selectionStart
            val selectionEnd = binding.etSearchMapBins.selectionEnd
            binding.etSearchMapBins.setText(state.searchInput)
            binding.etSearchMapBins.setSelection(
                selectionStart.coerceAtMost(state.searchInput.length),
                selectionEnd.coerceAtMost(state.searchInput.length)
            )
        }
        binding.btnClearSearch.isVisible = state.searchInput.isNotEmpty()
    }

    private fun renderActiveFilterChips(state: MapUiState) {
        val chips = state.activeChips
        binding.scrollActiveFilterChips.isVisible = chips.isNotEmpty()
        binding.layoutActiveFilterChips.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())
        chips.forEach { chip ->
            val chipBinding = ItemMapFilterChipBinding.inflate(inflater, binding.layoutActiveFilterChips, false)
            chipBinding.tvChipLabel.text = chip.label
            chipBinding.btnRemoveChip.setOnClickListener {
                it.applyPressEffect {
                    viewModel.handleAction(MapAction.RemoveFilterChip(chip.id))
                }
            }
            binding.layoutActiveFilterChips.addView(chipBinding.root)
        }
    }

    private fun renderStatusOverlays(state: MapUiState) {
        // Navigation Banner
        val nav = state.navigationState
        if (nav is NavigationState.Active) {
            binding.bannerTurnByTurnNav.isVisible = true
            binding.tvNavNextTurnDist.text = "↰ ${nav.distanceText}"
            binding.tvNavNextTurnInstruction.text = nav.nextTurnInstruction
        } else {
            binding.bannerTurnByTurnNav.isVisible = false
        }

        // Offline / Backend Banner
        val isNetworkOffline = state.networkState is NetworkState.NoInternet || state.networkState is NetworkState.BackendUnavailable
        binding.bannerOfflineWarning.isVisible = isNetworkOffline
        if (isNetworkOffline) {
            binding.bannerOfflineWarning.setOnClickListener {
                it.applyPressEffect { viewModel.handleAction(MapAction.RetryMapData) }
            }
        }
    }

    private fun renderFloatingControls(state: MapUiState) {
        val isRadar = state.radarState is RadarState.Active
        binding.btnToggleSelfPickRadar.isSelected = isRadar
    }

    private fun renderEmptyState(state: MapUiState) {
        binding.cardNoBinsFound.isVisible = (state.mode == MapMode.EMPTY_RESULT)
    }

    private fun renderBottomPreview(state: MapUiState) {
        if (state.loadingState == MapLoadingState.LoadingMap) return

        binding.cardDefaultRecentTasks.isVisible = false
        binding.cardSelectedBinPreview.isVisible = false
        binding.cardActiveJobRoute.isVisible = false
        binding.cardActiveNavigationBottom.isVisible = false
        binding.cardSelfPickRadarBottom.isVisible = false
        binding.cardGpsDisabled.isVisible = false

        when (state.mode) {
            MapMode.NAVIGATION -> {
                val nav = state.navigationState as? NavigationState.Active
                if (nav != null) {
                    binding.tvNavTotalDistance.text = nav.distanceText
                    binding.tvNavEtaText.text = nav.etaText
                }
                binding.cardActiveNavigationBottom.isVisible = true
            }

            MapMode.RADAR -> {
                val radar = state.radarState
                if (radar is RadarState.Active) {
                    val radiusText = formatRadius(radar.radiusMeters)
                    val count = radar.eligibleBins.size
                    binding.tvRadarFoundCount.text = "Tìm thấy $count thùng phù hợp trong bán kính $radiusText"
                    binding.btnOpenCreateSelfPickJobSheet.text = "Tạo job ($count điểm)"
                    binding.btnOpenCreateSelfPickJobSheet.isEnabled = (count > 0)
                    binding.btnOpenCreateSelfPickJobSheet.alpha = if (count > 0) 1.0f else 0.5f
                }
                binding.cardSelfPickRadarBottom.isVisible = true
            }

            MapMode.BIN_SELECTED -> {
                state.selectedBin?.let { bin ->
                    binding.tvSelectedBinName.text = bin.deviceId
                    binding.tvSelectedBinAddress.text = bin.location ?: "Chưa có thông tin vị trí"
                    renderSelectedBinFill(bin, state.thresholds)
                    binding.cardSelectedBinPreview.isVisible = true
                }
            }

            MapMode.ACTIVE_JOB -> {
                val activeJob = state.activeJob
                val activeState = state.activeJobState as? ActiveJobState.Available
                if (activeJob != null) {
                    binding.tvActiveJobCode.text = formatJobCode(activeJob.id)
                    val total = activeState?.totalStops ?: (activeJob.targetBinIds?.size ?: 0)
                    val done = activeState?.completedStops ?: (activeJob.completedBinIds?.size ?: 0)
                    val percent = if (total > 0) (done * 100 / total).coerceIn(0, 100) else 0
                    binding.tvActiveJobProgressFraction.text = "$done / $total điểm • Hoàn thành $percent%"

                    val nextStop = activeState?.nextStop
                    if (nextStop != null) {
                        binding.tvActiveJobNextStopName.text = "Tiếp theo: #${nextStop.binId}"
                        val nextBin = nextStop.bin
                        val nextAddr = nextBin?.location ?: "Điểm thu gom"
                        binding.tvActiveJobNextStopEta.text = nextAddr
                    } else {
                        binding.tvActiveJobNextStopName.text = "Tất cả điểm đã hoàn thành"
                        binding.tvActiveJobNextStopEta.text = "Sẵn sàng kết thúc ca"
                    }

                    binding.cardActiveJobRoute.isVisible = true
                }
            }

            MapMode.GPS_UNAVAILABLE -> {
                binding.cardGpsDisabled.isVisible = true
            }

            MapMode.EMPTY_RESULT -> {
                // Handled in renderEmptyState
            }

            MapMode.IDLE, MapMode.OFFLINE -> {
                val firstBin = state.displayedBins.firstOrNull()
                if (firstBin != null) {
                    binding.tvRecentBinId.text = firstBin.deviceId
                    val fill = (firstBin.levelPercent ?: 0.0).roundToInt()
                    binding.tvRecentBinBadge.text = "$fill%"
                    binding.cardDefaultRecentTasks.isVisible = true
                }
            }
        }
    }

    private fun renderSelectedBinFill(bin: SmartBinDto, thresholds: BinThresholds) {
        val fill = bin.levelPercent?.roundToInt()?.coerceIn(0, 100) ?: 0
        val category = MapStatePolicy.classifyBin(bin.levelPercent ?: 0.0, thresholds)
        binding.tvSelectedBinFillBadge.text = "$fill%"
        binding.tvSelectedBinFillBadge.setBackgroundResource(
            if (category == BinLevel.CRITICAL) R.drawable.badge_danger else R.drawable.badge_warning
        )
        binding.tvSelectedBinFillBadge.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (category == BinLevel.CRITICAL) R.color.profile_danger else R.color.profile_warning
            )
        )
    }

    private fun renderMap(state: MapUiState) {
        if (!isMapReady) return

        // 1. Render Map Layer (Sheet E Selection)
        if (lastRenderedLayer != state.mapLayer) {
            lastRenderedLayer = state.mapLayer
            val quotedLayer = JSONObject.quote(state.mapLayer.toJsString())
            binding.mapWebView.evaluateJavascript("SmartWasteMap.setLayer($quotedLayer);", null)
        }

        // 2. Render Driver Location & Heading
        state.driverLocation?.let { driver ->
            if (driver.isValid) {
                binding.mapWebView.evaluateJavascript(
                    "SmartWasteMap.setDriverLocation(${driver.latitude}, ${driver.longitude}, ${state.driverHeading});",
                    null
                )
            }
        }

        // 3. Render Markers with Model Deduplication
        val currentModels = state.displayedBins.mapNotNull { bin ->
            val lat = bin.latitude
            val lng = bin.longitude
            if (lat != null && lng != null && MapStatePolicy.isValidCoordinate(lat, lng)) {
                val level = bin.levelPercent ?: 0.0
                val category = MapStatePolicy.classifyBin(level, state.thresholds).toCategoryString()
                MapBinRenderModel(
                    id = bin.deviceId,
                    name = bin.name ?: "",
                    latitude = lat,
                    longitude = lng,
                    levelPercent = level.roundToInt(),
                    levelCategory = category,
                    isOnline = bin.isOnline ?: true,
                    isCollected = bin.collectionStatus == "COLLECTED"
                )
            } else null
        }

        if (lastRenderedBins != currentModels) {
            lastRenderedBins = currentModels
            renderBinsOnMap(state.displayedBins, state.thresholds)
        }

        // 4. Render Selection Halo with safe quoting
        val currentSelectedId = state.selectedBin?.deviceId
        if (lastSelectedBinId != currentSelectedId) {
            lastSelectedBinId = currentSelectedId
            val quotedId = currentSelectedId?.let { JSONObject.quote(it) } ?: "null"
            binding.mapWebView.evaluateJavascript("SmartWasteMap.setSelectedBin($quotedId);", null)
        }

        // 5. Render Route & Waypoints with Stop Numbers and Collected/Next Badges
        val currentRouteHash = state.routeCoordinates.hashCode() xor state.routeWaypointModels.hashCode()
        if (lastRouteHash != currentRouteHash) {
            lastRouteHash = currentRouteHash
            if (state.routeCoordinates.isNotEmpty()) {
                val jsonCoords = JSONArray()
                state.routeCoordinates.forEach { pt ->
                    if (pt.size >= 2 && MapStatePolicy.isValidCoordinate(pt[0], pt[1])) {
                        val p = JSONArray()
                        p.put(pt[0])
                        p.put(pt[1])
                        jsonCoords.put(p)
                    }
                }

                val jsonWaypoints = JSONArray()
                state.routeWaypointModels.forEach { wp ->
                    val coord = wp.coordinate
                    if (coord != null && coord.isValid) {
                        val obj = JSONObject()
                        obj.put("deviceId", wp.binId)
                        obj.put("order", wp.order)
                        obj.put("lat", coord.latitude)
                        obj.put("lng", coord.longitude)
                        obj.put("isCollected", wp.status == JobStopStatus.COLLECTED)
                        obj.put("isNext", wp.isNext)
                        jsonWaypoints.put(obj)
                    }
                }

                binding.mapWebView.evaluateJavascript(
                    "SmartWasteMap.setRoute($jsonCoords, $jsonWaypoints);",
                    null
                )
            } else {
                binding.mapWebView.evaluateJavascript("SmartWasteMap.clearRoute();", null)
            }
        }

        // 6. Render Radar Circle
        val radar = state.radarState
        if (lastRadarState != radar) {
            lastRadarState = radar
            if (radar is RadarState.Active && state.driverLocation != null && state.driverLocation.isValid) {
                val driver = state.driverLocation
                binding.mapWebView.evaluateJavascript(
                    "SmartWasteMap.setRadar(${driver.latitude}, ${driver.longitude}, ${radar.radiusMeters}, true);",
                    null
                )
            } else {
                binding.mapWebView.evaluateJavascript("SmartWasteMap.setRadar(0, 0, 0, false);", null)
            }
        }
    }

    private fun renderBinsOnMap(bins: List<SmartBinDto>, thresholds: BinThresholds) {
        if (!isMapReady) return
        val jsonArray = JSONArray()
        bins.forEach { bin ->
            val lat = bin.latitude
            val lng = bin.longitude
            if (lat != null && lng != null && MapStatePolicy.isValidCoordinate(lat, lng)) {
                val level = bin.levelPercent ?: 0.0
                val category = MapStatePolicy.classifyBin(level, thresholds).toCategoryString()
                val obj = JSONObject()
                obj.put("deviceId", bin.deviceId)
                obj.put("name", bin.name ?: "")
                obj.put("lat", lat)
                obj.put("lng", lng)
                obj.put("levelPercent", level)
                obj.put("levelCategory", category)
                obj.put("isOnline", bin.isOnline ?: true)
                obj.put("isCollected", bin.collectionStatus == "COLLECTED")
                jsonArray.put(obj)
            }
        }
        binding.mapWebView.evaluateJavascript("SmartWasteMap.setBins($jsonArray);", null)
    }

    // =========================================================================
    // 5. HANDLE MAP EFFECTS
    // =========================================================================

    private fun handleEffect(effect: MapEffect) {
        when (effect) {
            is MapEffect.ShowToast -> {
                Toast.makeText(requireContext(), effect.message, Toast.LENGTH_SHORT).show()
            }
            is MapEffect.NavigateToIncident -> {
                showIncidentReportBottomSheet(effect.binId)
            }
            is MapEffect.IncidentSubmissionSuccess -> {
                Toast.makeText(requireContext(), effect.message, Toast.LENGTH_LONG).show()
                activeIncidentDialog?.dismiss()
                activeIncidentDialog = null
            }
            is MapEffect.SelfPickSuccess -> {
                Toast.makeText(requireContext(), effect.message, Toast.LENGTH_LONG).show()
                activeSelfPickDialog?.dismiss()
                activeSelfPickDialog = null
            }
            is MapEffect.LidCommandResultEffect -> {
                Toast.makeText(requireContext(), effect.message, if (effect.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
            }
            is MapEffect.OperationFailed -> {
                Toast.makeText(requireContext(), effect.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // =========================================================================
    // 6. GPS TRACKING SETUP & NETWORK MONITORING
    // =========================================================================

    private fun setupGpsTracking() {
        val gps = GpsTracker.getInstance(requireContext()).also { gpsTracker = it }
        gps.startTracking()

        val loc = gps.getLastKnownLocation() ?: gps.getCurrentLocation()
        if (loc.latitude.isFinite() && loc.longitude.isFinite()) {
            viewModel.handleAction(MapAction.UpdateDriverLocation(loc.latitude, loc.longitude))
            viewModel.handleAction(MapAction.SetGpsState(GpsState.Available))
        }

        try {
            val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            locationManager = lm
            if (lm != null) {
                val fineGranted = ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (fineGranted) {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(l: Location) {
                            activity?.runOnUiThread {
                                if (_binding != null && isAdded && l.latitude.isFinite() && l.longitude.isFinite()) {
                                    val heading = if (l.hasBearing()) l.bearing else 0f
                                    val acc = if (l.hasAccuracy()) l.accuracy.toDouble() else null
                                    val speed = if (l.hasSpeed()) l.speed.toDouble() else null
                                    viewModel.handleAction(MapAction.UpdateDriverLocation(l.latitude, l.longitude, heading, acc, speed))
                                    viewModel.handleAction(MapAction.SetGpsState(GpsState.Available))
                                }
                            }
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                        override fun onProviderEnabled(p: String) {
                            activity?.runOnUiThread {
                                if (_binding != null && isAdded) {
                                    viewModel.handleAction(MapAction.SetGpsState(GpsState.Available))
                                }
                            }
                        }
                        override fun onProviderDisabled(p: String) {
                            activity?.runOnUiThread {
                                if (_binding != null && isAdded) {
                                    viewModel.handleAction(MapAction.SetGpsState(GpsState.Disabled))
                                }
                            }
                        }
                    }
                    locationListener = listener

                    if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 5f, listener)
                    } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 5f, listener)
                    } else {
                        viewModel.handleAction(MapAction.SetGpsState(GpsState.Disabled))
                    }
                } else {
                    viewModel.handleAction(MapAction.SetGpsState(GpsState.PermissionDenied))
                }
            }
        } catch (e: SecurityException) {
            viewModel.handleAction(MapAction.SetGpsState(GpsState.PermissionDenied))
        } catch (e: Exception) {
            // Non-fatal
        }
    }

    private fun setupNetworkMonitoring() {
        try {
            val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            connectivityManager = cm
            if (cm != null) {
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        activity?.runOnUiThread {
                            if (_binding != null && isAdded) {
                                viewModel.handleAction(MapAction.SetNetworkState(NetworkState.Online))
                            }
                        }
                    }

                    override fun onLost(network: Network) {
                        activity?.runOnUiThread {
                            if (_binding != null && isAdded) {
                                viewModel.handleAction(MapAction.SetNetworkState(NetworkState.NoInternet))
                            }
                        }
                    }
                }
                networkCallback = callback
                val req = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(req, callback)
            }
        } catch (e: Exception) {
            // Non-fatal
        }
    }

    // =========================================================================
    // 7. BOTTOM SHEET A: BỘ LỌC THÙNG RÁC
    // =========================================================================

    private fun showFilterBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_map_filter, null)
        dialog.setContentView(view)

        val cbCrit = view.findViewById<CheckBox>(R.id.cbFilterCritical)
        val cbWarn = view.findViewById<CheckBox>(R.id.cbFilterWarning)
        val cbNorm = view.findViewById<CheckBox>(R.id.cbFilterNormal)
        val cbOff = view.findViewById<CheckBox>(R.id.cbFilterOffline)
        val btnApply = view.findViewById<AppCompatButton>(R.id.btnApplyFilter)
        val btnReset = view.findViewById<AppCompatButton>(R.id.btnResetFilter)
        val btnClose = view.findViewById<ImageView>(R.id.btnCloseFilterSheet)

        val currentFilters = viewModel.uiState.value.filters
        cbCrit.isChecked = currentFilters.showCritical
        cbWarn.isChecked = currentFilters.showWarning
        cbNorm.isChecked = currentFilters.showNormal
        cbOff.isChecked = currentFilters.connectivity != ConnectivityFilter.ONLINE_ONLY

        btnClose?.setOnClickListener { dialog.dismiss() }

        btnApply.setOnClickListener {
            it.applyPressEffect {
                val conn = if (cbOff.isChecked) {
                    if (cbCrit.isChecked || cbWarn.isChecked || cbNorm.isChecked) ConnectivityFilter.ALL else ConnectivityFilter.OFFLINE_ONLY
                } else {
                    ConnectivityFilter.ONLINE_ONLY
                }
                val newFilters = MapFilters(
                    showCritical = cbCrit.isChecked,
                    showWarning = cbWarn.isChecked,
                    showNormal = cbNorm.isChecked,
                    connectivity = conn
                )
                viewModel.handleAction(MapAction.ApplyFilter(newFilters))
                dialog.dismiss()
            }
        }

        btnReset.setOnClickListener {
            it.applyPressEffect {
                cbCrit.isChecked = true
                cbWarn.isChecked = true
                cbNorm.isChecked = true
                cbOff.isChecked = true
            }
        }

        dialog.show()
    }

    // =========================================================================
    // 8. BOTTOM SHEET B: CHI TIẾT THÙNG RÁC
    // =========================================================================

    private fun showBinDetailBottomSheet(binId: String) {
        val dialog = BottomSheetDialog(requireContext()).also { activeBinDetailDialog = it }
        val view = layoutInflater.inflate(R.layout.bottom_sheet_bin_detail_map, null)
        dialog.setContentView(view)

        val tvId = view.findViewById<TextView>(R.id.tvBinDetailId)
        val tvAddress = view.findViewById<TextView>(R.id.tvBinDetailAddress)
        val tvBadge = view.findViewById<TextView>(R.id.tvBinDetailLevelBadge)
        val tvPercent = view.findViewById<TextView>(R.id.tvBinDetailPercentText)
        val pbFill = view.findViewById<ProgressBar>(R.id.pbBinDetailFill)
        val tvLastSeen = view.findViewById<TextView>(R.id.tvBinLastSeen)
        val tvTypeStatus = view.findViewById<TextView>(R.id.tvBinDetailTypeAndStatus)
        val tvLidStatus = view.findViewById<TextView>(R.id.tvBinDetailLidStatus)
        val tvMode = view.findViewById<TextView>(R.id.tvBinDetailMode)
        val tvCollectionStatus = view.findViewById<TextView>(R.id.tvBinDetailCollectionStatus)
        val tvConnectivity = view.findViewById<TextView>(R.id.tvBinDetailConnectivity)

        val btnClose = view.findViewById<ImageView>(R.id.btnCloseBinDetail)
        val btnNav = view.findViewById<LinearLayout>(R.id.btnNavigateToBin)
        val btnIncident = view.findViewById<LinearLayout>(R.id.btnReportIncidentSheet)
        val btnOpenLid = view.findViewById<LinearLayout>(R.id.btnRemoteOpenLidSheet)

        fun bindBin(bin: SmartBinDto, thresholds: BinThresholds) {
            tvId.text = bin.deviceId
            tvAddress.text = bin.location ?: "Chưa có thông tin vị trí"

            val fill = bin.levelPercent?.roundToInt()?.coerceIn(0, 100) ?: 0
            val category = MapStatePolicy.classifyBin(bin.levelPercent ?: 0.0, thresholds)
            val critThresh = thresholds.critical.roundToInt()

            tvBadge.text = if (category == BinLevel.CRITICAL) "≥ $critThresh%" else "$fill%"
            tvBadge.setBackgroundResource(
                if (category == BinLevel.CRITICAL) R.drawable.badge_danger else R.drawable.badge_warning
            )
            tvBadge.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (category == BinLevel.CRITICAL) R.color.profile_danger else R.color.profile_warning
                )
            )

            tvPercent.text = "$fill%"
            tvPercent.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (category == BinLevel.CRITICAL) R.color.profile_danger else R.color.profile_warning
                )
            )
            pbFill.progress = fill

            val formattedTime = bin.lastSeen?.let { TimeUtils.formatDisplayDateTime(it) } ?: "Chưa có dữ liệu"
            tvLastSeen.text = "Cập nhật: $formattedTime"

            val onlineText = if (bin.isOnline != false) "Trực tuyến" else "Ngoại tuyến"
            tvTypeStatus.text = "$onlineText • ${bin.modeText}"
            tvTypeStatus.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (bin.isOnline != false) R.color.profile_green_primary else R.color.profile_text_secondary
                )
            )

            tvLidStatus.text = bin.lidStatus
            tvMode.text = bin.modeText
            tvCollectionStatus.text = bin.collectionStatusText

            if (bin.isOnline != false) {
                tvConnectivity.text = "🟢 Trực tuyến"
                tvConnectivity.setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_green_primary))
                btnOpenLid.isEnabled = true
                btnOpenLid.alpha = 1.0f
            } else {
                tvConnectivity.text = "⚫ Ngoại tuyến"
                tvConnectivity.setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_text_secondary))
                btnOpenLid.isEnabled = false
                btnOpenLid.alpha = 0.5f
            }
        }

        // Initial binding from current state
        val initialBin = viewModel.uiState.value.allBins.find { it.deviceId == binId }
        if (initialBin != null) {
            bindBin(initialBin, viewModel.uiState.value.thresholds)
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        btnNav.setOnClickListener {
            it.applyPressEffect {
                dialog.dismiss()
                viewModel.handleAction(MapAction.StartNavigationToBin(binId))
            }
        }

        btnIncident.setOnClickListener {
            it.applyPressEffect {
                dialog.dismiss()
                viewModel.handleAction(MapAction.OpenIncidentSheet(binId))
                showIncidentReportBottomSheet(binId)
            }
        }

        btnOpenLid.setOnClickListener {
            it.applyPressEffect {
                dialog.dismiss()
                showRemoteOpenLidBottomSheet(binId)
            }
        }

        dialog.setOnDismissListener {
            if (activeBinDetailDialog === dialog) {
                activeBinDetailDialog = null
            }
            viewModel.handleAction(MapAction.CloseBinDetail)
        }

        dialog.show()
    }

    // =========================================================================
    // 9. REMOTE OPEN LID CONFIRMATION DIALOG
    // =========================================================================

    private fun showRemoteOpenLidBottomSheet(binId: String) {
        val dialog = BottomSheetDialog(requireContext()).also { activeOpenLidDialog = it }
        val view = layoutInflater.inflate(R.layout.bottom_sheet_remote_open_lid, null)
        dialog.setContentView(view)

        val tvId = view.findViewById<TextView>(R.id.tvOpenLidBinId)
        val tvBadge = view.findViewById<TextView>(R.id.tvOpenLidBinBadge)
        val pbProgress = view.findViewById<ProgressBar>(R.id.pbOpenLidProgress)
        val tvResult = view.findViewById<TextView>(R.id.tvOpenLidResult)
        val btnClose = view.findViewById<ImageView>(R.id.btnCloseOpenLidSheet)
        val btnConfirm = view.findViewById<AppCompatButton>(R.id.btnConfirmOpenLid)
        val btnCancel = view.findViewById<AppCompatButton>(R.id.btnCancelOpenLid)

        val bin = viewModel.uiState.value.allBins.find { it.deviceId == binId }
        tvId.text = binId
        val fill = bin?.levelPercent?.roundToInt() ?: 0
        tvBadge.text = "$fill%"

        btnClose.setOnClickListener { dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            it.applyPressEffect {
                btnConfirm.isEnabled = false
                btnConfirm.text = "Đang gửi lệnh..."
                pbProgress.isVisible = true
                tvResult.isVisible = false
                viewModel.handleAction(MapAction.ConfirmOpenLid(binId))
            }
        }

        dialog.setOnDismissListener {
            if (activeOpenLidDialog === dialog) {
                activeOpenLidDialog = null
            }
            viewModel.handleAction(MapAction.DismissLidCommandState)
        }

        dialog.show()
    }

    // =========================================================================
    // 10. SHEET C: XÁC NHẬN TẠO SELF-PICK JOB
    // =========================================================================

    private fun showCreateSelfPickJobSheet() {
        val radar = viewModel.uiState.value.radarState
        val eligibleBins = (radar as? RadarState.Active)?.eligibleBins ?: emptyList()

        if (eligibleBins.isEmpty()) {
            Toast.makeText(requireContext(), "Không có thùng phù hợp trong phạm vi quét radar 500m.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = BottomSheetDialog(requireContext()).also { activeSelfPickDialog = it }
        val view = layoutInflater.inflate(R.layout.bottom_sheet_create_self_pick_job, null)
        dialog.setContentView(view)

        val tvStops = view.findViewById<TextView>(R.id.tvSelfPickStopsCount)
        val tvDistance = view.findViewById<TextView>(R.id.tvSelfPickDistance)
        val tvDuration = view.findViewById<TextView>(R.id.tvSelfPickDuration)
        val llBinsList = view.findViewById<LinearLayout>(R.id.llSelfPickBinsList)
        val btnClose = view.findViewById<ImageView>(R.id.btnCloseSelfPickSheet)
        val btnCreate = view.findViewById<AppCompatButton>(R.id.btnConfirmCreateSelfPickJob)

        tvStops.text = "${eligibleBins.size} điểm"
        tvDistance.text = "--"
        tvDuration.text = "--"

        // Dynamically populate bins list
        llBinsList.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        eligibleBins.forEachIndexed { index, bin ->
            val row = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }

            val tvName = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                text = "🗑 0${index + 1}  ${bin.deviceId}"
                setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_text_primary))
                textSize = 13.5f
                paint.isFakeBoldText = true
            }

            val fill = (bin.levelPercent ?: 0.0).roundToInt()
            val tvFill = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                text = "$fill%"
                setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_danger))
                textSize = 12.5f
                paint.isFakeBoldText = true
            }

            row.addView(tvName)
            row.addView(tvFill)
            llBinsList.addView(row)
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        btnCreate.setOnClickListener {
            it.applyPressEffect {
                btnCreate.isEnabled = false
                btnCreate.text = "Đang tạo ca làm..."
                val binIds = eligibleBins.map { bin -> bin.deviceId }
                viewModel.handleAction(MapAction.ConfirmSelfPick(binIds))
            }
        }

        dialog.setOnDismissListener {
            if (activeSelfPickDialog === dialog) {
                activeSelfPickDialog = null
            }
            viewModel.handleAction(MapAction.CloseSelfPickConfirmation)
        }

        dialog.show()
    }

    private var currentIncidentPhotoBytes: ByteArray? = null

    private fun showIncidentReportBottomSheet(binId: String) {
        incidentPhotoTargetBinId = binId
        currentIncidentPhotoBytes = null
        val dialog = BottomSheetDialog(requireContext()).also { activeIncidentDialog = it }
        val view = layoutInflater.inflate(R.layout.bottom_sheet_incident_report_map, null)
        dialog.setContentView(view)

        val tvTitle = view.findViewById<TextView>(R.id.tvIncidentSheetTitle)
        val btnClose = view.findViewById<ImageView>(R.id.btnCloseIncidentSheet)

        val chipBroken = view.findViewById<TextView>(R.id.chipIssueBroken)
        val chipLidStuck = view.findViewById<TextView>(R.id.chipIssueLidStuck)
        val chipSensor = view.findViewById<TextView>(R.id.chipIssueSensor)
        val chipOverflow = view.findViewById<TextView>(R.id.chipIssueOverflow)
        val chipOther = view.findViewById<TextView>(R.id.chipIssueOther)

        val etDescription = view.findViewById<EditText>(R.id.etIncidentDescription)
        val containerPhoto1 = view.findViewById<FrameLayout>(R.id.containerPhoto1)
        val ivPhotoThumb1 = view.findViewById<ImageView>(R.id.ivPhotoThumb1)
        val btnAddPhoto = view.findViewById<FrameLayout>(R.id.btnAddPhotoIncident)
        val btnSubmit = view.findViewById<AppCompatButton>(R.id.btnSubmitIncidentSheet)

        tvTitle.text = "Báo cáo sự cố #$binId"

        val chipMap = mapOf(
            IncidentReason.BROKEN_BIN to chipBroken,
            IncidentReason.LID_STUCK to chipLidStuck,
            IncidentReason.SENSOR_FAILURE to chipSensor,
            IncidentReason.OVERFLOW to chipOverflow,
            IncidentReason.OTHER to chipOther
        )

        fun updateSelectedReason(reason: IncidentReason) {
            viewModel.handleAction(MapAction.SelectIncidentReason(reason))
            chipMap.forEach { (r, chip) ->
                val isSelected = (r == reason)
                chip.setBackgroundResource(if (isSelected) R.drawable.bg_chip_filter_active else R.drawable.bg_chip_filter_inactive)
                chip.setTextColor(ContextCompat.getColor(requireContext(), if (isSelected) R.color.profile_green_primary else R.color.profile_text_primary))
                chip.paint.isFakeBoldText = isSelected
            }
        }

        // Setup Reason Chips
        chipBroken.setOnClickListener { it.applyPressEffect { updateSelectedReason(IncidentReason.BROKEN_BIN) } }
        chipLidStuck.setOnClickListener { it.applyPressEffect { updateSelectedReason(IncidentReason.LID_STUCK) } }
        chipSensor.setOnClickListener { it.applyPressEffect { updateSelectedReason(IncidentReason.SENSOR_FAILURE) } }
        chipOverflow.setOnClickListener { it.applyPressEffect { updateSelectedReason(IncidentReason.OVERFLOW) } }
        chipOther.setOnClickListener { it.applyPressEffect { updateSelectedReason(IncidentReason.OTHER) } }

        updateSelectedReason(viewModel.uiState.value.incidentReason)

        // Description Text Change
        etDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.handleAction(MapAction.ChangeIncidentDescription(s?.toString().orEmpty()))
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        // Photo Attachment Button
        btnAddPhoto.setOnClickListener {
            it.applyPressEffect {
                photoPickerLauncher.launch("image/*")
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            it.applyPressEffect {
                btnSubmit.isEnabled = false
                btnSubmit.text = "Đang gửi báo cáo..."
                val bytes = currentIncidentPhotoBytes
                currentIncidentPhotoBytes = null
                viewModel.handleAction(MapAction.SubmitIncident(binId, bytes))
            }
        }

        dialog.setOnDismissListener {
            currentIncidentPhotoBytes = null
            if (activeIncidentDialog === dialog) {
                activeIncidentDialog = null
            }
            viewModel.handleAction(MapAction.CloseIncidentSheet)
        }

        dialog.show()
    }

    private fun processSelectedImage(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    // Compress JPEG
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                    val bytes = baos.toByteArray()
                    currentIncidentPhotoBytes = bytes

                    withContext(Dispatchers.Main) {
                        viewModel.handleAction(
                            MapAction.SelectIncidentAttachment(
                                uriString = uri.toString(),
                                displayName = "incident_photo.jpg",
                                sizeBytes = bytes.size.toLong()
                            )
                        )

                        // Update thumbnail in active dialog if open
                        activeIncidentDialog?.let { dialog ->
                            val containerPhoto1 = dialog.findViewById<FrameLayout>(R.id.containerPhoto1)
                            val ivPhotoThumb1 = dialog.findViewById<ImageView>(R.id.ivPhotoThumb1)
                            containerPhoto1?.isVisible = true
                            ivPhotoThumb1?.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Không thể tải ảnh đã chọn: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // =========================================================================
    // 12. SHEET E: CHỌN LỚP BẢN ĐỒ (PHASE 8)
    // =========================================================================

    private fun showMapLayersBottomSheet() {
        val dialog = BottomSheetDialog(requireContext()).also { activeMapLayersDialog = it }
        val view = layoutInflater.inflate(R.layout.bottom_sheet_map_layers, null)
        dialog.setContentView(view)

        val btnClose = view.findViewById<ImageView>(R.id.btnCloseLayersSheet)
        val btnDefault = view.findViewById<LinearLayout>(R.id.btnLayerDefault)
        val btnSatellite = view.findViewById<LinearLayout>(R.id.btnLayerSatellite)
        val btnTerrain = view.findViewById<LinearLayout>(R.id.btnLayerTerrain)

        val ivCheckDefault = view.findViewById<ImageView>(R.id.ivCheckDefault)
        val ivCheckSatellite = view.findViewById<ImageView>(R.id.ivCheckSatellite)
        val ivCheckTerrain = view.findViewById<ImageView>(R.id.ivCheckTerrain)

        fun updateChecks(layer: MapLayer) {
            ivCheckDefault.isVisible = (layer == MapLayer.DEFAULT)
            ivCheckSatellite.isVisible = (layer == MapLayer.SATELLITE)
            ivCheckTerrain.isVisible = (layer == MapLayer.TERRAIN)
        }

        updateChecks(viewModel.uiState.value.mapLayer)
        btnClose?.setOnClickListener { dialog.dismiss() }

        btnDefault.setOnClickListener {
            it.applyPressEffect {
                viewModel.handleAction(MapAction.SetMapLayer(MapLayer.DEFAULT))
                dialog.dismiss()
            }
        }

        btnSatellite.setOnClickListener {
            it.applyPressEffect {
                viewModel.handleAction(MapAction.SetMapLayer(MapLayer.SATELLITE))
                dialog.dismiss()
            }
        }

        btnTerrain.setOnClickListener {
            it.applyPressEffect {
                viewModel.handleAction(MapAction.SetMapLayer(MapLayer.TERRAIN))
                dialog.dismiss()
            }
        }

        dialog.setOnDismissListener {
            if (activeMapLayersDialog === dialog) {
                activeMapLayersDialog = null
            }
        }

        dialog.show()
    }

    private fun formatJobCode(id: String): String {
        val clean = id.removePrefix("#")
        return if (clean.startsWith("JOB_")) "#$clean" else "#JOB_$clean"
    }

    private fun formatRadius(radiusMeters: Double): String {
        return if (radiusMeters >= 1000.0) {
            val km = radiusMeters / 1000.0
            if (km % 1.0 == 0.0) "${km.toInt()} km" else String.format(java.util.Locale.US, "%.1f km", km)
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
        activeBinDetailDialog?.dismiss()
        activeBinDetailDialog = null

        activeOpenLidDialog?.dismiss()
        activeOpenLidDialog = null

        activeSelfPickDialog?.dismiss()
        activeSelfPickDialog = null

        activeIncidentDialog?.dismiss()
        activeIncidentDialog = null

        activeMapLayersDialog?.dismiss()
        activeMapLayersDialog = null

        locationListener?.let {
            try { locationManager?.removeUpdates(it) } catch (e: Exception) {}
        }
        locationListener = null
        locationManager = null

        networkCallback?.let {
            try { connectivityManager?.unregisterNetworkCallback(it) } catch (e: Exception) {}
        }
        networkCallback = null
        connectivityManager = null

        gpsTracker?.stopTracking()
        gpsTracker = null

        isMapReady = false
        binding.mapWebView.removeJavascriptInterface("AndroidBridge")
        binding.mapWebView.stopLoading()

        super.onDestroyView()
        _binding = null
    }
}
