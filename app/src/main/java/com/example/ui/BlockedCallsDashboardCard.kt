package com.example.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BlockedCallsSummary
import com.example.data.model.DailyBlockedStat
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.ceil
import kotlin.math.max

/**
 * Top summary dashboard visualizing blocked calls over the last 7 days with KPI cards,
 * animated native Compose bar chart, dashed gridlines, and tap-to-inspect day tooltips.
 */
@Composable
internal fun BlockedCallsDashboardCard(
    summary: BlockedCallsSummary,
    activeRuleCount: Int,
    modifier: Modifier = Modifier
) {
    val stats = summary.dailyStats
    var selectedDayIndex by remember(stats.size) {
        mutableIntStateOf((stats.size - 1).coerceAtLeast(0))
    }
    val safeSelectedIndex = selectedDayIndex.coerceIn(0, (stats.size - 1).coerceAtLeast(0))
    val selectedDay = stats.getOrNull(safeSelectedIndex)

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("blocked_calls_dashboard_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.dashboard_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.dashboard_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_badge_7d),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // 3 KPI Stat Tiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardMetricTile(
                    label = stringResource(R.string.dashboard_metric_7d_blocked),
                    value = summary.totalLast7Days.toString(),
                    accentColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_metric_7d_blocked")
                )
                DashboardMetricTile(
                    label = stringResource(R.string.dashboard_metric_daily_peak),
                    value = summary.peakDailyCount.toString(),
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_metric_daily_peak")
                )
                DashboardMetricTile(
                    label = stringResource(R.string.dashboard_metric_active_rules),
                    value = activeRuleCount.toString(),
                    accentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_metric_active_rules")
                )
            }

            // Interactive Day Tooltip Chip
            if (selectedDay != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_selected_tooltip")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selectedDay.count > 0) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    }
                                )
                        )
                        Text(
                            text = if (selectedDay.count == 1) {
                                stringResource(
                                    R.string.dashboard_tooltip_single,
                                    selectedDay.fullDateLabel
                                )
                            } else {
                                stringResource(
                                    R.string.dashboard_tooltip_plural,
                                    selectedDay.fullDateLabel,
                                    selectedDay.count
                                )
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Interactive 7-Day Bar Chart
            if (stats.isNotEmpty()) {
                SevenDayBlockedBarChart(
                    stats = stats,
                    selectedIndex = safeSelectedIndex,
                    onSelectDay = { selectedDayIndex = it }
                )
            }
        }
    }
}

@Composable
private fun DashboardMetricTile(
    label: String,
    value: String,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = accentColor,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SevenDayBlockedBarChart(
    stats: List<DailyBlockedStat>,
    selectedIndex: Int,
    onSelectDay: (Int) -> Unit
) {
    val maxDataValue = stats.maxOfOrNull { it.count } ?: 0
    // Round Y-axis ceiling to a clean even number >= 4 so gridlines look balanced
    val yAxisMax = if (maxDataValue <= 4) 4 else ceil(maxDataValue / 2.0).toInt() * 2
    val yAxisMid = yAxisMax / 2

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val errorColor = MaterialTheme.colorScheme.error
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant
    val trackHighlightColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    val zeroBarColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val chartDescription = stringResource(R.string.dashboard_chart_cd)

    // Smoothly animate each bar's normalized height fraction
    val animatedFractions = stats.map { day ->
        val target = if (yAxisMax > 0) day.count.toFloat() / yAxisMax.toFloat() else 0f
        animateFloatAsState(
            targetValue = target.coerceIn(0f, 1f),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "bar_fraction_${day.dayStartMillis}"
        ).value
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = chartDescription }
            .testTag("seven_day_bar_chart"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Y-Axis Ticks Column
            Column(
                modifier = Modifier
                    .width(28.dp)
                    .height(148.dp)
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = yAxisMax.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = yAxisMid.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Native Compose Canvas Bar Chart
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(148.dp)
                    .pointerInput(stats.size) {
                        detectTapGestures { offset ->
                            if (stats.isNotEmpty() && size.width > 0) {
                                val slotWidth = size.width.toFloat() / stats.size
                                val tappedIndex = (offset.x / slotWidth)
                                    .toInt()
                                    .coerceIn(0, stats.size - 1)
                                onSelectDay(tappedIndex)
                            }
                        }
                    }
            ) {
                val chartWidth = size.width
                val chartHeight = size.height
                val topPadding = 8.dp.toPx()
                val bottomPadding = 4.dp.toPx()
                val usableHeight = max(1f, chartHeight - topPadding - bottomPadding)

                // Horizontal Dashed Gridlines (100%, 50%, 0%)
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                val gridYs = listOf(
                    topPadding,
                    topPadding + usableHeight / 2f,
                    chartHeight - bottomPadding
                )
                gridYs.forEachIndexed { idx, y ->
                    drawLine(
                        color = gridLineColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = if (idx == 2) 1.5.dp.toPx() else 1.dp.toPx(),
                        pathEffect = if (idx == 2) null else dashEffect
                    )
                }

                val slotWidth = chartWidth / stats.size
                val barWidth = (slotWidth * 0.50f).coerceIn(14.dp.toPx(), 32.dp.toPx())
                val cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                val minZeroHeight = 5.dp.toPx()

                stats.forEachIndexed { index, day ->
                    val slotCenterX = slotWidth * index + slotWidth / 2f
                    val barLeft = slotCenterX - barWidth / 2f

                    // Subtle vertical track highlight behind selected column
                    if (index == selectedIndex) {
                        drawRoundRect(
                            color = trackHighlightColor,
                            topLeft = Offset(slotCenterX - (slotWidth * 0.38f), topPadding),
                            size = Size(slotWidth * 0.76f, usableHeight),
                            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                        )
                    }

                    val fraction = animatedFractions[index]
                    val barHeight = if (day.count == 0) {
                        minZeroHeight
                    } else {
                        max(12.dp.toPx(), usableHeight * fraction)
                    }
                    val barTop = (chartHeight - bottomPadding) - barHeight

                    if (day.count == 0) {
                        drawRoundRect(
                            color = if (index == selectedIndex) primaryColor.copy(alpha = 0.65f) else zeroBarColor,
                            topLeft = Offset(barLeft, barTop),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    } else {
                        val barBrush = if (index == selectedIndex) {
                            Brush.verticalGradient(
                                colors = listOf(errorColor, primaryColor),
                                startY = barTop,
                                endY = chartHeight - bottomPadding
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(secondaryColor, primaryColor),
                                startY = barTop,
                                endY = chartHeight - bottomPadding
                            )
                        }
                        drawRoundRect(
                            brush = barBrush,
                            topLeft = Offset(barLeft, barTop),
                            size = Size(barWidth, barHeight),
                            cornerRadius = cornerRadius
                        )
                    }
                }
            }
        }

        // X-Axis Day Labels Row (also tappable for accessibility)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            stats.forEachIndexed { index, day ->
                val isSelected = index == selectedIndex
                val labelText = if (day.isToday) {
                    stringResource(R.string.dashboard_day_today)
                } else {
                    day.shortDayLabel
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            }
                        )
                        .clickable { onSelectDay(index) }
                        .padding(vertical = 4.dp)
                        .testTag("dashboard_day_label_$index"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
    }
}
