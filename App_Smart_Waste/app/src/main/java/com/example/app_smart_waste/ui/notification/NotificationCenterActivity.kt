package com.example.app_smart_waste.ui.notification

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.app_smart_waste.R
import com.example.app_smart_waste.core.utils.applyNavigationBarBottomPadding
import com.example.app_smart_waste.data.repository.NotificationRepository
import com.example.app_smart_waste.databinding.ActivityNotificationCenterBinding
import kotlinx.coroutines.launch

/**
 * Trang: Trung Tâm Thông Báo (Notification Center Screen)
 * Displays grouped notification list with mark-all-as-read, unified header, and seamless detail routing.
 */
class NotificationCenterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationCenterBinding
    private val repository = NotificationRepository.getInstance()
    private lateinit var adapter: NotificationCenterAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationCenterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.layoutNotifCenterRoot.applyNavigationBarBottomPadding()

        setupHeader()
        setupRecyclerView()
        observeNotifications()
    }

    private fun setupHeader() {
        binding.appHeader.configure(
            title = "Thông báo",
            navIconRes = R.drawable.ic_arrow_back,
            onNavClick = { finish() },
            actionText = "Đánh dấu đã đọc",
            actionTextColor = ContextCompat.getColor(this, R.color.app_success),
            onActionTextClick = {
                repository.markAllAsRead()
            }
        )
    }

    private fun setupRecyclerView() {
        adapter = NotificationCenterAdapter { notification ->
            repository.markAsRead(notification.id)
            val intent = Intent(this, NotificationDetailActivity::class.java).apply {
                putExtra(NotificationDetailActivity.EXTRA_NOTIFICATION, notification)
            }
            startActivity(intent)
        }

        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = adapter
    }

    private fun observeNotifications() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.notifications.collect { list ->
                    if (list.isEmpty()) {
                        binding.layoutNotifEmpty.visibility = View.VISIBLE
                        binding.rvNotifications.visibility = View.GONE
                    } else {
                        binding.layoutNotifEmpty.visibility = View.GONE
                        binding.rvNotifications.visibility = View.VISIBLE

                        // Group by dateGroup (e.g. "Hôm nay", "Hôm qua")
                        val groupedItems = mutableListOf<NotificationListItem>()
                        val groups = list.groupBy { it.dateGroup }
                        for ((groupTitle, groupNotifs) in groups) {
                            groupedItems.add(NotificationListItem.Header(groupTitle))
                            groupNotifs.forEach { notif ->
                                groupedItems.add(NotificationListItem.Item(notif))
                            }
                        }
                        adapter.submitList(groupedItems)
                    }
                }
            }
        }
    }
}
