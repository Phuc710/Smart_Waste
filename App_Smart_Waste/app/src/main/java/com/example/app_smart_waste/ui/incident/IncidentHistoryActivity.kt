package com.example.app_smart_waste.ui.incident

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.model.IncidentReportDto
import com.example.app_smart_waste.data.repository.IncidentRepository
import com.example.app_smart_waste.databinding.ActivityIncidentHistoryBinding
import kotlinx.coroutines.launch

class IncidentHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncidentHistoryBinding
    private val incidentRepo by lazy { IncidentRepository(this) }
    private lateinit var adapter: IncidentAdapter

    private var allIncidents: List<IncidentReportDto> = emptyList()
    private var currentFilter: String = "ALL" // ALL, NEW, IN_REVIEW, RESOLVED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncidentHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Shared Unified AppHeader
        binding.incidentsAppHeader.configure(
            title = "Lịch sử sự cố",
            subtitle = "Danh sách các báo cáo đã gửi",
            navIconRes = R.drawable.ic_arrow_back,
            onNavClick = { finish() }
        )

        setupRecyclerView()
        setupFilterChips()
        setupListeners()
        playEntranceAnimation()

        loadIncidents()
    }

    override fun onResume() {
        super.onResume()
        loadIncidents()
    }

    private fun setupRecyclerView() {
        adapter = IncidentAdapter { incident ->
            showIncidentDetailDialog(incident)
        }

        binding.rvIncidentsList.apply {
            layoutManager = LinearLayoutManager(this@IncidentHistoryActivity)
            adapter = this@IncidentHistoryActivity.adapter
        }

        binding.incidentSwipeRefresh.setColorSchemeResources(R.color.profile_green_primary)
        binding.incidentSwipeRefresh.setOnRefreshListener {
            loadIncidents()
        }
    }

    private fun setupFilterChips() {
        binding.chipIncidentFilterAll.setOnClickListener { selectFilter("ALL") }
        binding.chipIncidentFilterNew.setOnClickListener { selectFilter("NEW") }
        binding.chipIncidentFilterInReview.setOnClickListener { selectFilter("IN_REVIEW") }
        binding.chipIncidentFilterResolved.setOnClickListener { selectFilter("RESOLVED") }
    }

    private fun selectFilter(filter: String) {
        currentFilter = filter
        val white = ContextCompat.getColor(this, R.color.white)
        val black = ContextCompat.getColor(this, R.color.app_text_primary)

        binding.chipIncidentFilterAll.apply {
            val active = filter == "ALL"
            setBackgroundResource(if (active) R.drawable.bg_chip_all_active else R.drawable.bg_chip_filter_inactive)
            setTextColor(if (active) white else black)
            typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        binding.chipIncidentFilterNew.apply {
            val active = filter == "NEW"
            setBackgroundResource(if (active) R.drawable.bg_chip_cancelled_active else R.drawable.bg_chip_filter_inactive)
            setTextColor(if (active) white else black)
            typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        binding.chipIncidentFilterInReview.apply {
            val active = filter == "IN_REVIEW"
            setBackgroundResource(if (active) R.drawable.bg_chip_inprogress_active else R.drawable.bg_chip_filter_inactive)
            setTextColor(if (active) white else black)
            typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        binding.chipIncidentFilterResolved.apply {
            val active = filter == "RESOLVED"
            setBackgroundResource(if (active) R.drawable.bg_chip_completed_active else R.drawable.bg_chip_filter_inactive)
            setTextColor(if (active) white else black)
            typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        filterList()
    }

    private fun filterList() {
        val filtered = when (currentFilter) {
            "NEW" -> allIncidents.filter { it.status.equals("NEW", ignoreCase = true) }
            "IN_REVIEW" -> allIncidents.filter {
                it.status.equals("IN_REVIEW", ignoreCase = true) || it.status.equals("IN_PROGRESS", ignoreCase = true)
            }
            "RESOLVED" -> allIncidents.filter {
                it.status.equals("RESOLVED", ignoreCase = true) || it.status.equals("DONE", ignoreCase = true)
            }
            else -> allIncidents
        }

        adapter.submitList(filtered)
        if (filtered.isEmpty()) {
            binding.layoutEmptyIncidents.visibility = View.VISIBLE
            binding.rvIncidentsList.visibility = View.GONE
        } else {
            binding.layoutEmptyIncidents.visibility = View.GONE
            binding.rvIncidentsList.visibility = View.VISIBLE
        }
    }

    private fun setupListeners() {
        binding.btnCreateNewIncident.setOnClickListener {
            val intent = Intent(this, IncidentReportActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadIncidents() {
        binding.incidentSwipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val result = incidentRepo.getMyIncidents()
            binding.incidentSwipeRefresh.isRefreshing = false

            if (result.isSuccess) {
                allIncidents = result.getOrDefault(emptyList())
                filterList()
            } else {
                Toast.makeText(this@IncidentHistoryActivity, "Không thể tải danh sách sự cố.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showIncidentDetailDialog(incident: IncidentReportDto) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_incident_detail)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val code = incident.id?.let { "#INC_$it" } ?: "#INC"
        dialog.findViewById<TextView>(R.id.tvDialogIncidentCode).text = code
        dialog.findViewById<TextView>(R.id.tvDialogIncidentBin).text = incident.binName ?: incident.deviceId
        dialog.findViewById<TextView>(R.id.tvDialogIncidentReason).text = incident.reason
        dialog.findViewById<TextView>(R.id.tvDialogIncidentDesc).text =
            if (!incident.description.isNullOrBlank()) incident.description else "Không có mô tả chi tiết."
        dialog.findViewById<TextView>(R.id.tvDialogIncidentTime).text = incident.createdAt ?: "--"

        val statusTv = dialog.findViewById<TextView>(R.id.tvDialogIncidentStatus)
        when (incident.status.uppercase()) {
            "NEW" -> {
                statusTv.text = "MỚI TIẾP NHẬN"
                statusTv.setTextColor(Color.parseColor("#DC2626"))
                statusTv.setBackgroundResource(R.drawable.bg_badge_pill_red)
            }
            "IN_REVIEW", "IN_PROGRESS" -> {
                statusTv.text = "ĐANG XỬ LÝ"
                statusTv.setTextColor(Color.parseColor("#2563EB"))
                statusTv.setBackgroundResource(R.drawable.bg_badge_pill_blue)
            }
            "RESOLVED", "DONE" -> {
                statusTv.text = "ĐÃ GIẢI QUYẾT"
                statusTv.setTextColor(Color.parseColor("#16A34A"))
                statusTv.setBackgroundResource(R.drawable.bg_badge_pill_green)
            }
            else -> {
                statusTv.text = incident.status
                statusTv.setTextColor(Color.GRAY)
                statusTv.setBackgroundResource(R.drawable.bg_badge_pill_yellow)
            }
        }

        dialog.findViewById<Button>(R.id.btnDialogCloseIncident).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun playEntranceAnimation() {
        val views = listOf(
            binding.incidentsAppHeader,
            binding.layoutIncidentFilters,
            binding.rvIncidentsList,
            binding.layoutIncidentBottomBar
        )
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = 20f
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((i * 40).toLong())
                .setDuration(280)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }
}
