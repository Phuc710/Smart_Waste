package com.example.app_smart_waste.ui.jobs

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.JobDisplayModel
import com.example.app_smart_waste.databinding.FragmentJobsBinding
import com.example.app_smart_waste.ui.history.HistoryAdapter
import com.example.app_smart_waste.ui.history.JobHistoryDetailActivity
import com.example.app_smart_waste.ui.main.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class JobsFragment : Fragment() {

    private var _binding: FragmentJobsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: JobsViewModel by viewModels()

    private lateinit var activeJobsAdapter: ActiveJobsAdapter
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJobsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Shared Unified AppHeader with Settings Gear on Top Right
        binding.appHeader.configure(
            title = "Nhiệm vụ",
            subtitle = "Quản lý ca thu gom",
            actionIconRes = R.drawable.ic_settings_gear,
            onActionClick = {
                showSettingsDialog()
            }
        )

        setupWorkAvailability()
        setupTopTabs()
        setupActiveSubFilters()
        setupRecyclerViews()
        setupHistoryFilters()
        observeViewModel()
        playEntranceAnimation()

        viewModel.loadAllJobData()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAllJobData()
    }

    private fun setupWorkAvailability() {
        binding.switchAvailability.setOnClickListener {
            val isChecked = binding.switchAvailability.isChecked
            if (!isChecked) {
                // User is trying to turn OFF -> Show Confirmation Dialog (Image 4)
                binding.switchAvailability.isChecked = true // Keep visually ON until confirmed
                showTurnOffConfirmDialog()
            } else {
                // User is turning ON
                viewModel.setAvailability(true)
                Toast.makeText(requireContext(), "🟢 Đã bật sẵn sàng nhận việc!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTurnOffConfirmDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_confirm_turn_off_availability)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val btnCancel = dialog.findViewById<Button>(R.id.btnCancelTurnOff)
        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirmTurnOff)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            viewModel.setAvailability(false)
            binding.switchAvailability.isChecked = false
            showTurnedOffSuccessDialog()
        }

        dialog.show()
    }

    private fun showTurnedOffSuccessDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_turned_off_success)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val btnClose = dialog.findViewById<Button>(R.id.btnCloseTurnedOffDialog)
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSettingsDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_settings_shift, null)
        dialog.setContentView(view)

        // 1. Bind Shift Views
        val tvStartTime = view.findViewById<TextView>(R.id.tvShiftStartTimeVal)
        val tvRemaining = view.findViewById<TextView>(R.id.tvShiftRemainingCountdownVal)
        val tvEndTimeNote = view.findViewById<TextView>(R.id.tvShiftEndTimeNote)
        val pbTimeline = view.findViewById<android.widget.ProgressBar>(R.id.pbShiftTimeline)

        // 2. Bind Vehicle Views
        val tvPlate = view.findViewById<TextView>(R.id.tvVehiclePlateNum)
        val tvModel = view.findViewById<TextView>(R.id.tvVehicleFullModelId)
        val tvVolume = view.findViewById<TextView>(R.id.tvVehicleVolumeBadge)
        val tvPayload = view.findViewById<TextView>(R.id.tvVehiclePayloadVal)
        val tvFuel = view.findViewById<TextView>(R.id.tvVehicleFuelVal)
        val tvGps = view.findViewById<TextView>(R.id.tvVehicleGpsStatusVal)

        val vehicle = viewModel.assignedVehicle.value
        tvPlate?.text = vehicle.plateNumber
        tvModel?.text = vehicle.modelName
        tvVolume?.text = "🔵 ${vehicle.volume}"
        tvPayload?.text = vehicle.payload
        tvFuel?.text = vehicle.fuelStatus
        tvGps?.text = vehicle.gpsStatus

        // 3. Bind Operational Settings
        val switchOverload = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchAlertOverload)
        val switchGps = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchAutoGps)
        val tvCache = view.findViewById<TextView>(R.id.tvMapCacheUsage)
        val btnClearCache = view.findViewById<Button>(R.id.btnClearMapCache)
        val btnClose = view.findViewById<android.widget.ImageView>(R.id.btnCloseSettingsSheet)

        switchOverload?.isChecked = viewModel.isAlertOverload.value
        switchGps?.isChecked = viewModel.isAutoGps.value
        tvCache?.text = "Đang sử dụng ${viewModel.mapCacheSizeMb.value} MB"

        switchOverload?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAlertOverload(isChecked)
            com.example.app_smart_waste.core.storage.AppConfig.setOverloadAlertEnabled(requireContext(), isChecked)
        }

        switchGps?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutoGps(isChecked)
            com.example.app_smart_waste.core.storage.AppConfig.setAutoGpsEnabled(requireContext(), isChecked)
        }

        btnClearCache?.setOnClickListener {
            val freedMb = viewModel.clearMapCache()
            tvCache?.text = "Đang sử dụng 0.0 MB"
            Toast.makeText(requireContext(), "✓ Đã xóa $freedMb MB bộ nhớ đệm bản đồ offline!", Toast.LENGTH_SHORT).show()
        }

        btnClose?.setOnClickListener {
            dialog.dismiss()
        }

        // Live Realtime Ticker for Shift Countdown (Updates every 1s) & Live Server Status
        val shiftJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.shiftCountdownState.collectLatest { state ->
                        tvStartTime?.text = state.startTimeStr
                        tvRemaining?.text = state.remainingTimeFormatted
                        tvEndTimeNote?.text = "kết thúc lúc ${state.endTimeStr}"
                        pbTimeline?.progress = state.progressPercent
                    }
                }
                launch {
                    viewModel.serverStatus.collectLatest { status ->
                        tvGps?.text = status
                    }
                }
            }
        }

        dialog.setOnDismissListener {
            shiftJob.cancel()
        }

        dialog.show()
    }

    private fun setupTopTabs() {
        binding.tabActiveJobs.setOnClickListener {
            it.applyPressEffect {
                selectTopTabUi(0)
                viewModel.selectTopTab(0)
            }
        }

        binding.tabHistoryJobs.setOnClickListener {
            it.applyPressEffect {
                selectTopTabUi(1)
                viewModel.selectTopTab(1)
            }
        }
    }

    private fun selectTopTabUi(tabIndex: Int) {
        val greenPrimary = ContextCompat.getColor(requireContext(), R.color.profile_green_primary)
        val blackColor = ContextCompat.getColor(requireContext(), R.color.app_text_primary)
        val whiteColor = ContextCompat.getColor(requireContext(), R.color.white)

        if (tabIndex == 0) {
            // Tab 1: Đang xử lý (Active: Green Background, White Text)
            binding.tabActiveJobs.setBackgroundResource(R.drawable.bg_segmented_tab_active)
            binding.tabActiveJobs.elevation = 2f
            binding.tvLabelTabActive.setTextColor(whiteColor)
            binding.tvLabelTabActive.typeface = Typeface.DEFAULT_BOLD
            binding.tvActiveCountBadge.setBackgroundResource(R.drawable.bg_badge_on_active_tab)
            binding.tvActiveCountBadge.setTextColor(greenPrimary)
            binding.tvActiveCountBadge.typeface = Typeface.DEFAULT_BOLD

            // Tab 2: Lịch sử (Inactive: Transparent Background, Black Text)
            binding.tabHistoryJobs.background = null
            binding.tabHistoryJobs.elevation = 0f
            binding.tvLabelTabHistory.setTextColor(blackColor)
            binding.tvLabelTabHistory.typeface = Typeface.DEFAULT
            binding.tvHistoryTabCountBadge.setBackgroundResource(R.drawable.bg_badge_on_inactive_tab)
            binding.tvHistoryTabCountBadge.setTextColor(blackColor)
            binding.tvHistoryTabCountBadge.typeface = Typeface.DEFAULT_BOLD

            binding.layoutActiveJobsContainer.visibility = View.VISIBLE
            binding.layoutHistoryJobsContainer.visibility = View.GONE
        } else {
            // Tab 2: Lịch sử (Active: Green Background, White Text)
            binding.tabHistoryJobs.setBackgroundResource(R.drawable.bg_segmented_tab_active)
            binding.tabHistoryJobs.elevation = 2f
            binding.tvLabelTabHistory.setTextColor(whiteColor)
            binding.tvLabelTabHistory.typeface = Typeface.DEFAULT_BOLD
            binding.tvHistoryTabCountBadge.setBackgroundResource(R.drawable.bg_badge_on_active_tab)
            binding.tvHistoryTabCountBadge.setTextColor(greenPrimary)
            binding.tvHistoryTabCountBadge.typeface = Typeface.DEFAULT_BOLD

            // Tab 1: Đang xử lý (Inactive: Transparent Background, Black Text)
            binding.tabActiveJobs.background = null
            binding.tabActiveJobs.elevation = 0f
            binding.tvLabelTabActive.setTextColor(blackColor)
            binding.tvLabelTabActive.typeface = Typeface.DEFAULT
            binding.tvActiveCountBadge.setBackgroundResource(R.drawable.bg_badge_on_inactive_tab)
            binding.tvActiveCountBadge.setTextColor(blackColor)
            binding.tvActiveCountBadge.typeface = Typeface.DEFAULT_BOLD

            binding.layoutActiveJobsContainer.visibility = View.GONE
            binding.layoutHistoryJobsContainer.visibility = View.VISIBLE
        }
    }

    private fun setupActiveSubFilters() {
        fun updateSubFilterUi(subTab: Int) {
            viewModel.selectActiveSubTab(subTab)
            val whiteColor = ContextCompat.getColor(requireContext(), R.color.white)
            val blackColor = ContextCompat.getColor(requireContext(), R.color.app_text_primary)

            binding.chipActiveSubAll.apply {
                val isActive = (subTab == 0)
                setBackgroundResource(if (isActive) R.drawable.bg_chip_all_active else R.drawable.bg_chip_filter_inactive)
                setTextColor(if (isActive) whiteColor else blackColor)
                typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            binding.chipActiveSubPending.apply {
                val isActive = (subTab == 1)
                setBackgroundResource(if (isActive) R.drawable.bg_chip_pending_active else R.drawable.bg_chip_filter_inactive)
                setTextColor(if (isActive) whiteColor else blackColor)
                typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            binding.chipActiveSubInProgress.apply {
                val isActive = (subTab == 2)
                setBackgroundResource(if (isActive) R.drawable.bg_chip_inprogress_active else R.drawable.bg_chip_filter_inactive)
                setTextColor(if (isActive) whiteColor else blackColor)
                typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        }

        binding.chipActiveSubAll.setOnClickListener { it.applyPressEffect { updateSubFilterUi(0) } }
        binding.chipActiveSubPending.setOnClickListener { it.applyPressEffect { updateSubFilterUi(1) } }
        binding.chipActiveSubInProgress.setOnClickListener { it.applyPressEffect { updateSubFilterUi(2) } }
        updateSubFilterUi(0)
    }

    private fun setupRecyclerViews() {
        // Active Jobs Adapter (supports card click to view Landmark 81 details, accept, start)
        activeJobsAdapter = ActiveJobsAdapter(
            onCardClick = { job ->
                val intent = Intent(requireContext(), JobDetailActivity::class.java).apply {
                    putExtra("JOB_ID", job.id)
                    putExtra("JOB_STATUS", job.status)
                }
                startActivity(intent)
            },
            onAcceptClick = { job ->
                viewModel.acceptJob(job.id)
                Toast.makeText(requireContext(), "✓ Đã tiếp nhận ca #${job.id}!", Toast.LENGTH_SHORT).show()
            },
            onRejectClick = { job ->
                com.example.app_smart_waste.ui.common.AppConfirmDialog.showCancelJobWithReason(
                    context = requireContext(),
                    jobId = job.id,
                    onConfirm = { reason ->
                        viewModel.rejectJob(job.id, reason)
                        Toast.makeText(requireContext(), "✓ Đã hủy ca #${job.id} (Lý do: $reason)", Toast.LENGTH_LONG).show()
                    }
                )
            },
            onExecuteClick = { job ->
                val intent = Intent(requireContext(), JobExecutionActivity::class.java).apply {
                    putExtra("JOB_ID", job.id)
                }
                startActivity(intent)
            }
        )

        binding.rvActiveJobsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = activeJobsAdapter
            isNestedScrollingEnabled = false
        }

        // Empty Map Button
        binding.btnOpenMapFromActiveEmpty.setOnClickListener {
            it.applyPressEffect {
                (activity as? MainActivity)?.selectTab(R.id.navItemMap)
            }
        }

        // History Adapter
        historyAdapter = HistoryAdapter { jobItem ->
            val intent = Intent(requireContext(), JobHistoryDetailActivity::class.java).apply {
                putExtra("JOB_ID", jobItem.rawJob.id)
            }
            startActivity(intent)
        }

        binding.rvHistoryJobsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
            isNestedScrollingEnabled = false
        }

        // Swipe Refresh
        binding.jobsSwipeRefresh.setProgressViewOffset(true, 100, 240)
        binding.jobsSwipeRefresh.setColorSchemeResources(R.color.profile_green_primary, R.color.app_success)
        binding.jobsSwipeRefresh.setOnRefreshListener {
            viewModel.loadAllJobData()
        }
    }

    private fun setupHistoryFilters() {
        fun updateChipUi(filter: String) {
            viewModel.setHistoryFilter(filter)
            filterHistoryList(filter)
            val whiteColor = ContextCompat.getColor(requireContext(), R.color.white)
            val blackColor = ContextCompat.getColor(requireContext(), R.color.app_text_primary)

            binding.chipFilterAll.apply {
                val isActive = (filter == "ALL")
                setBackgroundResource(if (isActive) R.drawable.bg_chip_all_active else R.drawable.bg_chip_filter_inactive)
                setTextColor(if (isActive) whiteColor else blackColor)
                typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            binding.chipFilterCompleted.apply {
                val isActive = (filter == "COMPLETED")
                setBackgroundResource(if (isActive) R.drawable.bg_chip_completed_active else R.drawable.bg_chip_filter_inactive)
                setTextColor(if (isActive) whiteColor else blackColor)
                typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            binding.chipFilterCancelled.apply {
                val isActive = (filter == "CANCELLED")
                setBackgroundResource(if (isActive) R.drawable.bg_chip_cancelled_active else R.drawable.bg_chip_filter_inactive)
                setTextColor(if (isActive) whiteColor else blackColor)
                typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        }

        binding.chipFilterAll.setOnClickListener { it.applyPressEffect { updateChipUi("ALL") } }
        binding.chipFilterCompleted.setOnClickListener { it.applyPressEffect { updateChipUi("COMPLETED") } }
        binding.chipFilterCancelled.setOnClickListener { it.applyPressEffect { updateChipUi("CANCELLED") } }
        updateChipUi("ALL")
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Availability State
                launch {
                    viewModel.isAvailableForSelfPick.collectLatest { available ->
                        if (available) {
                            binding.dotAvailabilityIndicator.setBackgroundResource(R.drawable.bg_dot_active_green)
                            binding.tvAvailabilityStatusLabel.text = "Sẵn sàng nhận việc"
                            binding.tvAvailabilityStatusLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_text_primary))
                            binding.tvAvailabilityDesc.text = "Hệ thống tự động điều phối nhiệm vụ thu gom tối ưu về xe của bạn."
                            if (!binding.switchAvailability.isChecked) {
                                binding.switchAvailability.isChecked = true
                            }
                        } else {
                            binding.dotAvailabilityIndicator.setBackgroundResource(R.drawable.bg_dot_inactive_red)
                            binding.tvAvailabilityStatusLabel.text = "Tạm nghỉ"
                            binding.tvAvailabilityStatusLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_text_primary))
                            binding.tvAvailabilityDesc.text = "Gạt công tắc để bắt đầu ca và nhận nhiệm vụ thu gom mới."
                            if (binding.switchAvailability.isChecked) {
                                binding.switchAvailability.isChecked = false
                            }
                        }
                    }
                }

                // 0. Centered Loading Indicator & Swipe Refresh
                launch {
                    viewModel.isLoading.collectLatest { loading ->
                        binding.jobsSwipeRefresh.isRefreshing = loading
                        if (loading && viewModel.activeJobs.value.isEmpty()) {
                            binding.layoutLoadingCenter.visibility = View.VISIBLE
                        } else {
                            binding.layoutLoadingCenter.visibility = View.GONE
                        }
                    }
                }

                // 1. Live Countdown Ticker
                launch {
                    viewModel.countdownTicker.collectLatest { _ ->
                        activeJobsAdapter.assignTimeoutMinutes = viewModel.assignTimeoutMinutes.value
                        activeJobsAdapter.notifyItemRangeChanged(0, activeJobsAdapter.itemCount, "PAYLOAD_COUNTDOWN")
                    }
                }

                // 2. Sub-Filter Counts
                launch {
                    viewModel.allActiveCount.collectLatest { count ->
                        binding.chipActiveSubAll.text = "Tất cả ($count)"
                    }
                }

                launch {
                    viewModel.pendingCount.collectLatest { count ->
                        binding.chipActiveSubPending.text = "Đang chờ ($count)"
                    }
                }

                launch {
                    viewModel.inProgressCount.collectLatest { count ->
                        binding.chipActiveSubInProgress.text = "Đang làm ($count)"
                        binding.tvActiveCountBadge.text = count.toString()
                        (activity as? MainActivity)?.updateJobsBadge(count)
                    }
                }

                // 3. Displayed Active Jobs
                launch {
                    viewModel.displayedActiveJobs.collectLatest { activeList ->
                        activeJobsAdapter.submitList(activeList)
                        if (activeList.isEmpty()) {
                            binding.layoutEmptyActiveJobs.visibility = View.VISIBLE
                            binding.rvActiveJobsList.visibility = View.GONE
                        } else {
                            binding.layoutEmptyActiveJobs.visibility = View.GONE
                            binding.rvActiveJobsList.visibility = View.VISIBLE
                        }
                    }
                }

                // 4. History Jobs List & Filtering
                launch {
                    viewModel.historyJobs.collectLatest { historyList ->
                        val completedCount = historyList.count { it.statusType.equals("COMPLETED", ignoreCase = true) }
                        val cancelledCount = historyList.count {
                            it.statusType.equals("CANCELLED", ignoreCase = true) ||
                            it.statusType.equals("CANCELED", ignoreCase = true) ||
                            it.statusType.equals("REJECTED", ignoreCase = true) ||
                            it.statusType.equals("EXPIRED", ignoreCase = true) ||
                            it.statusType.equals("INCIDENT", ignoreCase = true)
                        }

                        binding.chipFilterAll.text = "Tất cả (${historyList.size})"
                        binding.chipFilterCompleted.text = "Hoàn thành ($completedCount)"
                        binding.chipFilterCancelled.text = "Đã hủy ($cancelledCount)"

                        filterHistoryList(viewModel.historyFilter.value)
                        binding.tvHistoryTabCountBadge.text = historyList.size.toString()
                    }
                }
            }
        }
    }

    private fun filterHistoryList(filter: String) {
        val currentList = viewModel.historyJobs.value
        val filtered = when (filter.uppercase()) {
            "COMPLETED" -> currentList.filter { it.statusType.equals("COMPLETED", ignoreCase = true) }
            "CANCELLED", "CANCELED", "REJECTED", "EXPIRED", "ISSUES" -> currentList.filter {
                it.statusType.equals("CANCELLED", ignoreCase = true) ||
                it.statusType.equals("CANCELED", ignoreCase = true) ||
                it.statusType.equals("REJECTED", ignoreCase = true) ||
                it.statusType.equals("EXPIRED", ignoreCase = true) ||
                it.statusType.equals("INCIDENT", ignoreCase = true)
            }
            else -> currentList
        }
        historyAdapter.submitList(filtered)
        if (filtered.isEmpty()) {
            binding.layoutEmptyHistoryJobs.visibility = View.VISIBLE
            binding.rvHistoryJobsList.visibility = View.GONE
        } else {
            binding.layoutEmptyHistoryJobs.visibility = View.GONE
            binding.rvHistoryJobsList.visibility = View.VISIBLE
        }
    }

    private fun playEntranceAnimation() {
        val views = listOf(
            binding.appHeader,
            binding.cardWorkAvailability,
            binding.layoutJobsSegmentedControl,
            binding.layoutActiveJobsContainer
        )

        views.forEachIndexed { index, v ->
            v.alpha = 0f
            v.translationY = 24f
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((index * 40).toLong())
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
