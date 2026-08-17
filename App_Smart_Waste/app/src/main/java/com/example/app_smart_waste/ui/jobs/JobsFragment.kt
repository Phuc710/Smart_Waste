package com.example.app_smart_waste.ui.jobs

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
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
    private var isUserInteractingWithSwitch = false

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
        val view = layoutInflater.inflate(R.layout.dialog_system_settings, null)
        dialog.setContentView(view)

        val etBaseUrl = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etServerBaseUrl)
        val switchOverload = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchOverloadAlert)
        val switchGps = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchGpsAuto)
        val tvCache = view.findViewById<TextView>(R.id.tvCacheSize)
        val btnClear = view.findViewById<Button>(R.id.btnClearCache)

        // Load configs
        etBaseUrl?.setText(com.example.app_smart_waste.core.storage.AppConfig.getBaseUrl(requireContext()))
        switchOverload?.isChecked = com.example.app_smart_waste.core.storage.AppConfig.isOverloadAlertEnabled(requireContext())
        switchGps?.isChecked = com.example.app_smart_waste.core.storage.AppConfig.isAutoGpsEnabled(requireContext())

        btnClear?.setOnClickListener {
            tvCache?.text = "Đang sử dụng 0 KB (Đã dọn dẹp)"
            Toast.makeText(requireContext(), "Đã giải phóng bộ nhớ đệm", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<android.widget.ImageView>(R.id.btnCloseSystemSettings)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnSaveSystemSettings)?.setOnClickListener {
            val newUrl = etBaseUrl?.text?.toString()?.trim()
            if (!newUrl.isNullOrBlank()) {
                com.example.app_smart_waste.core.storage.AppConfig.setBaseUrl(requireContext(), newUrl)
            }
            switchOverload?.let { sw ->
                com.example.app_smart_waste.core.storage.AppConfig.setOverloadAlertEnabled(requireContext(), sw.isChecked)
            }
            switchGps?.let { sw ->
                com.example.app_smart_waste.core.storage.AppConfig.setAutoGpsEnabled(requireContext(), sw.isChecked)
            }

            dialog.dismiss()
            Toast.makeText(requireContext(), "✅ Đã lưu cài đặt & ca trực thành công!", Toast.LENGTH_SHORT).show()
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
        val textSecondary = ContextCompat.getColor(requireContext(), R.color.profile_text_secondary)

        if (tabIndex == 0) {
            // Tab 1: Đang xử lý
            binding.tabActiveJobs.setBackgroundResource(R.drawable.bg_card_profile)
            binding.tabActiveJobs.elevation = 2f
            binding.tvLabelTabActive.setTextColor(greenPrimary)

            binding.tabHistoryJobs.background = null
            binding.tabHistoryJobs.elevation = 0f
            binding.tvLabelTabHistory.setTextColor(textSecondary)

            binding.layoutActiveJobsContainer.visibility = View.VISIBLE
            binding.layoutHistoryJobsContainer.visibility = View.GONE
        } else {
            // Tab 2: Lịch sử
            binding.tabHistoryJobs.setBackgroundResource(R.drawable.bg_card_profile)
            binding.tabHistoryJobs.elevation = 2f
            binding.tvLabelTabHistory.setTextColor(greenPrimary)

            binding.tabActiveJobs.background = null
            binding.tabActiveJobs.elevation = 0f
            binding.tvLabelTabActive.setTextColor(textSecondary)

            binding.layoutActiveJobsContainer.visibility = View.GONE
            binding.layoutHistoryJobsContainer.visibility = View.VISIBLE
        }
    }

    private fun setupActiveSubFilters() {
        fun updateSubFilterUi(subTab: Int) {
            viewModel.selectActiveSubTab(subTab)
            val greenPrimary = ContextCompat.getColor(requireContext(), R.color.profile_green_primary)
            val textSecondary = ContextCompat.getColor(requireContext(), R.color.profile_text_secondary)

            binding.chipActiveSubAll.apply {
                setBackgroundResource(if (subTab == 0) R.drawable.bg_chip_filter_active else R.drawable.bg_chip_filter_inactive)
                setTextColor(if (subTab == 0) greenPrimary else textSecondary)
            }
            binding.chipActiveSubPending.apply {
                setBackgroundResource(if (subTab == 1) R.drawable.bg_chip_filter_active else R.drawable.bg_chip_filter_inactive)
                setTextColor(if (subTab == 1) greenPrimary else textSecondary)
            }
            binding.chipActiveSubInProgress.apply {
                setBackgroundResource(if (subTab == 2) R.drawable.bg_chip_filter_active else R.drawable.bg_chip_filter_inactive)
                setTextColor(if (subTab == 2) greenPrimary else textSecondary)
            }
        }

        binding.chipActiveSubAll.setOnClickListener { it.applyPressEffect { updateSubFilterUi(0) } }
        binding.chipActiveSubPending.setOnClickListener { it.applyPressEffect { updateSubFilterUi(1) } }
        binding.chipActiveSubInProgress.setOnClickListener { it.applyPressEffect { updateSubFilterUi(2) } }
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
                AlertDialog.Builder(requireContext())
                    .setTitle("Từ chối nhiệm vụ")
                    .setMessage("Bạn có chắc chắn muốn từ chối ca #${job.id}? Tuyến sẽ được điều phối cho tài xế khác.")
                    .setPositiveButton("Từ chối") { _, _ ->
                        viewModel.rejectJob(job.id)
                        Toast.makeText(requireContext(), "Đã từ chối nhiệm vụ.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Hủy", null)
                    .show()
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
        binding.jobsSwipeRefresh.setColorSchemeResources(R.color.profile_green_primary, R.color.app_success)
        binding.jobsSwipeRefresh.setOnRefreshListener {
            viewModel.loadAllJobData()
        }
    }

    private fun setupHistoryFilters() {
        fun updateChipUi(filter: String) {
            viewModel.setHistoryFilter(filter)
            val greenPrimary = ContextCompat.getColor(requireContext(), R.color.profile_green_primary)
            val textSecondary = ContextCompat.getColor(requireContext(), R.color.profile_text_secondary)

            binding.chipFilterAll.apply {
                setBackgroundResource(if (filter == "ALL") R.drawable.bg_chip_filter_active else R.drawable.bg_chip_filter_inactive)
                setTextColor(if (filter == "ALL") greenPrimary else textSecondary)
            }
            binding.chipFilterCompleted.apply {
                setBackgroundResource(if (filter == "COMPLETED") R.drawable.bg_chip_filter_active else R.drawable.bg_chip_filter_inactive)
                setTextColor(if (filter == "COMPLETED") greenPrimary else textSecondary)
            }
            binding.chipFilterCancelled.apply {
                setBackgroundResource(if (filter == "CANCELLED") R.drawable.bg_chip_filter_active else R.drawable.bg_chip_filter_inactive)
                setTextColor(if (filter == "CANCELLED") greenPrimary else textSecondary)
            }
        }

        binding.chipFilterAll.setOnClickListener { it.applyPressEffect { updateChipUi("ALL") } }
        binding.chipFilterCompleted.setOnClickListener { it.applyPressEffect { updateChipUi("COMPLETED") } }
        binding.chipFilterCancelled.setOnClickListener { it.applyPressEffect { updateChipUi("CANCELLED") } }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Availability State
                launch {
                    viewModel.isAvailableForSelfPick.collectLatest { available ->
                        if (available) {
                            binding.cardWorkAvailability.setBackgroundResource(R.drawable.bg_card_availability_on)
                            binding.tvAvailabilityStatusLabel.text = "🟢 Sẵn sàng nhận việc"
                            binding.tvAvailabilityStatusLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_green_primary))
                            binding.tvAvailabilityDesc.text = "Bạn đang sẵn sàng nhận nhiệm vụ tự chọn."
                            binding.badgeShiftActive.visibility = View.VISIBLE
                            if (!binding.switchAvailability.isChecked) {
                                binding.switchAvailability.isChecked = true
                            }
                        } else {
                            binding.cardWorkAvailability.setBackgroundResource(R.drawable.bg_card_availability_off)
                            binding.tvAvailabilityStatusLabel.text = "⚪ Tạm nghỉ"
                            binding.tvAvailabilityStatusLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_text_secondary))
                            binding.tvAvailabilityDesc.text = "Tắt chế độ tự nhận nhiệm vụ từ bản đồ. (Admin vẫn có thể giao việc realtime)"
                            binding.badgeShiftActive.visibility = View.GONE
                            if (binding.switchAvailability.isChecked) {
                                binding.switchAvailability.isChecked = false
                            }
                        }
                    }
                }

                // 2. Shift Time Range (Dynamic ON + 8 Hours)
                launch {
                    viewModel.shiftTimeRange.collectLatest { timeRange ->
                        binding.tvShiftTimeRange.text = timeRange
                    }
                }

                // 3. Sub-Filter Counts
                launch {
                    viewModel.allActiveCount.collectLatest { count ->
                        binding.chipActiveSubAll.text = "Tất cả ($count)"
                    }
                }

                launch {
                    viewModel.pendingCount.collectLatest { count ->
                        binding.chipActiveSubPending.text = "🕒 Đang chờ ($count)"
                    }
                }

                launch {
                    viewModel.inProgressCount.collectLatest { count ->
                        binding.chipActiveSubInProgress.text = "▶️ Đang làm ($count)"
                        binding.tvActiveCountBadge.text = count.toString()
                        (activity as? MainActivity)?.updateJobsBadge(count)
                    }
                }

                // 4. Displayed Active Jobs
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

                // 5. History Jobs List & Filtering (Image 1)
                launch {
                    viewModel.historyJobs.collectLatest { historyList ->
                        filterAndSubmitHistory(historyList, viewModel.historyFilter.value)
                        binding.tvHistoryTabCountBadge.text = historyList.size.toString()
                    }
                }
                            binding.tvAvailabilityDesc.text = "Gạt công tắc để bắt đầu ca và nhận nhiệm vụ mới."
                        }
                    }
                }
            }
        }
    }

    private fun filterHistoryList(filter: String) {
        val currentList = viewModel.historyJobs.value
        val filtered = when (filter) {
            "COMPLETED" -> currentList.filter { it.status == "COMPLETED" }
            "ISSUES" -> currentList.filter { it.status == "INCIDENT" || it.status == "CANCELLED" }
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
