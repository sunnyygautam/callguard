package com.example.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
}
