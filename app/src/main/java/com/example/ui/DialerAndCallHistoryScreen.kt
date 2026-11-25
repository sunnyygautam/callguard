package com.example.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.repository.DeviceCallLogEntry
import com.example.data.repository.DeviceCallType
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PrefixMonoStyle
import com.example.ui.theme.ShieldActiveEmerald

private data class DialKey(
    val digit: String,
    val letters: String,
    val longPressDigit: String? = null
)

private val DIALPAD_ROWS = listOf(
    listOf(DialKey("1", ""), DialKey("2", "ABC"), DialKey("3", "DEF")),
    listOf(DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO")),
    listOf(DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ")),
    listOf(DialKey("*", ""), DialKey("0", "+", longPressDigit = "+"), DialKey("#", ""))
)

/**
 * Calling (Dialpad) & Device Call History screen integrated as the Calls tab.
 */
@Composable
internal fun DialerAndCallHistoryScreen(
    uiState: CallGuardUiState,
    onDialerDigitPressed: (String) -> Unit,
    onDialerBackspace: () -> Unit,
    onDialerClear: () -> Unit,
    onFilterSelected: (CallHistoryFilter) -> Unit,
    onRefreshCallHistory: () -> Unit,
    onQuickBlockNumber: (String, String?) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isKeypadExpanded by remember { mutableStateOf(true) }
    var pendingDirectCallNumber by remember { mutableStateOf<String?>(null) }

    // Runtime permission launcher for CALL_PHONE (falls back to ACTION_DIAL if denied)
    val callPhonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val numberToCall = pendingDirectCallNumber
        pendingDirectCallNumber = null
        if (!numberToCall.isNullOrBlank()) {
            launchPhoneCall(
                context = context,
                phoneNumber = numberToCall,
                useDirectCall = isGranted,
                onShowMessage = onShowMessage
            )
        }
    }

    // Runtime permission launcher for READ_CALL_LOG
    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onRefreshCallHistory()
        if (!isGranted) {
            onShowMessage(context.getString(R.string.snackbar_permissions_denied))
        }
    }

    fun initiateCall(rawNumber: String) {
        val cleaned = rawNumber.trim()
        if (cleaned.isEmpty()) {
            onShowMessage(context.getString(R.string.dialer_error_empty))
            return
        }
        val hasCallPhone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCallPhone) {
            launchPhoneCall(
                context = context,
                phoneNumber = cleaned,
                useDirectCall = true,
                onShowMessage = onShowMessage
            )
        } else {
            pendingDirectCallNumber = cleaned
            callPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    val filteredCalls = remember(uiState.deviceCallLogs, uiState.callHistoryFilter) {
        when (uiState.callHistoryFilter) {
            CallHistoryFilter.ALL -> uiState.deviceCallLogs
            CallHistoryFilter.INCOMING -> uiState.deviceCallLogs.filter {
                it.type == DeviceCallType.INCOMING
            }
            CallHistoryFilter.OUTGOING -> uiState.deviceCallLogs.filter {
                it.type == DeviceCallType.OUTGOING
            }
            CallHistoryFilter.MISSED -> uiState.deviceCallLogs.filter {
                it.type == DeviceCallType.MISSED || it.type == DeviceCallType.REJECTED
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .testTag("calls_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item(key = "calls_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhoneInTalk,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.dialer_section_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.dialer_section_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Dialer Card
            item(key = "dialer_card") {
                DialerCard(
                    dialerInput = uiState.dialerInput,
                    isKeypadExpanded = isKeypadExpanded,
                    onToggleKeypad = { isKeypadExpanded = !isKeypadExpanded },
                    onDigitPressed = onDialerDigitPressed,
                    onBackspace = onDialerBackspace,
                    onClear = onDialerClear,
                    onPlaceCall = { initiateCall(uiState.dialerInput) },
                    onQuickBlock = {
                        if (uiState.dialerInput.isNotBlank()) {
                            onQuickBlockNumber(uiState.dialerInput, null)
                        }
                    }
                )
            }

            // Recent Call History Header + Filters
            item(key = "call_history_header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.call_history_header),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        IconButton(
                            onClick = onRefreshCallHistory,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("refresh_call_history_button")
                        ) {
                            if (uiState.isLoadingCallHistory) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.call_history_refresh_cd),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CallHistoryFilter.entries.forEach { filter ->
                            val labelRes = when (filter) {
                                CallHistoryFilter.ALL -> R.string.call_history_filter_all
                                CallHistoryFilter.INCOMING -> R.string.call_history_filter_incoming
                                CallHistoryFilter.OUTGOING -> R.string.call_history_filter_outgoing
                                CallHistoryFilter.MISSED -> R.string.call_history_filter_missed
                            }
                            FilterChip(
                                selected = uiState.callHistoryFilter == filter,
                                onClick = { onFilterSelected(filter) },
                                label = { Text(stringResource(labelRes)) },
                                modifier = Modifier.testTag("call_filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Permission Gate OR Call Log Rows OR Empty State
            if (!uiState.hasCallLogPermission) {
                item(key = "call_log_permission_card") {
                    CallLogPermissionCard(
                        onGrantPermission = {
                            callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                        }
                    )
                }
            } else if (filteredCalls.isEmpty() && !uiState.isLoadingCallHistory) {
                item(key = "empty_device_call_history") {
                    EmptyDeviceCallHistoryCard()
                }
            } else {
                items(
                    items = filteredCalls,
                    key = { "device_call_${it.id}" }
                ) { entry ->
                    DeviceCallLogRow(
                        entry = entry,
                        onCallBack = { initiateCall(entry.number) },
                        onBlockPrefix = { onQuickBlockNumber(entry.number, entry.cachedName) },
                        modifier = Modifier.animateItem()
                    )
                }
            }

            item(key = "calls_bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialerCard(
    dialerInput: String,
    isKeypadExpanded: Boolean,
    onToggleKeypad: () -> Unit,
    onDigitPressed: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onPlaceCall: () -> Unit,
    onQuickBlock: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dialer_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Number Display Bar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = dialerInput.ifEmpty { stringResource(R.string.dialer_input_placeholder) },
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = if (dialerInput.isEmpty()) 17.sp else 24.sp
                        ),
                        color = if (dialerInput.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialer_number_display")
                    )

                    if (dialerInput.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = onBackspace,
                                    onLongClick = onClear
                                )
                                .padding(10.dp)
                                .testTag("dialer_backspace_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                                contentDescription = stringResource(R.string.dialer_cd_backspace),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // Collapsible 3x4 Numeric Keypad
            AnimatedVisibility(visible = isKeypadExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DIALPAD_ROWS.forEach { rowKeys ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { dialKey ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 54.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .combinedClickable(
                                            onClick = { onDigitPressed(dialKey.digit) },
                                            onLongClick = {
                                                onDigitPressed(dialKey.longPressDigit ?: dialKey.digit)
                                            }
                                        )
                                        .testTag("dial_key_${dialKey.digit}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = dialKey.digit,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (dialKey.letters.isNotEmpty()) {
                                            Text(
                                                text = dialKey.letters,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Action Row: Call CTA + Block Prefix + Toggle Keypad
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlaceCall,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ShieldActiveEmerald,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag("dialer_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.dialer_btn_call),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (dialerInput.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onQuickBlock,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .heightIn(min = 50.dp)
                            .testTag("dialer_block_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Block,
                            contentDescription = stringResource(
                                R.string.dialer_cd_block_number,
                                dialerInput
                            ),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onToggleKeypad,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 50.dp)
                        .testTag("toggle_keypad_button")
                ) {
                    Icon(
                        imageVector = if (isKeypadExpanded) {
                            Icons.Rounded.ExpandLess
                        } else {
                            Icons.Outlined.Dialpad
                        },
                        contentDescription = if (isKeypadExpanded) {
                            stringResource(R.string.dialer_btn_hide_keypad)
                        } else {
                            stringResource(R.string.dialer_btn_show_keypad)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCallLogRow(
    entry: DeviceCallLogEntry,
    onCallBack: () -> Unit,
    onBlockPrefix: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (typeIcon, typeTint, typeBg, typeLabelRes) = when (entry.type) {
        DeviceCallType.INCOMING -> Quad(
            Icons.AutoMirrored.Rounded.CallReceived,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondaryContainer,
            R.string.call_type_incoming
        )
        DeviceCallType.OUTGOING -> Quad(
            Icons.AutoMirrored.Rounded.CallMade,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer,
            R.string.call_type_outgoing
        )
        DeviceCallType.MISSED -> Quad(
            Icons.AutoMirrored.Rounded.CallMissed,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer,
            R.string.call_type_missed
        )
        DeviceCallType.REJECTED -> Quad(
            Icons.Rounded.PhoneDisabled,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer,
            R.string.call_type_rejected
        )
    }

    val formattedTime = remember(entry.dateMillis) { formatBlockedTimestamp(entry.dateMillis) }
    val formattedDuration = remember(entry.durationSeconds) { formatCallDuration(entry.durationSeconds) }
    val displayTitle = entry.cachedName ?: entry.number.ifBlank {
        stringResource(R.string.call_unknown_number)
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_call_row_${entry.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(typeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = stringResource(typeLabelRes),
                    tint = typeTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = displayTitle,
                    style = if (entry.cachedName == null) PrefixMonoStyle else MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (entry.cachedName != null && entry.number.isNotBlank()) {
                    Text(
                        text = entry.number,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val metaText = buildString {
                    append(stringResource(typeLabelRes))
                    append(" • ")
                    append(formattedTime)
                    if (formattedDuration != null) {
                        append(" • ")
                        append(formattedDuration)
                    }
                }
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (entry.number.isNotBlank()) {
                IconButton(
                    onClick = onBlockPrefix,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("call_row_block_${entry.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Block,
                        contentDescription = stringResource(
                            R.string.dialer_cd_block_number,
                            entry.number
                        ),
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                IconButton(
                    onClick = onCallBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("call_row_call_${entry.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = stringResource(
                            R.string.dialer_cd_call_number,
                            entry.number
                        ),
                        tint = ShieldActiveEmerald
                    )
                }
            }
        }
    }
}

@Composable
private fun CallLogPermissionCard(
    onGrantPermission: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("call_log_permission_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = stringResource(R.string.call_history_permission_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.call_history_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onGrantPermission,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("grant_call_log_button")
            ) {
                Text(text = stringResource(R.string.call_history_grant_permission))
            }
        }
    }
}

@Composable
private fun EmptyDeviceCallHistoryCard() {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_device_call_history"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp)
            )
            Text(
                text = stringResource(R.string.call_history_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.call_history_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun formatCallDuration(durationSeconds: Long): String? {
    if (durationSeconds <= 0L) return null
    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val seconds = durationSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

private fun launchPhoneCall(
    context: Context,
    phoneNumber: String,
    useDirectCall: Boolean,
    onShowMessage: (String) -> Unit
) {
    val action = if (useDirectCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
    val intent = Intent(action, Uri.parse("tel:${Uri.encode(phoneNumber)}")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
        if (!useDirectCall) {
            onShowMessage(context.getString(R.string.dialer_opening_system_dialer, phoneNumber))
        }
    } catch (_: SecurityException) {
        // Fallback to ACTION_DIAL if system revoked CALL_PHONE
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phoneNumber)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(dialIntent)
        } catch (_: ActivityNotFoundException) {
            onShowMessage(context.getString(R.string.dialer_no_phone_app))
        }
    } catch (_: ActivityNotFoundException) {
        onShowMessage(context.getString(R.string.dialer_no_phone_app))
    }
}
