package com.example.app_smart_waste.ui.jobs

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.app_smart_waste.R
import com.example.app_smart_waste.databinding.ActivityJobDetailBinding

class JobDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJobDetailBinding
    private val viewModel: JobsViewModel by viewModels()
    private var jobId: String = "JOB_1723801234"
    private var jobStatus: String = "ASSIGNED"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJobDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jobId = intent.getStringExtra("JOB_ID") ?: "JOB_1723801234"
        jobStatus = intent.getStringExtra("JOB_STATUS") ?: "ASSIGNED"
        val code = if (jobId.startsWith("JOB_") || jobId.startsWith("#")) jobId else "#JOB_$jobId"

        // 1. Shared Unified Header
        binding.detailAppHeader.configure(
            title = "Chi tiết nhiệm vụ",
            subtitle = code,
            navIconRes = R.drawable.ic_arrow_back,
            onNavClick = { finish() }
        )

        bindHeroSummary(code)
        populateBinsList()
        setupListeners()
        playEntranceAnimation()
    }

    private fun bindHeroSummary(code: String) {
        binding.tvJobDetailCode.text = code

        when (jobStatus) {
            "ASSIGNED", "PENDING" -> {
                binding.tvJobDetailStatusPill.text = "Mới được giao"
                binding.tvJobDetailStatusPill.setTextColor(Color.parseColor("#D97706"))
                binding.tvJobDetailStatusPill.setBackgroundResource(R.drawable.bg_badge_pill_yellow)
                binding.btnDetailAcceptOrStartJob.text = "✓ Nhận & Bắt đầu ca"
                binding.btnDetailRejectJob.visibility = View.VISIBLE
            }
            "ACCEPTED" -> {
                binding.tvJobDetailStatusPill.text = "Đã nhận ca"
                binding.tvJobDetailStatusPill.setTextColor(Color.parseColor("#15803D"))
                binding.tvJobDetailStatusPill.setBackgroundResource(R.drawable.bg_role_badge_pill)
                binding.btnDetailAcceptOrStartJob.text = "▶ Bắt đầu ca làm"
                binding.btnDetailRejectJob.visibility = View.GONE
            }
            else -> {
                binding.tvJobDetailStatusPill.text = "Đang thực hiện"
                binding.tvJobDetailStatusPill.setTextColor(Color.parseColor("#15803D"))
                binding.tvJobDetailStatusPill.setBackgroundResource(R.drawable.bg_role_badge_pill)
                binding.btnDetailAcceptOrStartJob.text = "▶ Tiếp tục ca làm"
                binding.btnDetailRejectJob.visibility = View.GONE
            }
        }
    }

    private fun populateBinsList() {
        val container = binding.llBinsDetailContainer
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        data class BinDetailInfo(
            val id: String,
            val name: String,
            val address: String,
            val coords: String,
            val fillLevel: Int,
            val lidStatus: String,
            val lastSeen: String
        )

        val bins = listOf(
            BinDetailInfo(
                id = "BIN_HCM_04",
                name = "Thùng rác Landmark 81",
                address = "Công viên Central Park, Vinhomes Central Park, Bình Thạnh",
                coords = "10.7950, 106.7219",
                fillLevel = 88,
                lidStatus = "🔓 Đang mở",
                lastSeen = "22:38:53"
            ),
            BinDetailInfo(
                id = "BIN_HCM_01",
                name = "Thùng rác Chợ Bến Thành",
                address = "Cổng Tây Chợ Bến Thành, Đường Phan Chu Trinh, Q1",
                coords = "10.7725, 106.6980",
                fillLevel = 92,
                lidStatus = "🔒 Đã đóng",
                lastSeen = "22:35:10"
            ),
            BinDetailInfo(
                id = "BIN_HCM_07",
                name = "Thùng rác Cột Cờ Thủ Ngữ",
                address = "Vườn hoa Bến Bạch Đằng, Tôn Đức Thắng, Q1",
                coords = "10.7712, 106.7061",
                fillLevel = 78,
                lidStatus = "🔓 Đang mở",
                lastSeen = "22:37:40"
            )
        )

        bins.forEach { bin ->
            val v = inflater.inflate(R.layout.item_bin_detail_preview, container, false)
            v.findViewById<TextView>(R.id.tvBinPreviewId).text = bin.id
            v.findViewById<TextView>(R.id.tvBinPreviewName).text = bin.name
            v.findViewById<TextView>(R.id.tvBinPreviewAddress).text = bin.address
            v.findViewById<TextView>(R.id.tvBinPreviewCoords).text = "Tọa độ: ${bin.coords}"
            v.findViewById<TextView>(R.id.tvBinPreviewLevelBadge).text = "${bin.fillLevel}% Đầy"
            v.findViewById<TextView>(R.id.tvBinPreviewLidStatus).text = bin.lidStatus
            v.findViewById<TextView>(R.id.tvBinPreviewLastSeen).text = "⏱ ${bin.lastSeen}"
            container.addView(v)
        }
    }

    private fun setupListeners() {
        binding.btnDetailRejectJob.setOnClickListener {
            it.applyPressEffect {
                AlertDialog.Builder(this)
                    .setTitle("Từ chối nhiệm vụ")
                    .setMessage("Bạn có chắc chắn muốn từ chối ca $jobId?")
                    .setPositiveButton("Từ chối") { _, _ ->
                        viewModel.rejectJob(jobId)
                        Toast.makeText(this, "Đã từ chối nhiệm vụ.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .setNegativeButton("Hủy", null)
                    .show()
            }
        }

        binding.btnDetailAcceptOrStartJob.setOnClickListener {
            it.applyPressEffect {
                viewModel.acceptJob(jobId)
                val intent = Intent(this, JobExecutionActivity::class.java).apply {
                    putExtra("JOB_ID", jobId)
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun playEntranceAnimation() {
        val views = listOf(
            binding.detailAppHeader,
            binding.llBinsDetailContainer,
            binding.layoutDetailBottomActions
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

    private fun View.applyPressEffect(onEnd: () -> Unit) {
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
}
