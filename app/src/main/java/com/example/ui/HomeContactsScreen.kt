package com.example.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.repository.DeviceCallLogEntry
import com.example.data.repository.DeviceCallType
import com.example.data.repository.DeviceContactEntry
import com.example.telecom.PhoneNumberMatcher
import com.example.ui.theme.PrefixMonoStyle
import com.example.ui.theme.ShieldActiveEmerald

private const val INITIAL_CONTACTS_PREVIEW_LIMIT = 8

/**
 * Home tab featuring the Search Contacts section at the top, followed by Recent Call History
 * with real-time search, type filters, one-tap calling, and quick-block prefix actions.
 */
@Composable
internal fun HomeContactsScreen(
    uiState: CallGuardUiState,
    onContactsSearchChanged: (String) -> Unit,
    onRefreshContacts: () -> Unit,
    onCallHistoryFilterSelected: (CallHistoryFilter) -> Unit,
    onCallHistorySearchChanged: (String) -> Unit,
    onRefreshCallHistory: () -> Unit,
    onQuickBlockNumber: (String, String?) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAllContacts by remember { mutableStateOf(false) }
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }

    val callPhonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val numberToCall = pendingCallNumber
        pendingCallNumber = null
        if (!numberToCall.isNullOrBlank()) {
            launchPhoneCall(
                context = context,
                phoneNumber = numberToCall,
                useDirectCall = isGranted,
                onShowMessage = onShowMessage
            )
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onRefreshContacts()
        if (!isGranted) {
            onShowMessage(context.getString(R.string.snackbar_permissions_denied))
        }
    }

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
        if (cleaned.isEmpty()) return
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
            pendingCallNumber = cleaned
            callPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    val filteredContacts = remember(uiState.deviceContacts, uiState.contactsSearchQuery) {
        SuggestedContactsMatcher.filterContacts(
            contacts = uiState.deviceContacts,
            query = uiState.contactsSearchQuery
        )
    }

    val displayedContacts = remember(filteredContacts, showAllContacts, uiState.contactsSearchQuery) {
        if (uiState.contactsSearchQuery.isNotBlank() || showAllContacts) {
            filteredContacts
        } else {
            filteredContacts.take(INITIAL_CONTACTS_PREVIEW_LIMIT)
        }
    }

    val filteredCalls = remember(
        uiState.deviceCallLogs,
        uiState.callHistoryFilter,
        uiState.callHistorySearchQuery
    ) {
        filterDeviceCallLogs(
            logs = uiState.deviceCallLogs,
            filter = uiState.callHistoryFilter,
            searchQuery = uiState.callHistorySearchQuery
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
                .testTag("home_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Home Brand Header
            item(key = "home_header") {
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
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.home_header_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.home_header_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Search Contacts Section Header & Search Field
            item(key = "contacts_search_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.contacts_section_header),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (uiState.hasContactsPermission && uiState.deviceContacts.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.contacts_count_badge,
                                            filteredContacts.size
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = 3.dp
                                        )
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onRefreshContacts,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("refresh_contacts_button")
                        ) {
                            if (uiState.isLoadingContacts) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.contacts_refresh_cd),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = uiState.contactsSearchQuery,
                        onValueChange = onContactsSearchChanged,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.contacts_search_placeholder),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            if (uiState.contactsSearchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onContactsSearchChanged("") },
                                    modifier = Modifier.testTag("contacts_search_clear")
                                ) {
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
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("contacts_search_input")
                    )
                }
            }

            // Contacts Permission Gate / Empty / Contact List
            if (!uiState.hasContactsPermission) {
                item(key = "contacts_permission_card") {
                    ContactsPermissionCard(
                        onGrantPermission = {
                            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    )
                }
            } else if (filteredContacts.isEmpty() && !uiState.isLoadingContacts) {
                item(key = "contacts_empty_state") {
                    if (uiState.contactsSearchQuery.isNotBlank()) {
                        EmptySearchCard(
                            title = stringResource(R.string.contacts_search_empty_title),
                            body = stringResource(
                                R.string.contacts_search_empty_body,
                                uiState.contactsSearchQuery
                            ),
                            onClearSearch = { onContactsSearchChanged("") },
                            testTag = "empty_search_contacts"
                        )
                    } else {
                        EmptyContactsCard()
                    }
                }
            } else {
                items(
                    items = displayedContacts,
                    key = { "contact_${it.id}" }
                ) { contact ->
                    DeviceContactRow(
                        contact = contact,
                        onCall = { initiateCall(contact.phoneNumber) },
                        onBlockPrefix = {
                            onQuickBlockNumber(contact.phoneNumber, contact.displayName)
                        },
                        modifier = Modifier.animateItem()
                    )
                }

                if (uiState.contactsSearchQuery.isBlank() &&
                    filteredContacts.size > INITIAL_CONTACTS_PREVIEW_LIMIT
                ) {
                    item(key = "contacts_expand_toggle") {
                        TextButton(
                            onClick = { showAllContacts = !showAllContacts },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("contacts_expand_button")
                        ) {
                            Text(
                                text = if (showAllContacts) {
                                    stringResource(R.string.contacts_show_less)
                                } else {
                                    stringResource(R.string.contacts_show_all, filteredContacts.size)
                                }
                            )
                        }
                    }
                }
            }

            // Recent Call History Header, Search & Filters
            item(key = "call_history_header") {
                Spacer(modifier = Modifier.height(6.dp))
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

                    OutlinedTextField(
                        value = uiState.callHistorySearchQuery,
                        onValueChange = onCallHistorySearchChanged,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.call_history_search_placeholder),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            if (uiState.callHistorySearchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onCallHistorySearchChanged("") },
                                    modifier = Modifier.testTag("call_history_search_clear")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.cd_clear_input)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("call_history_search_input")
                    )

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
                                onClick = { onCallHistoryFilterSelected(filter) },
                                label = { Text(stringResource(labelRes)) },
                                modifier = Modifier.testTag("call_filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Recent Call History Permission Gate / Empty / Rows
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
                    if (uiState.callHistorySearchQuery.isNotBlank()) {
                        EmptySearchCard(
                            title = stringResource(R.string.call_history_search_empty_title),
                            body = stringResource(
                                R.string.call_history_search_empty_body,
                                uiState.callHistorySearchQuery
                            ),
                            onClearSearch = { onCallHistorySearchChanged("") },
                            testTag = "empty_search_call_history"
                        )
                    } else {
                        EmptyDeviceCallHistoryCard()
                    }
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

            item(key = "home_bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DeviceContactRow(
    contact: DeviceContactEntry,
    onCall: () -> Unit,
    onBlockPrefix: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initials = remember(contact.displayName) { extractInitials(contact.displayName) }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("contact_row_${contact.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onBlockPrefix,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("contact_block_${contact.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Block,
                    contentDescription = stringResource(
                        R.string.dialer_cd_block_number,
                        contact.phoneNumber
                    ),
                    tint = MaterialTheme.colorScheme.error
                )
            }

            IconButton(
                onClick = onCall,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("contact_call_${contact.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = stringResource(
                        R.string.dialer_cd_call_number,
                        contact.displayName
                    ),
                    tint = ShieldActiveEmerald
                )
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
        DeviceCallType.INCOMING -> CallTypeVisual(
            Icons.AutoMirrored.Rounded.CallReceived,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondaryContainer,
            R.string.call_type_incoming
        )
        DeviceCallType.OUTGOING -> CallTypeVisual(
            Icons.AutoMirrored.Rounded.CallMade,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer,
            R.string.call_type_outgoing
        )
        DeviceCallType.MISSED -> CallTypeVisual(
            Icons.AutoMirrored.Rounded.CallMissed,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer,
            R.string.call_type_missed
        )
        DeviceCallType.REJECTED -> CallTypeVisual(
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
private fun ContactsPermissionCard(onGrantPermission: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("contacts_permission_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Contacts,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = stringResource(R.string.contacts_permission_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.contacts_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onGrantPermission,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("grant_contacts_button")
            ) {
                Text(text = stringResource(R.string.contacts_grant_permission))
            }
        }
    }
}

@Composable
private fun EmptyContactsCard() {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_contacts_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Contacts,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = stringResource(R.string.contacts_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.contacts_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CallLogPermissionCard(onGrantPermission: () -> Unit) {
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
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
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
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
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

@Composable
private fun EmptySearchCard(
    title: String,
    body: String,
    onClearSearch: () -> Unit,
    testTag: String
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            OutlinedButton(
                onClick = onClearSearch,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(text = stringResource(R.string.call_history_clear_search))
            }
        }
    }
}

private data class CallTypeVisual<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

/**
 * Pure filter matching device call logs by call-type filter and case-insensitive name
 * or raw/normalized phone digit query.
 */
internal fun filterDeviceCallLogs(
    logs: List<DeviceCallLogEntry>,
    filter: CallHistoryFilter,
    searchQuery: String
): List<DeviceCallLogEntry> {
    val typeFiltered = when (filter) {
        CallHistoryFilter.ALL -> logs
        CallHistoryFilter.INCOMING -> logs.filter { it.type == DeviceCallType.INCOMING }
        CallHistoryFilter.OUTGOING -> logs.filter { it.type == DeviceCallType.OUTGOING }
        CallHistoryFilter.MISSED -> logs.filter {
            it.type == DeviceCallType.MISSED || it.type == DeviceCallType.REJECTED
        }
    }

    val trimmedQuery = searchQuery.trim()
    if (trimmedQuery.isEmpty()) return typeFiltered

    val normalizedQuery = PhoneNumberMatcher.normalize(trimmedQuery)
    return typeFiltered.filter { entry ->
        val matchesName = entry.cachedName?.contains(trimmedQuery, ignoreCase = true) == true
        val matchesRawNumber = entry.number.contains(trimmedQuery, ignoreCase = true)
        val matchesNormalizedDigits = normalizedQuery.isNotEmpty() &&
            PhoneNumberMatcher.normalize(entry.number).contains(normalizedQuery.removePrefix("+"))
        matchesName || matchesRawNumber || matchesNormalizedDigits
    }
}

internal fun extractInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "#"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}

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

internal fun launchPhoneCall(
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
