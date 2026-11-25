package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.spamBlockerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "call_guard_spam_blocker_prefs"
)

/**
 * User-configurable spam call blocking settings persisted locally via Jetpack DataStore.
 */
data class SpamBlockerSettings(
    val isSpamBlockerEnabled: Boolean = true,
    val blockBuiltInSpamSeries: Boolean = true,
    val blockHiddenPrivateCalls: Boolean = false,
    val blockSpoofedStirShakenCalls: Boolean = true,
    val silenceOnlyMode: Boolean = false
)

/**
 * Persists and streams [SpamBlockerSettings] for both the Compose UI and [com.example.telecom.PrefixCallScreeningService].
 */
class SpamBlockerPreferencesRepository(
    private val context: Context
) {
    private object Keys {
        val SPAM_BLOCKER_ENABLED = booleanPreferencesKey("spam_blocker_enabled")
        val BLOCK_BUILTIN_SPAM_SERIES = booleanPreferencesKey("block_builtin_spam_series")
        val BLOCK_HIDDEN_PRIVATE_CALLS = booleanPreferencesKey("block_hidden_private_calls")
        val BLOCK_SPOOFED_STIR_SHAKEN = booleanPreferencesKey("block_spoofed_stir_shaken")
        val SILENCE_ONLY_MODE = booleanPreferencesKey("silence_only_mode")
    }

    val settings: Flow<SpamBlockerSettings> = context.spamBlockerDataStore.data.map { prefs ->
        SpamBlockerSettings(
            isSpamBlockerEnabled = prefs[Keys.SPAM_BLOCKER_ENABLED] ?: true,
            blockBuiltInSpamSeries = prefs[Keys.BLOCK_BUILTIN_SPAM_SERIES] ?: true,
            blockHiddenPrivateCalls = prefs[Keys.BLOCK_HIDDEN_PRIVATE_CALLS] ?: false,
            blockSpoofedStirShakenCalls = prefs[Keys.BLOCK_SPOOFED_STIR_SHAKEN] ?: true,
            silenceOnlyMode = prefs[Keys.SILENCE_ONLY_MODE] ?: false
        )
    }

    suspend fun getCurrentSettings(): SpamBlockerSettings = settings.first()

    suspend fun setSpamBlockerEnabled(enabled: Boolean) {
        context.spamBlockerDataStore.edit { it[Keys.SPAM_BLOCKER_ENABLED] = enabled }
    }

    suspend fun setBlockBuiltInSpamSeries(enabled: Boolean) {
        context.spamBlockerDataStore.edit { it[Keys.BLOCK_BUILTIN_SPAM_SERIES] = enabled }
    }

    suspend fun setBlockHiddenPrivateCalls(enabled: Boolean) {
        context.spamBlockerDataStore.edit { it[Keys.BLOCK_HIDDEN_PRIVATE_CALLS] = enabled }
    }

    suspend fun setBlockSpoofedStirShakenCalls(enabled: Boolean) {
        context.spamBlockerDataStore.edit { it[Keys.BLOCK_SPOOFED_STIR_SHAKEN] = enabled }
    }

    suspend fun setSilenceOnlyMode(enabled: Boolean) {
        context.spamBlockerDataStore.edit { it[Keys.SILENCE_ONLY_MODE] = enabled }
    }
}
