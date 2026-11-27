package com.example.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.ActivityNotFoundException
import com.example.R
import com.example.data.local.CallRecordingEntity
import com.example.telecom.BatteryOptimizationHelper
import com.example.telecom.CallAudioRecorder
import com.example.telecom.CallNotificationManager
import com.example.telecom.CallScreeningRoleHelper
import com.example.telecom.CallScreeningStatus
import com.example.ui.theme.LocalCallGuardHeroColors
import com.example.ui.theme.ShieldActiveEmerald
import com.example.ui.theme.ShieldWarningAmber
import kotlinx.coroutines.launch

/**
 * Primary single-screen Jetpack Compose UI for CallGuard.
 *
 * Allows users to:
 * 1. Request Android's default Caller ID & Spam role (`ROLE_CALL_SCREENING`) and phone permissions.
 * 2. Add new manual number prefixes (e.g., `+91140`, `+1800`, `0114`) with optional labels.
 * 3. Manage saved prefixes in a LazyColumn with enable/disable Switches and Delete actions.
 */
@Composable
fun CallGuardScreen(
    viewModel: CallGuardViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showBatteryRationaleDialog by remember { mutableStateOf(false) }

    // Re-check role, phone permissions, mic permission, notification permission, battery status, contacts, and call history on resume.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.updateScreeningStatus(CallScreeningRoleHelper.getStatus(context))
        viewModel.updateBatteryOptimizationStatus(BatteryOptimizationHelper.getStatus(context))
        viewModel.updateNotificationPermissionStatus(
            CallNotificationManager.hasNotificationPermission(context)
        )
        viewModel.updateRecordAudioPermissionStatus(
            CallAudioRecorder.hasRecordAudioPermission(context)
        )
        viewModel.refreshDeviceContacts()
        viewModel.refreshDeviceCallHistory()
    }

    // Pressing Back from secondary tabs (Keypad / Shield) returns cleanly to Home
    // when not inside a Shield sub-screen (Blocked List / Call Recordings handle their own BackHandler).
    BackHandler(
        enabled = uiState.selectedTab != CallGuardTab.HOME &&
            !uiState.isBlockedListScreenOpen &&
            !uiState.isCallRecordingsScreenOpen
    ) {
        viewModel.selectTab(CallGuardTab.HOME)
    }

    // Activity Result launcher for RoleManager.createRequestRoleIntent(ROLE_DIALER)
    val defaultDialerRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val updatedStatus = CallScreeningRoleHelper.getStatus(context)
        viewModel.updateScreeningStatus(updatedStatus)
        val granted = result.resultCode == Activity.RESULT_OK || updatedStatus.isDefaultDialer
        val messageRes = if (granted) {
            R.string.snackbar_dialer_role_granted
        } else {
            R.string.snackbar_dialer_role_denied
        }
        if (granted && BatteryOptimizationHelper.shouldShowGuidedPrompt(
                status = BatteryOptimizationHelper.getStatus(context),
                isPromptDismissed = uiState.spamBlockerSettings.batteryOptimizationPromptDismissed,
                isBackgroundFeatureActive = true
            )
        ) {
            showBatteryRationaleDialog = true
        }
        coroutineScope.launch {
            snackbarHostState.showSnackbar(context.getString(messageRes))
        }
    }

    // Activity Result launcher for RoleManager.createRequestRoleIntent(ROLE_CALL_SCREENING)
    val callScreeningRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val updatedStatus = CallScreeningRoleHelper.getStatus(context)
        viewModel.updateScreeningStatus(updatedStatus)
        val granted = result.resultCode == Activity.RESULT_OK || updatedStatus.isRoleHeld
        val messageRes = if (granted) {
            R.string.snackbar_role_granted
        } else {
            R.string.snackbar_role_denied
        }
        if (granted && BatteryOptimizationHelper.shouldShowGuidedPrompt(
                status = BatteryOptimizationHelper.getStatus(context),
                isPromptDismissed = uiState.spamBlockerSettings.batteryOptimizationPromptDismissed,
                isBackgroundFeatureActive = true
            )
        ) {
            showBatteryRationaleDialog = true
        }
        coroutineScope.launch {
            snackbarHostState.showSnackbar(context.getString(messageRes))
        }
    }

    // Activity Result launcher for Android's Battery Optimization exclusion prompt / settings
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val updatedBatteryStatus = BatteryOptimizationHelper.getStatus(context)
        viewModel.updateBatteryOptimizationStatus(updatedBatteryStatus)
        val msgRes = if (updatedBatteryStatus.isUnrestricted) {
            R.string.snackbar_battery_opt_granted
        } else {
            R.string.snackbar_battery_opt_still_restricted
        }
        coroutineScope.launch {
            snackbarHostState.showSnackbar(context.getString(msgRes))
        }
    }

    val launchBatteryExemptionPrompt: () -> Unit = {
        try {
            batteryOptimizationLauncher.launch(
                BatteryOptimizationHelper.createRequestExemptionIntent(context)
            )
        } catch (_: ActivityNotFoundException) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.snackbar_battery_opt_unavailable)
                )
            }
        }
    }

    val launchBatterySettingsScreen: () -> Unit = {
        try {
            batteryOptimizationLauncher.launch(
                BatteryOptimizationHelper.createBatterySettingsIntent(context)
            )
        } catch (_: ActivityNotFoundException) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.snackbar_battery_opt_unavailable)
                )
            }
        }
    }

    // Activity Result launcher for optional/supplemental phone permissions
    val phonePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        viewModel.updateScreeningStatus(CallScreeningRoleHelper.getStatus(context))
        viewModel.refreshDeviceCallHistory()
        val allGranted = grantResults.values.all { it }
        val messageRes = if (allGranted) {
            R.string.snackbar_permissions_granted
        } else {
            R.string.snackbar_permissions_denied
        }
        coroutineScope.launch {
            snackbarHostState.showSnackbar(context.getString(messageRes))
        }
    }

    // Activity Result launcher for Android 13+ POST_NOTIFICATIONS permission
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateNotificationPermissionStatus(granted)
        if (granted) {
            CallNotificationManager.ensureChannels(context)
            viewModel.setNotificationsEnabled(true)
        }
        val msgRes = if (granted) {
            R.string.snackbar_notifications_granted
        } else {
            R.string.snackbar_notifications_denied
        }
        coroutineScope.launch {
            snackbarHostState.showSnackbar(context.getString(msgRes))
        }
    }

    var includeBlockedNumbersInBackup by remember { mutableStateOf(true) }
    var includeCallSettingsInBackup by remember { mutableStateOf(true) }
    var includeBlockedHistoryInBackup by remember { mutableStateOf(true) }
    var includeContactsInBackup by remember { mutableStateOf(true) }
    var pendingExportAfterContactsPrompt by remember { mutableStateOf(false) }

    // Zero-permission Storage Access Framework launcher for saving local JSON backup files
    val createBackupDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { destinationUri ->
        if (destinationUri != null) {
            viewModel.exportBackupToUri(
                destinationUri = destinationUri,
                includeBlockedNumbers = includeBlockedNumbersInBackup,
                includeCallSettings = includeCallSettingsInBackup,
                includeBlockedHistory = includeBlockedHistoryInBackup,
                includeContacts = includeContactsInBackup
            )
        }
    }

    // Zero-permission Storage Access Framework launcher for restoring a local JSON backup file
    val openBackupDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { sourceUri ->
        if (sourceUri != null) {
            viewModel.restoreBackupFromUri(sourceUri)
        }
    }

    // Contacts permission launcher for Data Backup card (auto-continues export if triggered by Export)
    val backupContactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshDeviceContacts()
        if (pendingExportAfterContactsPrompt) {
            pendingExportAfterContactsPrompt = false
            createBackupDocumentLauncher.launch(viewModel.createSuggestedBackupFileName())
        }
    }

    // Contacts permission launcher for Do Not Disturb (Contacts Only) mode
    val dndContactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshDeviceContacts()
        if (granted) {
            viewModel.setDoNotDisturbEnabled(true)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.snackbar_dnd_contacts_required)
                )
            }
        }
    }

    // Microphone permission launcher for Call Recording
    var pendingEnableRecordingAfterMicPrompt by remember { mutableStateOf(false) }
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateRecordAudioPermissionStatus(granted)
        if (granted) {
            if (pendingEnableRecordingAfterMicPrompt) {
                viewModel.setCallRecordingEnabled(true)
                if (BatteryOptimizationHelper.shouldShowGuidedPrompt(
                        status = BatteryOptimizationHelper.getStatus(context),
                        isPromptDismissed = uiState.spamBlockerSettings.batteryOptimizationPromptDismissed,
                        isBackgroundFeatureActive = true
                    )
                ) {
                    showBatteryRationaleDialog = true
                }
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.snackbar_mic_permission_required)
                )
            }
        }
        pendingEnableRecordingAfterMicPrompt = false
    }

    // Storage Access Framework launcher for exporting a saved .m4a call recording
    var pendingExportRecording by remember { mutableStateOf<CallRecordingEntity?>(null) }
    val exportRecordingDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/mp4")
    ) { destinationUri ->
        val recording = pendingExportRecording
        pendingExportRecording = null
        if (destinationUri != null && recording != null) {
            viewModel.exportCallRecordingToUri(recording, destinationUri)
        }
    }

    val startJsonBackupExport: () -> Unit = {
        if (includeContactsInBackup && !uiState.hasContactsPermission) {
            pendingExportAfterContactsPrompt = true
            backupContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        } else {
            createBackupDocumentLauncher.launch(viewModel.createSuggestedBackupFileName())
        }
    }

    // Observe one-off ViewModel events (prefix added / deleted with Undo / backup status)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CallGuardEvent.PrefixAdded -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.snackbar_prefix_added, event.prefix),
                        duration = SnackbarDuration.Short
                    )
                }
                is CallGuardEvent.PrefixDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(
                            R.string.snackbar_prefix_deleted,
                            event.entity.prefixString
                        ),
                        actionLabel = context.getString(R.string.snackbar_undo),
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete(event.entity)
                    }
                }
                is CallGuardEvent.BlockedCallDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(
                            R.string.snackbar_log_deleted,
                            event.log.phoneNumber
                        ),
                        actionLabel = context.getString(R.string.snackbar_undo),
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDeleteBlockedCall(event.log)
                    }
                }
                is CallGuardEvent.PrefilledBlockPrefix -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(
                            R.string.snackbar_prefilled_block_prefix,
                            event.prefix
                        ),
                        duration = SnackbarDuration.Short
                    )
                }
                is CallGuardEvent.SwipeNumberBlocked -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(
                            R.string.snackbar_swipe_blocked,
                            event.entity.prefixString
                        ),
                        actionLabel = context.getString(R.string.snackbar_undo),
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoSwipeBlock(event.entity)
                    }
                }
                is CallGuardEvent.SwipeNumberAlreadyBlocked -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(
                            R.string.snackbar_swipe_already_blocked,
                            event.number
                        ),
                        duration = SnackbarDuration.Short
                    )
                }
                is CallGuardEvent.CallHistoryEntryDeleted -> {
                    val label = event.entry.cachedName
                        ?: event.entry.number.ifBlank { context.getString(R.string.call_unknown_number) }
                    if (event.deletedFromDevice) {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.snackbar_call_history_deleted, label),
                            duration = SnackbarDuration.Short
                        )
                    } else {
                        val result = snackbarHostState.showSnackbar(
                            message = context.getString(R.string.snackbar_call_history_hidden, label),
                            actionLabel = context.getString(R.string.snackbar_undo),
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undoDismissCallHistoryEntry(event.entry)
                        }
                    }
                }
                is CallGuardEvent.SpamBlockerToggled -> {
                    val msgRes = if (event.enabled) {
                        R.string.snackbar_spam_blocker_enabled
                    } else {
                        R.string.snackbar_spam_blocker_paused
                    }
                    snackbarHostState.showSnackbar(
                        message = context.getString(msgRes),
                        duration = SnackbarDuration.Short
                    )
                }
                is CallGuardEvent.DoNotDisturbToggled -> {
                    val msgRes = if (event.enabled) {
                        R.string.snackbar_dnd_enabled
                    } else {
                        R.string.snackbar_dnd_disabled
                    }
                    snackbarHostState.showSnackbar(
                        message = context.getString(msgRes),
                        duration = SnackbarDuration.Short
                    )
                }
                is CallGuardEvent.CallRecordingToggled -> {
                    val msgRes = if (event.enabled) {
                        R.string.snackbar_call_recording_enabled
                    } else {
                        R.string.snackbar_call_recording_disabled
                    }
                    snackbarHostState.showSnackbar(
                        message = context.getString(msgRes),
                        duration = SnackbarDuration.Short
                    )
                }
                is CallGuardEvent.RecordingDeleted -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.snackbar_recording_deleted, event.label),
                        duration = SnackbarDuration.Short
                    )
                }
                is CallGuardEvent.RecordingExported -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.snackbar_recording_exported, event.fileName),
                        duration = SnackbarDuration.Short
                    )
                }
                is CallGuardEvent.RecordingExportFailed -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.snackbar_recording_export_failed, event.reason),
                        duration = SnackbarDuration.Long
                    )
                }
                is CallGuardEvent.RecordingSummarized -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.snackbar_recording_summarized, event.label),
                        duration = SnackbarDuration.Short
                    )
                }
                CallGuardEvent.RecordingsCleared -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.snackbar_recordings_cleared),
                        duration = SnackbarDuration.Short
                    )
                }
                is CallGuardEvent.BackupExported -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(
                            R.string.snackbar_backup_exported_v2,
                            event.result.fileName,
                            event.result.blockedNumberCount,
                            event.result.blockedCallCount,
                            event.result.contactCount
                        ),
                        duration = SnackbarDuration.Long
                    )
                }
                is CallGuardEvent.BackupExportFailed -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(
                            R.string.snackbar_backup_failed,
                            event.reason
                        ),
                        duration = SnackbarDuration.Long
                    )
                }
                is CallGuardEvent.BackupRestored -> {
                    val settingsSuffix = if (event.result.restoredCallSettings) {
                        context.getString(R.string.backup_restored_settings_suffix)
                    } else {
                        ""
                    }
                    snackbarHostState.showSnackbar(
                        message = context.getString(
                            R.string.snackbar_backup_restored,
                            event.result.fileName,
                            event.result.restoredBlockedNumbers,
                            settingsSuffix
                        ),
                        duration = SnackbarDuration.Long
                    )
                }
                is CallGuardEvent.BackupRestoreFailed -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(
                            R.string.snackbar_backup_restore_failed,
                            event.reason
                        ),
                        duration = SnackbarDuration.Long
                    )
                }
                CallGuardEvent.HistoryCleared -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.snackbar_history_cleared),
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        ClearHistoryConfirmationDialog(
            blockedCount = uiState.blockedCallCount,
            onDismiss = { showClearHistoryDialog = false },
            onConfirmClear = {
                showClearHistoryDialog = false
                viewModel.clearBlockedCallHistory()
            }
        )
    }

    if (showBatteryRationaleDialog) {
        BatteryOptimizationRationaleDialog(
            onConfirmExclude = {
                showBatteryRationaleDialog = false
                launchBatteryExemptionPrompt()
            },
            onDismiss = {
                showBatteryRationaleDialog = false
                viewModel.setBatteryOptimizationPromptDismissed(true)
            }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        NavigationBarItem(
                            selected = uiState.selectedTab == CallGuardTab.HOME,
                            onClick = { viewModel.selectTab(CallGuardTab.HOME) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.selectedTab == CallGuardTab.HOME) {
                                        Icons.Rounded.Home
                                    } else {
                                        Icons.Outlined.Home
                                    },
                                    contentDescription = stringResource(R.string.nav_home)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_home)) },
                            modifier = Modifier.testTag("nav_tab_home")
                        )
                        NavigationBarItem(
                            selected = uiState.selectedTab == CallGuardTab.KEYPAD,
                            onClick = { viewModel.selectTab(CallGuardTab.KEYPAD) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.selectedTab == CallGuardTab.KEYPAD) {
                                        Icons.Rounded.Dialpad
                                    } else {
                                        Icons.Outlined.Dialpad
                                    },
                                    contentDescription = stringResource(R.string.nav_keypad)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_keypad)) },
                            modifier = Modifier.testTag("nav_tab_keypad")
                        )
                        NavigationBarItem(
                            selected = uiState.selectedTab == CallGuardTab.SHIELD,
                            onClick = { viewModel.selectTab(CallGuardTab.SHIELD) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.selectedTab == CallGuardTab.SHIELD) {
                                        Icons.Rounded.Security
                                    } else {
                                        Icons.Outlined.Security
                                    },
                                    contentDescription = stringResource(R.string.nav_shield)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_shield)) },
                            modifier = Modifier.testTag("nav_tab_shield")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        NavigationRailItem(
                            selected = uiState.selectedTab == CallGuardTab.HOME,
                            onClick = { viewModel.selectTab(CallGuardTab.HOME) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.selectedTab == CallGuardTab.HOME) {
                                        Icons.Rounded.Home
                                    } else {
                                        Icons.Outlined.Home
                                    },
                                    contentDescription = stringResource(R.string.nav_home)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_home)) },
                            modifier = Modifier.testTag("nav_rail_home")
                        )
                        NavigationRailItem(
                            selected = uiState.selectedTab == CallGuardTab.KEYPAD,
                            onClick = { viewModel.selectTab(CallGuardTab.KEYPAD) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.selectedTab == CallGuardTab.KEYPAD) {
                                        Icons.Rounded.Dialpad
                                    } else {
                                        Icons.Outlined.Dialpad
                                    },
                                    contentDescription = stringResource(R.string.nav_keypad)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_keypad)) },
                            modifier = Modifier.testTag("nav_rail_keypad")
                        )
                        NavigationRailItem(
                            selected = uiState.selectedTab == CallGuardTab.SHIELD,
                            onClick = { viewModel.selectTab(CallGuardTab.SHIELD) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.selectedTab == CallGuardTab.SHIELD) {
                                        Icons.Rounded.Security
                                    } else {
                                        Icons.Outlined.Security
                                    },
                                    contentDescription = stringResource(R.string.nav_shield)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_shield)) },
                            modifier = Modifier.testTag("nav_rail_shield")
                        )
                    }
                }

                when (uiState.selectedTab) {
                    CallGuardTab.SHIELD -> {
                        if (uiState.isBlockedListScreenOpen) {
                            BlockedListScreen(
                                uiState = uiState,
                                onBack = viewModel::closeBlockedListScreen,
                                onMatchTypeSelected = viewModel::onBlockedMatchTypeSelected,
                                onInputChanged = viewModel::onPrefixInputChanged,
                                onLabelChanged = viewModel::onLabelInputChanged,
                                onApplyQuickPrefix = viewModel::applyQuickPrefix,
                                onAddClick = viewModel::addPrefix,
                                onToggleRuleActive = viewModel::togglePrefixActive,
                                onDeleteRule = viewModel::deletePrefix,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (uiState.isCallRecordingsScreenOpen) {
                            CallRecordingsScreen(
                                recordings = uiState.callRecordings,
                                summarizingRecordingIds = uiState.summarizingRecordingIds,
                                playbackState = uiState.recordingPlaybackState,
                                onBack = viewModel::closeCallRecordingsScreen,
                                onTogglePlayPause = viewModel::togglePlayRecording,
                                onSeekTo = viewModel::seekRecordingPlayback,
                                onSummarizeRecording = viewModel::summarizeCallRecording,
                                onResummarizeFromTranscript = viewModel::resummarizeRecordingFromTranscript,
                                onExportRecording = { recording ->
                                    pendingExportRecording = recording
                                    exportRecordingDocumentLauncher.launch(recording.fileName)
                                },
                                onDeleteRecording = viewModel::deleteCallRecording,
                                onClearAllRecordings = viewModel::clearAllCallRecordings,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                            LazyColumn(
                                modifier = Modifier
                                    .widthIn(max = 600.dp)
                                    .fillMaxSize()
                                    .testTag("prefixes_lazy_column"),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Brand Header
                                item(key = "brand_header") {
                                    CallGuardHeader(
                                        activeCount = uiState.activePrefixCount,
                                        totalCount = uiState.prefixes.size
                                    )
                                }

                                // 7-Day Blocked Calls Summary Dashboard at top of UI
                                item(key = "blocked_summary_dashboard") {
                                    BlockedCallsDashboardCard(
                                        summary = uiState.last7DaysSummary,
                                        activeRuleCount = uiState.activePrefixCount
                                    )
                                }

                                // Protection Status Banner + Default Phone (ROLE_DIALER) & Spam (ROLE_CALL_SCREENING) CTAs
                                item(key = "protection_banner") {
                                    ProtectionStatusBanner(
                                        status = uiState.screeningStatus,
                                        onRequestDefaultDialer = {
                                            val dialerIntent =
                                                CallScreeningRoleHelper.createRequestDefaultDialerIntent(
                                                    context
                                                )
                                            if (dialerIntent != null) {
                                                defaultDialerRoleLauncher.launch(dialerIntent)
                                            } else {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        context.getString(
                                                            R.string.snackbar_dialer_role_unavailable
                                                        )
                                                    )
                                                }
                                            }
                                        },
                                        onRequestDefaultRole = {
                                            val roleIntent =
                                                CallScreeningRoleHelper.createRequestCallScreeningRoleIntent(
                                                    context
                                                )
                                            if (roleIntent != null) {
                                                callScreeningRoleLauncher.launch(roleIntent)
                                            } else {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        context.getString(R.string.snackbar_role_unavailable)
                                                    )
                                                }
                                            }
                                        },
                                        onRequestPhonePermissions = {
                                            phonePermissionsLauncher.launch(
                                                CallScreeningRoleHelper.REQUIRED_PHONE_PERMISSIONS
                                            )
                                        }
                                    )
                                }

                                // Battery Optimization Exclusion Card (Background Stability for Screening & Recording)
                                item(key = "battery_optimization_card") {
                                    BatteryOptimizationCard(
                                        status = uiState.batteryOptimizationStatus,
                                        onRequestExemption = { showBatteryRationaleDialog = true },
                                        onOpenBatterySettings = launchBatterySettingsScreen
                                    )
                                }

                                // Do Not Disturb (Contacts Only) Mode Toggle Card
                                item(key = "do_not_disturb_card") {
                                    DoNotDisturbCard(
                                        enabled = uiState.spamBlockerSettings.doNotDisturbEnabled,
                                        hasContactsPermission = uiState.hasContactsPermission,
                                        onToggle = { enable ->
                                            if (enable && !uiState.hasContactsPermission) {
                                                dndContactsPermissionLauncher.launch(
                                                    Manifest.permission.READ_CONTACTS
                                                )
                                            } else {
                                                viewModel.setDoNotDisturbEnabled(enable)
                                            }
                                        },
                                        onRequestContactsPermission = {
                                            dndContactsPermissionLauncher.launch(
                                                Manifest.permission.READ_CONTACTS
                                            )
                                        }
                                    )
                                }

                                // Spam Call Blocker Master Switch & Real-Time Protection Layers
                                item(key = "spam_blocker_controls") {
                                    SpamBlockerControlsCard(
                                        settings = uiState.spamBlockerSettings,
                                        onToggleMaster = viewModel::setSpamBlockerEnabled,
                                        onToggleBuiltInSeries = viewModel::setBlockBuiltInSpamSeries,
                                        onToggleHiddenPrivate = viewModel::setBlockHiddenPrivateCalls,
                                        onToggleSpoofedStirShaken = viewModel::setBlockSpoofedStirShakenCalls,
                                        onToggleSilenceOnly = viewModel::setSilenceOnlyMode
                                    )
                                }

                                // Call Recording Settings & Saved Recordings Library Entry Card
                                item(key = "call_recording_controls") {
                                    CallRecordingControlCard(
                                        settings = uiState.spamBlockerSettings,
                                        hasRecordAudioPermission = uiState.hasRecordAudioPermission,
                                        recordingCount = uiState.callRecordingCount,
                                        totalSizeBytes = uiState.callRecordingsTotalBytes,
                                        isBatteryOptimizationUnrestricted = uiState.batteryOptimizationStatus.isUnrestricted,
                                        onRequestBatteryOptimization = { showBatteryRationaleDialog = true },
                                        onToggleEnabled = { enable ->
                                            if (enable && !uiState.hasRecordAudioPermission) {
                                                pendingEnableRecordingAfterMicPrompt = true
                                                recordAudioPermissionLauncher.launch(
                                                    Manifest.permission.RECORD_AUDIO
                                                )
                                            } else {
                                                viewModel.setCallRecordingEnabled(enable)
                                                if (enable && BatteryOptimizationHelper.shouldShowGuidedPrompt(
                                                        status = uiState.batteryOptimizationStatus,
                                                        isPromptDismissed = uiState.spamBlockerSettings.batteryOptimizationPromptDismissed,
                                                        isBackgroundFeatureActive = true
                                                    )
                                                ) {
                                                    showBatteryRationaleDialog = true
                                                }
                                            }
                                        },
                                        onToggleAutoRecord = viewModel::setAutoRecordCalls,
                                        onRequestMicPermission = {
                                            pendingEnableRecordingAfterMicPrompt = true
                                            recordAudioPermissionLauncher.launch(
                                                Manifest.permission.RECORD_AUDIO
                                            )
                                        },
                                        onOpenRecordingsLibrary = viewModel::openCallRecordingsScreen
                                    )
                                }

                                // Ringtone, Vibration & Notifications Controls Card
                                item(key = "call_alerts_settings") {
                                    CallAlertsSettingsCard(
                                        settings = uiState.spamBlockerSettings,
                                        hasNotificationPermission = uiState.hasNotificationPermission,
                                        onToggleRingtone = viewModel::setRingtoneEnabled,
                                        onToggleVibration = viewModel::setVibrationEnabled,
                                        onToggleNotifications = viewModel::setNotificationsEnabled,
                                        onRequestNotificationPermission = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                notificationPermissionLauncher.launch(
                                                    Manifest.permission.POST_NOTIFICATIONS
                                                )
                                            } else {
                                                viewModel.updateNotificationPermissionStatus(true)
                                                viewModel.setNotificationsEnabled(true)
                                            }
                                        }
                                    )
                                }

                                // Local JSON Data Backup & Restore Card
                                item(key = "data_backup_export") {
                                    DataBackupExportCard(
                                        blockedNumberCount = uiState.prefixes.size,
                                        blockedCallCount = uiState.blockedCallCount,
                                        contactCount = uiState.deviceContacts.size,
                                        hasContactsPermission = uiState.hasContactsPermission,
                                        includeBlockedNumbers = includeBlockedNumbersInBackup,
                                        includeCallSettings = includeCallSettingsInBackup,
                                        includeBlockedHistory = includeBlockedHistoryInBackup,
                                        includeContacts = includeContactsInBackup,
                                        isExporting = uiState.isExportingBackup,
                                        isRestoring = uiState.isRestoringBackup,
                                        lastBackupResult = uiState.lastBackupResult,
                                        lastRestoreResult = uiState.lastRestoreResult,
                                        onToggleIncludeBlockedNumbers = {
                                            includeBlockedNumbersInBackup = it
                                        },
                                        onToggleIncludeCallSettings = {
                                            includeCallSettingsInBackup = it
                                        },
                                        onToggleIncludeBlockedHistory = {
                                            includeBlockedHistoryInBackup = it
                                        },
                                        onToggleIncludeContacts = {
                                            includeContactsInBackup = it
                                        },
                                        onRequestContactsPermission = {
                                            pendingExportAfterContactsPrompt = false
                                            backupContactsPermissionLauncher.launch(
                                                Manifest.permission.READ_CONTACTS
                                            )
                                        },
                                        onExportClick = startJsonBackupExport,
                                        onRestoreClick = {
                                            openBackupDocumentLauncher.launch(
                                                arrayOf("application/json", "text/plain", "*/*")
                                            )
                                        }
                                    )
                                }

                                // Dedicated Blocked List Screen Entry Card (Numbers & Prefixes)
                                item(key = "blocked_list_nav_card") {
                                    BlockedListNavigationCard(
                                        totalCount = uiState.prefixes.size,
                                        activeCount = uiState.activePrefixCount,
                                        exactNumberCount = uiState.exactBlockedNumberCount,
                                        prefixCount = uiState.blockedPrefixSeriesCount,
                                        onOpenBlockedList = viewModel::openBlockedListScreen
                                    )
                                }

                                // Blocked Call History Section Header
                                item(key = "blocked_history_header") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    BlockedCallHistoryHeader(
                                        blockedCount = uiState.blockedCallCount,
                                        onExportClick = startJsonBackupExport,
                                        onClearAllClick = { showClearHistoryDialog = true }
                                    )
                                }

                                // Empty History State OR Logged Blocked Calls
                                if (uiState.blockedCallLogs.isEmpty()) {
                                    item(key = "empty_history_state") {
                                        EmptyBlockedHistoryCard()
                                    }
                                } else {
                                    items(
                                        items = uiState.blockedCallLogs,
                                        key = { "history_${it.id}" }
                                    ) { blockedLog ->
                                        BlockedCallLogRow(
                                            log = blockedLog,
                                            onDelete = { viewModel.deleteBlockedCall(blockedLog) },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }

                                item(key = "bottom_spacer") {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                        }
                    }

                    CallGuardTab.HOME -> {
                        HomeContactsScreen(
                            uiState = uiState,
                            onContactsSearchChanged = viewModel::onContactsSearchChanged,
                            onRefreshContacts = viewModel::refreshDeviceContacts,
                            onCallHistoryFilterSelected = viewModel::onCallHistoryFilterSelected,
                            onCallHistorySearchChanged = viewModel::onCallHistorySearchChanged,
                            onRefreshCallHistory = viewModel::refreshDeviceCallHistory,
                            onQuickBlockNumber = viewModel::prefillBlockPrefixFromCall,
                            onSwipeBlockNumber = viewModel::quickBlockNumberFromSwipe,
                            onDeleteCallHistoryEntry = viewModel::deleteCallHistoryEntry,
                            onShowMessage = { message ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    CallGuardTab.KEYPAD -> {
                        KeypadScreen(
                            uiState = uiState,
                            onDialerDigitPressed = viewModel::onDialerDigitPressed,
                            onDialerNumberSelected = viewModel::onDialerNumberSelected,
                            onDialerBackspace = viewModel::onDialerBackspace,
                            onDialerClear = viewModel::onDialerClear,
                            onQuickBlockNumber = viewModel::prefillBlockPrefixFromCall,
                            onShowMessage = { message ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallGuardHeader(
    activeCount: Int,
    totalCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = stringResource(R.string.cd_shield_icon),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.app_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Text(
                text = stringResource(R.string.stat_active_rules, activeCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ProtectionStatusBanner(
    status: CallScreeningStatus,
    onRequestDefaultDialer: () -> Unit,
    onRequestDefaultRole: () -> Unit,
    onRequestPhonePermissions: () -> Unit
) {
    val isFullControl = status.hasFullTelecomControl
    val isProtected = status.isRoleHeld
    val isDefaultDialer = status.isDefaultDialer
    val statusBadgeColor = if (isFullControl) ShieldActiveEmerald else ShieldWarningAmber
    val heroColors = LocalCallGuardHeroColors.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = heroColors.deepSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Custom generated security radar banner background
            Image(
                painter = painterResource(id = R.drawable.img_shield_banner),
                contentDescription = stringResource(R.string.status_shield_banner_cd),
                contentScale = ContentScale.Crop,
                alpha = 0.55f,
                modifier = Modifier.matchParentSize()
            )

            // High-contrast scrim harmonized with the user's dynamic wallpaper theme
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                heroColors.deepSurface.copy(alpha = 0.94f),
                                heroColors.midSurface.copy(alpha = 0.80f),
                                heroColors.deepSurface.copy(alpha = 0.55f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status Pill
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusBadgeColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, statusBadgeColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isFullControl) {
                                Icons.Rounded.GppGood
                            } else {
                                Icons.Rounded.GppBad
                            },
                            contentDescription = null,
                            tint = statusBadgeColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when {
                                isFullControl -> stringResource(R.string.status_full_control_title)
                                isProtected -> stringResource(R.string.status_active_title)
                                !status.isRoleSupported -> stringResource(R.string.status_unsupported_title)
                                else -> stringResource(R.string.status_inactive_title)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                // Explanatory Copy
                Text(
                    text = when {
                        isFullControl -> stringResource(R.string.status_full_control_subtitle)
                        isProtected -> stringResource(R.string.status_active_subtitle)
                        !status.isRoleSupported -> stringResource(R.string.status_unsupported_subtitle)
                        else -> stringResource(R.string.status_inactive_subtitle)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.86f)
                )

                // Primary Action 1: Request ROLE_DIALER (Default Phone / Dialer App)
                Button(
                    onClick = onRequestDefaultDialer,
                    enabled = status.isDialerRoleAvailable && !isDefaultDialer,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDefaultDialer) ShieldActiveEmerald else Color.White,
                        contentColor = if (isDefaultDialer) Color.White else heroColors.deepSurface,
                        disabledContainerColor = ShieldActiveEmerald.copy(alpha = 0.24f),
                        disabledContentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("request_default_dialer_button")
                ) {
                    Icon(
                        imageVector = if (isDefaultDialer) {
                            Icons.Rounded.VerifiedUser
                        } else {
                            Icons.Rounded.Call
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDefaultDialer) {
                            stringResource(R.string.btn_default_dialer_active)
                        } else {
                            stringResource(R.string.btn_set_default_dialer)
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Primary Action 2: Request ROLE_CALL_SCREENING (Default Caller ID & Spam App)
                Button(
                    onClick = onRequestDefaultRole,
                    enabled = status.isRoleSupported && !isProtected,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isProtected) ShieldActiveEmerald else Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White,
                        disabledContainerColor = ShieldActiveEmerald.copy(alpha = 0.24f),
                        disabledContentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("request_call_screening_role_button")
                ) {
                    Icon(
                        imageVector = if (isProtected) {
                            Icons.Rounded.VerifiedUser
                        } else {
                            Icons.Rounded.Security
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isProtected) {
                            stringResource(R.string.btn_default_screener_active)
                        } else {
                            stringResource(R.string.btn_set_default_screener)
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Supplemental Phone Permissions Status Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .then(
                            if (!status.hasPhonePermissions) {
                                Modifier.clickable(onClick = onRequestPhonePermissions)
                            } else {
                                Modifier
                            }
                        )
                        .minimumInteractiveComponentSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("phone_permissions_row"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (status.hasPhonePermissions) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Outlined.PhoneInTalk
                        },
                        contentDescription = null,
                        tint = if (status.hasPhonePermissions) {
                            ShieldActiveEmerald
                        } else {
                            Color.White.copy(alpha = 0.85f)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (status.hasPhonePermissions) {
                            stringResource(R.string.permissions_granted_label)
                        } else {
                            stringResource(R.string.btn_grant_phone_permissions)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
