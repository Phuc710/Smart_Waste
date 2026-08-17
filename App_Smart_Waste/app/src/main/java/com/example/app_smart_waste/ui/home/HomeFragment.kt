package com.example.app_smart_waste.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.core.model.UiState
import com.example.app_smart_waste.core.storage.SecureTokenStorage
import com.example.app_smart_waste.databinding.FragmentHomeBinding
import com.example.app_smart_waste.ui.bin.BinDetailActivity
import com.example.app_smart_waste.ui.main.MainActivity
import com.example.app_smart_waste.ui.route.RouteDetailActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private var currentActiveJob: JobDto? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val storage = SecureTokenStorage.getInstance(requireContext())
        val fullName = storage.getFullName()
        val displayName = if (!fullName.isNullOrBlank()) fullName else "Nguyễn Văn A"

        // Setup Shared AppHeader
        binding.appHeader.configure(
            title = "Xin chào, $displayName",
            subtitle = "Tài xế thu gom",
            navIconRes = R.drawable.ic_menu_hamburger,
            onNavClick = {
                (activity as? MainActivity)?.switchTab(R.id.navItemProfile)
            },
            actionIconRes = R.drawable.ic_bell,
            actionBadgeCount = 3,
            onActionClick = {
                (activity as? MainActivity)?.switchTab(R.id.navItemJobs)
            }
        )

        setupListeners()
        observeViewModel()
        playEntranceAnimation()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadHomeData()
    }

    private fun playEntranceAnimation() {
        val views = listOf(
            binding.appHeader,
            binding.vehicleCard,
            binding.containerRecentJobs
        )
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = 12f
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((i * 60).toLong())
                .setDuration(260)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun setupListeners() {
        // Pull to refresh
        binding.swipeRefresh.setColorSchemeResources(android.R.color.holo_green_dark)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadHomeData()
        }

        // Xem chi tiết & Xem tất cả → chuyển tab Jobs
        binding.btnViewDetail.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(R.id.navigation_jobs)
        }

        binding.btnViewAllJobs.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(R.id.navigation_jobs)
        }

        // Map Preview Click → chuyển sang Tab Map
        binding.mapPreviewContainer.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(R.id.navigation_map)
        }

        // Xem tuyến
        binding.btnViewRoute.setOnClickListener {
            openRouteDetail()
        }

        // Bắt đầu tuyến CTA
        binding.btnStartRouteHome.setOnClickListener {
            it.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }.start()
            openRouteDetail()
        }
    }

    private fun openBinDetail(binId: String) {
        val intent = Intent(requireContext(), BinDetailActivity::class.java).apply {
            putExtra("BIN_ID", binId)
            currentActiveJob?.id?.let { putExtra("JOB_ID", it) }
        }
        startActivity(intent)
    }

    private fun openRouteDetail() {
        val job = currentActiveJob
        val intent = Intent(requireContext(), RouteDetailActivity::class.java)
        if (job != null) {
            intent.putExtra("JOB_ID", job.id)
        }
        startActivity(intent)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.homeState.collectLatest { state ->
                binding.swipeRefresh.isRefreshing = (state is UiState.Loading)

                if (state is UiState.Success) {
                    val data = state.data
                    currentActiveJob = data.activeJob

                    // 1. Overview KPIs
                    binding.tvStatTotal.text = data.totalTasks.toString()
                    binding.tvStatPending.text = data.pendingTasks.toString()
                    binding.tvStatProgress.text = data.inProgressTasks.toString()
                    binding.tvStatDone.text = data.doneTasks.toString()

                    // 2. Vehicle Card
                    binding.tvTruckPlate.text = data.truckPlate
                    binding.tvFuelPercent.text = "${data.fuelPercent}%"
                    binding.progressFuel.progress = data.fuelPercent

                    // 3. Route Card
                    binding.tvRoutePoints.text = "${data.routePointsCount} điểm thu gom"
                    binding.tvRouteDistance.text = "${data.routeDistanceKm} km"
                    binding.tvRouteDuration.text = "${data.routeDurationMinutes} phút"

                    // 4. Dynamic Recent Tasks from Database + Phone GPS
                    val tasks = data.recentTasks
                    if (tasks.isNotEmpty()) {
                        tasks.getOrNull(0)?.let { item ->
                            bindTaskRow(item, binding.jobRow1, binding.icJob1Indicator, binding.tvJob1Name, binding.tvJob1Location, binding.tvJob1Level, binding.tvJob1Distance)
                        }
                        tasks.getOrNull(1)?.let { item ->
                            bindTaskRow(item, binding.jobRow2, binding.icJob2Indicator, binding.tvJob2Name, binding.tvJob2Location, binding.tvJob2Level, binding.tvJob2Distance)
                        }
                        tasks.getOrNull(2)?.let { item ->
                            bindTaskRow(item, binding.jobRow3, binding.icJob3Indicator, binding.tvJob3Name, binding.tvJob3Location, binding.tvJob3Level, binding.tvJob3Distance)
                        }
                        tasks.getOrNull(3)?.let { item ->
                            bindTaskRow(item, binding.jobRow4, binding.icJob4Indicator, binding.tvJob4Name, binding.tvJob4Location, binding.tvJob4Level, binding.tvJob4Distance)
                        }
                    }
                }
            }
        }
    }

    private fun bindTaskRow(
        item: RecentTaskItem,
        rowView: View,
        indicator: ImageView,
        tvName: TextView,
        tvLocation: TextView,
        tvLevel: TextView,
        tvDistance: TextView
    ) {
        tvName.text = item.displayCode
        tvLocation.text = item.location

        if (item.isCompleted) {
            indicator.setImageResource(R.drawable.ic_indicator_green)
            tvLevel.text = "Đã hoàn thành"
            tvLevel.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_600))
            tvDistance.text = item.completedTime ?: "08:45"
        } else {
            if (item.levelPercent >= 85) {
                indicator.setImageResource(R.drawable.ic_indicator_red)
                tvLevel.text = "${item.levelPercent}%"
                tvLevel.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_danger_main))
            } else if (item.levelPercent >= 70) {
                indicator.setImageResource(R.drawable.ic_indicator_yellow)
                tvLevel.text = "${item.levelPercent}%"
                tvLevel.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_warning_main))
            } else {
                indicator.setImageResource(R.drawable.ic_indicator_green)
                tvLevel.text = "${item.levelPercent}%"
                tvLevel.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_600))
            }
            tvDistance.text = "${item.distanceKm} km"
        }

        rowView.setOnClickListener {
            openBinDetail(item.binId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
