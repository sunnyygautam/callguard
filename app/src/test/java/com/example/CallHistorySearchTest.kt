package com.example

import com.example.data.repository.DeviceCallLogEntry
import com.example.data.repository.DeviceCallType
import com.example.ui.CallHistoryFilter
import com.example.ui.filterDeviceCallLogs
import org.junit.Assert.assertEquals
import org.junit.Test

class CallHistorySearchTest {

    private val sampleLogs = listOf(
        DeviceCallLogEntry(
            id = 1L,
            number = "+91 140-882-9911",
            cachedName = "Promo Desk",
            type = DeviceCallType.INCOMING,
            dateMillis = 3_000L,
            durationSeconds = 0L
        ),
        DeviceCallLogEntry(
            id = 2L,
            number = "+1 (415) 555-0199",
            cachedName = "Alice Carter",
            type = DeviceCallType.OUTGOING,
            dateMillis = 2_000L,
            durationSeconds = 95L
        ),
        DeviceCallLogEntry(
            id = 3L,
            number = "0114223344",
            cachedName = null,
            type = DeviceCallType.MISSED,
            dateMillis = 1_000L,
            durationSeconds = 0L
        )
    )

    @Test
    fun `search matches contact name case-insensitively`() {
        val results = filterDeviceCallLogs(sampleLogs, CallHistoryFilter.ALL, "alice")
        assertEquals(1, results.size)
        assertEquals(2L, results.first().id)
    }

    @Test
    fun `search matches normalized phone digits across spaces and dashes`() {
        val results = filterDeviceCallLogs(sampleLogs, CallHistoryFilter.ALL, "140882")
        assertEquals(1, results.size)
        assertEquals(1L, results.first().id)
    }

    @Test
    fun `search combines cleanly with call type filter`() {
        val missedOnly = filterDeviceCallLogs(sampleLogs, CallHistoryFilter.MISSED, "0114")
        assertEquals(1, missedOnly.size)
        assertEquals(3L, missedOnly.first().id)

        val mismatchedFilter = filterDeviceCallLogs(sampleLogs, CallHistoryFilter.OUTGOING, "0114")
        assertEquals(0, mismatchedFilter.size)
    }
}
