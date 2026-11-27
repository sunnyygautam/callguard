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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.example.data.local.BlockedPrefixEntity
import com.example.data.repository.DeviceCallLogEntry
import com.example.data.repository.DeviceCallType
import com.example.data.repository.DeviceContactEntry
import com.example.ml.CallerCategory
import com.example.ml.CallerCategoryResult
import com.example.ml.SpamMlAnalysisResult
import com.example.telecom.PhoneNumberMatcher
import com.example.ui.theme.PrefixMonoStyle
import com.example.ui.theme.ShieldActiveEmerald
import com.example.ui.theme.ShieldWarningAmber

/**
 * Google Phone–inspired Home tab featuring a single top "Search contacts by name or number"
 * pill bar, followed directly by All / Incoming / Outgoing / Missed tabs and Recent Call History.
 * Matching contacts appear only while actively searching—never cluttering the idle home view.
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
    onSwipeBlockNumber: (String, String?) -> Unit,
    onDeleteCallHistoryEntry: (DeviceCallLogEntry, Boolean) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    var pendingDeleteCallEntry by remember { mutableStateOf<DeviceCallLogEntry?>(null) }
    var inspectedSpamItem by remember {
        mutableStateOf<InspectedSpamNumber?>(null)
    }

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

    val writeCallLogPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val entryToDelete = pendingDeleteCallEntry
        pendingDeleteCallEntry = null
        if (entryToDelete != null) {
            onDeleteCallHistoryEntry(entryToDelete, isGranted)
        }
    }

    fun requestDeleteCallHistoryEntry(entry: DeviceCallLogEntry): Boolean {
        val hasWriteCallLog = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        return if (hasWriteCallLog) {
            onDeleteCallHistoryEntry(entry, true)
            true
        } else {
            pendingDeleteCallEntry = entry
            writeCallLogPermissionLauncher.launch(Manifest.permission.WRITE_CALL_LOG)
            false
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

    val unifiedSearchQuery = uiState.contactsSearchQuery
    val isSearching = unifiedSearchQuery.isNotBlank()

    val onUnifiedSearchChanged: (String) -> Unit = { query ->
        onContactsSearchChanged(query)
        onCallHistorySearchChanged(query)
    }

    val searchedContacts = uiState.searchedContacts
    val searchedNumberAnalysis = uiState.searchedNumberMlAnalysis
    val searchedNumberBlockedRule = uiState.searchedNumberBlockedRule

    val filteredCalls = remember(
        uiState.deviceCallLogs,
        uiState.callHistoryFilter,
        unifiedSearchQuery
    ) {
        filterDeviceCallLogs(
            logs = uiState.deviceCallLogs,
            filter = uiState.callHistoryFilter,
            searchQuery = unifiedSearchQuery
        )
    }

    inspectedSpamItem?.let { inspected ->
        SpamAnalysisDetailDialog(
            displayTitle = inspected.displayTitle,
            phoneNumber = inspected.phoneNumber,
            analysis = inspected.analysis,
            onDismiss = { inspectedSpamItem = null },
            onBlockPrefix = if (inspected.phoneNumber.isNotBlank()) {
                { onQuickBlockNumber(inspected.phoneNumber, inspected.contactName) }
            } else {
                null
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
                .testTag("home_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Google Phone–style Top Pill Search Bar ("Search contacts by name or number…")
            item(key = "home_search_bar") {
                OutlinedTextField(
                    value = unifiedSearchQuery,
                    onValueChange = onUnifiedSearchChanged,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.contacts_search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (isSearching) {
                            IconButton(
                                onClick = { onUnifiedSearchChanged("") },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("contacts_search_clear")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.cd_clear_input)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    onRefreshContacts()
                                    onRefreshCallHistory()
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("refresh_call_history_button")
                            ) {
                                if (uiState.isLoadingCallHistory || uiState.isLoadingContacts) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = stringResource(R.string.call_history_refresh_cd),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(999.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contacts_search_input")
                )
            }

            // All / Incoming / Outgoing / Missed Filter Tabs directly under Search
            item(key = "home_filter_tabs") {
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
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.testTag("call_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            // Show live Number Search Result & Phone Contacts ONLY while the user is actively searching
            if (isSearching) {
                if (searchedNumberAnalysis != null) {
                    val trimmedNumber = unifiedSearchQuery.trim()
                    val matchedContact = searchedContacts.firstOrNull {
                        PhoneNumberMatcher.normalize(it.phoneNumber) ==
                            PhoneNumberMatcher.normalize(trimmedNumber)
                    } ?: searchedContacts.firstOrNull()

                    item(key = "home_number_search_result_header") {
                        Text(
                            text = stringResource(R.string.home_search_number_result_header),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    item(key = "home_number_search_result_card") {
                        NumberSearchResultCard(
                            searchedNumber = trimmedNumber,
                            matchedContact = matchedContact,
                            blockedRule = searchedNumberBlockedRule,
                            mlAnalysis = searchedNumberAnalysis,
                            onInspectMlScore = {
                                inspectedSpamItem = InspectedSpamNumber(
                                    displayTitle = matchedContact?.displayName ?: trimmedNumber,
                                    phoneNumber = trimmedNumber,
                                    analysis = searchedNumberAnalysis,
                                    contactName = matchedContact?.displayName
                                )
                            },
                            onCall = { initiateCall(trimmedNumber) },
                            onBlock = {
                                onQuickBlockNumber(trimmedNumber, matchedContact?.displayName)
                            }
                        )
                    }
                }

                if (!uiState.hasContactsPermission) {
                    item(key = "search_contacts_permission_banner") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.contacts_permission_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        contactsPermissionLauncher.launch(
                                            Manifest.permission.READ_CONTACTS
                                        )
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("grant_contacts_button")
                                ) {
                                    Text(text = stringResource(R.string.backup_btn_allow_contacts))
                                }
                            }
                        }
                    }
                } else if (uiState.isSearchingContacts || searchedContacts.isNotEmpty()) {
                    item(key = "search_contacts_matches_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.home_search_contacts_header,
                                    searchedContacts.size
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (uiState.isSearchingContacts) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = stringResource(R.string.home_search_contacts_fetching),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    items(
                        items = searchedContacts,
                        key = { "contact_${it.id}" }
                    ) { contact ->
                        DeviceContactRow(
                            contact = contact,
                            searchQuery = unifiedSearchQuery,
                            onCall = { initiateCall(contact.phoneNumber) },
                            onBlockPrefix = {
                                onQuickBlockNumber(contact.phoneNumber, contact.displayName)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            // Recent Call History Section Header + Swipe Gesture Hint
            if (!isSearching || filteredCalls.isNotEmpty() || !uiState.hasCallLogPermission) {
                item(key = "call_history_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.call_history_header),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (uiState.hasCallLogPermission && filteredCalls.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.call_history_swipe_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                            )
                        }
                    }
                }
            }

            // Recent Call History Permission Gate / Empty / Swipeable Rows
            if (!uiState.hasCallLogPermission) {
                item(key = "call_log_permission_card") {
                    CallLogPermissionCard(
                        onGrantPermission = {
                            callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                        }
                    )
                }
            } else if (filteredCalls.isEmpty() && !uiState.isLoadingCallHistory) {
                if (!isSearching) {
                    item(key = "empty_device_call_history") {
                        EmptyDeviceCallHistoryCard()
                    }
                } else if (
                    searchedContacts.isEmpty() &&
                    searchedNumberAnalysis == null &&
                    !uiState.isSearchingContacts
                ) {
                    item(key = "empty_device_call_history") {
                        EmptySearchCard(
                            title = stringResource(R.string.call_history_search_empty_title),
                            body = stringResource(
                                R.string.call_history_search_empty_body,
                                unifiedSearchQuery
                            ),
                            onClearSearch = { onUnifiedSearchChanged("") },
                            testTag = "empty_search_call_history"
                        )
                    }
                }
            } else {
                items(
                    items = filteredCalls,
                    key = { "device_call_${it.id}" }
                ) { entry ->
                    val mlScore = uiState.callHistoryMlScores[entry.id]
                    val callerCategory = uiState.callHistoryCategories[entry.id]
                    val fallbackTitle = entry.cachedName ?: entry.number.ifBlank {
                        stringResource(R.string.call_unknown_number)
                    }
                    SwipeableCallLogItem(
                        entry = entry,
                        searchQuery = unifiedSearchQuery,
                        mlAnalysis = mlScore,
                        callerCategory = callerCategory,
                        onInspectMlScore = {
                            if (mlScore != null) {
                                inspectedSpamItem = InspectedSpamNumber(
                                    displayTitle = fallbackTitle,
                                    phoneNumber = entry.number,
                                    analysis = mlScore,
                                    contactName = entry.cachedName
                                )
                            }
                        },
                        onCallBack = { initiateCall(entry.number) },
                        onBlockPrefix = { onQuickBlockNumber(entry.number, entry.cachedName) },
                        onSwipeBlock = { onSwipeBlockNumber(entry.number, entry.cachedName) },
                        onSwipeDelete = { requestDeleteCallHistoryEntry(entry) },
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

private data class InspectedSpamNumber(
    val displayTitle: String,
    val phoneNumber: String,
    val analysis: SpamMlAnalysisResult,
    val contactName: String?
)

@Composable
private fun NumberSearchResultCard(
    searchedNumber: String,
    matchedContact: DeviceContactEntry?,
    blockedRule: BlockedPrefixEntity?,
    mlAnalysis: SpamMlAnalysisResult,
    onInspectMlScore: () -> Unit,
    onCall: () -> Unit,
    onBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBlocked = blockedRule != null
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_number_search_result_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isBlocked) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            }
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
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isBlocked) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isBlocked) {
                            Icons.Rounded.GppBad
                        } else {
                            Icons.Rounded.VerifiedUser
                        },
                        contentDescription = null,
                        tint = if (isBlocked) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = searchedNumber,
                        style = PrefixMonoStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (matchedContact != null) {
                            stringResource(
                                R.string.home_search_saved_contact_match,
                                matchedContact.displayName
                            )
                        } else {
                            stringResource(R.string.home_search_unsaved_number)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpamProbabilityBadge(
                    probabilityPercent = mlAnalysis.probabilityPercent,
                    riskTier = mlAnalysis.riskTier,
                    onClick = onInspectMlScore,
                    modifier = Modifier.testTag("home_number_search_spam_badge")
                )

                if (blockedRule != null) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = stringResource(
                                R.string.home_search_blocked_rule_match,
                                blockedRule.prefixString
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onCall,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag("home_number_search_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = null,
                        tint = ShieldActiveEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.home_search_btn_call))
                }

                OutlinedButton(
                    onClick = onBlock,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag("home_number_search_block_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Block,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.home_search_btn_block))
                }
            }
        }
    }
}

@Composable
private fun DeviceContactRow(
    contact: DeviceContactEntry,
    searchQuery: String,
    onCall: () -> Unit,
    onBlockPrefix: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initials = remember(contact.displayName) { extractInitials(contact.displayName) }
    val highlightColor = MaterialTheme.colorScheme.primary
    val highlightBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    val highlightedName = remember(contact.displayName, searchQuery, highlightColor, highlightBg) {
        val match = SubstringSearchEngine.findTextMatches(contact.displayName, searchQuery)
        SubstringSearchEngine.buildHighlightedText(
            text = contact.displayName,
            ranges = match.ranges,
            highlightColor = highlightColor,
            highlightBackground = highlightBg
        )
    }
    val highlightedPhone = remember(contact.phoneNumber, searchQuery, highlightColor, highlightBg) {
        val match = SubstringSearchEngine.findPhoneNumberMatch(contact.phoneNumber, searchQuery)
        SubstringSearchEngine.buildHighlightedText(
            text = contact.phoneNumber,
            ranges = match.ranges,
            highlightColor = highlightColor,
            highlightBackground = highlightBg
        )
    }

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
                    text = highlightedName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = highlightedPhone,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCallLogItem(
    entry: DeviceCallLogEntry,
    searchQuery: String,
    mlAnalysis: SpamMlAnalysisResult?,
    callerCategory: CallerCategoryResult?,
    onInspectMlScore: () -> Unit,
    onCallBack: () -> Unit,
    onBlockPrefix: () -> Unit,
    onSwipeBlock: () -> Unit,
    onSwipeDelete: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val currentOnSwipeBlock by rememberUpdatedState(onSwipeBlock)
    val currentOnSwipeDelete by rememberUpdatedState(onSwipeDelete)
    val canBlock = entry.number.isNotBlank()

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { targetValue ->
            when (targetValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (canBlock) {
                        currentOnSwipeBlock()
                    }
                    // Snap card back into place after blocking so user sees updated risk status
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    currentOnSwipeDelete()
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = canBlock,
        enableDismissFromEndToStart = true,
        modifier = modifier
            .fillMaxWidth()
            .testTag("swipeable_call_row_${entry.id}"),
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isBlockSwipe = direction == SwipeToDismissBoxValue.StartToEnd
            val isDeleteSwipe = direction == SwipeToDismissBoxValue.EndToStart

            val bgColor = when {
                isBlockSwipe -> ShieldWarningAmber.copy(alpha = 0.22f)
                isDeleteSwipe -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
            val contentColor = when {
                isBlockSwipe -> MaterialTheme.colorScheme.error
                isDeleteSwipe -> MaterialTheme.colorScheme.onError
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (isBlockSwipe) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (isBlockSwipe) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Block,
                            contentDescription = stringResource(R.string.call_history_swipe_action_block),
                            tint = contentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.call_history_swipe_action_block),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = contentColor
                        )
                    }
                } else if (isDeleteSwipe) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.call_history_swipe_action_delete),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = contentColor
                        )
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.call_history_swipe_action_delete),
                            tint = contentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    ) {
        DeviceCallLogRow(
            entry = entry,
            searchQuery = searchQuery,
            mlAnalysis = mlAnalysis,
            callerCategory = callerCategory,
            onInspectMlScore = onInspectMlScore,
            onCallBack = onCallBack,
            onBlockPrefix = onBlockPrefix
        )
    }
}

@Composable
private fun DeviceCallLogRow(
    entry: DeviceCallLogEntry,
    searchQuery: String,
    mlAnalysis: SpamMlAnalysisResult?,
    callerCategory: CallerCategoryResult?,
    onInspectMlScore: () -> Unit,
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
    val highlightColor = MaterialTheme.colorScheme.primary
    val highlightBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    val highlightedTitle = remember(displayTitle, entry.cachedName, searchQuery, highlightColor, highlightBg) {
        if (searchQuery.isBlank()) {
            SubstringSearchEngine.buildHighlightedText(displayTitle, emptyList(), highlightColor, highlightBg)
        } else {
            val match = if (entry.cachedName != null) {
                SubstringSearchEngine.findTextMatches(displayTitle, searchQuery)
            } else {
                SubstringSearchEngine.findPhoneNumberMatch(displayTitle, searchQuery)
            }
            SubstringSearchEngine.buildHighlightedText(
                text = displayTitle,
                ranges = match.ranges,
                highlightColor = highlightColor,
                highlightBackground = highlightBg
            )
        }
    }
    val highlightedSubNumber = remember(entry.number, searchQuery, highlightColor, highlightBg) {
        if (searchQuery.isBlank() || entry.number.isBlank()) {
            SubstringSearchEngine.buildHighlightedText(entry.number, emptyList(), highlightColor, highlightBg)
        } else {
            val match = SubstringSearchEngine.findPhoneNumberMatch(entry.number, searchQuery)
            SubstringSearchEngine.buildHighlightedText(
                text = entry.number,
                ranges = match.ranges,
                highlightColor = highlightColor,
                highlightBackground = highlightBg
            )
        }
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
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = highlightedTitle,
                    style = if (entry.cachedName == null) PrefixMonoStyle else MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (entry.cachedName != null && entry.number.isNotBlank()) {
                    Text(
                        text = highlightedSubNumber,
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

                if (mlAnalysis != null || callerCategory != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (callerCategory != null) {
                            CallerCategoryBadge(
                                categoryResult = callerCategory,
                                modifier = Modifier.testTag("call_row_category_badge_${entry.id}")
                            )
                        }
                        if (mlAnalysis != null) {
                            SpamProbabilityBadge(
                                probabilityPercent = mlAnalysis.probabilityPercent,
                                riskTier = mlAnalysis.riskTier,
                                onClick = onInspectMlScore,
                                modifier = Modifier.testTag("call_row_spam_badge_${entry.id}")
                            )
                        }
                    }
                }
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
private fun CallerCategoryBadge(
    categoryResult: CallerCategoryResult,
    modifier: Modifier = Modifier
) {
    val (icon, labelRes, containerColor, contentColor) = when (categoryResult.category) {
        CallerCategory.FREQUENT_CALLER -> CallTypeVisual(
            Icons.Rounded.Repeat,
            R.string.caller_category_frequent,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        CallerCategory.POTENTIAL_BUSINESS -> CallTypeVisual(
            Icons.Rounded.Business,
            R.string.caller_category_business,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        CallerCategory.OCCASIONAL_CALLER -> CallTypeVisual(
            Icons.Rounded.Schedule,
            R.string.caller_category_occasional,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        CallerCategory.ONE_TIME_CONTACT -> CallTypeVisual(
            Icons.Rounded.PersonOutline,
            R.string.caller_category_one_time,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val baseLabel = stringResource(labelRes)
    val badgeText = if (categoryResult.interactionCount > 1) {
        stringResource(
            R.string.caller_category_with_count,
            baseLabel,
            categoryResult.interactionCount
        )
    } else {
        baseLabel
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = contentColor,
                maxLines = 1
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
 * Pure filter matching device call logs by call-type filter and anywhere-substring matching
 * (using [SubstringSearchEngine] with `indexOf` and `Regex.find`) across caller names and
 * formatted or normalized phone numbers.
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

    return typeFiltered.filter { entry ->
        val matchesName = entry.cachedName?.let { name ->
            SubstringSearchEngine.findTextMatches(name, trimmedQuery).isMatch
        } == true
        val matchesNumber = SubstringSearchEngine.findPhoneNumberMatch(entry.number, trimmedQuery).isMatch
        matchesName || matchesNumber
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
