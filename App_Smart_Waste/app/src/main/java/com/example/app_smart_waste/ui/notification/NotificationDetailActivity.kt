package com.example.app_smart_waste.ui.notification

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.NotificationCategoryType
import com.example.app_smart_waste.core.model.NotificationModel
import com.example.app_smart_waste.core.utils.applyNavigationBarBottomPadding
import com.example.app_smart_waste.core.utils.applyPressEffect
import com.example.app_smart_waste.databinding.ActivityNotificationDetailBinding
import com.example.app_smart_waste.ui.main.MainActivity

/**
 * Trang: Chi Tiết Thông Báo (Notification Detail Screen)
 * Displays deep metadata, rich content explanation, map preview, and contextual CTA button.
 */
class NotificationDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTIFICATION = "EXTRA_NOTIFICATION"
    }

    private lateinit var binding: ActivityNotificationDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.layoutNotifDetailRoot.applyNavigationBarBottomPadding()

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_NOTIFICATION, NotificationModel::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_NOTIFICATION) as? NotificationModel
        }

        if (notification == null) {
            finish()
            return
        }

        setupHeader()
        bindData(notification)
        setupActions(notification)
    }

    private fun setupHeader() {
        binding.appHeader.configure(
            title = "Chi tiết thông báo",
            navIconRes = R.drawable.ic_arrow_back,
            onNavClick = { finish() }
        )
    }

    private fun bindData(notif: NotificationModel) {
        // 1. Header Card (Icon + Title + Time)
        binding.containerDetailIcon.setBackgroundResource(notif.iconBgRes)
        binding.ivDetailIcon.setImageResource(notif.iconRes)
        binding.tvDetailTitle.text = notif.title
        binding.tvDetailTimestamp.text = notif.fullDateStr

        // 2. Content Body
        binding.tvDetailContent.text = notif.content

        // 3. Metadata Table
        when (notif.type) {
            NotificationCategoryType.BIN_OVERFULL -> {
                binding.tvDetailMetaHeader.text = "Thông tin thùng rác"
                binding.tvMetaLabel1.text = "Mã thùng:"
                binding.tvMetaValue1.text = notif.binId ?: "BIN-015"

                binding.tvMetaLabel2.text = "Vị trí:"
                binding.tvMetaValue2.text = notif.location ?: "Bến Bạch Đằng, Quận 1"

                binding.tvMetaLabel3.text = "Mức đầy:"
                val percent = notif.fillPercent ?: 90
                val liters = notif.capacityLiters ?: 240
                binding.tvMetaValue3.text = "$percent% • $liters L"
                binding.tvMetaValue3.setTextColor(Color.parseColor("#EF4444")) // Red alert

                binding.tvMetaLabel4.text = "Cập nhật lúc:"
                binding.tvMetaValue4.text = notif.fullDateStr

                // Action Button Setup
                binding.ivDetailBtnIcon.setImageResource(R.drawable.ic_location_pin)
                binding.tvDetailBtnText.text = "Mở bản đồ"
                binding.cardMapPreviewContainer.visibility = View.VISIBLE
            }
            NotificationCategoryType.JOB_ASSIGNED -> {
                binding.tvDetailMetaHeader.text = "Thông tin nhiệm vụ"
                binding.tvMetaLabel1.text = "Mã nhiệm vụ:"
                binding.tvMetaValue1.text = notif.jobId ?: "#JOB-0258"

                binding.tvMetaLabel2.text = "Khu vực:"
                binding.tvMetaValue2.text = notif.location ?: "Quận 1, TP.HCM"

                binding.tvMetaLabel3.text = "Số điểm gom:"
                binding.tvMetaValue3.text = "${notif.totalBins ?: 3} điểm thùng rác"
                binding.tvMetaValue3.setTextColor(Color.parseColor("#16A34A")) // Green

                binding.tvMetaLabel4.text = "Thời gian giao:"
                binding.tvMetaValue4.text = notif.fullDateStr

                // Action Button Setup
                binding.ivDetailBtnIcon.setImageResource(R.drawable.ic_banner_clipboard_green)
                binding.tvDetailBtnText.text = "Xem nhiệm vụ"
                binding.cardMapPreviewContainer.visibility = View.VISIBLE
            }
            NotificationCategoryType.JOB_CANCELLED -> {
                binding.tvDetailMetaHeader.text = "Thông tin hủy ca"
                binding.tvMetaLabel1.text = "Mã nhiệm vụ:"
                binding.tvMetaValue1.text = notif.jobId ?: "#JOB-0245"

                binding.tvMetaLabel2.text = "Lý do hủy:"
                binding.tvMetaValue2.text = notif.reason ?: "Điều phối lại tuyến cho tài xế khác."

                binding.tvMetaLabel3.text = "Trạng thái:"
                binding.tvMetaValue3.text = "Đã bị hủy"
                binding.tvMetaValue3.setTextColor(Color.parseColor("#F59E0B")) // Amber

                binding.tvMetaLabel4.text = "Thời gian hủy:"
                binding.tvMetaValue4.text = notif.fullDateStr

                // Action Button Setup
                binding.ivDetailBtnIcon.setImageResource(R.drawable.ic_banner_clipboard_green)
                binding.tvDetailBtnText.text = "Xem danh sách việc"
                binding.cardMapPreviewContainer.visibility = View.GONE
            }
            NotificationCategoryType.BROADCAST_INCIDENT -> {
                binding.tvDetailMetaHeader.text = "Thông tin sự cố & Thông báo"
                binding.tvMetaLabel1.text = "Khu vực:"
                binding.tvMetaValue1.text = notif.location ?: "Toàn thành phố"

                binding.tvMetaLabel2.text = "Mức độ:"
                binding.tvMetaValue2.text = "Cảnh báo an toàn"

                binding.tvMetaLabel3.text = "Đơn vị phát:"
                binding.tvMetaValue3.text = "Ban điều hành Smart Waste"
                binding.tvMetaValue3.setTextColor(Color.parseColor("#2563EB")) // Blue

                binding.tvMetaLabel4.text = "Thời gian phát:"
                binding.tvMetaValue4.text = notif.fullDateStr

                // Action Button Setup
                binding.ivDetailBtnIcon.setImageResource(R.drawable.ic_check)
                binding.tvDetailBtnText.text = "Đã hiểu"
                binding.cardMapPreviewContainer.visibility = View.GONE
            }
            NotificationCategoryType.JOB_COMPLETED -> {
                binding.tvDetailMetaHeader.text = "Thông tin hoàn thành"
                binding.tvMetaLabel1.text = "Mã nhiệm vụ:"
                binding.tvMetaValue1.text = notif.jobId ?: "#JOB-0238"

                binding.tvMetaLabel2.text = "Khu vực:"
                binding.tvMetaValue2.text = notif.location ?: "Quận 7, TP.HCM"

                binding.tvMetaLabel3.text = "Kết quả:"
                binding.tvMetaValue3.text = "Hoàn thành 100%"
                binding.tvMetaValue3.setTextColor(Color.parseColor("#16A34A")) // Green

                binding.tvMetaLabel4.text = "Thời gian:"
                binding.tvMetaValue4.text = notif.fullDateStr

                // Action Button Setup
                binding.ivDetailBtnIcon.setImageResource(R.drawable.ic_banner_clipboard_green)
                binding.tvDetailBtnText.text = "Xem lịch sử ca"
                binding.cardMapPreviewContainer.visibility = View.GONE
            }
        }
    }

    private fun setupActions(notif: NotificationModel) {
        // Action Button Click
        binding.btnDetailAction.setOnClickListener {
            it.applyPressEffect {
                when (notif.type) {
                    NotificationCategoryType.BIN_OVERFULL -> {
                        // Open Map Tab and highlight bin
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("EXTRA_OPEN_TAB", "MAP")
                            putExtra("EXTRA_BIN_ID", notif.binId)
                        }
                        startActivity(intent)
                        finish()
                    }
                    NotificationCategoryType.JOB_ASSIGNED -> {
                        // Open Jobs Tab
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("EXTRA_OPEN_TAB", "JOBS")
                            putExtra("EXTRA_JOB_ID", notif.jobId)
                        }
                        startActivity(intent)
                        finish()
                    }
                    NotificationCategoryType.JOB_CANCELLED -> {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("EXTRA_OPEN_TAB", "JOBS")
                        }
                        startActivity(intent)
                        finish()
                    }
                    NotificationCategoryType.JOB_COMPLETED -> {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("EXTRA_OPEN_TAB", "HISTORY")
                        }
                        startActivity(intent)
                        finish()
                    }
                    NotificationCategoryType.BROADCAST_INCIDENT -> {
                        finish()
                    }
                }
            }
        }

        // Map Preview Click
        binding.cardMapPreviewContainer.setOnClickListener {
            it.applyPressEffect {
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("EXTRA_OPEN_TAB", "MAP")
                    putExtra("EXTRA_BIN_ID", notif.binId)
                }
                startActivity(intent)
                finish()
            }
        }
    }
}
