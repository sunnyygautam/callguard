package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ShieldActiveEmerald

private data class KeypadButtonSpec(
    val digit: String,
    val letters: String,
    val longPressDigit: String? = null
)

private val KEYPAD_ROWS = listOf(
    listOf(KeypadButtonSpec("1", ""), KeypadButtonSpec("2", "ABC"), KeypadButtonSpec("3", "DEF")),
    listOf(KeypadButtonSpec("4", "GHI"), KeypadButtonSpec("5", "JKL"), KeypadButtonSpec("6", "MNO")),
    listOf(KeypadButtonSpec("7", "PQRS"), KeypadButtonSpec("8", "TUV"), KeypadButtonSpec("9", "WXYZ")),
    listOf(KeypadButtonSpec("*", ""), KeypadButtonSpec("0", "+", longPressDigit = "+"), KeypadButtonSpec("#", ""))
)

/**
 * Keypad tab displaying the tactile numeric dialpad ("Enter phone number" bar, keys, and
 * call/block actions) on top, followed by the smart **Suggested** section (recent callers &
 * live T9/digit contact matches).
 */
@Composable
internal fun KeypadScreen(
    uiState: CallGuardUiState,
    onDialerDigitPressed: (String) -> Unit,
    onDialerNumberSelected: (String) -> Unit,
    onDialerBackspace: () -> Unit,
    onDialerClear: () -> Unit,
    onQuickBlockNumber: (String, String?) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
            pendingCallNumber = cleaned
            callPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    val suggestions = remember(
        uiState.dialerInput,
        uiState.deviceContacts,
        uiState.deviceCallLogs
    ) {
        SuggestedContactsMatcher.buildKeypadSuggestions(
            dialerInput = uiState.dialerInput,
            contacts = uiState.deviceContacts,
            recentCalls = uiState.deviceCallLogs,
            maxResults = if (uiState.dialerInput.isBlank()) 5 else 8
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
                .testTag("keypad_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Keypad Header
            item(key = "keypad_header") {
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
                            imageVector = Icons.Rounded.Dialpad,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.keypad_header_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.keypad_header_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Tactile Numeric Keypad & Call Controls on Top
            item(key = "keypad_dialer_card") {
                NumericKeypadCard(
                    dialerInput = uiState.dialerInput,
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

            // Suggested Section Below Keypad
            item(key = "keypad_suggested_section") {
                SuggestedCallersCard(
                    dialerInput = uiState.dialerInput,
                    suggestions = suggestions,
                    onSelectSuggestion = { onDialerNumberSelected(it.phoneNumber) },
                    onCallSuggestion = { initiateCall(it.phoneNumber) }
                )
            }

            item(key = "keypad_bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SuggestedCallersCard(
    dialerInput: String,
    suggestions: List<KeypadSuggestion>,
    onSelectSuggestion: (KeypadSuggestion) -> Unit,
    onCallSuggestion: (KeypadSuggestion) -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("keypad_suggested_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.keypad_suggested_header),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (suggestions.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = stringResource(
                                R.string.keypad_suggested_matches_badge,
                                suggestions.size
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (suggestions.isEmpty()) {
                Text(
                    text = if (dialerInput.isBlank()) {
                        stringResource(R.string.keypad_suggested_empty_idle)
                    } else {
                        stringResource(R.string.keypad_suggested_empty_query, dialerInput)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .testTag("keypad_suggested_empty")
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    suggestions.forEachIndexed { index, suggestion ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            )
                        }
                        SuggestionRow(
                            suggestion = suggestion,
                            highlightMatches = dialerInput.isNotBlank(),
                            onSelect = { onSelectSuggestion(suggestion) },
                            onCall = { onCallSuggestion(suggestion) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: KeypadSuggestion,
    highlightMatches: Boolean,
    onSelect: () -> Unit,
    onCall: () -> Unit
) {
    val initials = remember(suggestion.displayName) { extractInitials(suggestion.displayName) }
    val highlightColor = MaterialTheme.colorScheme.primary
    val highlightBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    val displayNameAnnotated = remember(
        suggestion.displayName,
        suggestion.nameMatchRanges,
        highlightMatches,
        highlightColor,
        highlightBg
    ) {
        SubstringSearchEngine.buildHighlightedText(
            text = suggestion.displayName,
            ranges = if (highlightMatches) suggestion.nameMatchRanges else emptyList(),
            highlightColor = highlightColor,
            highlightBackground = highlightBg
        )
    }
    val phoneNumberAnnotated = remember(
        suggestion.phoneNumber,
        suggestion.numberMatchRanges,
        highlightMatches,
        highlightColor,
        highlightBg
    ) {
        SubstringSearchEngine.buildHighlightedText(
            text = suggestion.phoneNumber,
            ranges = if (highlightMatches) suggestion.numberMatchRanges else emptyList(),
            highlightColor = highlightColor,
            highlightBackground = highlightBg
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag("keypad_suggestion_${suggestion.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (suggestion.isFromRecentCall) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (suggestion.isFromRecentCall) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = displayNameAnnotated,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = stringResource(
                            if (suggestion.isFromRecentCall) {
                                R.string.keypad_suggested_recent_badge
                            } else {
                                R.string.keypad_suggested_contact_badge
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                text = phoneNumberAnnotated,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onCall,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("keypad_suggestion_call_${suggestion.id}")
        ) {
            Icon(
                imageVector = Icons.Rounded.Call,
                contentDescription = stringResource(
                    R.string.dialer_cd_call_number,
                    suggestion.displayName
                ),
                tint = ShieldActiveEmerald
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumericKeypadCard(
    dialerInput: String,
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
                            fontSize = if (dialerInput.isEmpty()) 17.sp else 26.sp
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

            // 3x4 Tactile Numeric Keypad
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KEYPAD_ROWS.forEach { rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowKeys.forEach { dialKey ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 58.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .combinedClickable(
                                        onClick = { onDigitPressed(dialKey.digit) },
                                        onLongClick = {
                                            onDigitPressed(dialKey.longPressDigit ?: dialKey.digit)
                                        }
                                    )
                                    .testTag("dial_key_${dialKey.digit}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp),
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

            // Call & Quick-Block CTA Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlaceCall,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ShieldActiveEmerald,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 54.dp)
                        .testTag("dialer_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
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
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .heightIn(min = 54.dp)
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
            }
        }
    }
}
