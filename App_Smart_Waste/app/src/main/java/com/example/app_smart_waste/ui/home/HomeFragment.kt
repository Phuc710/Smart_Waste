package com.example.app_smart_waste.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.JobDto
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.model.UiState
import com.example.app_smart_waste.core.network.RealtimeManager
import com.example.app_smart_waste.core.storage.SecureTokenStorage
import com.example.app_smart_waste.core.utils.applyStatusBarTopPadding
import com.example.app_smart_waste.databinding.FragmentHomeBinding
import com.example.app_smart_waste.data.repository.NotificationRepository
import com.example.app_smart_waste.ui.notification.NotificationCenterActivity
import com.example.app_smart_waste.ui.incident.IncidentHistoryActivity
import com.example.app_smart_waste.ui.incident.IncidentReportActivity
import com.example.app_smart_waste.ui.jobs.JobDetailActivity
import com.example.app_smart_waste.ui.jobs.JobExecutionActivity
import com.example.app_smart_waste.ui.main.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private val realtimeManager by lazy { RealtimeManager(requireContext()) }

    private var activeJob: JobDto? = null
    private var currentBinId: String? = null

    companion object {
        private const val ESTIMATED_KG_PER_BIN = 40.0
    }

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

        // Status bar top spacing
        binding.homeHeaderContainer.applyStatusBarTopPadding(12)

        bindUserInfo()
        configureActions()
        observeState()
        playEntranceAnimation()

        viewModel.loadHomeData()
    }

    override fun onResume() {
        super.onResume()
        bindUserInfo()
        viewModel.loadHomeData()
        if (viewModel.isAvailable.value) {
            com.example.app_smart_waste.core.location.GpsTracker.getInstance(requireContext()).startTracking()
        }
    }

    override fun onStart() {
        super.onStart()
        realtimeManager.connect(object : RealtimeManager.Listener {
            override fun onJobUpdated() {
                activity?.runOnUiThread { viewModel.loadHomeData() }
            }

            override fun onBinOverfullAlert(alert: RealtimeManager.BinOverfullAlert) {
                activity?.runOnUiThread { renderOverfullAlert(alert) }
            }
        })
    }

    override fun onStop() {
        realtimeManager.disconnect()
        super.onStop()
    }

    private fun bindUserInfo() {
        val tokenStore = SecureTokenStorage.getInstance(requireContext())
        val fullName = tokenStore.getFullName()?.trim()?.ifBlank { null }
        val username = tokenStore.getUsername()?.trim()?.ifBlank { null }
        val isUserActive = tokenStore.isActive()

        val displayName = fullName ?: username ?: "Người dùng"
        val displayEmpId = username?.uppercase() ?: "--"

        binding.tvHomeGreeting.text = "Xin chào, 👋"
        binding.tvHomeFullName.text = "$displayName"    

        updateStatusBadge(isUserActive, viewModel.isAvailable.value)
    }

    private fun updateStatusBadge(isUserActive: Boolean, isAvailable: Boolean) {
        if (!isUserActive) {
            binding.badgeStatusPill.setBackgroundResource(R.drawable.bg_badge_status_inactive)
            binding.tvHomeStatusBadge.text = "🔴 Tài khoản bị khóa"
            binding.tvHomeStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_text_secondary))
        } else if (isAvailable) {
            binding.badgeStatusPill.setBackgroundResource(R.drawable.bg_badge_status_active)
            binding.tvHomeStatusBadge.text = "🟢 Đang hoạt động"
            binding.tvHomeStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_green_primary))
        } else {
            binding.badgeStatusPill.setBackgroundResource(R.drawable.bg_badge_status_inactive)
            binding.tvHomeStatusBadge.text = "⚪ Tạm dừng nhận việc"
            binding.tvHomeStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_text_secondary))
        }
    }

    private fun updateAvailabilityUi(isAvailable: Boolean) {
        if (isAvailable) {
            binding.dotAvailabilityWork.setBackgroundResource(R.drawable.bg_dot_active_green)
            binding.tvAvailabilityWorkTitle.text = "Sẵn sàng nhận việc"
            binding.tvAvailabilityWorkTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_green_primary))
            binding.tvAvailabilityWorkDesc.text = "Bạn đang sẵn sàng nhận nhiệm vụ thu gom mới."
        } else {
            binding.dotAvailabilityWork.setBackgroundResource(R.drawable.bg_dot_inactive_red)
            binding.tvAvailabilityWorkTitle.text = "Tạm nghỉ / Đang bận"
            binding.tvAvailabilityWorkTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.profile_text_secondary))
            binding.tvAvailabilityWorkDesc.text = "Hệ thống sẽ tạm dừng phân phối nhiệm vụ mới."
        }
        val tokenStore = SecureTokenStorage.getInstance(requireContext())
        updateStatusBadge(tokenStore.isActive(), isAvailable)
    }

    private fun configureActions() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadHomeData() }

        // Work Availability Switch
        binding.switchHomeAvailability.setOnCheckedChangeListener { _, isChecked ->
            if (viewModel.isAvailable.value != isChecked) {
                viewModel.updateAvailability(isChecked)
                if (isChecked) {
                    Toast.makeText(requireContext(), "🟢 Đã BẬT trạng thái sẵn sàng nhận việc", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "⚪ Đã TẮT nhận nhiệm vụ", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Header Action Buttons
        // 1. Chuông: Mở Trang Trung tâm Thông báo
        binding.btnHomeBell.setOnClickListener {
            it.applyPressEffect {
                startActivity(Intent(requireContext(), NotificationCenterActivity::class.java))
            }
        }

        // 2. Bên phải: Báo cáo sự cố đang xử lý
        binding.btnHomeChat.setOnClickListener {
            it.applyPressEffect {
                val intent = Intent(requireContext(), IncidentHistoryActivity::class.java).apply {
                    putExtra("FILTER", "IN_PROGRESS")
                }
                startActivity(intent)
            }
        }

        // Quick Actions
        binding.quickActionIncident.setOnClickListener {
            it.applyPressEffect {
                val intent = Intent(requireContext(), IncidentHistoryActivity::class.java).apply {
                    currentBinId?.let { binId -> putExtra("BIN_ID", binId) }
                }
                startActivity(intent)
            }
        }

        binding.quickActionRadar.setOnClickListener {
            it.applyPressEffect {
                (activity as? MainActivity)?.selectTab(R.id.navItemMap)
            }
        }

        // View All Buttons
        binding.btnViewAllJobs.setOnClickListener {
            it.applyPressEffect {
                (activity as? MainActivity)?.selectTab(R.id.navItemJobs)
            }
        }

        binding.btnViewAllAlerts.setOnClickListener {
            it.applyPressEffect {
                (activity as? MainActivity)?.selectTab(R.id.navItemMap)
            }
        }

        // Current Job Cards
        binding.cardCurrentJob.setOnClickListener {
            it.applyPressEffect { openActiveJob() }
        }

        binding.btnOpenJob.setOnClickListener {
            it.applyPressEffect { openActiveJob() }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.homeState.collectLatest { state ->
                        binding.swipeRefresh.isRefreshing = state is UiState.Loading
                        when (state) {
                            is UiState.Success -> render(state.data)
                            is UiState.Error -> {
                                renderFallbackState()
                            }
                            else -> Unit
                        }
                    }
                }

                launch {
                    viewModel.isAvailable.collectLatest { isAvailable ->
                        if (binding.switchHomeAvailability.isChecked != isAvailable) {
                            binding.switchHomeAvailability.isChecked = isAvailable
                        }
                        updateAvailabilityUi(isAvailable)
                    }
                }

                launch {
                    NotificationRepository.getInstance().unreadCount.collectLatest { unread ->
                        if (unread > 0) {
                            binding.tvHomeBellBadge.text = if (unread > 9) "9+" else unread.toString()
                            binding.tvHomeBellBadge.isVisible = true
                        } else {
                            binding.tvHomeBellBadge.isVisible = false
                        }
                    }
                }
            }
        }
    }

    private fun render(data: HomeData) {
        // 1. Stats Strip (Real data from API)
        val collections = data.stats.collectionCount
        val distanceKm = data.stats.distanceMeters / 1000.0
        val weightKg = data.stats.estimatedWeightKg

        binding.tvDailyCollections.text = collections.toString()
        binding.tvDailyDistance.text = String.format(java.util.Locale.US, "%.1f", distanceKm)
        binding.tvDailyWeight.text = formatNumber(weightKg)

        // 2. Realtime Alerts (Bảng tin cảnh báo nóng từ danh sách thùng thật)
        val highBins = data.allBins.sortedByDescending { it.levelPercent ?: 0.0 }
        if (highBins.isNotEmpty()) {
            val b1 = highBins[0]
            val pct1 = b1.levelPercent?.toInt() ?: 0
            binding.tvAlert1Title.text = "${b1.name ?: b1.deviceId} vượt mức $pct1%"
            binding.tvAlert1Meta.text = b1.location ?: "Khu vực thu gom"
        }
        if (highBins.size > 1) {
            val b2 = highBins[1]
            val pct2 = b2.levelPercent?.toInt() ?: 0
            binding.tvAlert2Title.text = "${b2.name ?: b2.deviceId} vượt mức $pct2%"
            binding.tvAlert2Meta.text = b2.location ?: "Khu vực thu gom"
        }
        if (highBins.size > 2) {
            val b3 = highBins[2]
            val pct3 = b3.levelPercent?.toInt() ?: 0
            binding.tvAlert3Title.text = "${b3.name ?: b3.deviceId} đạt mức $pct3%"
            binding.tvAlert3Meta.text = b3.location ?: "Khu vực thu gom"
        }

        // 3. Active Job Section
        val job = data.activeJob
        if (job != null && job.status in listOf("ASSIGNED", "PENDING", "ACCEPTED", "IN_PROGRESS", "PAUSED")) {
            activeJob = job
            currentBinId = data.currentBin?.deviceId

            binding.cardCurrentJob.isVisible = true
            binding.emptyJobState.isVisible = false

            val progress = job.progress
            val total = progress?.total ?: job.targetBinIds.orEmpty().size
            val collected = progress?.collected ?: job.completedBinIds.orEmpty().size
            val percent = progress?.percent ?: if (total > 0) (collected * 100 / total) else 0

            val distanceMeters = job.routeData?.distanceMeters
            val durationSeconds = job.routeData?.durationSeconds
            val kgPerBin = if (data.stats.estimateKgPerCollection > 0) data.stats.estimateKgPerCollection else ESTIMATED_KG_PER_BIN
            val estWeight = total * kgPerBin

            binding.tvJobStatus.text = statusLabel(job.status)
            binding.tvJobCode.text = if (job.id.startsWith("#")) job.id else "#${job.id}"
            binding.tvJobProgress.text = "$collected / $total điểm đã hoàn thành"
            binding.progressJob.progress = percent.coerceIn(0, 100)
            binding.tvJobPercent.text = "$percent%"
            binding.tvJobStops.text = "$total"
            binding.tvJobDistance.text = distanceMeters?.let { formatDistance(it.roundToInt()) } ?: "--"
            binding.tvJobDuration.text = durationSeconds?.let {
                "${maxOf(1, (it / 60.0).roundToInt())} phút"
            } ?: "--"
            binding.tvJobWeight.text = if (total > 0) "~${estWeight.toInt()} kg" else "--"

            binding.tvCurrentBin.text = data.currentBin?.let { bin ->
                buildString {
                    append(bin.deviceId)
                    bin.location?.takeIf { it.isNotBlank() }?.let {
                        append(" - ")
                        append(it)
                    }
                }
            } ?: if (job.targetBinIds?.isNotEmpty() == true) "Điểm đầu: ${job.targetBinIds!!.first()}" else "Chưa có điểm thu gom"
        } else {
            activeJob = null
            currentBinId = null

            binding.cardCurrentJob.isVisible = false
            binding.emptyJobState.isVisible = true
        }

        // 4. Header Badges: Nhiệm vụ cần xác nhận (Chuông) & Báo cáo sự cố đang xử lý (Bên phải)
        if (data.pendingJobsCount > 0) {
            binding.tvHomeBellBadge.text = data.pendingJobsCount.toString()
            binding.tvHomeBellBadge.isVisible = true
        } else {
            binding.tvHomeBellBadge.isVisible = false
        }

        if (data.unresolvedIncidentsCount > 0) {
            binding.tvHomeChatBadge.text = data.unresolvedIncidentsCount.toString()
            binding.tvHomeChatBadge.isVisible = true
        } else {
            binding.tvHomeChatBadge.isVisible = false
        }

        // Đồng bộ badge Nhiệm vụ ở thanh điều hướng dưới đáy
        (activity as? MainActivity)?.updateJobsBadge(data.pendingJobsCount)
    }

    private fun renderFallbackState() {
        activeJob = null
        currentBinId = null

        binding.cardCurrentJob.isVisible = false
        binding.emptyJobState.isVisible = true
    }

    private fun openActiveJob() {
        val job = activeJob ?: return
        if (job.status in listOf("ASSIGNED", "PENDING")) {
            val intent = Intent(requireContext(), JobDetailActivity::class.java).apply {
                putExtra("JOB_ID", job.id)
                putExtra("JOB_STATUS", job.status)
            }
            startActivity(intent)
        } else {
            val intent = Intent(requireContext(), JobExecutionActivity::class.java).apply {
                putExtra("JOB_ID", job.id)
            }
            startActivity(intent)
        }
    }

    private fun renderOverfullAlert(alert: RealtimeManager.BinOverfullAlert) {
        val title = if (alert.name.isNotBlank()) alert.name else alert.binId
        binding.tvAlert1Title.text = "$title vượt mức ${alert.levelPercent}%"
        binding.tvAlert1Meta.text = if (alert.location.isNotBlank()) alert.location else "Chưa có thông tin vị trí"
        binding.tvAlert1Time.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        binding.tvHomeBellBadge.isVisible = true
    }

    private fun playEntranceAnimation() {
        val views: List<View> = listOf(
            binding.homeHeaderContainer,
            binding.cardWorkAvailability,
            binding.tvDailyCollections,
            binding.quickActionIncident,
            binding.quickActionRadar,
            binding.cardCurrentJob
        )
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = 16f
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((i * 45).toLong())
                .setDuration(280)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun View.applyPressEffect(onEnd: () -> Unit) {
        this.animate().scaleX(0.96f).scaleY(0.96f).setDuration(85).withEndAction {
            this.animate().scaleX(1f).scaleY(1f).setDuration(85).withEndAction { onEnd() }.start()
        }.start()
    }

    private fun formatDistance(meters: Int): String = when {
        meters <= 0 -> "0 km"
        else -> "${String.format(java.util.Locale.US, "%.1f", meters / 1000.0)} km"
    }

    private fun formatNumber(value: Double): String {
        return if (value >= 1000.0) {
            String.format(java.util.Locale.US, "%.1fk", value / 1000.0)
        } else {
            value.roundToInt().toString()
        }
    }

    private fun statusLabel(status: String): String = when (status) {
        "ASSIGNED", "PENDING" -> "Mới được giao"
        "ACCEPTED" -> "Đã nhận nhiệm vụ"
        "IN_PROGRESS" -> "Đang thực hiện"
        "PAUSED" -> "Tạm dừng"
        "COMPLETED" -> "Hoàn thành"
        "CANCELLED" -> "Đã hủy"
        else -> status
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
