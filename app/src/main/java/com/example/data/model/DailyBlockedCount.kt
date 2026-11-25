package com.example.data.model

import com.example.data.local.BlockedCallLogEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Single daily bar data point for the 7-day blocked calls summary chart.
 */
data class DailyBlockedStat(
    val dayStartMillis: Long,
    val shortDayLabel: String,
    val fullDateLabel: String,
    val count: Int,
    val isToday: Boolean
)

/**
 * Aggregated 7-day summary metrics derived from local Room [BlockedCallLogEntity] logs.
 */
data class BlockedCallsSummary(
    val dailyStats: List<DailyBlockedStat> = emptyList(),
    val totalLast7Days: Int = 0,
    val peakDailyCount: Int = 0
)

/**
 * Pure utility that groups blocked call logs into 7 consecutive calendar-day buckets
 * ending on [nowMillis].
 */
object DailyBlockedCallsAggregator {

    private const val DAYS_WINDOW = 7

    fun buildLast7Days(
        logs: List<BlockedCallLogEntity>,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): BlockedCallsSummary {
        val shortDayFormat = SimpleDateFormat("EEE", locale).apply { this.timeZone = timeZone }
        val fullDateFormat = SimpleDateFormat("EEE, MMM d", locale).apply { this.timeZone = timeZone }

        // Start of today in the target timezone
        val todayStartCal = Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Build day start timestamps from 6 days ago through today (inclusive)
        val dayStarts = LongArray(DAYS_WINDOW)
        for (index in 0 until DAYS_WINDOW) {
            val cal = (todayStartCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, index - (DAYS_WINDOW - 1))
            }
            dayStarts[index] = cal.timeInMillis
        }

        val tomorrowStartMillis = (todayStartCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        val counts = IntArray(DAYS_WINDOW)
        val windowStart = dayStarts[0]

        for (log in logs) {
            val ts = log.blockedAt
            if (ts < windowStart || ts >= tomorrowStartMillis) continue

            // Find the exact day bucket (handles DST transitions safely without assuming 86400s)
            for (i in DAYS_WINDOW - 1 downTo 0) {
                if (ts >= dayStarts[i]) {
                    counts[i]++
                    break
                }
            }
        }

        val dailyStats = List(DAYS_WINDOW) { index ->
            val startMillis = dayStarts[index]
            val date = Date(startMillis)
            val isToday = index == DAYS_WINDOW - 1
            DailyBlockedStat(
                dayStartMillis = startMillis,
                shortDayLabel = if (isToday) "Today" else shortDayFormat.format(date),
                fullDateLabel = fullDateFormat.format(date),
                count = counts[index],
                isToday = isToday
            )
        }

        val total = counts.sum()
        val peak = counts.maxOrNull() ?: 0

        return BlockedCallsSummary(
            dailyStats = dailyStats,
            totalLast7Days = total,
            peakDailyCount = peak
        )
    }
}
