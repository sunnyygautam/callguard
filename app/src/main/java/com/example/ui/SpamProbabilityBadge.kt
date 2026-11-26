package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ml.SpamMlAnalysisResult
import com.example.ml.SpamRiskTier
import com.example.ui.theme.PrefixMonoStyle
import com.example.ui.theme.ShieldActiveEmerald
import com.example.ui.theme.ShieldWarningAmber

/**
 * Compact, color-coded pill badge showing the on-device ML Spam Probability score (e.g. "87% Spam").
 */
@Composable
internal fun SpamProbabilityBadge(
    probabilityPercent: Int,
    riskTier: SpamRiskTier,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val (bgColor, accentColor) = spamTierColors(riskTier)
    val inspectCd = stringResource(R.string.ml_cd_inspect_score, probabilityPercent)

    val clickModifier = if (onClick != null) {
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = inspectCd }
    } else {
        Modifier
    }

    Surface(
        modifier = modifier.then(clickModifier),
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Psychology,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = stringResource(R.string.ml_spam_score_badge, probabilityPercent),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
        }
    }
}

/**
 * Material 3 dialog presenting the full explainable on-device ML Spam Probability breakdown,
 * calibrated gauge, and detected local malicious patterns for a call history entry.
 */
@Composable
internal fun SpamAnalysisDetailDialog(
    displayTitle: String,
    phoneNumber: String,
    analysis: SpamMlAnalysisResult,
    onDismiss: () -> Unit,
    onBlockPrefix: (() -> Unit)? = null
) {
    val (_, accentColor) = spamTierColors(analysis.riskTier)
    val riskLabelRes = when (analysis.riskTier) {
        SpamRiskTier.LOW -> R.string.ml_risk_low
        SpamRiskTier.MODERATE -> R.string.ml_risk_moderate
        SpamRiskTier.HIGH -> R.string.ml_risk_high
        SpamRiskTier.CRITICAL -> R.string.ml_risk_critical
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("ml_analysis_detail_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Rounded.Psychology,
                contentDescription = null,
                tint = accentColor
            )
        },
        title = {
            Text(
                text = stringResource(R.string.ml_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (phoneNumber.isNotBlank() && phoneNumber != displayTitle) {
                        Text(
                            text = phoneNumber,
                            style = PrefixMonoStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Probability Score & Gauge Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = accentColor.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.ml_spam_probability_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${analysis.probabilityPercent}% • ${stringResource(riskLabelRes)}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }

                        LinearProgressIndicator(
                            progress = { analysis.probability.toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = accentColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }

                // Explainable ML Signals & Matched Local Malicious Patterns
                Text(
                    text = stringResource(R.string.ml_dialog_signals_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    analysis.signals.forEach { signal ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                            Text(
                                text = signal,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (onBlockPrefix != null && phoneNumber.isNotBlank()) {
                Button(
                    onClick = {
                        onDismiss()
                        onBlockPrefix()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(text = stringResource(R.string.ml_dialog_block_action))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.ml_dialog_close))
                }
            }
        },
        dismissButton = if (onBlockPrefix != null && phoneNumber.isNotBlank()) {
            {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.ml_dialog_close))
                }
            }
        } else {
            null
        }
    )
}

@Composable
private fun spamTierColors(riskTier: SpamRiskTier): Pair<Color, Color> {
    return when (riskTier) {
        SpamRiskTier.LOW -> Pair(
            ShieldActiveEmerald.copy(alpha = 0.12f),
            ShieldActiveEmerald
        )
        SpamRiskTier.MODERATE -> Pair(
            ShieldWarningAmber.copy(alpha = 0.15f),
            ShieldWarningAmber
        )
        SpamRiskTier.HIGH -> Pair(
            MaterialTheme.colorScheme.error.copy(alpha = 0.13f),
            MaterialTheme.colorScheme.error
        )
        SpamRiskTier.CRITICAL -> Pair(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error
        )
    }
}
