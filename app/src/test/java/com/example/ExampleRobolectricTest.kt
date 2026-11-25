package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BlockedPrefixEntity
import com.example.data.local.CallGuardDatabase
import com.example.data.repository.AddPrefixResult
import com.example.data.repository.BlockedCallLogRepository
import com.example.data.repository.PrefixRepository
import com.example.telecom.PhoneNumberMatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CallGuard", appName)
    }

    @Test
    fun `room repository inserts toggles and deletes blocked prefixes`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, CallGuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = PrefixRepository(db.blockedPrefixDao())

            val addResult = repository.addPrefix("+91 140", "Telemarketer")
            assertTrue(addResult is AddPrefixResult.Success)
            val saved = (addResult as AddPrefixResult.Success).entity
            assertEquals("+91140", saved.prefixString)

            // Duplicate check
            val duplicate = repository.addPrefix("+91140", "Spam")
            assertEquals(AddPrefixResult.DuplicatePrefix, duplicate)

            // Active screening query matches incoming call
            val activeRules = repository.getActivePrefixes()
            assertEquals(1, activeRules.size)
            assertNotNull(PhoneNumberMatcher.findMatchingPrefix("+91 140-555-8888", activeRules))

            // Disabling pauses matching
            repository.setPrefixActive(saved.id, false)
            assertEquals(0, repository.getActivePrefixes().size)
            assertNull(
                PhoneNumberMatcher.findMatchingPrefix(
                    "+91 140-555-8888",
                    repository.getActivePrefixes()
                )
            )

            // Deleting clears the table
            repository.deletePrefix(saved)
            assertTrue(repository.allPrefixes.first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `blocked call log repository records orders deletes and clears history`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, CallGuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val callLogRepository = BlockedCallLogRepository(db.blockedCallLogDao())
            val telemarketerRule = BlockedPrefixEntity(
                id = 1L,
                prefixString = "+91140",
                normalizedPrefix = "+91140",
                label = "Telemarketer",
                isActive = true
            )
            val tollFreeRule = BlockedPrefixEntity(
                id = 2L,
                prefixString = "+1800",
                normalizedPrefix = "+1800",
                label = "Toll-Free Spam",
                isActive = true
            )

            val olderLog = callLogRepository.logBlockedCall(
                incomingRawNumber = "+91 140-882-9911",
                matchedRule = telemarketerRule,
                blockedAt = 1_000L
            )
            val newerLog = callLogRepository.logBlockedCall(
                incomingRawNumber = "+1 (800) 555-0199",
                matchedRule = tollFreeRule,
                blockedAt = 2_000L
            )

            // Observed history should be sorted newest-first with normalized numbers and rule snapshots
            val history = callLogRepository.allBlockedCalls.first()
            assertEquals(2, history.size)
            assertEquals("+18005550199", history[0].phoneNumber)
            assertEquals("+1800", history[0].matchedPrefix)
            assertEquals("Toll-Free Spam", history[0].matchedLabel)
            assertEquals("+911408829911", history[1].phoneNumber)
            assertEquals("+91140", history[1].matchedPrefix)

            // Deleting one log removes only that record
            callLogRepository.deleteBlockedCall(newerLog)
            val afterDelete = callLogRepository.allBlockedCalls.first()
            assertEquals(1, afterDelete.size)
            assertEquals(olderLog.phoneNumber, afterDelete[0].phoneNumber)

            // Undo restoration re-inserts the log
            callLogRepository.restoreBlockedCall(newerLog)
            assertEquals(2, callLogRepository.allBlockedCalls.first().size)

            // Clear All empties the history table
            callLogRepository.clearHistory()
            assertTrue(callLogRepository.allBlockedCalls.first().isEmpty())
        } finally {
            db.close()
        }
    }
}
