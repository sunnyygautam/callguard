package com.example.ml

import com.example.data.repository.DeviceCallLogEntry
import com.example.data.repository.DeviceCallType
import com.example.data.repository.DeviceContactEntry
import com.example.telecom.PhoneNumberMatcher
import java.util.Calendar
import java.util.TimeZone

/**
 * Auto-assigned behavioral category for unknown (unsaved) phone numbers in Recent Call History.
 */
enum class CallerCategory {
    FREQUENT_CALLER,
    POTENTIAL_BUSINESS,
    OCCASIONAL_CALLER,
    ONE_TIME_CONTACT
}

/**
 * Categorization outcome for an unknown call history entry, including interaction frequency
 * and a concise human-readable reason.
 */
data class CallerCategoryResult(
    val category: CallerCategory,
    val interactionCount: Int,
    val reason: String
)

/**
 * On-device behavioral classifier that auto-tags unknown (unsaved) callers in Recent Call History
 * based on interaction frequency, bidirectional call patterns, business/toll-free numbering
 * conventions, and business-hours timing.
 *
 * Saved contacts are intentionally left untagged so only unknown numbers receive auto-category tags.
 */
object UnknownCallerCategorizer {

    // Toll-free, commercial customer service, and national business number prefixes (digits only)
    private val BUSINESS_PREFIXES = listOf(
        // North America toll-free
        "1800", "1833", "1844", "1855", "1866", "1877", "1888",
        "800", "833", "844", "855", "866", "877", "888",
        // India toll-free / enterprise customer care / telemarketing
        "911800", "911860", "91140", "1800", "1860", "140",
        // UK business / non-geographic service lines
        "44800", "44808", "44300", "44330", "44333", "0800", "0808", "0300", "0333",
        // Australia business / toll-free
        "611300", "611800", "1300"
    )

    /**
     * Classifies every unknown (unsaved) call entry in [callLogs] and returns a map of
     * `entry.id -> CallerCategoryResult`. Known saved contacts are excluded from the returned map.
     */
    fun categorizeCallHistoryBatch(
        callLogs: List<DeviceCallLogEntry>,
        contacts: List<DeviceContactEntry>,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Map<Long, CallerCategoryResult> {
        if (callLogs.isEmpty()) return emptyMap()

        val contactKeys = HashSet<String>(contacts.size * 2)
        for (contact in contacts) {
            val digits = contact.normalizedNumber.removePrefix("+")
            if (digits.isNotEmpty()) {
                contactKeys.add(digits)
                if (digits.length >= 10) {
                    contactKeys.add(digits.takeLast(10))
                }
            }
        }

        // Group non-empty numbers by canonical key (last 10 digits for >= 10-digit numbers so
        // +14155550101 and 4155550101 aggregate into the same interaction frequency bucket).
        val callsByKey = HashMap<String, MutableList<DeviceCallLogEntry>>()
        for (entry in callLogs) {
            val key = canonicalKey(entry.number)
            if (key.isNotEmpty()) {
                callsByKey.getOrPut(key) { mutableListOf() }.add(entry)
            }
        }

        val results = LinkedHashMap<Long, CallerCategoryResult>(callLogs.size)
        for (entry in callLogs) {
            val digits = PhoneNumberMatcher.normalize(entry.number).removePrefix("+")
            val isKnownContact = !entry.cachedName.isNullOrBlank() ||
                (digits.isNotEmpty() && (
                    contactKeys.contains(digits) ||
                        (digits.length >= 10 && contactKeys.contains(digits.takeLast(10)))
                    ))

            // Only auto-categorize unknown numbers
            if (isKnownContact) continue

            // Hidden/private numbers without digits are treated individually as one-time contacts
            if (digits.isEmpty()) {
                results[entry.id] = CallerCategoryResult(
                    category = CallerCategory.ONE_TIME_CONTACT,
                    interactionCount = 1,
                    reason = "Single hidden or private call"
                )
                continue
            }

            val peerCalls = callsByKey[canonicalKey(entry.number)].orEmpty()
            results[entry.id] = classifyUnknownNumber(
                digitsOnly = digits,
                peerCalls = peerCalls,
                timeZone = timeZone
            )
        }

        return results
    }

    internal fun classifyUnknownNumber(
        digitsOnly: String,
        peerCalls: List<DeviceCallLogEntry>,
        timeZone: TimeZone = TimeZone.getDefault()
    ): CallerCategoryResult {
        val totalCalls = peerCalls.size.coerceAtLeast(1)
        val hasOutgoing = peerCalls.any { it.type == DeviceCallType.OUTGOING }
        val hasIncomingOrMissed = peerCalls.any {
            it.type == DeviceCallType.INCOMING ||
                it.type == DeviceCallType.MISSED ||
                it.type == DeviceCallType.REJECTED
        }
        val hasTwoWayInteraction = hasOutgoing && hasIncomingOrMissed
        val isBusinessPrefix = isKnownBusinessPrefix(digitsOnly)

        val businessHourCalls = peerCalls.count { isBusinessHours(it.dateMillis, timeZone) }
        val mostlyBusinessHours = totalCalls >= 2 &&
            businessHourCalls * 2 >= totalCalls &&
            businessHourCalls >= 2

        // 1. Explicit business/toll-free/service prefixes always surface as Potential Business
        if (isBusinessPrefix) {
            return CallerCategoryResult(
                category = CallerCategory.POTENTIAL_BUSINESS,
                interactionCount = totalCalls,
                reason = "Matches toll-free or commercial business prefix"
            )
        }

        // 2. High frequency (>= 3 calls) or repeated bidirectional calls -> Frequent Caller
        if (totalCalls >= 3 || (totalCalls >= 2 && hasTwoWayInteraction)) {
            return CallerCategoryResult(
                category = CallerCategory.FREQUENT_CALLER,
                interactionCount = totalCalls,
                reason = "$totalCalls interactions in recent history"
            )
        }

        // 3. Repeated one-way weekday business-hours outreach -> Potential Business
        if (totalCalls == 2 && !hasOutgoing && mostlyBusinessHours) {
            return CallerCategoryResult(
                category = CallerCategory.POTENTIAL_BUSINESS,
                interactionCount = totalCalls,
                reason = "Repeated weekday business-hours calls"
            )
        }

        // 4. Two interactions outside business-hour one-way pattern -> Occasional Caller
        if (totalCalls == 2) {
            return CallerCategoryResult(
                category = CallerCategory.OCCASIONAL_CALLER,
                interactionCount = totalCalls,
                reason = "2 interactions in recent history"
            )
        }

        // 5. Single interaction -> One-time contact
        return CallerCategoryResult(
            category = CallerCategory.ONE_TIME_CONTACT,
            interactionCount = 1,
            reason = "Single interaction in recent history"
        )
    }

    internal fun isKnownBusinessPrefix(digitsOnly: String): Boolean {
        if (digitsOnly.isEmpty()) return false
        // 5-6 digit commercial short codes (e.g. bank/delivery/support desks)
        if (digitsOnly.length in 5..6) return true
        return BUSINESS_PREFIXES.any { prefix -> digitsOnly.startsWith(prefix) }
    }

    internal fun isBusinessHours(epochMillis: Long, timeZone: TimeZone): Boolean {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = epochMillis }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return isWeekday && hour in 8..17
    }

    private fun canonicalKey(rawNumber: String): String {
        val digits = PhoneNumberMatcher.normalize(rawNumber).removePrefix("+")
        if (digits.isEmpty()) return ""
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }
}
