package com.example.budgetapptracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.budgetapptracker.util.toRupeeString
import kotlin.math.roundToInt

data class BreakdownRow(
    val id: String,
    val name: String,
    val spentPaise: Long,
    val budgetPaise: Long,
    val color: Color,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingBreakdownSheet(
    rows: List<BreakdownRow>,
    totalPaise: Long,
    onDismiss: () -> Unit,
    onOpenEnvelope: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text("Where it went", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${totalPaise.toRupeeString()} spent from envelopes this month",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                ColumnLabel("Envelope", Modifier.weight(1f), TextAlign.Start)
                ColumnLabel("Spent", Modifier.width(104.dp), TextAlign.End)
                ColumnLabel("Share", Modifier.width(56.dp), TextAlign.End)
            }
            HorizontalDivider()

            rows.forEach { row ->
                val share = if (totalPaise == 0L) 0f else row.spentPaise.toFloat() / totalPaise
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenEnvelope(row.id) }
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(row.color),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (row.budgetPaise > 0) "of ${row.budgetPaise.toRupeeString()} budget"
                                else "No limit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = row.spentPaise.toRupeeString(),
                            modifier = Modifier.width(104.dp),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "${(share * 100).roundToInt()}%",
                            modifier = Modifier.width(56.dp),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(share.coerceIn(0.02f, 1f))
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(row.color),
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ColumnLabel(text: String, modifier: Modifier, align: TextAlign) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = align,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}