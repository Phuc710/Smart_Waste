package com.example.app_smart_waste.ui.history

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.utils.applyNavigationBarBottomPadding
import com.example.app_smart_waste.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.layoutHistoryRoot.applyNavigationBarBottomPadding()

        binding.appHeader.configure(
            title = "Lịch sử ca thu gom",
            navIconRes = R.drawable.ic_arrow_back,
            onNavClick = { finish() }
        )
    }
}
