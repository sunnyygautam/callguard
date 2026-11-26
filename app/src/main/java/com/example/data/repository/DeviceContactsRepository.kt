package com.example.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.telecom.PhoneNumberMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Represents a single contact phone entry loaded from Android's [ContactsContract].
 */
data class DeviceContactEntry(
    val id: Long,
    val displayName: String,
    val phoneNumber: String,
    val normalizedNumber: String
)

/**
 * Repository reading device contacts via [ContactsContract.CommonDataKinds.Phone.CONTENT_URI].
 */
class DeviceContactsRepository(
    private val context: Context
) {

    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Performs a fast indexed lookup via [ContactsContract.PhoneLookup.CONTENT_FILTER_URI] to check
     * whether [rawNumber] matches any saved contact on the device. Safe for low-latency screening
     * inside [com.example.telecom.PrefixCallScreeningService].
     */
    suspend fun isNumberInContacts(rawNumber: String?): Boolean = withContext(Dispatchers.IO) {
        val trimmed = rawNumber.orEmpty().trim()
        if (trimmed.isEmpty() || !hasContactsPermission()) return@withContext false

        if (lookupPhoneNumberExists(trimmed)) return@withContext true

        val normalized = PhoneNumberMatcher.normalize(trimmed)
        if (normalized.isNotEmpty() && normalized != trimmed) {
            return@withContext lookupPhoneNumberExists(normalized)
        }
        false
    }

    private fun lookupPhoneNumberExists(phoneNumber: String): Boolean {
        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        return try {
            context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null,
                null,
                null
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Queries phone-bearing contacts sorted alphabetically by display name on [Dispatchers.IO].
     * Deduplicates identical `(displayName, normalizedNumber)` pairs safely.
     */
    suspend fun fetchContacts(limit: Int = 500): List<DeviceContactEntry> =
        withContext(Dispatchers.IO) {
            if (!hasContactsPermission()) return@withContext emptyList()

            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone._ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            val results = mutableListOf<DeviceContactEntry>()
            val seenKeys = HashSet<String>()

            try {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone._ID
                    )
                    val nameIdx = cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY
                    )
                    val numberIdx = cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    )

                    while (cursor.moveToNext() && results.size < limit) {
                        val rawNumber = cursor.getString(numberIdx).orEmpty().trim()
                        if (rawNumber.isBlank()) continue

                        val displayName = cursor.getString(nameIdx)?.trim().orEmpty()
                            .ifBlank { rawNumber }
                        val normalized = PhoneNumberMatcher.normalize(rawNumber)
                        val dedupeKey = "${displayName.lowercase()}|${normalized.ifEmpty { rawNumber }}"
                        if (!seenKeys.add(dedupeKey)) continue

                        results.add(
                            DeviceContactEntry(
                                id = cursor.getLong(idIdx),
                                displayName = displayName,
                                phoneNumber = rawNumber,
                                normalizedNumber = normalized
                            )
                        )
                    }
                }
            } catch (_: SecurityException) {
                return@withContext emptyList()
            }

            results
        }

    /**
     * Fetches matching phone contacts directly from Android's [ContactsContract] using
     * [ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI] (which indexes both display
     * names and phone numbers across formatting). Also performs a digit-stripped query when
     * the user types formatted phone characters (spaces, dashes, country code).
     */
    suspend fun searchContacts(query: String, limit: Int = 50): List<DeviceContactEntry> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty() || !hasContactsPermission()) return@withContext emptyList()

            val results = mutableListOf<DeviceContactEntry>()
            val seenKeys = HashSet<String>()

            queryFilteredContacts(trimmed, limit, results, seenKeys)

            // If user entered formatted phone digits (e.g. "+1 (415) 555"), also query by raw digits
            val normalizedDigits = PhoneNumberMatcher.normalize(trimmed).removePrefix("+")
            if (normalizedDigits.length >= 2 && normalizedDigits != trimmed && results.size < limit) {
                queryFilteredContacts(normalizedDigits, limit, results, seenKeys)
            }

            results
        }

    private fun queryFilteredContacts(
        filterText: String,
        limit: Int,
        results: MutableList<DeviceContactEntry>,
        seenKeys: MutableSet<String>
    ) {
        val filterUri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
            Uri.encode(filterText)
        )
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        try {
            context.contentResolver.query(
                filterUri,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY
                )
                val numberIdx = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )

                while (cursor.moveToNext() && results.size < limit) {
                    val rawNumber = cursor.getString(numberIdx).orEmpty().trim()
                    if (rawNumber.isBlank()) continue

                    val displayName = cursor.getString(nameIdx)?.trim().orEmpty()
                        .ifBlank { rawNumber }
                    val normalized = PhoneNumberMatcher.normalize(rawNumber)
                    val dedupeKey = "${displayName.lowercase()}|${normalized.ifEmpty { rawNumber }}"
                    if (!seenKeys.add(dedupeKey)) continue

                    results.add(
                        DeviceContactEntry(
                            id = cursor.getLong(idIdx),
                            displayName = displayName,
                            phoneNumber = rawNumber,
                            normalizedNumber = normalized
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        } catch (_: Exception) {
            // Ignore malformed URI edge cases
        }
    }
}
