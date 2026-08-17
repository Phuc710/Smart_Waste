package com.example.app_smart_waste.ui.bin

import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.core.model.UiState
import com.example.app_smart_waste.databinding.ActivityBinDetailBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class BinDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBinDetailBinding
    private val viewModel: BinDetailViewModel by viewModels()

    private var binId: String? = null
    private var jobId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBinDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply Top and Bottom insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.binDetailHeaderBar) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(
                view.paddingLeft,
                statusBarInset + (12 * resources.displayMetrics.density).toInt(),
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutBinBottomActions) { view, insets ->
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                navBarInset + (12 * resources.displayMetrics.density).toInt()
            )
            insets
        }

        binId = intent.getStringExtra("BIN_ID")
        jobId = intent.getStringExtra("JOB_ID")

        setupListeners()
        observeViewModel()

        binId?.let { viewModel.loadBinDetail(it) }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnConfirmCollect = binding.root.findViewById(R.id.btnConfirmCollect)
        binding.btnConfirmCollect.setOnClickListener {
            val bId = binId ?: return@setOnClickListener
            val currentJobId = jobId ?: "ACTIVE_JOB"
            viewModel.collectBin(currentJobId, bId)
        }

        binding.btnReportIncident.setOnClickListener {
            showReportIncidentDialog()
        }
    }

    private fun showReportIncidentDialog() {
        val input = EditText(this).apply {
            hint = "Mô tả sự cố (VD: kẹt nắp, hỏng cảm biến)"
        }
        AlertDialog.Builder(this)
            .setTitle("Báo cáo sự cố")
            .setView(input)
            .setPositiveButton("Gửi báo cáo") { _, _ ->
                val desc = input.text.toString().trim()
                if (desc.isNotBlank()) {
                    binId?.let { viewModel.reportIncident(it, desc) }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.binDetailState.collectLatest { state ->
                when (state) {
                    is UiState.Success -> bindBinData(state.data)
                    is UiState.Error -> {
                        Toast.makeText(this@BinDetailActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }

        lifecycleScope.launch {
            viewModel.actionState.collectLatest { state ->
                when (state) {
                    is UiState.Success -> {
                        Toast.makeText(this@BinDetailActivity, state.data, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    is UiState.Error -> {
                        Toast.makeText(this@BinDetailActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun bindBinData(bin: SmartBinDto) {
        binding.tvBinDetailHeader.text = "Thùng rác #${bin.deviceId}"
        binding.tvBinName.text = bin.name ?: "Thùng rác ${bin.deviceId}"
        val level = (bin.levelPercent ?: 0.0).roundToInt()
        binding.tvBinLevelBig.text = "$level%"
        binding.pbBinLevel.progress = level
        binding.tvBinLocation.text = bin.location ?: "Chưa có thông tin vị trí"

        if (level >= 85) {
            binding.tvBinLevelBig.setTextColor(ContextCompat.getColor(this, R.color.status_danger_main))
            binding.tvBinStatusLabel.text = "QUÁ TẢI KHẨN CẤP"
            binding.tvBinStatusLabel.setBackgroundResource(R.drawable.badge_danger)
            binding.tvBinStatusLabel.setTextColor(ContextCompat.getColor(this, R.color.status_danger_text))
        } else if (level >= 70) {
            binding.tvBinLevelBig.setTextColor(ContextCompat.getColor(this, R.color.status_warning_main))
            binding.tvBinStatusLabel.text = "SẮP ĐẦY"
            binding.tvBinStatusLabel.setBackgroundResource(R.drawable.badge_warning)
            binding.tvBinStatusLabel.setTextColor(ContextCompat.getColor(this, R.color.status_warning_text))
        } else {
            binding.tvBinLevelBig.setTextColor(ContextCompat.getColor(this, R.color.primary_600))
            binding.tvBinStatusLabel.text = "BÌNH THƯỜNG"
            binding.tvBinStatusLabel.setBackgroundResource(R.drawable.badge_success)
            binding.tvBinStatusLabel.setTextColor(ContextCompat.getColor(this, R.color.status_success_text))
        }
    }
}
