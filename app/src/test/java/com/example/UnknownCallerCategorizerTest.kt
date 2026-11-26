package com.example

import com.example.data.repository.DeviceCallLogEntry
import com.example.data.repository.DeviceCallType
import com.example.data.repository.DeviceContactEntry
import com.example.ml.CallerCategory
import com.example.ml.UnknownCallerCategorizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class UnknownCallerCategorizerTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun epochUtc(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long {
        return Calendar.getInstance(utc).apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `saved contacts are never tagged as unknown categories`() {
        val t1 = epochUtc(2026, Calendar.NOVEMBER, 25, 10, 0)
        val logs = listOf(
            DeviceCallLogEntry(1L, "+14155550101", "Alice", DeviceCallType.INCOMING, t1, 60L),
            DeviceCallLogEntry(2L, "4155550102", null, DeviceCallType.INCOMING, t1, 30L)
        )
        val contacts = listOf(
            DeviceContactEntry(10L, "Bob", "+1 415-555-0102", "+14155550102")
        )

        val result = UnknownCallerCategorizer.categorizeCallHistoryBatch(logs, contacts, utc)

        assertNull(result[1L])
        assertNull(result[2L])
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single unknown interaction is tagged as One-time contact`() {
        val weekendEvening = epochUtc(2026, Calendar.NOVEMBER, 21, 20, 15) // Saturday 20:15 UTC
        val logs = listOf(
            DeviceCallLogEntry(10L, "+14155559876", null, DeviceCallType.INCOMING, weekendEvening, 25L)
        )

        val result = UnknownCallerCategorizer.categorizeCallHistoryBatch(logs, emptyList(), utc)
        val tag = result[10L]

        assertNotNull(tag)
        assertEquals(CallerCategory.ONE_TIME_CONTACT, tag!!.category)
        assertEquals(1, tag.interactionCount)
    }

    @Test
    fun `three or more interactions with unknown number are tagged as Frequent Caller`() {
        val t1 = epochUtc(2026, Calendar.NOVEMBER, 25, 19, 0)
        val t2 = epochUtc(2026, Calendar.NOVEMBER, 24, 19, 30)
        val t3 = epochUtc(2026, Calendar.NOVEMBER, 23, 20, 0)
        val logs = listOf(
            DeviceCallLogEntry(1L, "+14155550199", null, DeviceCallType.INCOMING, t1, 120L),
            DeviceCallLogEntry(2L, "415-555-0199", null, DeviceCallType.OUTGOING, t2, 95L),
            DeviceCallLogEntry(3L, "+1 415 555 0199", null, DeviceCallType.INCOMING, t3, 45L)
        )

        val result = UnknownCallerCategorizer.categorizeCallHistoryBatch(logs, emptyList(), utc)

        for (id in listOf(1L, 2L, 3L)) {
            assertEquals(CallerCategory.FREQUENT_CALLER, result[id]?.category)
            assertEquals(3, result[id]?.interactionCount)
        }
    }

    @Test
    fun `toll-free and weekday business-hours repeat callers are tagged as Potential Business`() {
        val weekdayMorning1 = epochUtc(2026, Calendar.NOVEMBER, 25, 10, 30) // Wed 10:30 UTC
        val weekdayMorning2 = epochUtc(2026, Calendar.NOVEMBER, 24, 14, 15) // Tue 14:15 UTC
        val logs = listOf(
            DeviceCallLogEntry(101L, "+18005550142", null, DeviceCallType.INCOMING, weekdayMorning1, 40L),
            DeviceCallLogEntry(102L, "+12125550188", null, DeviceCallType.INCOMING, weekdayMorning1, 20L),
            DeviceCallLogEntry(103L, "+12125550188", null, DeviceCallType.MISSED, weekdayMorning2, 0L)
        )

        val result = UnknownCallerCategorizer.categorizeCallHistoryBatch(logs, emptyList(), utc)

        assertEquals(CallerCategory.POTENTIAL_BUSINESS, result[101L]?.category)
        assertEquals(CallerCategory.POTENTIAL_BUSINESS, result[102L]?.category)
        assertEquals(CallerCategory.POTENTIAL_BUSINESS, result[103L]?.category)
        assertEquals(2, result[102L]?.interactionCount)
    }

    @Test
    fun `two-way call pair with unknown number is tagged as Frequent Caller`() {
        val weekend = epochUtc(2026, Calendar.NOVEMBER, 22, 18, 0) // Sunday
        val logs = listOf(
            DeviceCallLogEntry(201L, "+16505550123", null, DeviceCallType.INCOMING, weekend, 30L),
            DeviceCallLogEntry(202L, "+16505550123", null, DeviceCallType.OUTGOING, weekend + 60_000L, 90L)
        )

        val result = UnknownCallerCategorizer.categorizeCallHistoryBatch(logs, emptyList(), utc)

        assertEquals(CallerCategory.FREQUENT_CALLER, result[201L]?.category)
        assertEquals(CallerCategory.FREQUENT_CALLER, result[202L]?.category)
    }

    @Test
    fun `empty private numbers are never aggregated together as Frequent Caller`() {
        val t1 = epochUtc(2026, Calendar.NOVEMBER, 25, 11, 0)
        val logs = listOf(
            DeviceCallLogEntry(301L, "", null, DeviceCallType.INCOMING, t1, 0L),
            DeviceCallLogEntry(302L, "", null, DeviceCallType.MISSED, t1 + 10_000L, 0L),
            DeviceCallLogEntry(303L, "", null, DeviceCallType.REJECTED, t1 + 20_000L, 0L)
        )

        val result = UnknownCallerCategorizer.categorizeCallHistoryBatch(logs, emptyList(), utc)

        for (id in listOf(301L, 302L, 303L)) {
            assertEquals(CallerCategory.ONE_TIME_CONTACT, result[id]?.category)
            assertEquals(1, result[id]?.interactionCount)
            assertFalse(result[id]?.category == CallerCategory.FREQUENT_CALLER)
        }
    }
}
