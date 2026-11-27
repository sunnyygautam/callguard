package com.example.ui

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.BlockedCallLogEntity
import com.example.ml.SpamMlAnalysisService
import com.example.ui.theme.PrefixMonoStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun BlockedCallHistoryHeader(
    blockedCount: Int,
    onExportClick: () -> Unit,
    onClearAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.blocked_history_header),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (blockedCount > 0) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = stringResource(R.string.blocked_history_count, blockedCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TextButton(
                onClick = onExportClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("history_quick_export_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = stringResource(R.string.backup_cd_quick_export),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.backup_btn_quick_export),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            if (blockedCount > 0) {
                TextButton(
                    onClick = onClearAllClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("clear_history_button")
                ) {
                    Text(
                        text = stringResource(R.string.btn_clear_history),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
internal fun BlockedCallLogRow(
    log: BlockedCallLogEntity,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedTimestamp = remember(log.blockedAt) {
        formatBlockedTimestamp(log.blockedAt)
    }
    var showMlDetailDialog by remember { mutableStateOf(false) }

    val mlAnalysis = remember(log.id, log.phoneNumber, log.spamProbability, log.mlReason) {
        val computed = SpamMlAnalysisService.analyzeNumber(
            rawNumber = log.phoneNumber,
            isHiddenOrPrivate = log.matchedPrefix.equals("Private", ignoreCase = true) ||
                log.normalizedNumber.isBlank(),
            isStirShakenFailed = log.matchedPrefix.equals("STIR/SHAKEN", ignoreCase = true),
            behaviorAnomalyScore = 0.65,
            behaviorReasons = listOf("Intercepted & blocked by CallGuard (${log.matchedPrefix})")
        )
        if (log.spamProbability in 0..100) {
            val mergedSignals = buildList {
                if (log.mlReason.isNotBlank()) add(log.mlReason)
                computed.signals.forEach { if (it != log.mlReason) add(it) }
            }
            computed.copy(
                probability = log.spamProbability / 100.0,
                probabilityPercent = log.spamProbability,
                riskTier = SpamMlAnalysisService.tierForPercent(log.spamProbability),
                topReason = log.mlReason.ifBlank { computed.topReason },
                signals = mergedSignals
            )
        } else {
            computed
        }
    }

    if (showMlDetailDialog) {
        SpamAnalysisDetailDialog(
            displayTitle = log.phoneNumber,
            phoneNumber = log.phoneNumber,
            analysis = mlAnalysis,
            onDismiss = { showMlDetailDialog = false }
        )
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("blocked_call_card_${log.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
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
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhoneDisabled,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(21.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = log.phoneNumber,
                    style = PrefixMonoStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = stringResource(R.string.matched_prefix_badge, log.matchedPrefix),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    SpamProbabilityBadge(
                        probabilityPercent = mlAnalysis.probabilityPercent,
                        riskTier = mlAnalysis.riskTier,
                        onClick = { showMlDetailDialog = true },
                        modifier = Modifier.testTag("blocked_call_spam_badge_${log.id}")
                    )
                }

                if (log.matchedLabel.isNotBlank()) {
                    Text(
                        text = log.matchedLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = formattedTimestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("blocked_call_delete_${log.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete_blocked_log, log.phoneNumber),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun EmptyBlockedHistoryCard(
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("empty_history_state"),
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
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = stringResource(R.string.empty_history_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.empty_history_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun ClearHistoryConfirmationDialog(
    blockedCount: Int,
    onDismiss: () -> Unit,
    onConfirmClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_clear_history_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(R.string.dialog_clear_history_body, blockedCount),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmClear,
                modifier = Modifier.testTag("confirm_clear_history_button")
            ) {
                Text(
                    text = stringResource(R.string.dialog_confirm_clear),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dialog_cancel))
            }
        }
    )
}

private data class CachedDateFormatters(
    val locale: Locale,
    val todayFormat: SimpleDateFormat,
    val otherDayFormat: SimpleDateFormat
)

private val cachedTimestampFormatters = ThreadLocal<CachedDateFormatters>()

internal fun formatBlockedTimestamp(epochMillis: Long): String {
    val currentLocale = Locale.getDefault()
    var cached = cachedTimestampFormatters.get()
    if (cached == null || cached.locale != currentLocale) {
        cached = CachedDateFormatters(
            locale = currentLocale,
            todayFormat = SimpleDateFormat("'Today,' h:mm a", currentLocale),
            otherDayFormat = SimpleDateFormat("MMM d, h:mm a", currentLocale)
        )
        cachedTimestampFormatters.set(cached)
    }
    val formatter = if (DateUtils.isToday(epochMillis)) cached.todayFormat else cached.otherDayFormat
    return formatter.format(Date(epochMillis))
}
