package com.example.app_smart_waste.ui.map

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.app_smart_waste.core.location.GpsTracker
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.data.repository.BinsRepository
import com.example.app_smart_waste.data.repository.JobsRepository
import com.example.app_smart_waste.databinding.FragmentMapBinding
import com.example.app_smart_waste.ui.bin.BinDetailActivity
import com.example.app_smart_waste.ui.route.RouteDetailActivity
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val binsRepo by lazy { BinsRepository(requireContext()) }
    private val jobsRepo by lazy { JobsRepository(requireContext()) }

    private var allBins: List<SmartBinDto> = emptyList()
    private var activeJob: JobDto? = null
    private var isMapReady = false

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

        // Apply Top Insets on topSummaryPill
        ViewCompat.setOnApplyWindowInsetsListener(binding.topSummaryPill) { pillView, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val params = pillView.layoutParams as? ViewGroup.MarginLayoutParams
            params?.topMargin = statusBarInset + (12 * resources.displayMetrics.density).toInt()
            pillView.layoutParams = params
            insets
        }

        setupWebView()
        setupListeners()
        loadMapData()
    }

    override fun onResume() {
        super.onResume()
        if (isMapReady) {
            loadMapData()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.mapWebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isMapReady = true
                renderBinsOnMap()
            }
        }

        webView.loadUrl("file:///android_asset/leaflet_map.html")
    }

    private fun setupListeners() {
        binding.btnRecenterMap.setOnClickListener {
            binding.mapWebView.evaluateJavascript("centerOnTruck()", null)
        }

        binding.btnViewAllRoute.setOnClickListener {
            val intent = Intent(requireContext(), RouteDetailActivity::class.java).apply {
                activeJob?.id?.let { putExtra("JOB_ID", it) }
            }
            startActivity(intent)
        }

        binding.btnCollectNextBin.setOnClickListener {
            val nextBin = getNextPendingBin()
            if (nextBin != null) {
                val intent = Intent(requireContext(), BinDetailActivity::class.java).apply {
                    putExtra("BIN_ID", nextBin.deviceId)
                    activeJob?.id?.let { putExtra("JOB_ID", it) }
                }
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "Tất cả các điểm đã được thu gom!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadMapData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val binsResult = binsRepo.getBins()
            allBins = binsResult.getOrNull() ?: emptyList()

            val jobResult = jobsRepo.getActiveJob()
            activeJob = jobResult.getOrNull()

            updateNextBinCard()
            renderBinsOnMap()
        }
    }

    private fun updateNextBinCard() {
        val job = activeJob
        val completedIds = job?.completedBinIds ?: emptyList()
        val totalStops = job?.totalBins ?: (job?.targetBinIds?.size ?: allBins.size)
        val doneStops = completedIds.size

        binding.tvProgressFraction.text = "$doneStops / $totalStops"

        val nextBin = getNextPendingBin()
        if (nextBin != null) {
            val level = (nextBin.levelPercent ?: 0.0).roundToInt()
            binding.tvNextBinTitle.text = nextBin.name ?: nextBin.deviceId
            binding.tvNextBinLevel.text = "$level% ĐẦY"
            binding.tvNextBinAddress.text = "📍 ${nextBin.location ?: "TP. Hồ Chí Minh"}"
        }
    }

    private fun getNextPendingBin(): SmartBinDto? {
        val completedIds = activeJob?.completedBinIds ?: emptyList()
        val targetIds = activeJob?.targetBinIds

        if (targetIds != null && targetIds.isNotEmpty()) {
            val pendingId = targetIds.firstOrNull { !completedIds.contains(it) }
            if (pendingId != null) {
                return allBins.find { it.deviceId == pendingId }
            }
        }
        return allBins.firstOrNull { it.collectionStatus != "COLLECTED" }
    }

    private fun renderBinsOnMap() {
        if (!isMapReady || _binding == null) return

        val loc = GpsTracker.getInstance(requireContext()).getCurrentLocation()
        val truckJs = "updateTruckLocation(${loc.latitude}, ${loc.longitude});"
        binding.mapWebView.evaluateJavascript(truckJs, null)

        val binsArray = JSONArray()
        val completedIds = activeJob?.completedBinIds ?: emptyList()

        allBins.forEach { bin ->
            val isDone = completedIds.contains(bin.deviceId) || bin.collectionStatus == "COLLECTED"
            val status = if (isDone) "COLLECTED" else (bin.collectionStatus ?: "IDLE")

            val obj = JSONObject().apply {
                put("id", bin.deviceId)
                put("name", bin.name ?: bin.deviceId)
                put("lat", bin.latitude ?: 10.7725)
                put("lng", bin.longitude ?: 106.6980)
                put("level", (bin.levelPercent ?: 0.0).roundToInt())
                put("status", status)
            }
            binsArray.put(obj)
        }

        val jsCall = "setBins($binsArray);"
        binding.mapWebView.evaluateJavascript(jsCall, null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
