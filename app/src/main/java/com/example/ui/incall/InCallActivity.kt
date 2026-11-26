package com.example.ui.incall

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.telecom.ActiveCallUiState
import com.example.telecom.CallAudioRecorder
import com.example.telecom.CallLifecycleState
import com.example.telecom.CallSessionManager
import com.example.ui.theme.CallGuardTheme
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalCallGuardHeroColors
import com.example.ui.theme.ShieldActiveEmerald
import kotlinx.coroutines.delay
import java.util.Locale

private val IN_CALL_DTMF_ROWS = listOf(
    listOf('1', '2', '3'),
    listOf('4', '5', '6'),
    listOf('7', '8', '9'),
    listOf('*', '0', '#')
)

/**
 * Full-screen In-Call Activity launched by [com.example.telecom.CallGuardInCallService].
 * Displays over the lockscreen and handles incoming Answer/Decline as well as active
 * call controls (Mute, Speaker, Hold, DTMF Keypad, End Call).
 */
class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenPresentation()
        enableEdgeToEdge()

        setContent {
            CallGuardTheme(darkTheme = true) {
                val activeCall by CallSessionManager.activeCallState.collectAsStateWithLifecycle()

                // Close cleanly once the call is disconnected or removed by Telecom.
                LaunchedEffect(activeCall?.state) {
                    val state = activeCall?.state
                    if (state == null) {
                        finishAndRemoveTask()
                    } else if (state == CallLifecycleState.DISCONNECTED) {
                        delay(900L)
                        finishAndRemoveTask()
                    }
                }

                val context = LocalContext.current
                val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        CallSessionManager.toggleRecording()
                    } else {
                        CallSessionManager.onRecordingStateChanged(
                            isRecording = false,
                            errorMessage = context.getString(R.string.incall_record_permission_denied)
                        )
                    }
                }

                activeCall?.let { callUiState ->
                    InCallScreen(
                        callState = callUiState,
                        onAnswer = CallSessionManager::answerCall,
                        onRejectOrEnd = CallSessionManager::rejectOrDisconnectCall,
                        onToggleMute = CallSessionManager::toggleMute,
                        onToggleSpeaker = CallSessionManager::toggleSpeaker,
                        onToggleHold = CallSessionManager::toggleHold,
                        onToggleRecord = {
                            if (callUiState.isRecording || CallAudioRecorder.hasRecordAudioPermission(context)) {
                                CallSessionManager.toggleRecording()
                            } else {
                                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onPressDtmf = CallSessionManager::playDtmfDigit
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureLockScreenPresentation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun InCallScreen(
    callState: ActiveCallUiState,
    onAnswer: () -> Unit,
    onRejectOrEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleHold: () -> Unit,
    onToggleRecord: () -> Unit,
    onPressDtmf: (Char) -> Unit
) {
    var showKeypad by remember { mutableStateOf(false) }
    var elapsedSeconds by remember(callState.connectTimeMillis, callState.state) {
        mutableLongStateOf(0L)
    }
    var recordingElapsedSeconds by remember(callState.isRecording, callState.recordingStartedAtMillis) {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(callState.state, callState.connectTimeMillis) {
        if (callState.state == CallLifecycleState.ACTIVE && callState.connectTimeMillis > 0L) {
            while (true) {
                elapsedSeconds =
                    ((System.currentTimeMillis() - callState.connectTimeMillis) / 1000L)
                        .coerceAtLeast(0L)
                delay(1_000L)
            }
        }
    }

    LaunchedEffect(callState.isRecording, callState.recordingStartedAtMillis) {
        if (callState.isRecording && callState.recordingStartedAtMillis > 0L) {
            while (true) {
                recordingElapsedSeconds =
                    ((System.currentTimeMillis() - callState.recordingStartedAtMillis) / 1000L)
                        .coerceAtLeast(0L)
                delay(1_000L)
            }
        } else {
            recordingElapsedSeconds = 0L
        }
    }

    val statusText = when (callState.state) {
        CallLifecycleState.RINGING -> stringResource(R.string.incall_state_ringing)
        CallLifecycleState.DIALING -> stringResource(R.string.incall_state_dialing)
        CallLifecycleState.CONNECTING -> stringResource(R.string.incall_state_connecting)
        CallLifecycleState.ACTIVE -> formatElapsedDuration(elapsedSeconds)
        CallLifecycleState.HOLDING -> stringResource(R.string.incall_state_holding)
        CallLifecycleState.DISCONNECTING -> stringResource(R.string.incall_state_disconnecting)
        CallLifecycleState.DISCONNECTED -> stringResource(R.string.incall_state_disconnected)
    }

    val heroColors = LocalCallGuardHeroColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        heroColors.deepSurface,
                        heroColors.midSurface,
                        heroColors.bottomSurface
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Shield Badge & Caller Identity
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = ShieldActiveEmerald.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, ShieldActiveEmerald.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = ShieldActiveEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.incall_protected_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(48.dp)
                    )
                }

                Text(
                    text = callState.callerDisplayName ?: callState.phoneNumber,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = if (callState.callerDisplayName == null) {
                            JetBrainsMonoFontFamily
                        } else {
                            MaterialTheme.typography.headlineLarge.fontFamily
                        }
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("incall_caller_title")
                )

                if (callState.callerDisplayName != null) {
                    Text(
                        text = callState.phoneNumber,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = if (callState.state == CallLifecycleState.ACTIVE) {
                            JetBrainsMonoFontFamily
                        } else {
                            MaterialTheme.typography.titleMedium.fontFamily
                        }
                    ),
                    color = ShieldActiveEmerald,
                    modifier = Modifier.testTag("incall_status_text")
                )

                // Live Call Recording Pill Indicator
                AnimatedVisibility(visible = callState.isRecording) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = CrimsonError.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.70f)),
                        modifier = Modifier.testTag("incall_recording_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FiberManualRecord,
                                contentDescription = null,
                                tint = CrimsonError,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(
                                    R.string.incall_recording_live_badge,
                                    formatElapsedDuration(recordingElapsedSeconds)
                                ),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }

                // Recording Error Notice (e.g., mic denied or unavailable)
                val recError = callState.recordingError
                if (!recError.isNullOrBlank() && !callState.isRecording) {
                    Text(
                        text = recError,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFB4AB),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Optional In-Call DTMF Keypad
            AnimatedVisibility(visible = showKeypad && callState.state != CallLifecycleState.RINGING) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IN_CALL_DTMF_ROWS.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { digit ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.10f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onPressDtmf(digit) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = digit.toString(),
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 22.sp
                                            ),
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Call Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                if (callState.state == CallLifecycleState.RINGING) {
                    // Incoming Call: Decline & Answer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularCallActionButton(
                            icon = Icons.Rounded.CallEnd,
                            label = stringResource(R.string.incall_action_decline),
                            containerColor = CrimsonError,
                            onClick = onRejectOrEnd,
                            testTag = "incall_decline_button"
                        )
                        CircularCallActionButton(
                            icon = Icons.Rounded.Call,
                            label = stringResource(R.string.incall_action_answer),
                            containerColor = ShieldActiveEmerald,
                            onClick = onAnswer,
                            testTag = "incall_answer_button"
                        )
                    }
                } else {
                    // Outgoing / Active Call Controls Grid + End Call CTA
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        InCallToggleControl(
                            icon = if (callState.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            label = stringResource(R.string.incall_action_mute),
                            isActive = callState.isMuted,
                            onClick = onToggleMute,
                            testTag = "incall_mute_button"
                        )
                        InCallToggleControl(
                            icon = Icons.Outlined.Dialpad,
                            label = stringResource(R.string.incall_action_keypad),
                            isActive = showKeypad,
                            onClick = { showKeypad = !showKeypad },
                            testTag = "incall_keypad_button"
                        )
                        InCallToggleControl(
                            icon = Icons.AutoMirrored.Rounded.VolumeUp,
                            label = stringResource(R.string.incall_action_speaker),
                            isActive = callState.isSpeakerOn,
                            onClick = onToggleSpeaker,
                            testTag = "incall_speaker_button"
                        )
                        InCallToggleControl(
                            icon = if (callState.state == CallLifecycleState.HOLDING) {
                                Icons.Rounded.PlayArrow
                            } else {
                                Icons.Rounded.Pause
                            },
                            label = stringResource(R.string.incall_action_hold),
                            isActive = callState.state == CallLifecycleState.HOLDING,
                            onClick = onToggleHold,
                            testTag = "incall_hold_button"
                        )
                        InCallToggleControl(
                            icon = if (callState.isRecording) {
                                Icons.Rounded.StopCircle
                            } else {
                                Icons.Rounded.FiberManualRecord
                            },
                            label = if (callState.isRecording) {
                                stringResource(R.string.incall_action_stop_record)
                            } else {
                                stringResource(R.string.incall_action_record)
                            },
                            isActive = callState.isRecording,
                            activeContainerColor = CrimsonError,
                            activeIconTint = Color.White,
                            onClick = onToggleRecord,
                            testTag = "incall_record_button"
                        )
                    }

                    CircularCallActionButton(
                        icon = Icons.Rounded.CallEnd,
                        label = stringResource(R.string.incall_action_end),
                        containerColor = CrimsonError,
                        onClick = onRejectOrEnd,
                        testTag = "incall_end_button"
                    )
                }
            }
        }
    }
}

@Composable
private fun CircularCallActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(containerColor)
                .clickable(onClick = onClick)
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Composable
private fun InCallToggleControl(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String,
    activeContainerColor: Color = Color.White,
    activeIconTint: Color = LocalCallGuardHeroColors.current.deepSurface
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) activeContainerColor else Color.White.copy(alpha = 0.14f)
                )
                .clickable(onClick = onClick)
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeIconTint else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

private fun formatElapsedDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
