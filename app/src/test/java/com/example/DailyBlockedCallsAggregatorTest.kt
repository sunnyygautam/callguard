package com.example

import com.example.data.local.BlockedCallLogEntity
import com.example.data.model.DailyBlockedCallsAggregator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class DailyBlockedCallsAggregatorTest {

    @Test
    fun `buildLast7Days buckets calls by day excludes older entries and computes peak`() {
        val utc = TimeZone.getTimeZone("UTC")
        val nowCal = Calendar.getInstance(utc, Locale.US).apply {
            set(2026, Calendar.NOVEMBER, 25, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowMillis = nowCal.timeInMillis

        fun millisDaysAgo(daysAgo: Int, hour: Int = 10): Long {
            return Calendar.getInstance(utc, Locale.US).apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, -daysAgo)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val logs = listOf(
            // Today (index 6): 2 blocked calls
            sampleLog(1L, millisDaysAgo(0, 9)),
            sampleLog(2L, millisDaysAgo(0, 12)),
            // 2 days ago (index 4): 3 blocked calls (peak)
            sampleLog(3L, millisDaysAgo(2, 8)),
            sampleLog(4L, millisDaysAgo(2, 11)),
            sampleLog(5L, millisDaysAgo(2, 16)),
            // 6 days ago (index 0): 1 blocked call (oldest included day)
            sampleLog(6L, millisDaysAgo(6, 23)),
            // 7 days ago: outside the 7-day window -> excluded
            sampleLog(7L, millisDaysAgo(7, 23))
        )

        val summary = DailyBlockedCallsAggregator.buildLast7Days(
            logs = logs,
            nowMillis = nowMillis,
            timeZone = utc,
            locale = Locale.US
        )

        assertEquals(7, summary.dailyStats.size)
        assertEquals(6, summary.totalLast7Days)
        assertEquals(3, summary.peakDailyCount)

        assertEquals(1, summary.dailyStats[0].count)
        assertEquals(0, summary.dailyStats[1].count)
        assertEquals(0, summary.dailyStats[2].count)
        assertEquals(0, summary.dailyStats[3].count)
        assertEquals(3, summary.dailyStats[4].count)
        assertEquals(0, summary.dailyStats[5].count)
        assertEquals(2, summary.dailyStats[6].count)
        assertTrue(summary.dailyStats[6].isToday)
    }

    private fun sampleLog(id: Long, blockedAt: Long): BlockedCallLogEntity {
        return BlockedCallLogEntity(
            id = id,
            phoneNumber = "+91140000000$id",
            normalizedNumber = "+91140000000$id",
            matchedPrefix = "+91140",
            matchedLabel = "Telemarketer",
            blockedAt = blockedAt
        )
    }
}
