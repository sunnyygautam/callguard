package com.example.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.backup.BackupExportResult
import com.example.data.backup.BackupRestoreResult
import com.example.data.backup.LocalBackupExporter
import com.example.data.local.BlockedCallLogEntity
import com.example.data.local.BlockedMatchType
import com.example.data.local.BlockedPrefixEntity
import com.example.data.local.CallRecordingEntity
import com.example.data.model.BlockedCallsSummary
import com.example.data.model.DailyBlockedCallsAggregator
import com.example.data.repository.AddPrefixResult
import com.example.data.repository.BlockedCallLogRepository
import com.example.data.repository.CallRecordingRepository
import com.example.data.repository.DeviceCallLogEntry
import com.example.data.repository.DeviceCallLogRepository
import com.example.data.repository.DeviceContactEntry
import com.example.data.repository.DeviceContactsRepository
import com.example.data.repository.PrefixRepository
import com.example.data.repository.RecordingExportResult
import com.example.data.repository.SpamBlockerPreferencesRepository
import com.example.data.repository.SpamBlockerSettings
import com.example.ml.CallerCategoryResult
import com.example.ml.OnDeviceCallSummarizer
import com.example.ml.SpamMlAnalysisResult
import com.example.ml.SpamMlAnalysisService
import com.example.ml.UnknownCallerCategorizer
import com.example.telecom.BatteryOptimizationStatus
import com.example.telecom.CallRecordingPlayer
import com.example.telecom.CallScreeningStatus
import com.example.telecom.PhoneNumberMatcher
import com.example.telecom.RecordingPlaybackState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val isCallRecordingsScreenOpen: Boolean = false,
    val selectedBlockedMatchType: String = BlockedMatchType.EXACT_NUMBER,
    val prefixInput: String = "",
    val labelInput: String = "",
    @StringRes val prefixErrorRes: Int? = null,
    val prefixes: List<BlockedPrefixEntity> = emptyList(),
    val blockedCallLogs: List<BlockedCallLogEntity> = emptyList(),
    val callRecordings: List<CallRecordingEntity> = emptyList(),
    val summarizingRecordingIds: Set<Long> = emptySet(),
    val recordingPlaybackState: RecordingPlaybackState = RecordingPlaybackState(),
    val hasRecordAudioPermission: Boolean = false,
    val last7DaysSummary: BlockedCallsSummary = DailyBlockedCallsAggregator.buildLast7Days(emptyList()),
    val spamBlockerSettings: SpamBlockerSettings = SpamBlockerSettings(),
    val screeningStatus: CallScreeningStatus = CallScreeningStatus(
        isRoleSupported = true,
        isRoleAvailable = true,
        isRoleHeld = false,
        hasPhonePermissions = false
    ),
    val batteryOptimizationStatus: BatteryOptimizationStatus = BatteryOptimizationStatus(),
    val dialerInput: String = "",
    val deviceContacts: List<DeviceContactEntry> = emptyList(),
    val searchedContacts: List<DeviceContactEntry> = emptyList(),
    val isSearchingContacts: Boolean = false,
    val searchedNumberMlAnalysis: SpamMlAnalysisResult? = null,
    val searchedNumberBlockedRule: BlockedPrefixEntity? = null,
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
    val callHistoryCategories: Map<Long, CallerCategoryResult> = emptyMap(),
    val isExportingBackup: Boolean = false,
    val lastBackupResult: BackupExportResult.Success? = null,
    val isRestoringBackup: Boolean = false,
    val lastRestoreResult: BackupRestoreResult.Success? = null
) {
    val activePrefixCount: Int
        get() = prefixes.count { it.isActive }

    val exactBlockedNumberCount: Int
        get() = prefixes.count { it.isExactNumber }

    val blockedPrefixSeriesCount: Int
        get() = prefixes.count { !it.isExactNumber }

    val blockedCallCount: Int
        get() = blockedCallLogs.size

    val callRecordingCount: Int
        get() = callRecordings.size

    val callRecordingsTotalBytes: Long
        get() = callRecordings.sumOf { it.fileSizeBytes }
}

/**
 * One-off UI events rendered as Snackbars.
 */
sealed interface CallGuardEvent {
    data class PrefixAdded(val prefix: String) : CallGuardEvent
    data class PrefixDeleted(val entity: BlockedPrefixEntity) : CallGuardEvent
    data class BlockedCallDeleted(val log: BlockedCallLogEntity) : CallGuardEvent
    data class PrefilledBlockPrefix(val prefix: String) : CallGuardEvent
    data class SwipeNumberBlocked(val entity: BlockedPrefixEntity) : CallGuardEvent
    data class SwipeNumberAlreadyBlocked(val number: String) : CallGuardEvent
    data class CallHistoryEntryDeleted(
        val entry: DeviceCallLogEntry,
        val deletedFromDevice: Boolean
    ) : CallGuardEvent
    data class SpamBlockerToggled(val enabled: Boolean) : CallGuardEvent
    data class DoNotDisturbToggled(val enabled: Boolean) : CallGuardEvent
    data class CallRecordingToggled(val enabled: Boolean) : CallGuardEvent
    data class RecordingSummarized(val label: String) : CallGuardEvent
    data class RecordingDeleted(val label: String) : CallGuardEvent
    data class RecordingExported(val fileName: String) : CallGuardEvent
    data class RecordingExportFailed(val reason: String) : CallGuardEvent
    data object RecordingsCleared : CallGuardEvent
    data class BackupExported(val result: BackupExportResult.Success) : CallGuardEvent
    data class BackupExportFailed(val reason: String) : CallGuardEvent
    data class BackupRestored(val result: BackupRestoreResult.Success) : CallGuardEvent
    data class BackupRestoreFailed(val reason: String) : CallGuardEvent
    data object HistoryCleared : CallGuardEvent
}

/**
 * ViewModel managing Home contacts & call history, Keypad dialer & suggestions,
 * and Shield spam blocker rules & analytics.
 */
class CallGuardViewModel(
    private val prefixRepository: PrefixRepository,
    private val callLogRepository: BlockedCallLogRepository,
    private val callRecordingRepository: CallRecordingRepository,
    private val onDeviceCallSummarizer: OnDeviceCallSummarizer,
    private val deviceCallLogRepository: DeviceCallLogRepository,
    private val deviceContactsRepository: DeviceContactsRepository,
    private val spamPrefsRepository: SpamBlockerPreferencesRepository,
    private val localBackupExporter: LocalBackupExporter,
    initialStatus: CallScreeningStatus,
    initialHasRecordAudioPermission: Boolean = false,
    initialBatteryOptimizationStatus: BatteryOptimizationStatus = BatteryOptimizationStatus()
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
        val isCallRecordingsScreenOpen: Boolean = false,
        val hasRecordAudioPermission: Boolean = false,
        val batteryOptimizationStatus: BatteryOptimizationStatus = BatteryOptimizationStatus(),
        val dialerInput: String = "",
        val deviceContacts: List<DeviceContactEntry> = emptyList(),
        val searchedContacts: List<DeviceContactEntry> = emptyList(),
        val isSearchingContacts: Boolean = false,
        val contactsSearchQuery: String = "",
        val hasContactsPermission: Boolean = false,
        val isLoadingContacts: Boolean = false,
        val deviceCallLogs: List<DeviceCallLogEntry> = emptyList(),
        val dismissedCallLogIds: Set<Long> = emptySet(),
        val callHistoryFilter: CallHistoryFilter = CallHistoryFilter.ALL,
        val callHistorySearchQuery: String = "",
        val hasCallLogPermission: Boolean = false,
        val hasNotificationPermission: Boolean = true,
        val isLoadingCallHistory: Boolean = false,
        val isExportingBackup: Boolean = false,
        val lastBackupResult: BackupExportResult.Success? = null,
        val isRestoringBackup: Boolean = false,
        val lastRestoreResult: BackupRestoreResult.Success? = null
    )

    private data class CoreShieldSnapshot(
        val form: FormState,
        val prefixes: List<BlockedPrefixEntity>,
        val callLogs: List<BlockedCallLogEntity>,
        val spamSettings: SpamBlockerSettings
    )

    private data class RecordingsSnapshot(
        val recordings: List<CallRecordingEntity>,
        val playbackState: RecordingPlaybackState,
        val summarizingIds: Set<Long>
    )

    private val recordingPlayer = CallRecordingPlayer()
    private val summarizingRecordingIds = MutableStateFlow<Set<Long>>(emptySet())
    private var contactSearchJob: Job? = null
    private val formState = MutableStateFlow(FormState())
    private val screeningStatus = MutableStateFlow(initialStatus)
    private val phoneUiState = MutableStateFlow(
        PhoneUiState(
            hasRecordAudioPermission = initialHasRecordAudioPermission,
            batteryOptimizationStatus = initialBatteryOptimizationStatus,
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

    private val recordingsState = combine(
        callRecordingRepository.allRecordings,
        recordingPlayer.playbackState,
        summarizingRecordingIds
    ) { recordings, playback, summarizingIds ->
        RecordingsSnapshot(recordings, playback, summarizingIds)
    }

    val uiState: StateFlow<CallGuardUiState> = combine(
        coreShieldState,
        screeningStatus,
        phoneUiState,
        recordingsState
    ) { core, status, phone, rec ->
        val activePrefixes = core.prefixes.filter { it.isActive }
        val visibleCallLogs = if (phone.dismissedCallLogIds.isEmpty()) {
            phone.deviceCallLogs
        } else {
            phone.deviceCallLogs.filterNot { it.id in phone.dismissedCallLogIds }
        }
        val mlScores = SpamMlAnalysisService.analyzeCallHistoryBatch(
            callLogs = visibleCallLogs,
            contacts = phone.deviceContacts,
            activeManualPrefixes = activePrefixes
        )
        val callerCategories = UnknownCallerCategorizer.categorizeCallHistoryBatch(
            callLogs = visibleCallLogs,
            contacts = phone.deviceContacts
        )

        // When user searches a phone number under Home tab (>= 3 digits, no letters),
        // compute immediate on-device ML spam risk & Blocked List match for that number.
        val trimmedSearch = phone.contactsSearchQuery.trim()
        val isPhoneNumberQuery = trimmedSearch.isNotEmpty() &&
            trimmedSearch.none { it.isLetter() } &&
            trimmedSearch.count { it.isDigit() } >= 3
        val mergedSearchContacts = if (trimmedSearch.isBlank()) {
            emptyList()
        } else {
            mergeUniqueContacts(
                primary = phone.searchedContacts,
                secondary = SuggestedContactsMatcher.filterContacts(
                    contacts = phone.deviceContacts,
                    query = trimmedSearch
                )
            )
        }
        val normalizedSearchedNumber = PhoneNumberMatcher.normalize(trimmedSearch)
        val isSearchedNumberInContacts = isPhoneNumberQuery && mergedSearchContacts.any { contact ->
            val contactNorm = contact.normalizedNumber.removePrefix("+")
            val queryNorm = normalizedSearchedNumber.removePrefix("+")
            contactNorm.isNotEmpty() && queryNorm.isNotEmpty() &&
                (contactNorm == queryNorm ||
                    (contactNorm.length >= 10 && queryNorm.length >= 10 &&
                        contactNorm.takeLast(10) == queryNorm.takeLast(10)))
        }
        val searchedNumberBlockedRule = if (isPhoneNumberQuery) {
            PhoneNumberMatcher.findMatchingPrefix(trimmedSearch, activePrefixes)
        } else {
            null
        }
        val searchedNumberMl = if (isPhoneNumberQuery) {
            SpamMlAnalysisService.analyzeNumber(
                rawNumber = trimmedSearch,
                activeManualPrefixes = activePrefixes,
                isHiddenOrPrivate = false,
                isStirShakenFailed = false,
                isKnownContact = isSearchedNumberInContacts
            )
        } else {
            null
        }

        CallGuardUiState(
            selectedTab = phone.selectedTab,
            isBlockedListScreenOpen = phone.isBlockedListScreenOpen,
            isCallRecordingsScreenOpen = phone.isCallRecordingsScreenOpen,
            selectedBlockedMatchType = core.form.selectedMatchType,
            prefixInput = core.form.prefixInput,
            labelInput = core.form.labelInput,
            prefixErrorRes = core.form.prefixErrorRes,
            prefixes = core.prefixes,
            blockedCallLogs = core.callLogs,
            callRecordings = rec.recordings,
            summarizingRecordingIds = rec.summarizingIds,
            recordingPlaybackState = rec.playbackState,
            hasRecordAudioPermission = phone.hasRecordAudioPermission,
            last7DaysSummary = DailyBlockedCallsAggregator.buildLast7Days(core.callLogs),
            spamBlockerSettings = core.spamSettings,
            screeningStatus = status,
            batteryOptimizationStatus = phone.batteryOptimizationStatus,
            dialerInput = phone.dialerInput,
            deviceContacts = phone.deviceContacts,
            searchedContacts = mergedSearchContacts,
            isSearchingContacts = phone.isSearchingContacts,
            searchedNumberMlAnalysis = searchedNumberMl,
            searchedNumberBlockedRule = searchedNumberBlockedRule,
            contactsSearchQuery = phone.contactsSearchQuery,
            hasContactsPermission = phone.hasContactsPermission,
            isLoadingContacts = phone.isLoadingContacts,
            deviceCallLogs = visibleCallLogs,
            callHistoryFilter = phone.callHistoryFilter,
            callHistorySearchQuery = phone.callHistorySearchQuery,
            hasCallLogPermission = phone.hasCallLogPermission,
            hasNotificationPermission = phone.hasNotificationPermission,
            isLoadingCallHistory = phone.isLoadingCallHistory,
            callHistoryMlScores = mlScores,
            callHistoryCategories = callerCategories,
            isExportingBackup = phone.isExportingBackup,
            lastBackupResult = phone.lastBackupResult,
            isRestoringBackup = phone.isRestoringBackup,
            lastRestoreResult = phone.lastRestoreResult
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CallGuardUiState(
            screeningStatus = initialStatus,
            batteryOptimizationStatus = initialBatteryOptimizationStatus,
            hasRecordAudioPermission = initialHasRecordAudioPermission,
            hasContactsPermission = deviceContactsRepository.hasContactsPermission(),
            hasCallLogPermission = deviceCallLogRepository.hasCallLogPermission()
        )
    )

    init {
        refreshDeviceContacts()
        refreshDeviceCallHistory()
    }

    override fun onCleared() {
        recordingPlayer.release()
        super.onCleared()
    }

    fun selectTab(tab: CallGuardTab) {
        if (tab != CallGuardTab.SHIELD) {
            recordingPlayer.stop()
        }
        phoneUiState.update {
            it.copy(
                selectedTab = tab,
                isBlockedListScreenOpen = if (tab != CallGuardTab.SHIELD) false else it.isBlockedListScreenOpen,
                isCallRecordingsScreenOpen = if (tab != CallGuardTab.SHIELD) false else it.isCallRecordingsScreenOpen
            )
        }
        if (tab == CallGuardTab.HOME || tab == CallGuardTab.KEYPAD) {
            refreshDeviceContacts()
            refreshDeviceCallHistory()
        }
    }

    fun openBlockedListScreen() {
        recordingPlayer.stop()
        phoneUiState.update {
            it.copy(
                selectedTab = CallGuardTab.SHIELD,
                isBlockedListScreenOpen = true,
                isCallRecordingsScreenOpen = false
            )
        }
    }

    fun closeBlockedListScreen() {
        phoneUiState.update { it.copy(isBlockedListScreenOpen = false) }
    }

    fun openCallRecordingsScreen() {
        phoneUiState.update {
            it.copy(
                selectedTab = CallGuardTab.SHIELD,
                isBlockedListScreenOpen = false,
                isCallRecordingsScreenOpen = true
            )
        }
    }

    fun closeCallRecordingsScreen() {
        recordingPlayer.stop()
        phoneUiState.update { it.copy(isCallRecordingsScreenOpen = false) }
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

    /**
     * Immediately blocks a phone number triggered via swipe-to-block in Recent Call History,
     * saving it directly into the Room Blocked List so [com.example.telecom.PrefixCallScreeningService]
     * rejects future calls from this number right away.
     */
    fun quickBlockNumberFromSwipe(rawNumber: String, callerName: String?) {
        val normalized = PhoneNumberMatcher.normalize(rawNumber)
        val target = normalized.ifEmpty { rawNumber.trim() }
        if (target.isEmpty()) return

        val matchType = if (PhoneNumberMatcher.isValidExactNumber(target)) {
            BlockedMatchType.EXACT_NUMBER
        } else {
            BlockedMatchType.PREFIX
        }
        val label = callerName?.trim()?.take(40).orEmpty().ifBlank { "Blocked from call history" }

        viewModelScope.launch {
            when (
                val result = prefixRepository.addPrefix(
                    rawPrefix = target,
                    rawLabel = label,
                    matchType = matchType
                )
            ) {
                is AddPrefixResult.Success -> {
                    _events.emit(CallGuardEvent.SwipeNumberBlocked(result.entity))
                }
                AddPrefixResult.DuplicatePrefix -> {
                    _events.emit(CallGuardEvent.SwipeNumberAlreadyBlocked(target))
                }
                AddPrefixResult.EmptyInput,
                AddPrefixResult.InvalidFormat -> {
                    prefillBlockPrefixFromCall(rawNumber, callerName)
                }
            }
        }
    }

    fun undoSwipeBlock(entity: BlockedPrefixEntity) {
        viewModelScope.launch {
            prefixRepository.deletePrefix(entity)
        }
    }

    /**
     * Deletes a call history entry triggered via swipe-to-delete. Removes the entry immediately
     * from CallGuard's Recent Call History list and, when [deleteFromDeviceLog] is true and
     * WRITE_CALL_LOG is granted, deletes it from Android's system CallLog provider as well.
     */
    fun deleteCallHistoryEntry(entry: DeviceCallLogEntry, deleteFromDeviceLog: Boolean) {
        phoneUiState.update { state ->
            state.copy(
                dismissedCallLogIds = state.dismissedCallLogIds + entry.id,
                deviceCallLogs = state.deviceCallLogs.filterNot { it.id == entry.id }
            )
        }
        viewModelScope.launch {
            val deletedFromDevice = if (deleteFromDeviceLog) {
                deviceCallLogRepository.deleteCallLogEntry(entry.id)
            } else {
                false
            }
            _events.emit(
                CallGuardEvent.CallHistoryEntryDeleted(
                    entry = entry,
                    deletedFromDevice = deletedFromDevice
                )
            )
        }
    }

    fun undoDismissCallHistoryEntry(entry: DeviceCallLogEntry) {
        phoneUiState.update { state ->
            val restored = (state.deviceCallLogs + entry)
                .distinctBy { it.id }
                .sortedByDescending { it.dateMillis }
            state.copy(
                dismissedCallLogIds = state.dismissedCallLogIds - entry.id,
                deviceCallLogs = restored
            )
        }
        refreshDeviceCallHistory()
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
        val cleaned = query.take(60)
        contactSearchJob?.cancel()
        if (cleaned.isBlank()) {
            phoneUiState.update {
                it.copy(
                    contactsSearchQuery = "",
                    searchedContacts = emptyList(),
                    isSearchingContacts = false
                )
            }
            return
        }

        val hasContactsPerm = deviceContactsRepository.hasContactsPermission()
        phoneUiState.update {
            it.copy(
                contactsSearchQuery = cleaned,
                hasContactsPermission = hasContactsPerm,
                isSearchingContacts = hasContactsPerm
            )
        }

        if (!hasContactsPerm) return

        contactSearchJob = viewModelScope.launch {
            delay(160L)
            val fetched = deviceContactsRepository.searchContacts(cleaned)
            phoneUiState.update { current ->
                if (current.contactsSearchQuery != cleaned) {
                    current
                } else {
                    current.copy(
                        searchedContacts = fetched,
                        isSearchingContacts = false
                    )
                }
            }
        }
    }

    private fun mergeUniqueContacts(
        primary: List<DeviceContactEntry>,
        secondary: List<DeviceContactEntry>
    ): List<DeviceContactEntry> {
        if (primary.isEmpty()) return secondary
        if (secondary.isEmpty()) return primary
        val seen = HashSet<String>()
        val merged = ArrayList<DeviceContactEntry>(primary.size + secondary.size)
        for (entry in primary) {
            val key = "${entry.displayName.lowercase()}|${entry.normalizedNumber.ifEmpty { entry.phoneNumber }}"
            if (seen.add(key)) merged.add(entry)
        }
        for (entry in secondary) {
            val key = "${entry.displayName.lowercase()}|${entry.normalizedNumber.ifEmpty { entry.phoneNumber }}"
            if (seen.add(key)) merged.add(entry)
        }
        return merged
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

    fun updateBatteryOptimizationStatus(status: BatteryOptimizationStatus) {
        phoneUiState.update { it.copy(batteryOptimizationStatus = status) }
    }

    fun setBatteryOptimizationPromptDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            spamPrefsRepository.setBatteryOptimizationPromptDismissed(dismissed)
        }
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

    fun updateRecordAudioPermissionStatus(granted: Boolean) {
        phoneUiState.update { it.copy(hasRecordAudioPermission = granted) }
    }

    fun setCallRecordingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            spamPrefsRepository.setCallRecordingEnabled(enabled)
            _events.emit(CallGuardEvent.CallRecordingToggled(enabled))
        }
    }

    fun setAutoRecordCalls(enabled: Boolean) {
        viewModelScope.launch {
            spamPrefsRepository.setAutoRecordCalls(enabled)
        }
    }

    fun togglePlayRecording(recording: CallRecordingEntity) {
        recordingPlayer.togglePlayPause(recording.id, recording.filePath)
    }

    fun seekRecordingPlayback(positionMs: Long) {
        recordingPlayer.seekTo(positionMs)
    }

    /**
     * Runs on-device audio decoding, offline speech recognition (Android 13+ when available),
     * and local NLP summarization for a recorded call and saves the result in Room.
     */
    fun summarizeCallRecording(recording: CallRecordingEntity) {
        if (recording.id in summarizingRecordingIds.value) return
        viewModelScope.launch {
            summarizingRecordingIds.update { it + recording.id }
            try {
                val summary = onDeviceCallSummarizer.summarizeRecording(recording)
                callRecordingRepository.saveRecordingSummary(
                    recordingId = recording.id,
                    transcript = summary.transcript,
                    summaryText = summary.summaryText,
                    keyPoints = summary.keyPoints,
                    topics = summary.topics,
                    processingMode = summary.processingMode
                )
                val label = recording.callerName.ifBlank { recording.phoneNumber }
                _events.emit(CallGuardEvent.RecordingSummarized(label))
            } finally {
                summarizingRecordingIds.update { it - recording.id }
            }
        }
    }

    /**
     * Re-runs the pure on-device NLP summarizer over updated conversation transcript text.
     */
    fun resummarizeRecordingFromTranscript(
        recording: CallRecordingEntity,
        transcriptText: String
    ) {
        if (recording.id in summarizingRecordingIds.value) return
        viewModelScope.launch {
            summarizingRecordingIds.update { it + recording.id }
            try {
                val summary = onDeviceCallSummarizer.summarizeTranscriptText(recording, transcriptText)
                callRecordingRepository.saveRecordingSummary(
                    recordingId = recording.id,
                    transcript = summary.transcript,
                    summaryText = summary.summaryText,
                    keyPoints = summary.keyPoints,
                    topics = summary.topics,
                    processingMode = summary.processingMode
                )
                val label = recording.callerName.ifBlank { recording.phoneNumber }
                _events.emit(CallGuardEvent.RecordingSummarized(label))
            } finally {
                summarizingRecordingIds.update { it - recording.id }
            }
        }
    }

    fun deleteCallRecording(recording: CallRecordingEntity) {
        recordingPlayer.stopIfPlaying(recording.id)
        viewModelScope.launch {
            callRecordingRepository.deleteRecording(recording)
            val label = recording.callerName.ifBlank { recording.phoneNumber }
            _events.emit(CallGuardEvent.RecordingDeleted(label))
        }
    }

    fun clearAllCallRecordings() {
        recordingPlayer.stop()
        viewModelScope.launch {
            callRecordingRepository.clearAllRecordings()
            _events.emit(CallGuardEvent.RecordingsCleared)
        }
    }

    fun exportCallRecordingToUri(recording: CallRecordingEntity, destinationUri: Uri) {
        viewModelScope.launch {
            when (val result = callRecordingRepository.exportRecordingToUri(recording, destinationUri)) {
                is RecordingExportResult.Success -> {
                    _events.emit(CallGuardEvent.RecordingExported(result.fileName))
                }
                is RecordingExportResult.Failure -> {
                    _events.emit(CallGuardEvent.RecordingExportFailed(result.reason))
                }
            }
        }
    }

    fun updateScreeningStatus(status: CallScreeningStatus) {
        screeningStatus.value = status
    }

    fun createSuggestedBackupFileName(): String =
        localBackupExporter.createSuggestedFileName()

    /**
     * Exports blocked numbers/prefixes, call settings, blocked call history, and/or device contacts
     * to the user-selected Storage Access Framework JSON document [destinationUri].
     */
    fun exportBackupToUri(
        destinationUri: Uri,
        includeBlockedNumbers: Boolean = true,
        includeCallSettings: Boolean = true,
        includeBlockedHistory: Boolean = true,
        includeContacts: Boolean = true
    ) {
        if (phoneUiState.value.isExportingBackup || phoneUiState.value.isRestoringBackup) return

        viewModelScope.launch {
            phoneUiState.update { it.copy(isExportingBackup = true) }
            when (
                val result = localBackupExporter.exportToUri(
                    destinationUri = destinationUri,
                    includeBlockedNumbers = includeBlockedNumbers,
                    includeCallSettings = includeCallSettings,
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

    /**
     * Restores blocked phone numbers/prefixes, call & shield settings, and blocked call history
     * from a previously exported CallGuard JSON backup document [sourceUri].
     */
    fun restoreBackupFromUri(sourceUri: Uri) {
        if (phoneUiState.value.isRestoringBackup || phoneUiState.value.isExportingBackup) return

        viewModelScope.launch {
            phoneUiState.update { it.copy(isRestoringBackup = true) }
            when (val result = localBackupExporter.restoreFromUri(sourceUri)) {
                is BackupRestoreResult.Success -> {
                    phoneUiState.update {
                        it.copy(
                            isRestoringBackup = false,
                            lastRestoreResult = result
                        )
                    }
                    _events.emit(CallGuardEvent.BackupRestored(result))
                }
                is BackupRestoreResult.Failure -> {
                    phoneUiState.update { it.copy(isRestoringBackup = false) }
                    _events.emit(CallGuardEvent.BackupRestoreFailed(result.reason))
                }
            }
        }
    }

    class Factory(
        private val prefixRepository: PrefixRepository,
        private val callLogRepository: BlockedCallLogRepository,
        private val callRecordingRepository: CallRecordingRepository,
        private val onDeviceCallSummarizer: OnDeviceCallSummarizer,
        private val deviceCallLogRepository: DeviceCallLogRepository,
        private val deviceContactsRepository: DeviceContactsRepository,
        private val spamPrefsRepository: SpamBlockerPreferencesRepository,
        private val localBackupExporter: LocalBackupExporter,
        private val initialStatus: CallScreeningStatus,
        private val initialHasRecordAudioPermission: Boolean = false,
        private val initialBatteryOptimizationStatus: BatteryOptimizationStatus = BatteryOptimizationStatus()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CallGuardViewModel::class.java)) {
                return CallGuardViewModel(
                    prefixRepository = prefixRepository,
                    callLogRepository = callLogRepository,
                    callRecordingRepository = callRecordingRepository,
                    onDeviceCallSummarizer = onDeviceCallSummarizer,
                    deviceCallLogRepository = deviceCallLogRepository,
                    deviceContactsRepository = deviceContactsRepository,
                    spamPrefsRepository = spamPrefsRepository,
                    localBackupExporter = localBackupExporter,
                    initialStatus = initialStatus,
                    initialHasRecordAudioPermission = initialHasRecordAudioPermission,
                    initialBatteryOptimizationStatus = initialBatteryOptimizationStatus
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
