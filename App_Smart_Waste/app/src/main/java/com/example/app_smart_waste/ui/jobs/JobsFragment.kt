package com.example.app_smart_waste.ui.jobs

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.ActiveJobUiModel
import com.example.app_smart_waste.core.model.JobHistoryUiModel
import com.example.app_smart_waste.core.model.JobStatus
import com.example.app_smart_waste.core.model.JobsActiveFilter
import com.example.app_smart_waste.core.model.JobsHistoryFilter
import com.example.app_smart_waste.core.model.JobsScreenState
import com.example.app_smart_waste.core.model.JobsUiState
import com.example.app_smart_waste.core.network.RealtimeManager
import com.example.app_smart_waste.core.utils.TimeUtils
import com.example.app_smart_waste.core.utils.applyStatusBarTopPadding
import com.example.app_smart_waste.databinding.FragmentJobsBinding
import com.example.app_smart_waste.ui.main.MainActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class JobsFragment : Fragment() {

    private var _binding: FragmentJobsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: JobsViewModel by viewModels()
    private lateinit var historyAdapter: JobHistoryAdapter

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

        setupHeader()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
        setupRealtime()
    }

    override fun onResume() {
        super.onResume()
        viewModel.handleAction(JobsAction.LoadData)
    }

    private fun setupHeader() {
        binding.appHeader.configure(
            title = "Nhiệm vụ",
            actionIconRes = R.drawable.ic_lucide_arrow_up_down,
            onActionClick = {
                toggleSortOrder()
            }
        )
    }

    private fun setupRecyclerView() {
        historyAdapter = JobHistoryAdapter { item ->
            openJobHistoryDetail(item)
        }
        binding.rvHistoryJobsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }

    private fun setupListeners() {
        // Swipe to Refresh
        binding.jobsSwipeRefresh.setOnRefreshListener {
            viewModel.handleAction(JobsAction.Refresh)
        }

        // Primary Tab Switching
        binding.tabActiveJobsHeader.setOnClickListener {
            viewModel.handleAction(JobsAction.SelectTab(0))
        }
        binding.tabHistoryJobsHeader.setOnClickListener {
            viewModel.handleAction(JobsAction.SelectTab(1))
        }

        // Retry on Error
        binding.btnRetryJobsData.setOnClickListener {
            viewModel.handleAction(JobsAction.Refresh)
        }

        // Refresh Empty State
        binding.btnRefreshEmptyJobs.setOnClickListener {
            viewModel.handleAction(JobsAction.Refresh)
        }

        // View History Link from Empty State
        binding.tvViewHistoryFromEmpty.setOnClickListener {
            viewModel.handleAction(JobsAction.SelectTab(1))
        }

        // Assigned Job Card Click (Case 02)
        binding.cardAssignedJobItem.setOnClickListener {
            val assigned = viewModel.uiState.value.assignedJob
            if (assigned != null) {
                val intent = Intent(requireContext(), JobDetailActivity::class.java).apply {
                    putExtra("JOB_ID", assigned.id)
                }
                startActivity(intent)
            }
        }

        // In Progress Job Card Click (Case 03 / Case 04)
        binding.cardInProgressJobItem.setOnClickListener {
            val inProgress = viewModel.uiState.value.inProgressJob
            if (inProgress != null) {
                val intent = Intent(requireContext(), JobExecutionActivity::class.java).apply {
                    putExtra("JOB_ID", inProgress.id)
                }
                startActivity(intent)
            }
        }

        // Active Quick Filter Chips (Tất cả | Đã giao | Đang thực hiện | Tạm dừng)
        binding.chipActiveAll.setOnClickListener { viewModel.handleAction(JobsAction.SelectActiveQuickFilter("ALL")) }
        binding.chipActiveAssigned.setOnClickListener { viewModel.handleAction(JobsAction.SelectActiveQuickFilter("ASSIGNED")) }
        binding.chipActiveInProgress.setOnClickListener { viewModel.handleAction(JobsAction.SelectActiveQuickFilter("IN_PROGRESS")) }
        binding.chipActivePaused.setOnClickListener { viewModel.handleAction(JobsAction.SelectActiveQuickFilter("PAUSED")) }

        // History Quick Filter Chips (Tất cả | Hoàn thành | Đã hủy | Hết hạn)
        binding.chipHistoryAll.setOnClickListener { viewModel.handleAction(JobsAction.SelectHistoryQuickFilter("ALL")) }
        binding.chipHistoryCompleted.setOnClickListener { viewModel.handleAction(JobsAction.SelectHistoryQuickFilter("COMPLETED")) }
        binding.chipHistoryCancelled.setOnClickListener { viewModel.handleAction(JobsAction.SelectHistoryQuickFilter("CANCELLED")) }
        binding.chipHistoryExpired.setOnClickListener { viewModel.handleAction(JobsAction.SelectHistoryQuickFilter("EXPIRED")) }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        renderUiState(state)
                    }
                }
                launch {
                    viewModel.effects.collectLatest { effect ->
                        handleEffect(effect)
                    }
                }
            }
        }
    }

    private fun renderUiState(state: JobsUiState) {
        binding.jobsSwipeRefresh.isRefreshing = state.isRefreshing

        // 1. Render Tabs (2 Màn hình riêng biệt: Đang hoạt động vs Lịch sử)
        val isTabActive = state.activeTab == 0
        binding.containerActiveTabContent.visibility = if (isTabActive) View.VISIBLE else View.GONE
        binding.containerHistoryTabContent.visibility = if (!isTabActive) View.VISIBLE else View.GONE

        val activeColor = ContextCompat.getColor(requireContext(), R.color.profile_green_primary)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.profile_text_secondary)

        if (isTabActive) {
            binding.tvTabActiveLabel.setTextColor(activeColor)
            binding.tvTabActiveLabel.typeface = android.graphics.Typeface.DEFAULT_BOLD
            binding.indicatorTabActive.visibility = View.VISIBLE

            binding.tvTabHistoryLabel.setTextColor(inactiveColor)
            binding.tvTabHistoryLabel.typeface = android.graphics.Typeface.DEFAULT
            binding.indicatorTabHistory.visibility = View.INVISIBLE
        } else {
            binding.tvTabActiveLabel.setTextColor(inactiveColor)
            binding.tvTabActiveLabel.typeface = android.graphics.Typeface.DEFAULT
            binding.indicatorTabActive.visibility = View.INVISIBLE

            binding.tvTabHistoryLabel.setTextColor(activeColor)
            binding.tvTabHistoryLabel.typeface = android.graphics.Typeface.DEFAULT_BOLD
            binding.indicatorTabHistory.visibility = View.VISIBLE
        }

        // Update Bottom Nav Badge in MainActivity: Chỉ hiện số lượng nhiệm vụ CẦN XÁC NHẬN (ASSIGNED)
        val pendingConfirmCount = if (state.assignedJob != null && state.assignedJob.status == JobStatus.ASSIGNED) 1 else 0
        (activity as? MainActivity)?.updateJobsBadge(pendingConfirmCount)

        // 2. Render Active Tab Content
        renderActiveQuickFilterChips(state)

        when (state.screenState) {
            is JobsScreenState.InitialLoading -> {
                binding.layoutJobsLoadingSkeleton.visibility = View.VISIBLE
                binding.cardJobsOfflineWarning.visibility = View.GONE
                binding.layoutJobsEmptyState.visibility = View.GONE
                binding.sectionAssignedJobs.visibility = View.GONE
                binding.sectionInProgressJobs.visibility = View.GONE
            }
            is JobsScreenState.Error -> {
                binding.layoutJobsLoadingSkeleton.visibility = View.GONE
                binding.cardJobsOfflineWarning.visibility = View.VISIBLE
                val errorMsg = (state.screenState as JobsScreenState.Error).message
                if (!errorMsg.isNullOrBlank()) {
                    binding.tvJobsErrorDescription.text = errorMsg
                } else {
                    binding.tvJobsErrorDescription.text = "Vui lòng kiểm tra kết nối mạng rồi thử lại."
                }
                binding.layoutJobsEmptyState.visibility = View.GONE
                binding.sectionAssignedJobs.visibility = View.GONE
                binding.sectionInProgressJobs.visibility = View.GONE
            }
            else -> {
                binding.layoutJobsLoadingSkeleton.visibility = View.GONE
                binding.cardJobsOfflineWarning.visibility = View.GONE

                val hasAssigned = state.assignedJob != null
                val hasInProgress = state.inProgressJob != null

                val showAssigned = hasAssigned && state.activeFilter.showAssigned && (state.activeQuickFilter == "ALL" || state.activeQuickFilter == "ASSIGNED")
                val showInProgress = hasInProgress && (
                    (state.activeFilter.showInProgress && (state.activeQuickFilter == "ALL" || state.activeQuickFilter == "IN_PROGRESS") && state.inProgressJob?.status in listOf(JobStatus.ACCEPTED, JobStatus.IN_PROGRESS)) ||
                    (state.activeFilter.showPaused && (state.activeQuickFilter == "ALL" || state.activeQuickFilter == "PAUSED") && state.inProgressJob?.status == JobStatus.PAUSED)
                )

                if (!showAssigned && !showInProgress && (state.screenState is JobsScreenState.NoActiveJob || (!hasAssigned && !hasInProgress))) {
                    // Case 07: No Task Empty State
                    binding.layoutJobsEmptyState.visibility = View.VISIBLE
                    binding.sectionAssignedJobs.visibility = View.GONE
                    binding.sectionInProgressJobs.visibility = View.GONE
                } else {
                    binding.layoutJobsEmptyState.visibility = View.GONE

                    // Section 1: Assigned Job (Case 01 / Case 02 preview)
                    if (showAssigned) {
                        binding.sectionAssignedJobs.visibility = View.VISIBLE
                        bindAssignedJobCard(state.assignedJob!!)
                    } else {
                        binding.sectionAssignedJobs.visibility = View.GONE
                    }

                    // Section 2: In Progress Job (Case 01 / Case 03 preview)
                    if (showInProgress) {
                        binding.sectionInProgressJobs.visibility = View.VISIBLE
                        bindInProgressJobCard(state.inProgressJob!!)
                    } else {
                        binding.sectionInProgressJobs.visibility = View.GONE
                    }
                }
            }
        }

        // 4. Render History Tab Content (Case 06)
        renderHistoryQuickFilterChips(state)

        if (state.history.isEmpty()) {
            binding.layoutHistoryEmptyState.visibility = View.VISIBLE
            binding.rvHistoryJobsList.visibility = View.GONE
        } else {
            binding.layoutHistoryEmptyState.visibility = View.GONE
            binding.rvHistoryJobsList.visibility = View.VISIBLE
            historyAdapter.submitList(ArrayList(state.history))
        }
    }

    private fun renderActiveQuickFilterChips(state: JobsUiState) {
        val assignedCount = if (state.assignedJob != null) 1 else 0
        val inProgressCount = if (state.inProgressJob != null && state.inProgressJob.status in listOf(JobStatus.ACCEPTED, JobStatus.IN_PROGRESS)) 1 else 0
        val pausedCount = if (state.inProgressJob != null && state.inProgressJob.status == JobStatus.PAUSED) 1 else 0
        val totalActive = assignedCount + inProgressCount + pausedCount

        binding.chipActiveAll.text = "Tất cả ($totalActive)"
        binding.chipActiveAssigned.text = "Đã giao ($assignedCount)"
        binding.chipActiveInProgress.text = "Đang thực hiện ($inProgressCount)"
        binding.chipActivePaused.text = "Tạm dừng ($pausedCount)"

        val chips = listOf(
            binding.chipActiveAll to "ALL",
            binding.chipActiveAssigned to "ASSIGNED",
            binding.chipActiveInProgress to "IN_PROGRESS",
            binding.chipActivePaused to "PAUSED"
        )
        chips.forEach { (view, key) ->
            if (key == state.activeQuickFilter) {
                view.setBackgroundResource(R.drawable.bg_quick_filter_chip_active)
                view.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                view.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                view.setBackgroundResource(R.drawable.bg_quick_filter_chip_inactive)
                view.setTextColor(Color.parseColor("#4B5563"))
                view.typeface = android.graphics.Typeface.DEFAULT
            }
        }
    }

    private fun renderHistoryQuickFilterChips(state: JobsUiState) {
        val rawList = state.rawHistory.ifEmpty { state.history }
        val totalHistory = rawList.size
        val completedCount = rawList.count { it.status == JobStatus.COMPLETED }
        val cancelledCount = rawList.count { it.status == JobStatus.CANCELLED || it.status == JobStatus.REJECTED }
        val expiredCount = rawList.count { it.status == JobStatus.EXPIRED }

        binding.chipHistoryAll.text = "Tất cả ($totalHistory)"
        binding.chipHistoryCompleted.text = "Hoàn thành ($completedCount)"
        binding.chipHistoryCancelled.text = "Đã hủy ($cancelledCount)"
        binding.chipHistoryExpired.text = "Hết hạn ($expiredCount)"

        val chips = listOf(
            binding.chipHistoryAll to "ALL",
            binding.chipHistoryCompleted to "COMPLETED",
            binding.chipHistoryCancelled to "CANCELLED",
            binding.chipHistoryExpired to "EXPIRED"
        )
        chips.forEach { (view, key) ->
            if (key == state.historyQuickFilter) {
                view.setBackgroundResource(R.drawable.bg_quick_filter_chip_active)
                view.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                view.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                view.setBackgroundResource(R.drawable.bg_quick_filter_chip_inactive)
                view.setTextColor(Color.parseColor("#4B5563"))
                view.typeface = android.graphics.Typeface.DEFAULT
            }
        }
    }

    private fun bindAssignedJobCard(job: ActiveJobUiModel) {
        val cleanId = job.id.removePrefix("#")
        binding.tvAssignedJobCode.text = if (cleanId.startsWith("JOB_")) "#$cleanId" else "#JOB_$cleanId"
        binding.tvAssignedJobPill.text = "ĐÃ GIAO"
        binding.tvAssignedJobPill.setBackgroundResource(R.drawable.bg_tag_da_giao)
        binding.tvAssignedJobPill.setTextColor(Color.parseColor("#D97706"))

        binding.tvAssignedAreaFooter.text = if (job.fromArea.isNotBlank() && job.toArea.isNotBlank() && job.fromArea != job.toArea) {
            "${job.fromArea} → ${job.toArea}"
        } else {
            job.fromArea.ifBlank { "Tuyến thu gom trung tâm" }
        }

        binding.tvAssignedStopsMetric.text = "${job.totalStops} điểm"
        val distKm = (job.distanceMeters ?: (job.totalStops * 950)) / 1000.0
        binding.tvAssignedDistanceMetric.text = String.format(Locale.US, "%.1f km", distKm)
        val durMins = ((job.durationSeconds ?: (job.totalStops * 360)) / 60)
        binding.tvAssignedDurationMetric.text = "$durMins phút"

        val assignedTime = (job.assignedAt ?: job.rawJob?.createdAt ?: job.id).let { TimeUtils.formatDisplayTime(it) }
        val finalAssignedTime = if (assignedTime != "--") assignedTime else TimeUtils.getCurrentVnTimeOnly()
        binding.tvAssignedJobTime.text = "Giao lúc: $finalAssignedTime"
    }

    private fun bindInProgressJobCard(job: ActiveJobUiModel) {
        val cleanId = job.id.removePrefix("#")
        binding.tvInProgressJobCode.text = if (cleanId.startsWith("JOB_")) "#$cleanId" else "#JOB_$cleanId"

        binding.tvInProgressAreaFooter.text = if (job.fromArea.isNotBlank() && job.toArea.isNotBlank() && job.fromArea != job.toArea) {
            "${job.fromArea} → ${job.toArea}"
        } else {
            job.fromArea.ifBlank { "Tuyến thu gom trung tâm" }
        }

        binding.tvInProgressStopsMetric.text = "${job.totalStops} điểm"
        val inProgressDistKm = (job.distanceMeters ?: (job.totalStops * 950)) / 1000.0
        binding.tvInProgressDistanceMetric.text = String.format(Locale.US, "%.1f km", inProgressDistKm)
        val inProgressDurMins = ((job.durationSeconds ?: (job.totalStops * 360)) / 60)
        binding.tvInProgressDurationMetric.text = "$inProgressDurMins phút"

        if (job.status == JobStatus.PAUSED) {
            binding.tvInProgressJobPill.text = "TẠM DỪNG"
            binding.tvInProgressJobPill.setBackgroundResource(R.drawable.bg_tag_tam_dung)
            binding.tvInProgressJobPill.setTextColor(Color.parseColor("#EA580C"))
            val pausedTime = (job.pausedAt ?: job.startedAt ?: job.assignedAt ?: job.id).let { TimeUtils.formatDisplayTime(it) }
            val finalPausedTime = if (pausedTime != "--") pausedTime else TimeUtils.getCurrentVnTimeOnly()
            binding.tvInProgressJobTime.text = "Tạm dừng lúc: $finalPausedTime"
        } else if (job.status == JobStatus.ACCEPTED) {
            binding.tvInProgressJobPill.text = "ĐÃ NHẬN"
            binding.tvInProgressJobPill.setBackgroundResource(R.drawable.bg_tag_dang_thuc_hien)
            binding.tvInProgressJobPill.setTextColor(Color.parseColor("#2563EB"))
            val acceptedTime = (job.startedAt ?: job.assignedAt ?: job.rawJob?.createdAt ?: job.id).let { TimeUtils.formatDisplayTime(it) }
            val finalAcceptedTime = if (acceptedTime != "--") acceptedTime else TimeUtils.getCurrentVnTimeOnly()
            binding.tvInProgressJobTime.text = "Nhận lúc: $finalAcceptedTime"
        } else {
            binding.tvInProgressJobPill.text = "ĐANG THỰC HIỆN"
            binding.tvInProgressJobPill.setBackgroundResource(R.drawable.bg_tag_dang_thuc_hien)
            binding.tvInProgressJobPill.setTextColor(Color.parseColor("#2563EB"))
            val startedTime = (job.startedAt ?: job.assignedAt ?: job.rawJob?.createdAt ?: job.id).let { TimeUtils.formatDisplayTime(it) }
            val finalStartedTime = if (startedTime != "--") startedTime else TimeUtils.getCurrentVnTimeOnly()
            binding.tvInProgressJobTime.text = "Bắt đầu lúc: $finalStartedTime"
        }
    }

    // =========================================================================
    // BOTTOM SHEET: SẮP XẾP NHIỆM VỤ (MỚI NHẤT / CŨ NHẤT)
    // =========================================================================

    private fun toggleSortOrder() {
        val current = viewModel.uiState.value.sortOrder
        val next = if (current == "NEWEST") "OLDEST" else "NEWEST"
        viewModel.handleAction(JobsAction.ChangeSortOrder(next))
        val toastMsg = if (next == "NEWEST") "Đã sắp xếp: Mới nhất" else "Đã sắp xếp: Cũ nhất"
        Toast.makeText(requireContext(), toastMsg, Toast.LENGTH_SHORT).show()
    }

    // =========================================================================
    // BOTTOM SHEET: BỘ LỌC CẦN XỬ LÝ (TAB 0)
    // =========================================================================

    private fun showActiveFilterBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_active_filter, null)
        dialog.setContentView(sheetView)

        val currentFilter = viewModel.uiState.value.activeFilter
        val cbAssigned = sheetView.findViewById<CheckBox>(R.id.cbActiveAssigned)
        val cbInProgress = sheetView.findViewById<CheckBox>(R.id.cbActiveInProgress)
        val cbPaused = sheetView.findViewById<CheckBox>(R.id.cbActivePaused)
        val btnClose = sheetView.findViewById<View>(R.id.btnCloseActiveFilterSheet)
        val btnReset = sheetView.findViewById<View>(R.id.btnResetActiveFilter)
        val btnApply = sheetView.findViewById<View>(R.id.btnApplyActiveFilter)

        cbAssigned?.isChecked = currentFilter.showAssigned
        cbInProgress?.isChecked = currentFilter.showInProgress
        cbPaused?.isChecked = currentFilter.showPaused

        btnClose?.setOnClickListener { dialog.dismiss() }

        btnReset?.setOnClickListener {
            viewModel.handleAction(JobsAction.ResetActiveFilter)
            dialog.dismiss()
        }

        btnApply?.setOnClickListener {
            val newFilter = JobsActiveFilter(
                showAssigned = cbAssigned?.isChecked ?: true,
                showInProgress = cbInProgress?.isChecked ?: true,
                showPaused = cbPaused?.isChecked ?: true
            )
            viewModel.handleAction(JobsAction.ApplyActiveFilter(newFilter))
            dialog.dismiss()
        }

        dialog.show()
    }

    // =========================================================================
    // BOTTOM SHEET C: BỘ LỌC LỊCH SỬ (TAB 1)
    // =========================================================================

    private fun showHistoryFilterBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_history_filter, null)
        dialog.setContentView(sheetView)

        val currentFilter = viewModel.uiState.value.historyFilter
        val tvFromDate = sheetView.findViewById<TextView>(R.id.tvFilterFromDate)
        val tvToDate = sheetView.findViewById<TextView>(R.id.tvFilterToDate)
        val btnFromDate = sheetView.findViewById<View>(R.id.btnSelectFromDate)
        val btnToDate = sheetView.findViewById<View>(R.id.btnSelectToDate)

        val cbCompleted = sheetView.findViewById<CheckBox>(R.id.cbHistoryCompleted)
        val cbCancelled = sheetView.findViewById<CheckBox>(R.id.cbHistoryCancelled)
        val cbExpired = sheetView.findViewById<CheckBox>(R.id.cbHistoryExpired)
        val btnClose = sheetView.findViewById<View>(R.id.btnCloseHistoryFilterSheet)
        val btnReset = sheetView.findViewById<View>(R.id.btnResetHistoryFilter)
        val btnApply = sheetView.findViewById<View>(R.id.btnApplyHistoryFilter)

        val calNow = java.util.Calendar.getInstance()
        val defaultTo = String.format(Locale.US, "%02d/%02d/%04d", calNow.get(java.util.Calendar.DAY_OF_MONTH), calNow.get(java.util.Calendar.MONTH) + 1, calNow.get(java.util.Calendar.YEAR))
        calNow.add(java.util.Calendar.DAY_OF_YEAR, -7)
        val defaultFrom = String.format(Locale.US, "%02d/%02d/%04d", calNow.get(java.util.Calendar.DAY_OF_MONTH), calNow.get(java.util.Calendar.MONTH) + 1, calNow.get(java.util.Calendar.YEAR))

        var selectedFromDate = currentFilter.fromDate ?: defaultFrom
        var selectedToDate = currentFilter.toDate ?: defaultTo

        tvFromDate?.text = selectedFromDate
        tvToDate?.text = selectedToDate

        btnFromDate?.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    selectedFromDate = String.format(Locale.US, "%02d/%02d/%04d", dayOfMonth, month + 1, year)
                    tvFromDate?.text = selectedFromDate
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnToDate?.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    selectedToDate = String.format(Locale.US, "%02d/%02d/%04d", dayOfMonth, month + 1, year)
                    tvToDate?.text = selectedToDate
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }

        cbCompleted?.isChecked = currentFilter.showCompleted
        cbCancelled?.isChecked = currentFilter.showCancelled
        cbExpired?.isChecked = currentFilter.showExpired

        btnClose?.setOnClickListener { dialog.dismiss() }

        btnReset?.setOnClickListener {
            viewModel.handleAction(JobsAction.ResetHistoryFilter)
            dialog.dismiss()
        }

        btnApply?.setOnClickListener {
            val newFilter = currentFilter.copy(
                fromDate = selectedFromDate,
                toDate = selectedToDate,
                showCompleted = cbCompleted?.isChecked ?: true,
                showCancelled = cbCancelled?.isChecked ?: true,
                showExpired = cbExpired?.isChecked ?: true
            )
            viewModel.handleAction(JobsAction.ApplyHistoryFilter(newFilter))
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun openJobHistoryDetail(item: JobHistoryUiModel) {
        val intent = Intent(requireContext(), JobDetailActivity::class.java).apply {
            putExtra("JOB_ID", item.id)
            putExtra("IS_HISTORY", true)
        }
        startActivity(intent)
    }

    fun selectTab(tabIndex: Int) {
        viewModel.handleAction(JobsAction.SelectTab(tabIndex))
    }

    fun showJobCompletedDialog(totalStops: Int = 7, distanceKm: Double = 6.1, durationMins: Int = 28) {
        val dialog = android.app.Dialog(requireContext())
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val dialogView = layoutInflater.inflate(R.layout.dialog_job_completed, null)
        dialog.setContentView(dialogView)

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setGravity(android.view.Gravity.CENTER)
        dialog.window?.attributes?.windowAnimations = android.R.style.Animation_Dialog

        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        val tvStops = dialogView.findViewById<TextView>(R.id.tvCompletedStopsCount)
        val tvDistance = dialogView.findViewById<TextView>(R.id.tvCompletedDistance)
        val tvDuration = dialogView.findViewById<TextView>(R.id.tvCompletedDuration)
        val btnViewHistory = dialogView.findViewById<View>(R.id.btnCompletedViewHistory)
        val btnBackToList = dialogView.findViewById<View>(R.id.btnCompletedBackToList)

        tvStops?.text = "$totalStops/$totalStops"
        tvDistance?.text = String.format(Locale.US, "%.1f km", distanceKm)
        tvDuration?.text = "$durationMins phút"

        btnViewHistory?.setOnClickListener {
            dialog.dismiss()
            selectTab(1)
        }

        btnBackToList?.setOnClickListener {
            dialog.dismiss()
            selectTab(0)
        }

        dialog.show()
    }

    private fun handleEffect(effect: JobsEffect) {
        when (effect) {
            is JobsEffect.ShowToast -> Toast.makeText(requireContext(), effect.message, Toast.LENGTH_SHORT).show()
            is JobsEffect.JobCompleted -> showJobCompletedDialog()
            is JobsEffect.OperationFailed -> Toast.makeText(requireContext(), "Lỗi: ${effect.message}", Toast.LENGTH_SHORT).show()
            is JobsEffect.NavigateToJobDetail -> {
                val intent = Intent(requireContext(), JobDetailActivity::class.java).apply {
                    putExtra("JOB_ID", effect.jobId)
                }
                startActivity(intent)
            }
            is JobsEffect.NavigateToJobExecution -> {
                val intent = Intent(requireContext(), JobExecutionActivity::class.java).apply {
                    putExtra("JOB_ID", effect.jobId)
                }
                startActivity(intent)
            }
            is JobsEffect.OpenMapForJob -> {
                (activity as? MainActivity)?.selectTab(R.id.navItemMap)
            }
            is JobsEffect.OpenMapForBin -> {
                (activity as? MainActivity)?.selectTab(R.id.navItemMap)
            }
        }
    }

    private val realtimeManager by lazy { RealtimeManager(requireContext()) }

    private fun setupRealtime() {
        realtimeManager.connect(object : RealtimeManager.Listener {
            override fun onJobUpdated(jobId: String?) {
                viewModel.handleAction(JobsAction.Refresh)
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        realtimeManager.disconnect()
        _binding = null
    }
}
