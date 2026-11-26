package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.repository.SpamBlockerSettings
import com.example.ui.theme.ShieldWarningAmber

/**
 * Dedicated Do Not Disturb (Contacts Only) mode toggle card.
 * When enabled, [com.example.telecom.PrefixCallScreeningService] automatically rejects every
 * incoming call whose number is not found in the user's Contacts.
 */
@Composable
internal fun DoNotDisturbCard(
    enabled: Boolean,
    hasContactsPermission: Boolean,
    onToggle: (Boolean) -> Unit,
    onRequestContactsPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconContainerColor by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "dnd_icon_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "dnd_icon_tint"
    )
    val dndSwitchCd = stringResource(R.string.dnd_cd_switch)

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("do_not_disturb_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        .background(iconContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DoNotDisturbOn,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(23.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dnd_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (enabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = stringResource(
                                    if (enabled) R.string.dnd_status_on else R.string.dnd_status_off
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (enabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = stringResource(
                            if (enabled) R.string.dnd_subtitle_on else R.string.dnd_subtitle_off
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .semantics { contentDescription = dndSwitchCd }
                        .testTag("do_not_disturb_switch")
                )
            }

            if (enabled && !hasContactsPermission) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = ShieldWarningAmber.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, ShieldWarningAmber.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dnd_contacts_permission_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            onClick = onRequestContactsPermission,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dnd_grant_contacts_button")
                        ) {
                            Text(
                                text = stringResource(R.string.backup_btn_allow_contacts),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Spam Call Blocker control panel allowing users to enable/disable the master
 * spam blocker and configure individual real-time Telecom protection layers.
 */
@Composable
internal fun SpamBlockerControlsCard(
    settings: SpamBlockerSettings,
    onToggleMaster: (Boolean) -> Unit,
    onToggleBuiltInSeries: (Boolean) -> Unit,
    onToggleHiddenPrivate: (Boolean) -> Unit,
    onToggleSpoofedStirShaken: (Boolean) -> Unit,
    onToggleSilenceOnly: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val iconContainerColor by animateColorAsState(
        targetValue = if (settings.isSpamBlockerEnabled) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "spam_blocker_icon_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (settings.isSpamBlockerEnabled) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "spam_blocker_icon_tint"
    )
    val masterSwitchCd = stringResource(R.string.cd_master_spam_switch)

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("spam_blocker_controls_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (settings.isSpamBlockerEnabled) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Master Spam Blocker Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(iconContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(23.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.spam_blocker_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (settings.isSpamBlockerEnabled) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = stringResource(
                                    if (settings.isSpamBlockerEnabled) {
                                        R.string.spam_blocker_status_on
                                    } else {
                                        R.string.spam_blocker_status_off
                                    }
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (settings.isSpamBlockerEnabled) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = stringResource(
                            if (settings.isSpamBlockerEnabled) {
                                R.string.spam_blocker_subtitle_on
                            } else {
                                R.string.spam_blocker_subtitle_off
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = settings.isSpamBlockerEnabled,
                    onCheckedChange = onToggleMaster,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                        checkedTrackColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .semantics { contentDescription = masterSwitchCd }
                        .testTag("master_spam_blocker_switch")
                )
            }

            // Granular Spam Protection Layers (visible when Master Switch is enabled)
            AnimatedVisibility(visible = settings.isSpamBlockerEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SpamProtectionLayerRow(
                        icon = Icons.Outlined.Report,
                        title = stringResource(R.string.spam_toggle_builtin_title),
                        description = stringResource(R.string.spam_toggle_builtin_desc),
                        checked = settings.blockBuiltInSpamSeries,
                        onCheckedChange = onToggleBuiltInSeries,
                        testTag = "toggle_builtin_spam_series"
                    )

                    SpamProtectionLayerRow(
                        icon = Icons.Outlined.VerifiedUser,
                        title = stringResource(R.string.spam_toggle_spoofed_title),
                        description = stringResource(R.string.spam_toggle_spoofed_desc),
                        checked = settings.blockSpoofedStirShakenCalls,
                        onCheckedChange = onToggleSpoofedStirShaken,
                        testTag = "toggle_spoofed_stir_shaken"
                    )

                    SpamProtectionLayerRow(
                        icon = Icons.Outlined.PersonOff,
                        title = stringResource(R.string.spam_toggle_private_title),
                        description = stringResource(R.string.spam_toggle_private_desc),
                        checked = settings.blockHiddenPrivateCalls,
                        onCheckedChange = onToggleHiddenPrivate,
                        testTag = "toggle_hidden_private_calls"
                    )

                    SpamProtectionLayerRow(
                        icon = Icons.Outlined.NotificationsOff,
                        title = stringResource(R.string.spam_toggle_silence_title),
                        description = stringResource(R.string.spam_toggle_silence_desc),
                        checked = settings.silenceOnlyMode,
                        onCheckedChange = onToggleSilenceOnly,
                        testTag = "toggle_silence_only_mode"
                    )
                }
            }
        }
    }
}

@Composable
private fun SpamProtectionLayerRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(testTag)
        )
    }
}

/**
 * Dedicated settings card for Incoming Ringtone, Vibration, and Call/Spam Notifications.
 */
@Composable
internal fun CallAlertsSettingsCard(
    settings: SpamBlockerSettings,
    hasNotificationPermission: Boolean,
    onToggleRingtone: (Boolean) -> Unit,
    onToggleVibration: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("call_alerts_settings_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.alerts_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.alerts_card_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SpamProtectionLayerRow(
                icon = Icons.Outlined.NotificationsActive,
                title = stringResource(R.string.toggle_ringtone_title),
                description = stringResource(R.string.toggle_ringtone_desc),
                checked = settings.ringtoneEnabled,
                onCheckedChange = onToggleRingtone,
                testTag = "toggle_ringtone_enabled"
            )

            SpamProtectionLayerRow(
                icon = Icons.Outlined.Vibration,
                title = stringResource(R.string.toggle_vibration_title),
                description = stringResource(R.string.toggle_vibration_desc),
                checked = settings.vibrationEnabled,
                onCheckedChange = onToggleVibration,
                testTag = "toggle_vibration_enabled"
            )

            SpamProtectionLayerRow(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.toggle_notifications_title),
                description = stringResource(R.string.toggle_notifications_desc),
                checked = settings.notificationsEnabled && hasNotificationPermission,
                onCheckedChange = { enable ->
                    if (enable && !hasNotificationPermission) {
                        onRequestNotificationPermission()
                    } else {
                        onToggleNotifications(enable)
                    }
                },
                testTag = "toggle_notifications_enabled"
            )

            if (!hasNotificationPermission) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onRequestNotificationPermission,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_notification_permission_button")
                ) {
                    Text(
                        text = stringResource(R.string.btn_grant_notification_permission),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
