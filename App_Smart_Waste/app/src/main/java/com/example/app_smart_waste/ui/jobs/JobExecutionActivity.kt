package com.example.app_smart_waste.ui.jobs

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.app_smart_waste.R
import com.example.app_smart_waste.databinding.ActivityJobExecutionBinding
import com.example.app_smart_waste.ui.incident.IncidentReportActivity
import com.example.app_smart_waste.ui.route.RouteDetailActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class JobExecutionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJobExecutionBinding
    private val viewModel: JobsViewModel by viewModels()
    private var jobId: String = "JOB_1723801234"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJobExecutionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jobId = intent.getStringExtra("JOB_ID") ?: "JOB_1723801234"
        val code = if (jobId.startsWith("JOB_") || jobId.startsWith("#")) jobId else "#JOB_$jobId"

        // 1. Shared Unified Header
        binding.execAppHeader.configure(
            title = "Thực hiện ca thu gom",
            subtitle = code,
            navIconRes = R.drawable.ic_arrow_back,
            onNavClick = { finish() }
        )

        setupListeners()
        observeViewModel()
        playEntranceAnimation()
    }

    private fun setupListeners() {
        // Pause / Resume Job Pill
        binding.btnExecPauseJob.setOnClickListener {
            it.applyPressEffect {
                if (viewModel.isPaused.value) {
                    viewModel.togglePauseActiveJob()
                    Toast.makeText(this, "▶ Đã tiếp tục ca làm!", Toast.LENGTH_SHORT).show()
                } else {
                    showPauseReasonDialog()
                }
            }
        }

        // Navigate to Current Bin (BIN_HCM_04)
        binding.btnExecNavigate.setOnClickListener {
            it.applyPressEffect {
                val gmmIntentUri = Uri.parse("google.navigation:q=10.7735,106.7035&mode=d")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                try {
                    startActivity(mapIntent)
                } catch (_: Exception) {
                    val intent = Intent(this, RouteDetailActivity::class.java).apply {
                        putExtra("JOB_ID", jobId)
                    }
                    startActivity(intent)
                }
            }
        }

        // Remote IoT Lid Unlock
        binding.btnExecUnlockLid.setOnClickListener {
            it.applyPressEffect {
                Toast.makeText(this, "📶 Đã gửi tín hiệu mở nắp thùng BIN_HCM_04 từ xa!", Toast.LENGTH_LONG).show()
            }
        }

        // Confirm Bin Collection
        binding.btnExecConfirmCollect.setOnClickListener {
            it.applyPressEffect {
                lifecycleScope.launch {
                    binding.btnExecConfirmCollect.isEnabled = false
                    binding.btnExecConfirmCollect.text = "Đang đồng bộ..."
                    val success = viewModel.confirmCollectCurrentBin()
                    binding.btnExecConfirmCollect.isEnabled = true
                    binding.btnExecConfirmCollect.text = "✓ Đã thu gom hoàn tất"
                    if (success) {
                        binding.tvExecStopsCount.text = "3 / 3 điểm"
                        binding.tvExecPercent.text = "100%"
                        binding.pbExecProgress.progress = 100
                        binding.tvExecSummaryText.text = "Đã hoàn thành 3/3 điểm thu gom"
                        binding.tvExecRemainingKm.text = "0.0 km (Hoàn tất)"

                        showCompletedDialog()
                    }
                }
            }
        }

        // Report Incident
        binding.btnExecReportIncident.setOnClickListener {
            it.applyPressEffect {
                val intent = Intent(this, IncidentReportActivity::class.java).apply {
                    putExtra("BIN_ID", "BIN_HCM_04")
                }
                startActivity(intent)
            }
        }

        // Next Stop Info
        binding.itemExecNextStop.setOnClickListener {
            it.applyPressEffect {
                Toast.makeText(this, "Điểm tiếp theo: BIN_HCM_07 (Cột cờ Thủ Ngữ, Q1)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPauseReasonDialog() {
        val input = EditText(this).apply {
            hint = "Nhập lý do (ví dụ: Kẹt xe, sự cố xe...)"
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Tạm dừng ca làm")
            .setMessage("Vui lòng nhập lý do tạm dừng để tổng đài ghi nhận:")
            .setView(input)
            .setPositiveButton("Tạm dừng") { _, _ ->
                val reason = input.text.toString().trim().ifBlank { "Tài xế tạm dừng" }
                viewModel.togglePauseActiveJob()
                Toast.makeText(this, "⏸ Đã tạm dừng ca làm ($reason)", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showCompletedDialog() {
        AlertDialog.Builder(this)
            .setTitle("🎉 Hoàn thành ca thu gom!")
            .setMessage("Chúc mừng bạn đã thu gom xong toàn bộ 3 điểm của ca $jobId. Dữ liệu đã được lưu vào Lịch sử.")
            .setPositiveButton("Xem lịch sử ca") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isPaused.collectLatest { paused ->
                    if (paused) {
                        binding.tvExecPauseLabel.text = "Tiếp tục"
                        binding.tvExecPauseLabel.setTextColor(Color.parseColor("#15803D"))
                        binding.ivExecPauseIcon.setImageResource(R.drawable.ic_play_orange)
                    } else {
                        binding.tvExecPauseLabel.text = "Tạm dừng ca"
                        binding.tvExecPauseLabel.setTextColor(Color.parseColor("#0F172A"))
                        binding.ivExecPauseIcon.setImageResource(R.drawable.ic_pause_orange)
                    }
                }
            }
        }
    }

    private fun playEntranceAnimation() {
        val views = listOf(
            binding.execAppHeader,
            binding.cardExecProgress,
            binding.cardExecCurrentBin,
            binding.cardExecUpcoming
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
