package com.example.app_smart_waste.ui.incident

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.app_smart_waste.R
import com.example.app_smart_waste.data.repository.IncidentRepository
import com.example.app_smart_waste.databinding.ActivityIncidentReportBinding
import kotlinx.coroutines.launch

class IncidentReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncidentReportBinding
    private val incidentRepo by lazy { IncidentRepository(this) }
    private var binId = "BIN_HCM_02"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncidentReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binId = intent.getStringExtra("BIN_ID") ?: "BIN_HCM_02"

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSubmitIncident.setOnClickListener {
            val desc = binding.etIncidentDesc.text.toString().trim()
            if (desc.isBlank()) {
                Toast.makeText(this, "Vui lòng nhập mô tả sự cố.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val issueType = when (binding.rgIncidentType.checkedRadioButtonId) {
                R.id.rbSensorFault -> "SENSOR_FAULT"
                R.id.rbOverflow -> "OVERFLOW"
                R.id.rbBlocked -> "BLOCKED"
                else -> "LID_BROKEN"
            }

            binding.btnSubmitIncident.isEnabled = false

            lifecycleScope.launch {
                val result = incidentRepo.reportIncident(binId, issueType, desc)
                if (result.isSuccess) {
                    Toast.makeText(this@IncidentReportActivity, "Đã gửi báo cáo sự cố thành công!", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    binding.btnSubmitIncident.isEnabled = true
                    Toast.makeText(this@IncidentReportActivity, "Gửi báo cáo thất bại, vui lòng thử lại.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
