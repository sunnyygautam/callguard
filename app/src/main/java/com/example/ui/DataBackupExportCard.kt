package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.backup.BackupExportResult
import com.example.data.backup.BackupRestoreResult
import com.example.ui.theme.ShieldActiveEmerald
import com.example.ui.theme.ShieldWarningAmber

/**
 * Material 3 card allowing users to back up and restore their blocked phone numbers/prefixes,
 * call & shield settings, blocked call history, and device contacts using a local `.json` file
 * via Android's zero-permission Storage Access Framework.
 */
@Composable
internal fun DataBackupExportCard(
    blockedNumberCount: Int,
    blockedCallCount: Int,
    contactCount: Int,
    hasContactsPermission: Boolean,
    includeBlockedNumbers: Boolean,
    includeCallSettings: Boolean,
    includeBlockedHistory: Boolean,
    includeContacts: Boolean,
    isExporting: Boolean,
    isRestoring: Boolean,
    lastBackupResult: BackupExportResult.Success?,
    lastRestoreResult: BackupRestoreResult.Success?,
    onToggleIncludeBlockedNumbers: (Boolean) -> Unit,
    onToggleIncludeCallSettings: (Boolean) -> Unit,
    onToggleIncludeBlockedHistory: (Boolean) -> Unit,
    onToggleIncludeContacts: (Boolean) -> Unit,
    onRequestContactsPermission: () -> Unit,
    onExportClick: () -> Unit,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasAnyScopeSelected =
        includeBlockedNumbers || includeCallSettings || includeBlockedHistory || includeContacts
    val canExport = hasAnyScopeSelected && !isExporting && !isRestoring
    val canRestore = !isExporting && !isRestoring

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("data_backup_export_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FileDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.backup_card_title_v2),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.backup_card_subtitle_v2),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.backup_badge_json),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Export scope selectors
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupScopeOptionRow(
                    icon = Icons.Outlined.Block,
                    title = stringResource(R.string.backup_option_blocked_numbers),
                    subtitle = stringResource(R.string.backup_count_blocked_numbers, blockedNumberCount),
                    checked = includeBlockedNumbers,
                    onCheckedChange = onToggleIncludeBlockedNumbers,
                    testTag = "backup_checkbox_blocked_numbers"
                )

                BackupScopeOptionRow(
                    icon = Icons.Outlined.Tune,
                    title = stringResource(R.string.backup_option_call_settings),
                    subtitle = stringResource(R.string.backup_desc_call_settings),
                    checked = includeCallSettings,
                    onCheckedChange = onToggleIncludeCallSettings,
                    testTag = "backup_checkbox_call_settings"
                )

                BackupScopeOptionRow(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.backup_option_blocked_calls),
                    subtitle = stringResource(R.string.backup_count_blocked_calls, blockedCallCount),
                    checked = includeBlockedHistory,
                    onCheckedChange = onToggleIncludeBlockedHistory,
                    testTag = "backup_checkbox_blocked_calls"
                )

                BackupScopeOptionRow(
                    icon = Icons.Outlined.Contacts,
                    title = stringResource(R.string.backup_option_contacts),
                    subtitle = if (hasContactsPermission) {
                        stringResource(R.string.backup_count_contacts, contactCount)
                    } else {
                        stringResource(R.string.backup_contacts_permission_needed)
                    },
                    subtitleWarning = !hasContactsPermission,
                    checked = includeContacts,
                    onCheckedChange = onToggleIncludeContacts,
                    testTag = "backup_checkbox_contacts"
                )
            }

            // Inline Contacts permission helper when contacts are selected but not yet granted
            if (includeContacts && !hasContactsPermission) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = ShieldWarningAmber.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, ShieldWarningAmber.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.backup_contacts_permission_needed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onRequestContactsPermission,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("backup_allow_contacts_button")
                        ) {
                            Text(
                                text = stringResource(R.string.backup_btn_allow_contacts),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            if (!hasAnyScopeSelected) {
                Text(
                    text = stringResource(R.string.backup_select_at_least_one),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Primary Export & Restore JSON Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onExportClick,
                    enabled = canExport,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag("export_backup_button")
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.backup_btn_exporting),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.backup_btn_export),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRestoreClick,
                    enabled = canRestore,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag("restore_backup_button")
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.backup_btn_restoring),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.backup_btn_restore),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Last successful export confirmation strip
            if (lastBackupResult != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_last_saved_banner"),
                    shape = RoundedCornerShape(14.dp),
                    color = ShieldActiveEmerald.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, ShieldActiveEmerald.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = ShieldActiveEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(
                                R.string.backup_last_saved_v2,
                                lastBackupResult.fileName,
                                lastBackupResult.blockedNumberCount,
                                lastBackupResult.blockedCallCount,
                                lastBackupResult.contactCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Last successful restore confirmation strip
            if (lastRestoreResult != null) {
                val settingsSuffix = if (lastRestoreResult.restoredCallSettings) {
                    stringResource(R.string.backup_restored_settings_suffix)
                } else {
                    ""
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_last_restored_banner"),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(
                                R.string.backup_last_restored,
                                lastRestoreResult.fileName,
                                lastRestoreResult.restoredBlockedNumbers,
                                settingsSuffix
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupScopeOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    subtitleWarning: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Checkbox) { onCheckedChange(!checked) }
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (checked) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (subtitleWarning) {
                            ShieldWarningAmber
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Checkbox(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.minimumInteractiveComponentSize()
            )
        }
    }
}
