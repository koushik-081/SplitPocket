package com.example.budgetapptracker.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.budgetapptracker.ui.theme.LocalAppColors
import com.example.budgetapptracker.ui.theme.transparentTopBarColors
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import com.example.budgetapptracker.ui.export.ExportPdfButton

private val wholeRupees = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
    .apply { maximumFractionDigits = 0 }

private fun Long.toShortRupees(): String = wholeRupees.format(this / 100.0)

// Status colours carry meaning (good / careful), so they stay the same in both themes.
private val OnTrack = Color(0xFF2FBF8F)
private val SpendingFast = Color(0xFFF2A000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(viewModel: InsightsViewModel = viewModel(factory = InsightsViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = transparentTopBarColors(),
                title = { Text("Insights") },
                actions = { ExportPdfButton() },
            )
        },

    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ComparisonCard(state) }
            if (state.count == 0) {
                item {
                    Text(
                        text = "Add or scan a few expenses and this page will fill with charts.",
                        modifier = Modifier.padding(4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item { StatGrid(state) }
                item { DailyCard(state) }
                if (state.envelopes.isNotEmpty()) item { PaceCard(state) }
                item { TopPlacesCard(state) }
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.panel),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun PanelTitle(title: String, note: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ComparisonCard(state: InsightsState) {
    val difference = state.spentPaise - state.lastMonthSamePeriodPaise

    Panel {
        Text(
            text = "Spent so far this month",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(state.spentPaise.toShortRupees(), style = MaterialTheme.typography.displaySmall)
        when {
            state.lastMonthSamePeriodPaise == 0L -> Text(
                text = "Nothing to compare with yet: ${state.lastMonthName} has no spending recorded up to this date.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            difference <= 0 -> Text(
                text = "${abs(difference).toShortRupees()} less than at this point in ${state.lastMonthName}",
                style = MaterialTheme.typography.labelLarge,
                color = OnTrack,
            )
            else -> Text(
                text = "${difference.toShortRupees()} more than at this point in ${state.lastMonthName}",
                style = MaterialTheme.typography.labelLarge,
                color = SpendingFast,
            )
        }
    }
}

@Composable
private fun StatGrid(state: InsightsState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Daily average", state.dailyAveragePaise.toShortRupees(), Modifier.weight(1f))
            StatTile("No-spend days", state.noSpendDays.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Biggest expense",
                value = state.biggest?.amountPaise?.toShortRupees() ?: "₹0",
                modifier = Modifier.weight(1f),
                note = state.biggest?.merchant,
            )
            StatTile("Expenses", state.count.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, note: String? = null) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.panel),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = note ?: " ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DailyCard(state: InsightsState) {
    val peak = state.dailyPaise.maxOrNull() ?: 0L
    val colors = LocalAppColors.current
    val barColor = colors.accents[3]
    val peakColor = MaterialTheme.colorScheme.primary

    Panel {
        PanelTitle("Day by day", if (peak > 0) "Peak ${peak.toShortRupees()}" else null)
        Row(
            modifier = Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            state.dailyPaise.forEachIndexed { index, amount ->
                val fraction = if (peak == 0L) 0f else amount.toFloat() / peak
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction.coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    index >= state.today -> colors.track.copy(alpha = 0.06f)
                                    amount == 0L -> colors.track
                                    amount == peak -> peakColor
                                    else -> barColor
                                }
                            ),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(1, state.daysInMonth / 2, state.daysInMonth).forEach { day ->
                Text(
                    text = day.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PaceCard(state: InsightsState) {
    val colors = LocalAppColors.current
    val markerColor = MaterialTheme.colorScheme.onSurface
    val errorColor = MaterialTheme.colorScheme.error

    Panel {
        PanelTitle("Envelope pace", "${(state.monthFraction * 100).roundToInt()}% of month gone")
        state.envelopes.forEachIndexed { index, envelope ->
            val status = when {
                envelope.overBudget -> "Over budget" to errorColor
                envelope.usedFraction > state.monthFraction + 0.1f -> "Spending fast" to SpendingFast
                else -> "On track" to OnTrack
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${envelope.name} · ${(envelope.usedFraction * 100).roundToInt()}% used",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(status.first, style = MaterialTheme.typography.labelLarge, color = status.second)
                }
                val fill = if (envelope.overBudget) errorColor else colors.accents[index % colors.accents.size]
                Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(color = colors.track, cornerRadius = radius)
                    drawRoundRect(
                        color = fill,
                        size = Size(size.width * envelope.usedFraction.coerceIn(0f, 1f), size.height),
                        cornerRadius = radius,
                    )
                    // Where an envelope would be if it were spent evenly through the month.
                    val x = size.width * state.monthFraction
                    drawLine(
                        color = markerColor,
                        start = Offset(x, -3.dp.toPx()),
                        end = Offset(x, size.height + 3.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
        }
        Text(
            text = "The line marks today. A bar that stops before the line is spending slower than the month is passing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TopPlacesCard(state: InsightsState) {
    val colors = LocalAppColors.current
    val top = state.topMerchants.firstOrNull()?.amountPaise ?: 0L

    Panel {
        PanelTitle("Top places", "This month")
        state.topMerchants.forEachIndexed { index, merchant ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = merchant.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (merchant.count == 1) "1 payment" else "${merchant.count} payments",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(0.dp))
                    Text(merchant.amountPaise.toShortRupees(), style = MaterialTheme.typography.titleSmall)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(colors.track),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (top == 0L) 0f else merchant.amountPaise.toFloat() / top)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(colors.accents[index % colors.accents.size]),
                    )
                }
            }
        }
    }
}
