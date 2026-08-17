package com.example.app_smart_waste.ui.profile

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
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
import com.example.app_smart_waste.ui.incident.IncidentReportActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

        // 3. Change Avatar Button
        binding.btnChangeAvatar.setOnClickListener {
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
                showIncidentHistoryBottomSheet()
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

    private fun showIncidentHistoryBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_incident_history, null)
        dialog.setContentView(view)

        val incidents = viewModel.incidentsState.value
        val container = view.findViewById<LinearLayout>(R.id.llIncidentsListContainer)
        val tvTotal = view.findViewById<TextView>(R.id.tvIncidentTotalCount)

        tvTotal?.text = "Tổng số: ${incidents.size} sự cố đã gửi"
        container?.removeAllViews()

        if (incidents.isEmpty()) {
            val emptyTv = TextView(requireContext()).apply {
                text = "Bạn chưa gửi báo cáo sự cố nào."
                setTextColor(Color.parseColor("#64748B"))
                textSize = 14f
                setPadding(0, 40, 0, 40)
                gravity = android.view.Gravity.CENTER
            }
            container?.addView(emptyTv)
        } else {
            incidents.forEach { item ->
                val cardView = layoutInflater.inflate(R.layout.item_incident_card, container, false)

                cardView.findViewById<TextView>(R.id.tvIncidentBinName)?.text = item.binName ?: "Thùng #${item.deviceId}"
                cardView.findViewById<TextView>(R.id.tvIncidentReason)?.text = item.reason
                cardView.findViewById<TextView>(R.id.tvIncidentDescription)?.text = if (!item.description.isNullOrBlank()) item.description else "Không có mô tả bổ sung"
                cardView.findViewById<TextView>(R.id.tvIncidentLocation)?.text = if (!item.binLocation.isNullOrBlank()) "📍 ${item.binLocation}" else "📍 Tuyến thu gom Quận 1"
                val formattedTime = formatIncidentTime(item.createdAt)
                cardView.findViewById<TextView>(R.id.tvIncidentTime)?.text = formattedTime

                // 3 Status Badges: WAITING (Vàng) | IN_REVIEW (Cam) | RESOLVED (Xanh lá)
                val tvStatus = cardView.findViewById<TextView>(R.id.tvIncidentStatusBadge)
                when (item.status.uppercase()) {
                    "RESOLVED", "DONE" -> {
                        tvStatus?.text = "ĐÃ XỬ LÝ XONG"
                        tvStatus?.setTextColor(Color.parseColor("#15803D"))
                        tvStatus?.setBackgroundColor(Color.parseColor("#DCFCE7"))
                    }
                    "IN_REVIEW", "REVIEWING" -> {
                        tvStatus?.text = "ĐANG KIỂM TRA"
                        tvStatus?.setTextColor(Color.parseColor("#C2410C"))
                        tvStatus?.setBackgroundColor(Color.parseColor("#FFEDD5"))
                    }
                    else -> { // "WAITING" / "NEW"
                        tvStatus?.text = "CHỜ TIẾP NHẬN"
                        tvStatus?.setTextColor(Color.parseColor("#B45309"))
                        tvStatus?.setBackgroundColor(Color.parseColor("#FEF3C7"))
                    }
                }

                val btnPhoto = cardView.findViewById<View>(R.id.btnViewIncidentPhoto)
                if (item.hasPhoto || !item.imageUrl.isNullOrBlank()) {
                    btnPhoto?.visibility = View.VISIBLE
                    btnPhoto?.setOnClickListener {
                        showPhotoPreviewDialog(item.imageUrl, item.binName ?: item.deviceId)
                    }
                } else {
                    btnPhoto?.visibility = View.GONE
                }

                container?.addView(cardView)
            }
        }

        view.findViewById<ImageView>(R.id.btnCloseIncidents)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnDoneIncidents)?.setOnClickListener { dialog.dismiss() }

        // Open + Báo Cáo Sự Cố Mới BottomSheet
        view.findViewById<Button>(R.id.btnNewIncidentReport)?.setOnClickListener {
            dialog.dismiss()
            showCreateIncidentBottomSheet()
        }

        dialog.show()
    }

    private fun showCreateIncidentBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_create_incident, null)
        dialog.setContentView(view)

        val bins = viewModel.binsState.value
        val spBins = view.findViewById<Spinner>(R.id.spTargetBin)
        val etDesc = view.findViewById<TextInputEditText>(R.id.etIncidentDescription)
        val tvCounter = view.findViewById<TextView>(R.id.tvDescCharCounter)
        val rgReason = view.findViewById<RadioGroup>(R.id.rgIncidentReason)
        val btnCapture = view.findViewById<Button>(R.id.btnCapturePhoto)
        val ivPhoto = view.findViewById<ImageView>(R.id.ivIncidentPhotoPreview)
        val tvPhotoStatus = view.findViewById<TextView>(R.id.tvPhotoStatus)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitEmergencyIncident)

        // Setup Bins Spinner with real dynamic data from Backend
        val currentBins = viewModel.binsState.value
        val initialOptions = if (currentBins.isNotEmpty()) {
            currentBins.map { "${it.deviceId} - ${it.name ?: it.location ?: "Thùng rác thông minh"}" }
        } else {
            listOf("Đang tải danh sách thùng rác...")
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, initialOptions)
        spBins?.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.binsState.collectLatest { freshBins ->
                if (freshBins.isNotEmpty()) {
                    val updatedOptions = freshBins.map { "${it.deviceId} - ${it.name ?: it.location ?: "Thùng rác thông minh"}" }
                    spBins?.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, updatedOptions)
                }
            }
        }

        // Character counter
        etDesc?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tvCounter?.text = "${s?.length ?: 0}/500"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Capture Photo Simulation (Camera intent / compression)
        var attachedPhotoUrl: String? = null
        btnCapture?.setOnClickListener {
            attachedPhotoUrl = "https://images.unsplash.com/photo-1532996122724-e3c354a0b15b?auto=format&fit=crop&w=600&q=80"
            ivPhoto?.setImageResource(R.drawable.login_bg)
            tvPhotoStatus?.text = "✓ Đã chụp ảnh (Kích thước: 1.2 MB)"
            tvPhotoStatus?.setTextColor(Color.parseColor("#15803D"))
            Toast.makeText(requireContext(), "Đã chụp và nén ảnh hiện trường (< 5MB)", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<ImageView>(R.id.btnCloseCreateIncident)?.setOnClickListener { dialog.dismiss() }

        btnSubmit?.setOnClickListener {
            val selectedBinText = spBins?.selectedItem?.toString().orEmpty()
            val binId = selectedBinText.substringBefore(" - ").trim()
            if (binId.isBlank() || binId.startsWith("Đang tải")) {
                Toast.makeText(requireContext(), "Vui lòng chọn thùng rác gặp sự cố", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val reason = when (rgReason?.checkedRadioButtonId) {
                R.id.rbHingeBroken -> "Gãy bản lề / Hỏng nắp thùng"
                R.id.rbSensorFault -> "Hỏng cảm biến khoảng cách / Báo sai % rác"
                R.id.rbOverflow -> "Rác tràn đầy miệng thùng"
                R.id.rbOtherReason -> "Sự cố khác..."
                else -> "Nắp kẹt không mở / không đóng được"
            }

            val desc = etDesc?.text?.toString()?.trim().orEmpty()
            if (desc.isBlank()) {
                etDesc?.error = "Vui lòng nhập mô tả chi tiết sự cố"
                return@setOnClickListener
            }

            btnSubmit.isEnabled = false
            btnSubmit.text = "Đang gửi báo cáo khẩn cấp..."

            viewLifecycleOwner.lifecycleScope.launch {
                val res = viewModel.submitIncident(binId, reason, desc, attachedPhotoUrl)
                if (res.isSuccess) {
                    dialog.dismiss()
                    Toast.makeText(requireContext(), "🚨 Đã gửi Báo cáo Sự cố khẩn cấp thành công!", Toast.LENGTH_LONG).show()
                    showIncidentHistoryBottomSheet()
                } else {
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "🚨 Gửi Báo Cáo Khẩn Cấp"
                    Toast.makeText(requireContext(), "Gửi thất bại, vui lòng thử lại.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

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

        val etBaseUrl = view.findViewById<TextInputEditText>(R.id.etServerBaseUrl)
        val switchOverload = view.findViewById<MaterialSwitch>(R.id.switchOverloadAlert)
        val switchGps = view.findViewById<MaterialSwitch>(R.id.switchGpsAuto)
        val tvCache = view.findViewById<TextView>(R.id.tvCacheSize)
        val btnClear = view.findViewById<Button>(R.id.btnClearCache)

        // Load current configurations from AppConfig
        etBaseUrl?.setText(com.example.app_smart_waste.core.storage.AppConfig.getBaseUrl(requireContext()))
        switchOverload?.isChecked = com.example.app_smart_waste.core.storage.AppConfig.isOverloadAlertEnabled(requireContext())
        switchGps?.isChecked = com.example.app_smart_waste.core.storage.AppConfig.isAutoGpsEnabled(requireContext())

        btnClear?.setOnClickListener {
            tvCache?.text = "Đang sử dụng 0 KB (Đã dọn dẹp)"
            Toast.makeText(requireContext(), "Đã giải phóng bộ nhớ đệm", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<ImageView>(R.id.btnCloseSystemSettings)?.setOnClickListener { dialog.dismiss() }
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
            Toast.makeText(requireContext(), "✅ Đã lưu cấu hình & cập nhật IP Server thành công", Toast.LENGTH_LONG).show()
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
        view.findViewById<TextView>(R.id.tvReportVehicle)?.text = "${stats.vehiclePlate} (${stats.vehicleType})"

        view.findViewById<ImageView>(R.id.btnCloseReport)?.setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnDoneReport)?.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showVehicleDetailBottomSheet() {
        val stats = viewModel.statsState.value
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_vehicle_detail, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvVehiclePlateDetail)?.text = stats.vehiclePlate
        view.findViewById<TextView>(R.id.tvVehicleTypeDetail)?.text = stats.vehicleType

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
        val view = layoutInflater.inflate(R.layout.dialog_logout_confirm, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.findViewById<Button>(R.id.btnCancelLogout)?.setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<Button>(R.id.btnConfirmLogout)?.setOnClickListener {
            dialog.dismiss()
            viewModel.logout()
            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            activity?.finish()
        }

        dialog.show()
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
