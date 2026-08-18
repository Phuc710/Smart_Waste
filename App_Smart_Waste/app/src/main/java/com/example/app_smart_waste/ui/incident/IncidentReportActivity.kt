package com.example.app_smart_waste.ui.incident

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.SmartBinDto
import com.example.app_smart_waste.data.repository.BinsRepository
import com.example.app_smart_waste.data.repository.IncidentRepository
import com.example.app_smart_waste.databinding.ActivityIncidentReportBinding
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class IncidentReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncidentReportBinding
    private val incidentRepo by lazy { IncidentRepository(this) }
    private val binsRepo by lazy { BinsRepository(this) }

    private var preselectedBinId: String? = null
    private var availableBins: List<SmartBinDto> = emptyList()
    private var incidentPhoto: Bitmap? = null

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            incidentPhoto = bitmap
            binding.ivIncidentPhoto.setImageBitmap(bitmap)
            binding.layoutPhotoPreview.visibility = View.VISIBLE
            binding.btnCaptureIncident.text = "Chụp lại ảnh khác"
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null)
        else Toast.makeText(this, "Cần quyền camera để chụp ảnh hiện trường.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncidentReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preselectedBinId = intent.getStringExtra("BIN_ID")?.trim()?.takeIf { it.isNotEmpty() }

        binding.reportAppHeader.configure(
            title = "Báo cáo sự cố",
            subtitle = "Gửi thông tin về trung tâm điều phối",
            navIconRes = R.drawable.ic_arrow_back,
            onNavClick = { finish() }
        )

        setupListeners()
        loadBins()
    }

    private fun setupListeners() {
        binding.btnCaptureIncident.setOnClickListener { requestCamera() }
        binding.btnRemovePhoto.setOnClickListener {
            incidentPhoto = null
            binding.layoutPhotoPreview.visibility = View.GONE
            binding.btnCaptureIncident.text = "Chụp ảnh hiện trường"
        }
        binding.btnSubmitIncident.setOnClickListener { submitIncident() }
    }

    private fun loadBins() {
        lifecycleScope.launch {
            val result = binsRepo.getBins()
            availableBins = result.getOrDefault(emptyList())

            if (availableBins.isNotEmpty()) {
                val binLabels = availableBins.map { bin ->
                    val name = bin.name ?: bin.deviceId
                    val location = bin.location?.takeIf { it.isNotBlank() }
                    if (location != null) "$name ($location)" else name
                }
                val adapter = ArrayAdapter(this@IncidentReportActivity, android.R.layout.simple_spinner_dropdown_item, binLabels)
                binding.spIncidentBinPicker.adapter = adapter

                // If preselected, select that bin
                val targetId = preselectedBinId
                if (targetId != null) {
                    val index = availableBins.indexOfFirst { it.deviceId == targetId }
                    if (index >= 0) {
                        binding.spIncidentBinPicker.setSelection(index)
                        binding.spIncidentBinPicker.isEnabled = false
                    }
                }
            } else {
                val fallback = listOf(preselectedBinId ?: "BIN_HCM_01")
                val adapter = ArrayAdapter(this@IncidentReportActivity, android.R.layout.simple_spinner_dropdown_item, fallback)
                binding.spIncidentBinPicker.adapter = adapter
            }
        }
    }

    private fun requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun submitIncident() {
        val selectedIndex = binding.spIncidentBinPicker.selectedItemPosition
        val targetBinId = if (selectedIndex in availableBins.indices) {
            availableBins[selectedIndex].deviceId
        } else {
            preselectedBinId ?: "BIN_HCM_01"
        }

        val description = binding.etIncidentDesc.text.toString().trim()
        if (description.isBlank()) {
            Toast.makeText(this, "Vui lòng nhập mô tả sự cố.", Toast.LENGTH_SHORT).show()
            binding.etIncidentDesc.requestFocus()
            return
        }

        val issueType = when (binding.rgIncidentType.checkedRadioButtonId) {
            R.id.rbOverflow -> "Rác quá đầy tràn ra ngoài"
            R.id.rbLidBroken -> "Nắp thùng bị hỏng / kẹt cơ học"
            R.id.rbSensorFault -> "Cảm biến báo sai mức rác"
            R.id.rbBlocked -> "Đường bị chặn / Không thể tiếp cận"
            else -> "Sự cố khác"
        }

        binding.btnSubmitIncident.isEnabled = false
        binding.btnSubmitIncident.text = "Đang gửi báo cáo..."

        lifecycleScope.launch {
            val photo = incidentPhoto
            val result = if (photo == null) {
                incidentRepo.reportIncident(targetBinId, issueType, description)
            } else {
                incidentRepo.reportIncidentWithPhoto(targetBinId, issueType, description, photo.toJpegBytes())
            }

            if (result.isSuccess) {
                Toast.makeText(this@IncidentReportActivity, "✅ Đã gửi báo cáo sự cố thành công!", Toast.LENGTH_LONG).show()
                finish()
            } else {
                binding.btnSubmitIncident.isEnabled = true
                binding.btnSubmitIncident.text = "Gửi Báo Cáo Sự Cố"
                Toast.makeText(
                    this@IncidentReportActivity,
                    result.exceptionOrNull()?.message ?: "Gửi báo cáo thất bại.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun Bitmap.toJpegBytes(): ByteArray = ByteArrayOutputStream().use { output ->
        compress(Bitmap.CompressFormat.JPEG, 90, output)
        output.toByteArray()
    }
}
