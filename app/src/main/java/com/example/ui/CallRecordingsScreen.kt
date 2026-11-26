package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CallRecordingEntity
import com.example.data.repository.SpamBlockerSettings
import com.example.telecom.CallAudioRecorder
import com.example.telecom.RecordingPlaybackState
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ShieldActiveEmerald
import com.example.ui.theme.ShieldWarningAmber
import java.text.DateFormat
import java.util.Date

private enum class RecordingsFilterMode {
    ALL,
    INCOMING,
    OUTGOING
}

/**
 * Call Recording settings and library entry card rendered on the Shield tab.
 */
@Composable
fun CallRecordingControlCard(
    settings: SpamBlockerSettings,
    hasRecordAudioPermission: Boolean,
    recordingCount: Int,
    totalSizeBytes: Long,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleAutoRecord: (Boolean) -> Unit,
    onRequestMicPermission: () -> Unit,
    onOpenRecordingsLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    isBatteryOptimizationUnrestricted: Boolean = true,
    onRequestBatteryOptimization: () -> Unit = {}
) {
    val isEnabled = settings.callRecordingEnabled && hasRecordAudioPermission
    val switchCd = stringResource(R.string.call_recording_cd_master_switch)
    val openLibraryCd = stringResource(R.string.call_recording_open_library_cd)

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("call_recording_control_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isEnabled) {
                CrimsonError.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header row + Master Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isEnabled) {
                                CrimsonError.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FiberManualRecord,
                        contentDescription = null,
                        tint = if (isEnabled) {
                            CrimsonError
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.call_recording_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (isEnabled) {
                                CrimsonError.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = stringResource(
                                    if (isEnabled) R.string.call_recording_status_on
                                    else R.string.call_recording_status_off
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isEnabled) {
                                    CrimsonError
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = stringResource(
                            if (isEnabled) R.string.call_recording_subtitle_on
                            else R.string.call_recording_subtitle_off
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onError,
                        checkedTrackColor = CrimsonError
                    ),
                    modifier = Modifier
                        .semantics { contentDescription = switchCd }
                        .testTag("call_recording_master_switch")
                )
            }

            // Permission Banner if RECORD_AUDIO not yet granted
            if (!hasRecordAudioPermission) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.call_recording_permission_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onRequestMicPermission,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("call_recording_grant_mic_button")
                        ) {
                            Text(
                                text = stringResource(R.string.call_recording_btn_grant_mic),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // Auto-Record Toggle Row (visible when enabled)
            AnimatedVisibility(visible = isEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.call_recording_auto_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.call_recording_auto_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoRecordCalls,
                            onCheckedChange = onToggleAutoRecord,
                            modifier = Modifier.testTag("call_recording_auto_switch")
                        )
                    }

                    if (!isBatteryOptimizationUnrestricted) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ShieldWarningAmber.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, ShieldWarningAmber.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BatteryAlert,
                                    contentDescription = null,
                                    tint = ShieldWarningAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.battery_opt_recording_banner),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = onRequestBatteryOptimization,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("recording_battery_opt_exclude_button")
                                ) {
                                    Text(
                                        text = stringResource(R.string.battery_opt_recording_banner_cta),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Saved Recordings Library Entry Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))
                    .clickable(onClick = onOpenRecordingsLibrary)
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .semantics { contentDescription = openLibraryCd }
                    .testTag("open_call_recordings_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.call_recording_view_library_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (recordingCount == 0) {
                            stringResource(R.string.call_recording_view_library_empty)
                        } else {
                            stringResource(
                                R.string.call_recording_view_library_count,
                                recordingCount,
                                CallAudioRecorder.formatFileSize(totalSizeBytes)
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Dedicated Call Recordings screen inside the Shield tab providing search, direction filtering,
 * built-in audio playback with seek controls, SAF .m4a export, and recording deletion.
 */
@Composable
fun CallRecordingsScreen(
    recordings: List<CallRecordingEntity>,
    summarizingRecordingIds: Set<Long>,
    playbackState: RecordingPlaybackState,
    onBack: () -> Unit,
    onTogglePlayPause: (CallRecordingEntity) -> Unit,
    onSeekTo: (Long) -> Unit,
    onSummarizeRecording: (CallRecordingEntity) -> Unit,
    onResummarizeFromTranscript: (CallRecordingEntity, String) -> Unit,
    onExportRecording: (CallRecordingEntity) -> Unit,
    onDeleteRecording: (CallRecordingEntity) -> Unit,
    onClearAllRecordings: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf(RecordingsFilterMode.ALL) }
    var showClearDialog by remember { mutableStateOf(false) }
    var activeSummaryRecordingId by remember { mutableStateOf<Long?>(null) }

    val totalBytes = remember(recordings) { recordings.sumOf { it.fileSizeBytes } }
    val activeSummaryRecording = remember(recordings, activeSummaryRecordingId) {
        recordings.firstOrNull { it.id == activeSummaryRecordingId }
    }

    val filteredRecordings = remember(recordings, searchQuery, filterMode) {
        val query = searchQuery.trim().lowercase()
        recordings.filter { rec ->
            val matchesDirection = when (filterMode) {
                RecordingsFilterMode.ALL -> true
                RecordingsFilterMode.INCOMING -> rec.isIncoming
                RecordingsFilterMode.OUTGOING -> !rec.isIncoming
            }
            val matchesQuery = query.isEmpty() ||
                rec.phoneNumber.lowercase().contains(query) ||
                rec.callerName.lowercase().contains(query) ||
                rec.fileName.lowercase().contains(query) ||
                rec.summaryText.lowercase().contains(query) ||
                rec.transcript.lowercase().contains(query)
            matchesDirection && matchesQuery
        }
    }

    activeSummaryRecording?.let { recording ->
        CallSummaryDetailDialog(
            recording = recording,
            isSummarizing = recording.id in summarizingRecordingIds,
            onDismiss = { activeSummaryRecordingId = null },
            onReanalyzeAudio = { onSummarizeRecording(recording) },
            onResummarizeText = { updatedTranscript ->
                onResummarizeFromTranscript(recording, updatedTranscript)
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.call_recordings_dialog_clear_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.call_recordings_dialog_clear_body,
                        recordings.size
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearAllRecordings()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.call_recordings_dialog_confirm_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .testTag("call_recordings_screen_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Bar
            item(key = "recordings_top_bar") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("call_recordings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.blocked_list_back_cd)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.call_recordings_screen_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(
                                R.string.call_recordings_screen_subtitle,
                                recordings.size,
                                CallAudioRecorder.formatFileSize(totalBytes)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (recordings.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearDialog = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.testTag("clear_all_recordings_button")
                        ) {
                            Text(stringResource(R.string.btn_clear_history))
                        }
                    }
                }
            }

            // Search + Filter Chips (shown when recordings exist)
            if (recordings.isNotEmpty()) {
                item(key = "recordings_search_filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it.take(60) },
                            placeholder = {
                                Text(stringResource(R.string.call_recordings_search_placeholder))
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = stringResource(R.string.cd_clear_input)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("call_recordings_search_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = filterMode == RecordingsFilterMode.ALL,
                                onClick = { filterMode = RecordingsFilterMode.ALL },
                                label = { Text(stringResource(R.string.call_recordings_filter_all)) }
                            )
                            FilterChip(
                                selected = filterMode == RecordingsFilterMode.INCOMING,
                                onClick = { filterMode = RecordingsFilterMode.INCOMING },
                                label = { Text(stringResource(R.string.call_recordings_filter_incoming)) }
                            )
                            FilterChip(
                                selected = filterMode == RecordingsFilterMode.OUTGOING,
                                onClick = { filterMode = RecordingsFilterMode.OUTGOING },
                                label = { Text(stringResource(R.string.call_recordings_filter_outgoing)) }
                            )
                        }
                    }
                }
            }

            // Playback Error Banner if any
            val playbackError = playbackState.errorMessage
            if (!playbackError.isNullOrBlank()) {
                item(key = "playback_error_banner") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = playbackError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Empty States or Recordings List
            if (recordings.isEmpty()) {
                item(key = "recordings_empty_state") {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.call_recordings_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.call_recordings_empty_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (filteredRecordings.isEmpty()) {
                item(key = "recordings_filter_empty") {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = stringResource(R.string.call_recordings_empty_filter),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                items(
                    items = filteredRecordings,
                    key = { "recording_${it.id}" }
                ) { recording ->
                    val isActive = playbackState.activeRecordingId == recording.id
                    CallRecordingItemCard(
                        recording = recording,
                        isActive = isActive,
                        isPlaying = isActive && playbackState.isPlaying,
                        isSummarizing = recording.id in summarizingRecordingIds,
                        currentPositionMs = if (isActive) playbackState.currentPositionMs else 0L,
                        playerDurationMs = if (isActive && playbackState.durationMs > 0L) {
                            playbackState.durationMs
                        } else {
                            recording.durationMillis.coerceAtLeast(1L)
                        },
                        onTogglePlayPause = { onTogglePlayPause(recording) },
                        onSeekTo = onSeekTo,
                        onSummarize = { onSummarizeRecording(recording) },
                        onViewSummary = { activeSummaryRecordingId = recording.id },
                        onExport = { onExportRecording(recording) },
                        onDelete = { onDeleteRecording(recording) },
                        modifier = Modifier.animateItem()
                    )
                }
            }

            item(key = "recordings_bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CallRecordingItemCard(
    recording: CallRecordingEntity,
    isActive: Boolean,
    isPlaying: Boolean,
    isSummarizing: Boolean,
    currentPositionMs: Long,
    playerDurationMs: Long,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSummarize: () -> Unit,
    onViewSummary: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayTitle = recording.callerName.ifBlank { recording.phoneNumber }
    val dateText = remember(recording.recordedAt) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(recording.recordedAt))
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recording_card_${recording.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Play / Pause Circular Control
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlaying) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("recording_play_${recording.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.call_recordings_cd_pause
                            else R.string.call_recordings_cd_play,
                            displayTitle
                        ),
                        tint = if (isPlaying) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = if (recording.callerName.isBlank()) {
                                JetBrainsMonoFontFamily
                            } else {
                                MaterialTheme.typography.titleSmall.fontFamily
                            },
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (recording.callerName.isNotBlank()) {
                        Text(
                            text = recording.phoneNumber,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (recording.isIncoming) {
                                Icons.AutoMirrored.Rounded.CallReceived
                            } else {
                                Icons.AutoMirrored.Rounded.CallMade
                            },
                            contentDescription = null,
                            tint = if (recording.isIncoming) ShieldActiveEmerald else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "$dateText • ${CallAudioRecorder.formatDurationMillis(recording.durationMillis)} • ${CallAudioRecorder.formatFileSize(recording.fileSizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onExport,
                    modifier = Modifier.testTag("recording_export_${recording.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = stringResource(
                            R.string.call_recordings_cd_export,
                            recording.fileName
                        ),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("recording_delete_${recording.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(
                            R.string.call_recordings_cd_delete,
                            displayTitle
                        ),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Interactive Seek Slider when this recording is loaded/playing
            AnimatedVisibility(visible = isActive) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val maxFloat = playerDurationMs.coerceAtLeast(1L).toFloat()
                    Slider(
                        value = currentPositionMs.coerceIn(0L, playerDurationMs).toFloat(),
                        onValueChange = { newPos -> onSeekTo(newPos.toLong()) },
                        valueRange = 0f..maxFloat,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = CallAudioRecorder.formatDurationMillis(currentPositionMs),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CallAudioRecorder.formatDurationMillis(playerDurationMs),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // On-Device Conversation Summary Preview (shown once generated)
            if (recording.hasSummary) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onViewSummary)
                        .testTag("recording_summary_preview_${recording.id}"),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.recording_summary_badge_on_device),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (recording.topicsList.isNotEmpty()) {
                                Spacer(modifier = Modifier.weight(1f))
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                ) {
                                    Text(
                                        text = recording.topicsList.first(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = recording.summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // On-Device Summarize / View Summary Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = if (recording.hasSummary) onViewSummary else onSummarize,
                    enabled = !isSummarizing,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .testTag("recording_summarize_button_${recording.id}")
                ) {
                    if (isSummarizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.recording_btn_summarizing),
                            style = MaterialTheme.typography.labelLarge
                        )
                    } else if (recording.hasSummary) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Subject,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.recording_btn_view_summary),
                            style = MaterialTheme.typography.labelLarge
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.recording_btn_summarize),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallSummaryDetailDialog(
    recording: CallRecordingEntity,
    isSummarizing: Boolean,
    onDismiss: () -> Unit,
    onReanalyzeAudio: () -> Unit,
    onResummarizeText: (String) -> Unit
) {
    val displayTitle = recording.callerName.ifBlank { recording.phoneNumber }
    var editableTranscript by remember(recording.id, recording.summaryGeneratedAt) {
        mutableStateOf(recording.transcript)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 560.dp)
                .testTag("call_summary_detail_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.recording_summary_dialog_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$displayTitle • ${CallAudioRecorder.formatDurationMillis(recording.durationMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_summary_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.dialog_cancel)
                        )
                    }
                }

                // On-Device Privacy & Mode Pill
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            tint = ShieldActiveEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.recording_summary_privacy_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (recording.summaryMode.isNotBlank()) {
                                Text(
                                    text = recording.summaryMode,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Summary Overview
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.recording_summary_section_overview),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Text(
                            text = recording.summaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }

                // Key Points & Action Items
                if (recording.keyPointsList.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.recording_summary_section_keypoints),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            recording.keyPointsList.forEach { point ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = point,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Detected Topics
                if (recording.topicsList.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.recording_summary_section_topics),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recording.topicsList.forEach { topic ->
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = topic,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Editable Transcript / Conversation Notes for On-Device Re-Summarization
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.recording_summary_section_transcript),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editableTranscript,
                        onValueChange = { editableTranscript = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.recording_summary_transcript_placeholder),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recording_summary_transcript_input")
                    )
                }

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onReanalyzeAudio,
                        enabled = !isSummarizing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("reanalyze_audio_button")
                    ) {
                        if (isSummarizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.recording_summary_btn_reanalyze_audio),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = { onResummarizeText(editableTranscript) },
                        enabled = !isSummarizing && editableTranscript.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("resummarize_text_button")
                    ) {
                        Text(
                            text = stringResource(R.string.recording_summary_btn_resummarize_text),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
