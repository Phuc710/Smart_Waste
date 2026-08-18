package com.example.app_smart_waste.core.location

import android.content.Context
import android.util.Log
import com.example.app_smart_waste.core.model.BatchLocationItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.LinkedList

/**
 * Senior Enterprise Offline Location FIFO Buffer
 * Persists unsent GPS points in local storage when network connectivity is lost,
 * and enables batched sync once the connection is restored.
 */
class OfflineLocationQueue(context: Context) {

    companion object {
        private const val TAG = "OfflineLocationQueue"
        private const val PREFS_NAME = "smartwaste_gps_offline_queue"
        private const val KEY_QUEUE = "offline_points_queue"
        private const val MAX_CAPACITY = 200

        @Volatile
        private var INSTANCE: OfflineLocationQueue? = null

        fun getInstance(context: Context): OfflineLocationQueue {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OfflineLocationQueue(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val memoryQueue = LinkedList<BatchLocationItem>()

    init {
        loadFromStorage()
    }

    @Synchronized
    private fun loadFromStorage() {
        try {
            val json = prefs.getString(KEY_QUEUE, null)
            if (!json.isNullOrBlank()) {
                val listType = object : TypeToken<List<BatchLocationItem>>() {}.type
                val items: List<BatchLocationItem> = gson.fromJson(json, listType) ?: emptyList()
                memoryQueue.clear()
                memoryQueue.addAll(items)
                Log.d(TAG, "Loaded ${memoryQueue.size} offline GPS points from local storage.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load offline queue: ${e.message}")
            memoryQueue.clear()
        }
    }

    @Synchronized
    private fun saveToStorage() {
        try {
            val json = gson.toJson(memoryQueue)
            prefs.edit().putString(KEY_QUEUE, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist offline queue: ${e.message}")
        }
    }

    @Synchronized
    fun enqueue(item: BatchLocationItem) {
        while (memoryQueue.size >= MAX_CAPACITY) {
            memoryQueue.pollFirst() // Evict oldest point
        }
        memoryQueue.addLast(item)
        saveToStorage()
        Log.d(TAG, "Enqueued offline location. Current queue size: ${memoryQueue.size}")
    }

    @Synchronized
    fun peekBatch(maxSize: Int = 25): List<BatchLocationItem> {
        return memoryQueue.take(maxSize)
    }

    @Synchronized
    fun removeBatch(count: Int) {
        var removed = 0
        while (removed < count && memoryQueue.isNotEmpty()) {
            memoryQueue.pollFirst()
            removed++
        }
        saveToStorage()
        Log.d(TAG, "Removed $removed synced points. Remaining in queue: ${memoryQueue.size}")
    }

    @Synchronized
    fun size(): Int = memoryQueue.size

    @Synchronized
    fun isEmpty(): Boolean = memoryQueue.isEmpty()

    @Synchronized
    fun clear() {
        memoryQueue.clear()
        prefs.edit().remove(KEY_QUEUE).apply()
    }

    @Synchronized
    fun getAll(): List<BatchLocationItem> {
        return ArrayList(memoryQueue)
    }
}
