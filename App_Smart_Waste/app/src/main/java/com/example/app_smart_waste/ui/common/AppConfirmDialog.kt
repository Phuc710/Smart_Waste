package com.example.app_smart_waste.ui.common

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.example.app_smart_waste.R
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Unified Confirmation Modal for all destructive & alert actions
 * (Xóa, Hủy bỏ, Đăng xuất, Bỏ qua sự cố).
 */
object AppConfirmDialog {

    fun showDanger(
        context: Context,
        title: String,
        message: String,
        actionButtonText: String = "Xóa",
        cancelButtonText: String = "Quay lại",
        detailPillText: String? = null,
        iconRes: Int = R.drawable.ic_trash_bin_red,
        onConfirm: () -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_unified_confirm, null)
        dialog.setContentView(view)

        val imgIcon = view.findViewById<ImageView>(R.id.imgConfirmIcon)
        val tvTitle = view.findViewById<TextView>(R.id.tvConfirmTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvConfirmMessage)
        val tvDetailPill = view.findViewById<TextView>(R.id.tvConfirmDetailPill)
        val btnCancel = view.findViewById<Button>(R.id.btnConfirmCancel)
        val btnAction = view.findViewById<Button>(R.id.btnConfirmAction)

        imgIcon?.setImageResource(iconRes)
        tvTitle?.text = title
        tvMessage?.text = message

        if (!detailPillText.isNullOrBlank()) {
            tvDetailPill?.text = detailPillText
            tvDetailPill?.visibility = View.VISIBLE
        } else {
            tvDetailPill?.visibility = View.GONE
        }

        btnCancel?.text = cancelButtonText
        btnAction?.text = actionButtonText

        btnCancel?.setOnClickListener {
            dialog.dismiss()
        }

        btnAction?.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }

        dialog.show()
    }

    fun showCancelJobWithReason(
        context: Context,
        jobId: String,
        onConfirm: (reason: String) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_cancel_job_with_reason, null)
        dialog.setContentView(view)

        val tvJobIdPill = view.findViewById<TextView>(R.id.tvCancelJobIdPill)
        val rgReasons = view.findViewById<RadioGroup>(R.id.rgCancelReasons)
        val edtCustomReason = view.findViewById<EditText>(R.id.edtCustomReason)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelBack)
        val btnConfirm = view.findViewById<Button>(R.id.btnConfirmCancelJob)

        val code = if (jobId.startsWith("JOB_") || jobId.startsWith("#")) jobId else "#JOB_$jobId"
        tvJobIdPill?.text = "Mã ca: $code"

        rgReasons?.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbReasonOther) {
                edtCustomReason?.visibility = View.VISIBLE
                edtCustomReason?.requestFocus()
            } else {
                edtCustomReason?.visibility = View.GONE
            }
        }

        btnCancel?.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm?.setOnClickListener {
            val selectedId = rgReasons?.checkedRadioButtonId ?: R.id.rbReasonVehicle
            val reason = when (selectedId) {
                R.id.rbReasonVehicle -> "Phương tiện gặp sự cố kỹ thuật / hỏng xe"
                R.id.rbReasonTraffic -> "Kẹt xe nghiêm trọng không thể tiếp cận"
                R.id.rbReasonWeather -> "Thời tiết xấu / ngập lụt tuyến đường"
                R.id.rbReasonRoadBlock -> "Đường bị rào chắn / cấm xe tải vào"
                R.id.rbReasonOther -> {
                    val custom = edtCustomReason?.text?.toString()?.trim()
                    if (!custom.isNullOrBlank()) custom else "Lý do khác"
                }
                else -> "Tài xế yêu cầu hủy"
            }
            dialog.dismiss()
            onConfirm(reason)
        }

        dialog.show()
    }
}
