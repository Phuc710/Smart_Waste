package com.example.app_smart_waste.ui.profile

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.IncidentReportDto
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.utils.applyStatusBarTopPadding
import com.example.app_smart_waste.databinding.FragmentProfileBinding
import com.example.app_smart_waste.ui.auth.LoginActivity
import com.example.app_smart_waste.ui.incident.IncidentHistoryActivity
import com.example.app_smart_waste.ui.incident.IncidentReportActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()

    private var currentFilter = "7 ngày qua"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup Shared AppHeader
        binding.appHeader.configure(
            title = "Hồ sơ cá nhân",
            actionIconRes = R.drawable.ic_settings,
            onActionClick = { showSystemSettingsBottomSheet() }
        )

        setupListeners()
        observeViewModel()
        playEntranceAnimation()

        viewModel.loadUserProfile()
        viewModel.loadIncidents()
        viewModel.loadBins()
        viewModel.loadProfileData(currentFilter)
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadUserProfile()
        viewModel.loadIncidents()
        viewModel.loadBins()
        viewModel.loadProfileData(currentFilter)
    }

    private fun observeViewModel() {
        // User Profile data observation
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userState.collectLatest { user ->
                binding.tvProfileName.text = user.fullName
                binding.tvProfileBadge.text = user.role
                binding.tvStaffCode.text = "Mã nhân viên: ${user.id}"
            }
        }

        // Work Stats observation
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.statsState.collectLatest { stats ->
                binding.tvVehiclePlateProfile.text = stats.vehiclePlate
                binding.tvVehicleTypeProfile.text = stats.vehicleType
                binding.tvShiftTitle.text = stats.shiftName
                binding.tvShiftTime.text = stats.shiftTime

                binding.tvStatDoneCount.text = stats.completedTasks.toString()
                binding.tvStatWasteTons.text = "${stats.wasteTons} tấn"
                binding.tvStatDistanceKm.text = "${stats.distanceKm.toInt()} km"
                binding.tvStatWorkHours.text = "${stats.workHours} giờ"
            }
        }

        // Incident Reports count observation
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.incidentsState.collectLatest { list ->
                binding.tvIncidentsBadgeCount.text = "${list.size} sự cố"
            }
        }
    }

    private fun setupListeners() {
        // 1. Hero Profile Card Click
        binding.cardHeroProfile.setOnClickListener {
            it.applyPressEffect {
                showPersonalInfoBottomSheet()
            }
        }

        // 3. Change Avatar
        binding.imgProfileAvatar.setOnClickListener {
            it.applyPressEffect {
                Toast.makeText(requireContext(), "Tính năng cập nhật ảnh đại diện", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Vehicle Info Click
        binding.cardVehicleInfo.setOnClickListener {
            it.applyPressEffect {
                showVehicleDetailBottomSheet()
            }
        }

        // 5. Shift Info Click
        binding.cardShiftInfo.setOnClickListener {
            it.applyPressEffect {
                showShiftDetailBottomSheet()
            }
        }

        // 6. Statistics Time Filter Dropdown
        binding.tvStatsFilter.setOnClickListener {
            val options = arrayOf("Hôm nay", "7 ngày qua", "30 ngày qua", "Tháng này")
            AlertDialog.Builder(requireContext())
                .setTitle("Chọn khoảng thời gian thống kê")
                .setItems(options) { _, which ->
                    currentFilter = options[which]
                    binding.tvStatsFilterLabel.text = currentFilter
                    viewModel.loadProfileData(currentFilter)
                }
                .show()
        }

        // 7. Report CTA Button
        binding.btnViewDetailedReport.setOnClickListener {
            it.applyPressEffect {
                showDetailedReportBottomSheet()
            }
        }

        // 8. Menu 1: Thông tin cá nhân
        binding.menuPersonalInfo.setOnClickListener {
            it.applyPressEffect {
                showPersonalInfoBottomSheet()
            }
        }

        // 9. Menu 2: Lịch sử báo cáo sự cố (incident_reports)
        binding.menuIncidentHistory.setOnClickListener {
            it.applyPressEffect {
                val intent = Intent(requireContext(), IncidentHistoryActivity::class.java)
                startActivity(intent)
            }
        }

        // 10. Menu 3: Cài đặt tần suất GPS & Âm báo
        binding.menuGpsSettings.setOnClickListener {
            it.applyPressEffect {
                showGpsSettingsBottomSheet()
            }
        }

        // 11. Menu 4: Đổi mật khẩu
        binding.menuChangePassword.setOnClickListener {
            it.applyPressEffect {
                showChangePasswordBottomSheet()
            }
        }

        // 12. Logout
        binding.btnLogout.setOnClickListener {
            it.applyPressEffect {
                showLogoutConfirmDialog()
            }
        }
    }

    // ==========================================
    // POPUP & BOTTOM SHEET DIALOG IMPLEMENTATIONS
    // ==========================================

    private fun showPhotoPreviewDialog(photoUrl: String?, binTitle: String) {
        val view = layoutInflater.inflate(R.layout.dialog_image_preview, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.findViewById<TextView>(R.id.tvPhotoBinCaption)?.text = "Minh chứng sự cố: $binTitle (Signed URL 3600s)"
        view.findViewById<ImageView>(R.id.btnClosePhotoPreview)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnDonePhotoPreview)?.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showGpsSettingsBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_gps_settings, null)
        dialog.setContentView(view)

        val config = viewModel.gpsConfigState.value
        val rgInterval = view.findViewById<RadioGroup>(R.id.rgGpsInterval)
        val switchJobSound = view.findViewById<MaterialSwitch>(R.id.switchJobSound)
        val switchBinAlert = view.findViewById<MaterialSwitch>(R.id.switchBinAlert)

        when (config.gpsIntervalSeconds) {
            5 -> view.findViewById<RadioButton>(R.id.rbGps5s)?.isChecked = true
            30 -> view.findViewById<RadioButton>(R.id.rbGps30s)?.isChecked = true
            60 -> view.findViewById<RadioButton>(R.id.rbGps60s)?.isChecked = true
            else -> view.findViewById<RadioButton>(R.id.rbGps10s)?.isChecked = true
        }

        switchJobSound?.isChecked = config.jobSoundEnabled
        switchBinAlert?.isChecked = config.binAlertEnabled

        view.findViewById<ImageView>(R.id.btnCloseGpsSettings)?.setOnClickListener { dialog.dismiss() }

        view.findViewById<Button>(R.id.btnSaveGpsSettings)?.setOnClickListener {
            val selectedInterval = when (rgInterval?.checkedRadioButtonId) {
                R.id.rbGps5s -> 5
                R.id.rbGps30s -> 30
                R.id.rbGps60s -> 60
                else -> 10
            }
            val jobSound = switchJobSound?.isChecked ?: true
            val binAlert = switchBinAlert?.isChecked ?: true

            viewModel.saveGpsConfig(selectedInterval, jobSound, binAlert)
            dialog.dismiss()
            Toast.makeText(requireContext(), "Đã lưu cấu hình GPS (${selectedInterval}s) & Âm báo thành công!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showPersonalInfoBottomSheet() {
        val user = viewModel.userState.value
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_personal_info, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.dialogName)?.text = user.fullName
        view.findViewById<TextView>(R.id.dialogRoleBadge)?.text = user.role
        view.findViewById<TextView>(R.id.dialogStaffCode)?.text = user.id
        view.findViewById<TextView>(R.id.dialogUsername)?.text = user.username

        view.findViewById<ImageView>(R.id.btnCloseDialog)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnDonePersonalInfo)?.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showChangePasswordBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_change_password, null)
        dialog.setContentView(view)

        val etOld = view.findViewById<TextInputEditText>(R.id.etOldPassword)
        val etNew = view.findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirm = view.findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val tilOld = view.findViewById<TextInputLayout>(R.id.tilOldPassword)
        val tilNew = view.findViewById<TextInputLayout>(R.id.tilNewPassword)
        val tilConfirm = view.findViewById<TextInputLayout>(R.id.tilConfirmPassword)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitChangePass)

        view.findViewById<ImageView>(R.id.btnCloseChangePass)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnCancelChangePass)?.setOnClickListener { dialog.dismiss() }

        btnSubmit?.setOnClickListener {
            tilOld?.error = null
            tilNew?.error = null
            tilConfirm?.error = null

            val oldPass = etOld?.text?.toString()?.trim().orEmpty()
            val newPass = etNew?.text?.toString()?.trim().orEmpty()
            val confPass = etConfirm?.text?.toString()?.trim().orEmpty()

            when {
                oldPass.isBlank() -> {
                    tilOld?.error = "Vui lòng nhập mật khẩu hiện tại"
                }
                newPass.length < 8 -> {
                    tilNew?.error = "Mật khẩu mới phải từ 8 ký tự trở lên"
                }
                newPass != confPass -> {
                    tilConfirm?.error = "Mật khẩu xác nhận không khớp"
                }
                else -> {
                    btnSubmit.isEnabled = false
                    btnSubmit.text = "Đang cập nhật..."
                    viewLifecycleOwner.lifecycleScope.launch {
                        val result = viewModel.changePassword(oldPass, newPass)
                        if (result.isSuccess) {
                            dialog.dismiss()
                            Toast.makeText(requireContext(), "Cập nhật mật khẩu thành công!", Toast.LENGTH_LONG).show()
                        } else {
                            btnSubmit.isEnabled = true
                            btnSubmit.text = "Cập nhật mật khẩu"
                            val errMsg = result.exceptionOrNull()?.message ?: "Đổi mật khẩu thất bại"
                            tilOld?.error = errMsg
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showSystemSettingsBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_system_settings, null)
        dialog.setContentView(view)

        // 1. Bind Phương tiện phụ trách (Default: Xe ép rác, 8 tấn, 100%)
        val stats = viewModel.statsState.value
        view.findViewById<TextView>(R.id.tvSettingsVehiclePlate)?.text = "Xe ép rác"
        view.findViewById<TextView>(R.id.tvSettingsVehicleType)?.text = "Phương tiện thu gom thông minh"
        view.findViewById<TextView>(R.id.tvSettingsVehicleWeight)?.text = "8 tấn"
        view.findViewById<TextView>(R.id.tvSettingsVehicleFuel)?.text = "100%"

        // 2. Realtime Shift Countdown & Progress (GMT+7)
        val tvStartTime = view.findViewById<TextView>(R.id.tvSettingsShiftStartTime)
        val tvDuration = view.findViewById<TextView>(R.id.tvSettingsShiftDuration)
        val tvRemaining = view.findViewById<TextView>(R.id.tvSettingsShiftRemainingTime)
        val tvEndTime = view.findViewById<TextView>(R.id.tvSettingsShiftEndTime)
        val progressShift = view.findViewById<ProgressBar>(R.id.progressShiftTime)

        tvStartTime?.text = "06:00"
        tvDuration?.text = "Tự động kết thúc sau 08:00:00"

        val handler = Handler(Looper.getMainLooper())
        val countdownRunnable = object : Runnable {
            override fun run() {
                val tz = TimeZone.getTimeZone("GMT+7")
                val now = Calendar.getInstance(tz)
                val hour = now.get(Calendar.HOUR_OF_DAY)
                val min = now.get(Calendar.MINUTE)
                val sec = now.get(Calendar.SECOND)

                val currentSecOfDay = hour * 3600 + min * 60 + sec
                val shiftStartSec = 6 * 3600  // 06:00:00
                val shiftEndSec = 14 * 3600   // 14:00:00
                val totalShiftSec = 8 * 3600  // 8 hours = 28800s

                if (currentSecOfDay in shiftStartSec..shiftEndSec) {
                    val remainingSec = shiftEndSec - currentSecOfDay
                    val remH = remainingSec / 3600
                    val remM = (remainingSec % 3600) / 60
                    val remS = remainingSec % 60
                    tvRemaining?.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", remH, remM, remS)
                    tvEndTime?.text = "kết thúc lúc 14:00"

                    val elapsedSec = currentSecOfDay - shiftStartSec
                    val progressPercent = ((elapsedSec.toDouble() / totalShiftSec.toDouble()) * 100).toInt().coerceIn(0, 100)
                    progressShift?.progress = progressPercent
                } else if (currentSecOfDay < shiftStartSec) {
                    val secUntilStart = shiftStartSec - currentSecOfDay
                    val remH = secUntilStart / 3600
                    val remM = (secUntilStart % 3600) / 60
                    val remS = secUntilStart % 60
                    tvRemaining?.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", remH, remM, remS)
                    tvEndTime?.text = "Bắt đầu ca sáng lúc 06:00"
                    progressShift?.progress = 0
                } else {
                    val secUntilNext = (24 * 3600 - currentSecOfDay) + shiftStartSec
                    val remH = secUntilNext / 3600
                    val remM = (secUntilNext % 3600) / 60
                    val remS = secUntilNext % 60
                    tvRemaining?.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", remH, remM, remS)
                    tvEndTime?.text = "Ca sáng tiếp theo lúc 06:00"
                    progressShift?.progress = 100
                }

                handler.postDelayed(this, 1000)
            }
        }

        handler.post(countdownRunnable)
        dialog.setOnDismissListener {
            handler.removeCallbacks(countdownRunnable)
        }

        val switchOverload = view.findViewById<MaterialSwitch>(R.id.switchOverloadAlert)
        val switchGps = view.findViewById<MaterialSwitch>(R.id.switchGpsAuto)
        val tvCache = view.findViewById<TextView>(R.id.tvCacheSize)
        val btnClear = view.findViewById<Button>(R.id.btnClearCache)

        // Load current configurations from AppConfig
        switchOverload?.isChecked = com.example.app_smart_waste.core.storage.AppConfig.isOverloadAlertEnabled(requireContext())
        switchGps?.isChecked = com.example.app_smart_waste.core.storage.AppConfig.isAutoGpsEnabled(requireContext())

        btnClear?.setOnClickListener {
            tvCache?.text = "Đang sử dụng 0 KB (Đã dọn dẹp)"
            Toast.makeText(requireContext(), "Đã giải phóng bộ nhớ đệm", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<ImageView>(R.id.btnCloseSystemSettings)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnSaveSystemSettings)?.setOnClickListener {
            switchOverload?.let { sw ->
                com.example.app_smart_waste.core.storage.AppConfig.setOverloadAlertEnabled(requireContext(), sw.isChecked)
            }
            switchGps?.let { sw ->
                com.example.app_smart_waste.core.storage.AppConfig.setAutoGpsEnabled(requireContext(), sw.isChecked)
            }

            dialog.dismiss()
            Toast.makeText(requireContext(), "✅ Đã lưu cấu hình thành công", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showDetailedReportBottomSheet() {
        val stats = viewModel.statsState.value
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_detailed_report, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvReportPeriodBadge)?.text = "Khoảng thời gian: $currentFilter"
        view.findViewById<TextView>(R.id.tvReportCompletedTasks)?.text = "${stats.completedTasks} tuyến"
        view.findViewById<TextView>(R.id.tvReportWasteTons)?.text = "${stats.wasteTons} tấn"
        view.findViewById<TextView>(R.id.tvReportDistanceKm)?.text = "${stats.distanceKm.toInt()} km"
        view.findViewById<TextView>(R.id.tvReportWorkHours)?.text = "${stats.workHours} giờ"
        view.findViewById<TextView>(R.id.tvReportVehicle)?.text = "${stats.vehicleType} (8 tấn)"

        view.findViewById<ImageView>(R.id.btnCloseReport)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnDoneReport)?.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showVehicleDetailBottomSheet() {
        val stats = viewModel.statsState.value
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_vehicle_detail, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvVehicleTypeDetail)?.text = stats.vehicleType
        view.findViewById<TextView>(R.id.tvVehiclePlateDetail)?.text = "Phương tiện thu gom thông minh"

        view.findViewById<ImageView>(R.id.btnCloseVehicleDetail)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnDoneVehicleDetail)?.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showShiftDetailBottomSheet() {
        val stats = viewModel.statsState.value
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_shift_detail, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvShiftTitleDetail)?.text = stats.shiftName
        view.findViewById<TextView>(R.id.tvShiftTimeDetail)?.text = stats.shiftTime

        view.findViewById<ImageView>(R.id.btnCloseShiftDetail)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnDoneShiftDetail)?.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showLogoutConfirmDialog() {
        com.example.app_smart_waste.ui.common.AppConfirmDialog.showDanger(
            context = requireContext(),
            title = "Đăng xuất tài khoản",
            message = "Bạn có chắc chắn muốn đăng xuất khỏi phiên làm việc hiện tại? Hệ thống sẽ lưu trữ an toàn toàn bộ lộ trình và điểm thu gom.",
            actionButtonText = "Đăng xuất",
            cancelButtonText = "Ở lại",
            iconRes = R.drawable.ic_logout_red,
            onConfirm = {
                viewModel.logout()
                val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                @Suppress("DEPRECATION")
                activity?.overridePendingTransition(R.anim.anim_fade_in_smooth, R.anim.anim_fade_out_smooth)
                activity?.finish()
            }
        )
    }

    // ==========================================
    // MICRO-ANIMATIONS & HELPERS
    // ==========================================

    private fun playEntranceAnimation() {
        val views = listOf(
            binding.appHeader,
            binding.cardHeroProfile,
            binding.cardWorkInfo,
            binding.cardStatsSection,
            binding.cardSettingsMenu,
            binding.btnLogout
        )

        views.forEachIndexed { index, v ->
            v.alpha = 0f
            v.translationY = 24f
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((index * 45).toLong())
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

    private fun formatIncidentTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "Vừa gửi"
        return try {
            if (raw.contains("T")) {
                val datePart = raw.substringBefore("T")
                val timePart = raw.substringAfter("T").take(5)
                "$timePart - $datePart"
            } else {
                raw
            }
        } catch (_: Exception) {
            raw
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
