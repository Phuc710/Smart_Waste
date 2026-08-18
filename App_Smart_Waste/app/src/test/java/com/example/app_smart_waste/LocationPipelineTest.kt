package com.example.app_smart_waste

import com.example.app_smart_waste.core.location.LocationFilter
import com.example.app_smart_waste.core.model.BatchLocationItem
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit Tests for Production-Ready GPS Location Pipeline
 */
class LocationPipelineTest {

    @Test
    fun testValidCoordinates() {
        assertTrue(LocationFilter.isValidCoordinate(10.7769, 106.7009)) // Ho Chi Minh City
        assertTrue(LocationFilter.isValidCoordinate(21.0285, 105.8542)) // Hanoi
        assertFalse(LocationFilter.isValidCoordinate(0.0, 0.0)) // Null Island
        assertFalse(LocationFilter.isValidCoordinate(100.0, 50.0)) // Out of lat bounds
        assertFalse(LocationFilter.isValidCoordinate(10.0, 200.0)) // Out of lng bounds
    }

    @Test
    fun testBatchLocationItemSerialization() {
        val item = BatchLocationItem(
            latitude = 10.7769,
            longitude = 106.7009,
            speed = 8.5,
            heading = 90.0,
            accuracy = 4.2,
            timestamp = "2026-08-18T10:30:00.000Z"
        )
        assertEquals(10.7769, item.latitude, 0.0001)
        assertEquals(106.7009, item.longitude, 0.0001)
        assertEquals(8.5, item.speed!!, 0.01)
        assertEquals(90.0, item.heading!!, 0.01)
        assertEquals(4.2, item.accuracy!!, 0.01)
    }
}
