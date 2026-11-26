package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.AddModerator
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.BlockedMatchType
import com.example.data.local.BlockedPrefixEntity
import com.example.telecom.PhoneNumberMatcher
import com.example.ui.theme.PrefixMonoStyle

private enum class BlockedListFilter {
    ALL,
    EXACT_NUMBERS,
    PREFIXES
}

private data class BlockedPrefixPreset(
    val prefix: String,
    val label: String
)

private val QUICK_BLOCK_PREFIX_PRESETS = listOf(
    BlockedPrefixPreset("+91140", "Telemarketer"),
    BlockedPrefixPreset("+1800", "Toll-Free Spam"),
    BlockedPrefixPreset("0114", "Promotional")
)

/**
 * Prominent navigation card on the Shield tab that summarizes the Room Blocked List
 * and opens the dedicated [BlockedListScreen].
 */
@Composable
internal fun BlockedListNavigationCard(
    totalCount: Int,
    activeCount: Int,
    exactNumberCount: Int,
    prefixCount: Int,
    onOpenBlockedList: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onOpenBlockedList)
            .testTag("open_blocked_list_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(23.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.blocked_list_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.stat_active_rules, activeCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = if (totalCount == 0) {
                        stringResource(R.string.blocked_list_card_subtitle_empty)
                    } else {
                        stringResource(
                            R.string.blocked_list_card_subtitle_counts,
                            exactNumberCount,
                            prefixCount
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.blocked_list_open_cd),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * Dedicated screen inside the Shield tab allowing users to manually add specific phone numbers
 * (exact match) or number prefixes (starts-with series) to the Room-backed Blocked List,
 * toggle rules active/paused, filter/search saved rules, and remove entries with Undo.
 */
@Composable
internal fun BlockedListScreen(
    uiState: CallGuardUiState,
    onBack: () -> Unit,
    onMatchTypeSelected: (String) -> Unit,
    onInputChanged: (String) -> Unit,
    onLabelChanged: (String) -> Unit,
    onApplyQuickPrefix: (String, String) -> Unit,
    onAddClick: () -> Unit,
    onToggleRuleActive: (Long, Boolean) -> Unit,
    onDeleteRule: (BlockedPrefixEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var selectedFilter by remember { mutableStateOf(BlockedListFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    BackHandler(onBack = onBack)

    val filteredEntries = remember(uiState.prefixes, selectedFilter, searchQuery) {
        val byType = when (selectedFilter) {
            BlockedListFilter.ALL -> uiState.prefixes
            BlockedListFilter.EXACT_NUMBERS -> uiState.prefixes.filter { it.isExactNumber }
            BlockedListFilter.PREFIXES -> uiState.prefixes.filter { !it.isExactNumber }
        }
        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) {
            byType
        } else {
            val normalizedQuery = PhoneNumberMatcher.normalize(trimmed).removePrefix("+")
            byType.filter { entry ->
                entry.prefixString.contains(trimmed, ignoreCase = true) ||
                    entry.label.contains(trimmed, ignoreCase = true) ||
                    (normalizedQuery.isNotEmpty() &&
                        entry.normalizedPrefix.removePrefix("+").contains(normalizedQuery))
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
                .testTag("blocked_list_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Navigation Header with Back Button
            item(key = "blocked_list_top_bar") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("blocked_list_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.blocked_list_back_cd),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.blocked_list_screen_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.blocked_list_screen_subtitle),
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
                            text = stringResource(R.string.stat_active_rules, uiState.activePrefixCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Add Specific Phone Number or Prefix Card
            item(key = "blocked_list_add_form") {
                AddBlockedEntryCard(
                    selectedMatchType = uiState.selectedBlockedMatchType,
                    input = uiState.prefixInput,
                    labelInput = uiState.labelInput,
                    inputError = uiState.prefixErrorRes?.let { stringResource(it) },
                    onMatchTypeSelected = onMatchTypeSelected,
                    onInputChanged = onInputChanged,
                    onLabelChanged = onLabelChanged,
                    onApplyQuickPrefix = onApplyQuickPrefix,
                    onAddClick = {
                        focusManager.clearFocus()
                        onAddClick()
                    }
                )
            }

            // Saved Blocked List Filter & Search Header
            item(key = "blocked_list_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.blocked_list_saved_header),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.stat_total_rules, uiState.prefixes.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (uiState.prefixes.isNotEmpty()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it.take(40) },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.blocked_list_search_placeholder),
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
                            shape = RoundedCornerShape(999.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("blocked_list_search_input")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedFilter == BlockedListFilter.ALL,
                                onClick = { selectedFilter = BlockedListFilter.ALL },
                                label = { Text(stringResource(R.string.blocked_list_filter_all)) },
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.testTag("blocked_list_filter_all")
                            )
                            FilterChip(
                                selected = selectedFilter == BlockedListFilter.EXACT_NUMBERS,
                                onClick = { selectedFilter = BlockedListFilter.EXACT_NUMBERS },
                                label = { Text(stringResource(R.string.blocked_list_filter_numbers)) },
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.testTag("blocked_list_filter_numbers")
                            )
                            FilterChip(
                                selected = selectedFilter == BlockedListFilter.PREFIXES,
                                onClick = { selectedFilter = BlockedListFilter.PREFIXES },
                                label = { Text(stringResource(R.string.blocked_list_filter_prefixes)) },
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.testTag("blocked_list_filter_prefixes")
                            )
                        }
                    }
                }
            }

            // Empty State OR Saved Blocked List Items
            if (uiState.prefixes.isEmpty()) {
                item(key = "blocked_list_empty") {
                    EmptyBlockedListCard()
                }
            } else if (filteredEntries.isEmpty()) {
                item(key = "blocked_list_no_match") {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = stringResource(R.string.blocked_list_empty_filter),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        )
                    }
                }
            } else {
                items(
                    items = filteredEntries,
                    key = { it.id }
                ) { entry ->
                    BlockedListRuleRow(
                        entity = entry,
                        onToggleActive = { isActive -> onToggleRuleActive(entry.id, isActive) },
                        onDelete = { onDeleteRule(entry) },
                        modifier = Modifier.animateItem()
                    )
                }
            }

            item(key = "blocked_list_bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AddBlockedEntryCard(
    selectedMatchType: String,
    input: String,
    labelInput: String,
    inputError: String?,
    onMatchTypeSelected: (String) -> Unit,
    onInputChanged: (String) -> Unit,
    onLabelChanged: (String) -> Unit,
    onApplyQuickPrefix: (String, String) -> Unit,
    onAddClick: () -> Unit
) {
    val isExactMode = selectedMatchType == BlockedMatchType.EXACT_NUMBER

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("add_blocked_entry_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.blocked_list_add_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isExactMode) {
                        stringResource(R.string.blocked_list_add_hint_number)
                    } else {
                        stringResource(R.string.blocked_list_add_hint_prefix)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Mode Selector: Specific Phone Number vs Number Prefix
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = isExactMode,
                    onClick = { onMatchTypeSelected(BlockedMatchType.EXACT_NUMBER) },
                    label = { Text(stringResource(R.string.blocked_list_mode_number)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_exact_number_chip")
                )
                FilterChip(
                    selected = !isExactMode,
                    onClick = { onMatchTypeSelected(BlockedMatchType.PREFIX) },
                    label = { Text(stringResource(R.string.blocked_list_mode_prefix)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Dialpad,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_prefix_chip")
                )
            }

            // Number / Prefix Text Field
            OutlinedTextField(
                value = input,
                onValueChange = onInputChanged,
                label = {
                    Text(
                        text = stringResource(
                            if (isExactMode) R.string.blocked_list_label_number
                            else R.string.label_prefix_input
                        )
                    )
                },
                placeholder = {
                    Text(
                        text = stringResource(
                            if (isExactMode) R.string.blocked_list_placeholder_number
                            else R.string.placeholder_prefix_input
                        ),
                        style = PrefixMonoStyle.copy(fontWeight = FontWeight.Normal)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isExactMode) Icons.Rounded.Phone else Icons.Rounded.Dialpad,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (input.isNotEmpty()) {
                        IconButton(onClick = { onInputChanged("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cd_clear_input)
                            )
                        }
                    }
                },
                isError = inputError != null,
                supportingText = if (inputError != null) {
                    { Text(text = inputError) }
                } else null,
                singleLine = true,
                textStyle = PrefixMonoStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("prefix_input_field")
            )

            // Optional Label / Reason Text Field
            OutlinedTextField(
                value = labelInput,
                onValueChange = onLabelChanged,
                label = { Text(stringResource(R.string.label_reason_input)) },
                placeholder = { Text(stringResource(R.string.placeholder_reason_input)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Label,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (labelInput.isNotEmpty()) {
                        IconButton(onClick = { onLabelChanged("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cd_clear_input)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onAddClick() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("label_input_field")
            )

            // Quick Spam Series Presets (visible in Prefix mode)
            if (!isExactMode) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.quick_examples_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QUICK_BLOCK_PREFIX_PRESETS.forEach { preset ->
                            SuggestionChip(
                                onClick = { onApplyQuickPrefix(preset.prefix, preset.label) },
                                label = {
                                    Text(
                                        text = "${preset.prefix} • ${preset.label}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.testTag("quick_chip_${preset.prefix}")
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("add_prefix_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddModerator,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (isExactMode) R.string.blocked_list_btn_add_number
                        else R.string.btn_add_prefix
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun BlockedListRuleRow(
    entity: BlockedPrefixEntity,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (entity.isActive) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        label = "blocked_rule_card_bg"
    )
    val iconBg by animateColorAsState(
        targetValue = if (entity.isActive) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "blocked_rule_icon_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (entity.isActive) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "blocked_rule_icon_tint"
    )
    val toggleCd = stringResource(R.string.cd_toggle_prefix, entity.prefixString)

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("prefix_card_${entity.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            width = 1.dp,
            color = if (entity.isActive) {
                MaterialTheme.colorScheme.outlineVariant
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            }
        )
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
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (entity.isExactNumber) Icons.Rounded.Phone else Icons.Rounded.Block,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(21.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = entity.prefixString,
                        style = PrefixMonoStyle,
                        color = if (entity.isActive) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (entity.isExactNumber) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = stringResource(
                                if (entity.isExactNumber) R.string.blocked_list_badge_exact
                                else R.string.blocked_list_badge_prefix
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entity.isExactNumber) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = entity.label.ifBlank {
                            stringResource(
                                if (entity.isExactNumber) R.string.blocked_list_default_number_label
                                else R.string.default_rule_label
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (entity.isActive) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = stringResource(
                                if (entity.isActive) R.string.rule_status_blocking
                                else R.string.rule_status_paused
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entity.isActive) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Switch(
                checked = entity.isActive,
                onCheckedChange = onToggleActive,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .semantics { contentDescription = toggleCd }
                    .testTag("prefix_switch_${entity.id}")
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("prefix_delete_${entity.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete_prefix, entity.prefixString),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EmptyBlockedListCard() {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_state_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
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
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = stringResource(R.string.blocked_list_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.blocked_list_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
