package com.example.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.backup.BackupExportResult
import com.example.data.backup.LocalBackupExporter
import com.example.data.local.BlockedCallLogEntity
import com.example.data.local.BlockedMatchType
import com.example.data.local.BlockedPrefixEntity
import com.example.data.model.BlockedCallsSummary
import com.example.data.model.DailyBlockedCallsAggregator
import com.example.data.repository.AddPrefixResult
import com.example.data.repository.BlockedCallLogRepository
import com.example.data.repository.DeviceCallLogEntry
import com.example.data.repository.DeviceCallLogRepository
import com.example.data.repository.DeviceContactEntry
import com.example.data.repository.DeviceContactsRepository
import com.example.data.repository.PrefixRepository
import com.example.data.repository.SpamBlockerPreferencesRepository
import com.example.data.repository.SpamBlockerSettings
import com.example.ml.SpamMlAnalysisResult
import com.example.ml.SpamMlAnalysisService
import com.example.telecom.CallScreeningStatus
import com.example.telecom.PhoneNumberMatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Top-level destinations in CallGuard.
 */
enum class CallGuardTab {
    HOME,
    KEYPAD,
    SHIELD
}

/**
 * Filter options for the device Call History list.
 */
enum class CallHistoryFilter {
    ALL,
    INCOMING,
    OUTGOING,
    MISSED
}

/**
 * Immutable UI state for CallGuard's Home (Search Contacts & Call History),
 * Keypad (Suggested & Dialer), and Shield (Dashboard, Spam Blocker, Prefixes, Blocked History).
 */
data class CallGuardUiState(
    val selectedTab: CallGuardTab = CallGuardTab.HOME,
    val isBlockedListScreenOpen: Boolean = false,
    val selectedBlockedMatchType: String = BlockedMatchType.EXACT_NUMBER,
    val prefixInput: String = "",
    val labelInput: String = "",
    @StringRes val prefixErrorRes: Int? = null,
    val prefixes: List<BlockedPrefixEntity> = emptyList(),
    val blockedCallLogs: List<BlockedCallLogEntity> = emptyList(),
    val last7DaysSummary: BlockedCallsSummary = DailyBlockedCallsAggregator.buildLast7Days(emptyList()),
    val spamBlockerSettings: SpamBlockerSettings = SpamBlockerSettings(),
    val screeningStatus: CallScreeningStatus = CallScreeningStatus(
        isRoleSupported = true,
        isRoleAvailable = true,
        isRoleHeld = false,
        hasPhonePermissions = false
    ),
    val dialerInput: String = "",
    val deviceContacts: List<DeviceContactEntry> = emptyList(),
    val contactsSearchQuery: String = "",
    val hasContactsPermission: Boolean = false,
    val isLoadingContacts: Boolean = false,
    val deviceCallLogs: List<DeviceCallLogEntry> = emptyList(),
    val callHistoryFilter: CallHistoryFilter = CallHistoryFilter.ALL,
    val callHistorySearchQuery: String = "",
    val hasCallLogPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val isLoadingCallHistory: Boolean = false,
    val callHistoryMlScores: Map<Long, SpamMlAnalysisResult> = emptyMap(),
    val isExportingBackup: Boolean = false,
    val lastBackupResult: BackupExportResult.Success? = null
) {
    val activePrefixCount: Int
        get() = prefixes.count { it.isActive }

    val exactBlockedNumberCount: Int
        get() = prefixes.count { it.isExactNumber }

    val blockedPrefixSeriesCount: Int
        get() = prefixes.count { !it.isExactNumber }

    val blockedCallCount: Int
        get() = blockedCallLogs.size
}

/**
 * One-off UI events rendered as Snackbars.
 */
sealed interface CallGuardEvent {
    data class PrefixAdded(val prefix: String) : CallGuardEvent
    data class PrefixDeleted(val entity: BlockedPrefixEntity) : CallGuardEvent
    data class BlockedCallDeleted(val log: BlockedCallLogEntity) : CallGuardEvent
    data class PrefilledBlockPrefix(val prefix: String) : CallGuardEvent
    data class SpamBlockerToggled(val enabled: Boolean) : CallGuardEvent
    data class DoNotDisturbToggled(val enabled: Boolean) : CallGuardEvent
    data class BackupExported(val result: BackupExportResult.Success) : CallGuardEvent
    data class BackupExportFailed(val reason: String) : CallGuardEvent
    data object HistoryCleared : CallGuardEvent
}

/**
 * ViewModel managing Home contacts & call history, Keypad dialer & suggestions,
 * and Shield spam blocker rules & analytics.
 */
class CallGuardViewModel(
    private val prefixRepository: PrefixRepository,
    private val callLogRepository: BlockedCallLogRepository,
    private val deviceCallLogRepository: DeviceCallLogRepository,
    private val deviceContactsRepository: DeviceContactsRepository,
    private val spamPrefsRepository: SpamBlockerPreferencesRepository,
    private val localBackupExporter: LocalBackupExporter,
    initialStatus: CallScreeningStatus
) : ViewModel() {

    private data class FormState(
        val selectedMatchType: String = BlockedMatchType.EXACT_NUMBER,
        val prefixInput: String = "",
        val labelInput: String = "",
        @StringRes val prefixErrorRes: Int? = null
    )

    private data class PhoneUiState(
        val selectedTab: CallGuardTab = CallGuardTab.HOME,
        val isBlockedListScreenOpen: Boolean = false,
        val dialerInput: String = "",
        val deviceContacts: List<DeviceContactEntry> = emptyList(),
        val contactsSearchQuery: String = "",
        val hasContactsPermission: Boolean = false,
        val isLoadingContacts: Boolean = false,
        val deviceCallLogs: List<DeviceCallLogEntry> = emptyList(),
        val callHistoryFilter: CallHistoryFilter = CallHistoryFilter.ALL,
        val callHistorySearchQuery: String = "",
        val hasCallLogPermission: Boolean = false,
        val hasNotificationPermission: Boolean = true,
        val isLoadingCallHistory: Boolean = false,
        val isExportingBackup: Boolean = false,
        val lastBackupResult: BackupExportResult.Success? = null
    )

    private data class CoreShieldSnapshot(
        val form: FormState,
        val prefixes: List<BlockedPrefixEntity>,
        val callLogs: List<BlockedCallLogEntity>,
        val spamSettings: SpamBlockerSettings
    )

    private val formState = MutableStateFlow(FormState())
    private val screeningStatus = MutableStateFlow(initialStatus)
    private val phoneUiState = MutableStateFlow(
        PhoneUiState(
            hasContactsPermission = deviceContactsRepository.hasContactsPermission(),
            hasCallLogPermission = deviceCallLogRepository.hasCallLogPermission()
        )
    )

    private val _events = MutableSharedFlow<CallGuardEvent>()
    val events: SharedFlow<CallGuardEvent> = _events.asSharedFlow()

    private val coreShieldState = combine(
        formState,
        prefixRepository.allPrefixes,
        callLogRepository.allBlockedCalls,
        spamPrefsRepository.settings
    ) { form, prefixes, callLogs, spamSettings ->
        CoreShieldSnapshot(form, prefixes, callLogs, spamSettings)
    }

    val uiState: StateFlow<CallGuardUiState> = combine(
        coreShieldState,
        screeningStatus,
        phoneUiState
    ) { core, status, phone ->
        val activePrefixes = core.prefixes.filter { it.isActive }
        val mlScores = SpamMlAnalysisService.analyzeCallHistoryBatch(
            callLogs = phone.deviceCallLogs,
            contacts = phone.deviceContacts,
            activeManualPrefixes = activePrefixes
        )
        CallGuardUiState(
            selectedTab = phone.selectedTab,
            isBlockedListScreenOpen = phone.isBlockedListScreenOpen,
            selectedBlockedMatchType = core.form.selectedMatchType,
            prefixInput = core.form.prefixInput,
            labelInput = core.form.labelInput,
            prefixErrorRes = core.form.prefixErrorRes,
            prefixes = core.prefixes,
            blockedCallLogs = core.callLogs,
            last7DaysSummary = DailyBlockedCallsAggregator.buildLast7Days(core.callLogs),
            spamBlockerSettings = core.spamSettings,
            screeningStatus = status,
            dialerInput = phone.dialerInput,
            deviceContacts = phone.deviceContacts,
            contactsSearchQuery = phone.contactsSearchQuery,
            hasContactsPermission = phone.hasContactsPermission,
            isLoadingContacts = phone.isLoadingContacts,
            deviceCallLogs = phone.deviceCallLogs,
            callHistoryFilter = phone.callHistoryFilter,
            callHistorySearchQuery = phone.callHistorySearchQuery,
            hasCallLogPermission = phone.hasCallLogPermission,
            hasNotificationPermission = phone.hasNotificationPermission,
            isLoadingCallHistory = phone.isLoadingCallHistory,
            callHistoryMlScores = mlScores,
            isExportingBackup = phone.isExportingBackup,
            lastBackupResult = phone.lastBackupResult
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CallGuardUiState(
            screeningStatus = initialStatus,
            hasContactsPermission = deviceContactsRepository.hasContactsPermission(),
            hasCallLogPermission = deviceCallLogRepository.hasCallLogPermission()
        )
    )

    init {
        refreshDeviceContacts()
        refreshDeviceCallHistory()
    }

    fun selectTab(tab: CallGuardTab) {
        phoneUiState.update {
            it.copy(
                selectedTab = tab,
                isBlockedListScreenOpen = if (tab != CallGuardTab.SHIELD) false else it.isBlockedListScreenOpen
            )
        }
        if (tab == CallGuardTab.HOME || tab == CallGuardTab.KEYPAD) {
            refreshDeviceContacts()
            refreshDeviceCallHistory()
        }
    }

    fun openBlockedListScreen() {
        phoneUiState.update {
            it.copy(
                selectedTab = CallGuardTab.SHIELD,
                isBlockedListScreenOpen = true
            )
        }
    }

    fun closeBlockedListScreen() {
        phoneUiState.update { it.copy(isBlockedListScreenOpen = false) }
    }

    fun onBlockedMatchTypeSelected(matchType: String) {
        formState.update { it.copy(selectedMatchType = matchType, prefixErrorRes = null) }
    }

    fun onPrefixInputChanged(value: String) {
        val filtered = value.filter { ch ->
            ch.isDigit() || ch == '+' || ch == ' ' || ch == '-' || ch == '(' || ch == ')'
        }
        formState.update { it.copy(prefixInput = filtered, prefixErrorRes = null) }
    }

    fun onLabelInputChanged(value: String) {
        formState.update { it.copy(labelInput = value.take(40)) }
    }

    fun applyQuickPrefix(prefix: String, suggestedLabel: String) {
        formState.update { current ->
            current.copy(
                selectedMatchType = BlockedMatchType.PREFIX,
                prefixInput = prefix,
                labelInput = current.labelInput.ifBlank { suggestedLabel },
                prefixErrorRes = null
            )
        }
    }

    /**
     * Prefills a caller number from Home Contacts, Recent Calls, or Keypad into the Shield
     * Blocked List screen (defaulting to Exact Phone Number when >= 7 digits) for quick blocking.
     */
    fun prefillBlockPrefixFromCall(rawNumber: String, callerName: String?) {
        val normalized = PhoneNumberMatcher.normalize(rawNumber)
        val suggestedValue = normalized.ifEmpty { rawNumber.trim() }
        val inferredType = if (PhoneNumberMatcher.isValidExactNumber(suggestedValue)) {
            BlockedMatchType.EXACT_NUMBER
        } else {
            BlockedMatchType.PREFIX
        }
        formState.update {
            it.copy(
                selectedMatchType = inferredType,
                prefixInput = suggestedValue,
                labelInput = callerName?.take(40) ?: it.labelInput,
                prefixErrorRes = null
            )
        }
        phoneUiState.update {
            it.copy(
                selectedTab = CallGuardTab.SHIELD,
                isBlockedListScreenOpen = true
            )
        }
        viewModelScope.launch {
            _events.emit(CallGuardEvent.PrefilledBlockPrefix(suggestedValue))
        }
    }

    fun addPrefix() {
        val snapshot = formState.value
        viewModelScope.launch {
            when (
                val result = prefixRepository.addPrefix(
                    rawPrefix = snapshot.prefixInput,
                    rawLabel = snapshot.labelInput,
                    matchType = snapshot.selectedMatchType
                )
            ) {
                is AddPrefixResult.Success -> {
                    formState.value = FormState(selectedMatchType = snapshot.selectedMatchType)
                    _events.emit(CallGuardEvent.PrefixAdded(result.entity.prefixString))
                }
                AddPrefixResult.EmptyInput -> {
                    formState.update { it.copy(prefixErrorRes = R.string.error_prefix_empty) }
                }
                AddPrefixResult.InvalidFormat -> {
                    val errorRes = if (snapshot.selectedMatchType == BlockedMatchType.EXACT_NUMBER) {
                        R.string.error_exact_number_invalid
                    } else {
                        R.string.error_prefix_invalid
                    }
                    formState.update { it.copy(prefixErrorRes = errorRes) }
                }
                AddPrefixResult.DuplicatePrefix -> {
                    formState.update { it.copy(prefixErrorRes = R.string.error_prefix_duplicate) }
                }
            }
        }
    }

    fun togglePrefixActive(id: Long, isActive: Boolean) {
        viewModelScope.launch {
            prefixRepository.setPrefixActive(id = id, isActive = isActive)
        }
    }

    fun deletePrefix(entity: BlockedPrefixEntity) {
        viewModelScope.launch {
            prefixRepository.deletePrefix(entity)
            _events.emit(CallGuardEvent.PrefixDeleted(entity))
        }
    }

    fun undoDelete(entity: BlockedPrefixEntity) {
        viewModelScope.launch {
            prefixRepository.restorePrefix(entity)
        }
    }

    fun deleteBlockedCall(log: BlockedCallLogEntity) {
        viewModelScope.launch {
            callLogRepository.deleteBlockedCall(log)
            _events.emit(CallGuardEvent.BlockedCallDeleted(log))
        }
    }

    fun undoDeleteBlockedCall(log: BlockedCallLogEntity) {
        viewModelScope.launch {
            callLogRepository.restoreBlockedCall(log)
        }
    }

    fun clearBlockedCallHistory() {
        viewModelScope.launch {
            callLogRepository.clearHistory()
            _events.emit(CallGuardEvent.HistoryCleared)
        }
    }

    fun onDialerDigitPressed(digit: String) {
        phoneUiState.update { current ->
            if (current.dialerInput.length >= 20) return@update current
            current.copy(dialerInput = current.dialerInput + digit)
        }
    }

    fun onDialerNumberSelected(number: String) {
        phoneUiState.update { it.copy(dialerInput = number.trim().take(20)) }
    }

    fun onDialerBackspace() {
        phoneUiState.update { current ->
            if (current.dialerInput.isEmpty()) current
            else current.copy(dialerInput = current.dialerInput.dropLast(1))
        }
    }

    fun onDialerClear() {
        phoneUiState.update { it.copy(dialerInput = "") }
    }

    /**
     * Handles incoming ACTION_DIAL or ACTION_VIEW `tel:` intents by opening the Keypad tab.
     */
    fun onExternalDialIntent(rawNumber: String?) {
        val cleaned = rawNumber.orEmpty().trim()
        phoneUiState.update { current ->
            current.copy(
                selectedTab = CallGuardTab.KEYPAD,
                dialerInput = if (cleaned.isNotEmpty()) cleaned.take(20) else current.dialerInput
            )
        }
        refreshDeviceContacts()
        refreshDeviceCallHistory()
    }

    fun onContactsSearchChanged(query: String) {
        phoneUiState.update { it.copy(contactsSearchQuery = query.take(60)) }
    }

    fun onCallHistoryFilterSelected(filter: CallHistoryFilter) {
        phoneUiState.update { it.copy(callHistoryFilter = filter) }
    }

    fun onCallHistorySearchChanged(query: String) {
        phoneUiState.update { it.copy(callHistorySearchQuery = query.take(60)) }
    }

    fun updateNotificationPermissionStatus(granted: Boolean) {
        phoneUiState.update { it.copy(hasNotificationPermission = granted) }
    }

    fun refreshDeviceContacts() {
        val hasPermission = deviceContactsRepository.hasContactsPermission()
        if (!hasPermission) {
            phoneUiState.update {
                it.copy(
                    hasContactsPermission = false,
                    deviceContacts = emptyList(),
                    isLoadingContacts = false
                )
            }
            return
        }

        viewModelScope.launch {
            phoneUiState.update { it.copy(hasContactsPermission = true, isLoadingContacts = true) }
            val contacts = deviceContactsRepository.fetchContacts()
            phoneUiState.update {
                it.copy(
                    hasContactsPermission = true,
                    deviceContacts = contacts,
                    isLoadingContacts = false
                )
            }
        }
    }

    fun refreshDeviceCallHistory() {
        val hasPermission = deviceCallLogRepository.hasCallLogPermission()
        if (!hasPermission) {
            phoneUiState.update {
                it.copy(
                    hasCallLogPermission = false,
                    deviceCallLogs = emptyList(),
                    isLoadingCallHistory = false
                )
            }
            return
        }

        viewModelScope.launch {
            phoneUiState.update { it.copy(hasCallLogPermission = true, isLoadingCallHistory = true) }
            val recentCalls = deviceCallLogRepository.fetchRecentCalls()
            phoneUiState.update {
                it.copy(
                    hasCallLogPermission = true,
                    deviceCallLogs = recentCalls,
                    isLoadingCallHistory = false
                )
            }
        }
    }

    fun setSpamBlockerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            spamPrefsRepository.setSpamBlockerEnabled(enabled)
            _events.emit(CallGuardEvent.SpamBlockerToggled(enabled))
        }
    }

    fun setDoNotDisturbEnabled(enabled: Boolean) {
        viewModelScope.launch {
            spamPrefsRepository.setDoNotDisturbEnabled(enabled)
            _events.emit(CallGuardEvent.DoNotDisturbToggled(enabled))
        }
    }

    fun setBlockBuiltInSpamSeries(enabled: Boolean) {
        viewModelScope.launch {
            spamPrefsRepository.setBlockBuiltInSpamSeries(enabled)
        }
    }

    fun setBlockHiddenPrivateCalls(enabled: Boolean) {
        viewModelScope.launch {
            spamPrefsRepository.setBlockHiddenPrivateCalls(enabled)
        }
    }

    fun setBlockSpoofedStirShakenCalls(enabled: Boolean) {
        viewModelScope.launch {
            spamPrefsRepository.setBlockSpoofedStirShakenCalls(enabled)
        }
    }

    fun setSilenceOnlyMode(enabled: Boolean) {
        viewModelScope.launch {
            spamPrefsRepository.setSilenceOnlyMode(enabled)
        }
    }

    fun setRingtoneEnabled(enabled: Boolean) {
        viewModelScope.launch {
            spamPrefsRepository.setRingtoneEnabled(enabled)
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            spamPrefsRepository.setVibrationEnabled(enabled)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            spamPrefsRepository.setNotificationsEnabled(enabled)
        }
    }

    fun updateScreeningStatus(status: CallScreeningStatus) {
        screeningStatus.value = status
    }

    fun createSuggestedBackupFileName(): String =
        localBackupExporter.createSuggestedFileName()

    /**
     * Exports blocked call history and/or device contacts to the user-selected Storage Access
     * Framework JSON document [destinationUri].
     */
    fun exportBackupToUri(
        destinationUri: Uri,
        includeBlockedHistory: Boolean = true,
        includeContacts: Boolean = true
    ) {
        if (phoneUiState.value.isExportingBackup) return

        viewModelScope.launch {
            phoneUiState.update { it.copy(isExportingBackup = true) }
            when (
                val result = localBackupExporter.exportToUri(
                    destinationUri = destinationUri,
                    includeBlockedHistory = includeBlockedHistory,
                    includeContacts = includeContacts
                )
            ) {
                is BackupExportResult.Success -> {
                    phoneUiState.update {
                        it.copy(
                            isExportingBackup = false,
                            lastBackupResult = result
                        )
                    }
                    _events.emit(CallGuardEvent.BackupExported(result))
                }
                is BackupExportResult.Failure -> {
                    phoneUiState.update { it.copy(isExportingBackup = false) }
                    _events.emit(CallGuardEvent.BackupExportFailed(result.reason))
                }
            }
        }
    }

    class Factory(
        private val prefixRepository: PrefixRepository,
        private val callLogRepository: BlockedCallLogRepository,
        private val deviceCallLogRepository: DeviceCallLogRepository,
        private val deviceContactsRepository: DeviceContactsRepository,
        private val spamPrefsRepository: SpamBlockerPreferencesRepository,
        private val localBackupExporter: LocalBackupExporter,
        private val initialStatus: CallScreeningStatus
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CallGuardViewModel::class.java)) {
                return CallGuardViewModel(
                    prefixRepository = prefixRepository,
                    callLogRepository = callLogRepository,
                    deviceCallLogRepository = deviceCallLogRepository,
                    deviceContactsRepository = deviceContactsRepository,
                    spamPrefsRepository = spamPrefsRepository,
                    localBackupExporter = localBackupExporter,
                    initialStatus = initialStatus
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
