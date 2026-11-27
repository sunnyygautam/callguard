package com.example.ui.incall

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ShieldActiveEmerald
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal const val SWIPE_UP_CONFIRM_THRESHOLD = 0.68f

private val SWIPE_TRACK_WIDTH = 86.dp
private val SWIPE_TRACK_HEIGHT = 192.dp
private val SWIPE_HANDLE_SIZE = 72.dp
private val SWIPE_TRACK_VERTICAL_PADDING = 7.dp

internal fun computeSwipeUpProgress(offsetY: Float, maxTravelPx: Float): Float {
    if (maxTravelPx <= 0f) return 0f
    return (-offsetY / maxTravelPx).coerceIn(0f, 1f)
}

internal fun isSwipeUpConfirmed(
    offsetY: Float,
    maxTravelPx: Float,
    threshold: Float = SWIPE_UP_CONFIRM_THRESHOLD
): Boolean = computeSwipeUpProgress(offsetY, maxTravelPx) >= threshold

private enum class ActiveSwipePrompt {
    IDLE,
    DRAGGING_ANSWER,
    READY_ANSWER,
    DRAGGING_REJECT,
    READY_REJECT
}

/**
 * Tactile incoming-call controls requiring the user to swipe up on the green Answer slider
 * or red Reject slider to receive or decline the call. Prevents accidental single-tap actions
 * while keeping full TalkBack accessibility support.
 */
@Composable
internal fun IncomingCallSwipeControls(
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activePrompt by remember { mutableStateOf(ActiveSwipePrompt.IDLE) }

    val promptText = when (activePrompt) {
        ActiveSwipePrompt.IDLE -> stringResource(R.string.incall_swipe_up_hint)
        ActiveSwipePrompt.DRAGGING_ANSWER -> stringResource(R.string.incall_swipe_up_answer_prompt)
        ActiveSwipePrompt.READY_ANSWER -> stringResource(R.string.incall_release_to_answer)
        ActiveSwipePrompt.DRAGGING_REJECT -> stringResource(R.string.incall_swipe_up_reject_prompt)
        ActiveSwipePrompt.READY_REJECT -> stringResource(R.string.incall_release_to_reject)
    }

    val promptTint = when (activePrompt) {
        ActiveSwipePrompt.READY_ANSWER -> ShieldActiveEmerald
        ActiveSwipePrompt.READY_REJECT -> CrimsonError
        else -> Color.White.copy(alpha = 0.85f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("incall_swipe_controls"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = when (activePrompt) {
                ActiveSwipePrompt.READY_ANSWER -> ShieldActiveEmerald.copy(alpha = 0.22f)
                ActiveSwipePrompt.READY_REJECT -> CrimsonError.copy(alpha = 0.22f)
                else -> Color.White.copy(alpha = 0.10f)
            },
            border = BorderStroke(
                width = 1.dp,
                color = when (activePrompt) {
                    ActiveSwipePrompt.READY_ANSWER -> ShieldActiveEmerald.copy(alpha = 0.65f)
                    ActiveSwipePrompt.READY_REJECT -> CrimsonError.copy(alpha = 0.65f)
                    else -> Color.White.copy(alpha = 0.18f)
                }
            )
        ) {
            AnimatedContent(
                targetState = promptText,
                label = "incoming_swipe_prompt"
            ) { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = promptTint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("incall_swipe_hint_text")
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            SwipeUpCallActionSlider(
                icon = Icons.Rounded.CallEnd,
                label = stringResource(R.string.incall_action_reject),
                accessibilityDescription = stringResource(R.string.incall_cd_swipe_up_reject),
                accentColor = CrimsonError,
                onConfirm = onReject,
                onDragStateChanged = { isDragging, isReady ->
                    activePrompt = when {
                        !isDragging -> ActiveSwipePrompt.IDLE
                        isReady -> ActiveSwipePrompt.READY_REJECT
                        else -> ActiveSwipePrompt.DRAGGING_REJECT
                    }
                },
                trackTestTag = "incall_decline_swipe_track",
                handleTestTag = "incall_decline_button"
            )

            SwipeUpCallActionSlider(
                icon = Icons.Rounded.Call,
                label = stringResource(R.string.incall_action_answer),
                accessibilityDescription = stringResource(R.string.incall_cd_swipe_up_answer),
                accentColor = ShieldActiveEmerald,
                onConfirm = onAnswer,
                onDragStateChanged = { isDragging, isReady ->
                    activePrompt = when {
                        !isDragging -> ActiveSwipePrompt.IDLE
                        isReady -> ActiveSwipePrompt.READY_ANSWER
                        else -> ActiveSwipePrompt.DRAGGING_ANSWER
                    }
                },
                trackTestTag = "incall_answer_swipe_track",
                handleTestTag = "incall_answer_button"
            )
        }
    }
}

@Composable
private fun SwipeUpCallActionSlider(
    icon: ImageVector,
    label: String,
    accessibilityDescription: String,
    accentColor: Color,
    onConfirm: () -> Unit,
    onDragStateChanged: (isDragging: Boolean, isReadyToConfirm: Boolean) -> Unit,
    trackTestTag: String,
    handleTestTag: String
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnDragStateChanged by rememberUpdatedState(onDragStateChanged)

    val maxTravelPx = remember(density) {
        with(density) {
            (SWIPE_TRACK_HEIGHT - SWIPE_HANDLE_SIZE - SWIPE_TRACK_VERTICAL_PADDING * 2).toPx()
        }
    }

    val offsetY = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isCommitted by remember { mutableStateOf(false) }
    var crossedThreshold by remember { mutableStateOf(false) }

    val progress = computeSwipeUpProgress(offsetY.value, maxTravelPx)
    val isReady = progress >= SWIPE_UP_CONFIRM_THRESHOLD

    // Gentle idle animations inviting the user to swipe upward
    val infiniteTransition = rememberInfiniteTransition(label = "swipe_up_idle_$label")
    val idleBounceDp by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_bounce_$label"
    )
    val chevronWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "chevron_wave_$label"
    )

    val handleOffsetPx = if (!isDragging && !isCommitted) {
        with(density) { idleBounceDp.dp.toPx() }
    } else {
        offsetY.value
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(SWIPE_TRACK_WIDTH)
                .height(SWIPE_TRACK_HEIGHT)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.14f + 0.30f * progress),
                            Color.White.copy(alpha = 0.08f + 0.08f * progress),
                            accentColor.copy(alpha = 0.10f + 0.16f * progress)
                        )
                    )
                )
                .border(
                    width = if (isReady) 2.dp else 1.25.dp,
                    color = accentColor.copy(alpha = if (isReady) 0.90f else 0.34f + 0.40f * progress),
                    shape = RoundedCornerShape(999.dp)
                )
                .pointerInput(maxTravelPx) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            if (isCommitted) return@detectVerticalDragGestures
                            isDragging = true
                            crossedThreshold = false
                            currentOnDragStateChanged(true, false)
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (isCommitted) return@detectVerticalDragGestures
                            change.consume()
                            val nextOffset = (offsetY.value + dragAmount).coerceIn(-maxTravelPx, 0f)
                            val readyNow = isSwipeUpConfirmed(nextOffset, maxTravelPx)
                            if (readyNow && !crossedThreshold) {
                                crossedThreshold = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (!readyNow && crossedThreshold) {
                                crossedThreshold = false
                            }
                            currentOnDragStateChanged(true, readyNow)
                            coroutineScope.launch {
                                offsetY.snapTo(nextOffset)
                            }
                        },
                        onDragEnd = {
                            if (isCommitted) return@detectVerticalDragGestures
                            if (isSwipeUpConfirmed(offsetY.value, maxTravelPx)) {
                                isCommitted = true
                                isDragging = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    offsetY.animateTo(
                                        targetValue = -maxTravelPx,
                                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                    )
                                    currentOnConfirm()
                                }
                            } else {
                                isDragging = false
                                crossedThreshold = false
                                currentOnDragStateChanged(false, false)
                                coroutineScope.launch {
                                    offsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            if (isCommitted) return@detectVerticalDragGestures
                            isDragging = false
                            crossedThreshold = false
                            currentOnDragStateChanged(false, false)
                            coroutineScope.launch {
                                offsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        }
                    )
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibilityDescription
                    onClick(label = label) {
                        if (!isCommitted) {
                            isCommitted = true
                            currentOnConfirm()
                        }
                        true
                    }
                }
                .testTag(trackTestTag)
        ) {
            // Top target halo that illuminates when the user nears the confirm threshold
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = SWIPE_TRACK_VERTICAL_PADDING)
                    .size(SWIPE_HANDLE_SIZE)
                    .clip(CircleShape)
                    .background(
                        accentColor.copy(alpha = if (isReady) 0.34f else 0.08f * progress)
                    )
            )

            // Animated upward chevron indicators (fade out smoothly as the handle rises over them)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 22.dp)
                    .alpha((1f - progress * 1.4f).coerceIn(0f, 1f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy((-10).dp)
            ) {
                for (step in 2 downTo 0) {
                    val distance = kotlin.math.abs(chevronWave - step.toFloat())
                    val chevronAlpha = (1f - (distance / 1.4f)).coerceIn(0.22f, 0.95f)
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = null,
                        tint = accentColor.copy(alpha = chevronAlpha),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Draggable circular Answer / Reject handle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = SWIPE_TRACK_VERTICAL_PADDING),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x = 0, y = handleOffsetPx.roundToInt()) }
                        .scale(1f + 0.07f * progress)
                        .size(SWIPE_HANDLE_SIZE)
                        .clip(CircleShape)
                        .background(accentColor)
                        .border(
                            width = if (isReady) 2.5.dp else 1.5.dp,
                            color = Color.White.copy(alpha = if (isReady) 0.92f else 0.35f),
                            shape = CircleShape
                        )
                        .testTag(handleTestTag),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = if (isReady) accentColor else Color.White.copy(alpha = 0.92f)
        )
    }
}
