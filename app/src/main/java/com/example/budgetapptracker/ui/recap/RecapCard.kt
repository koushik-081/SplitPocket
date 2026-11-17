package com.example.budgetapptracker.ui.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.budgetapptracker.data.MonthRecap
import com.example.budgetapptracker.ui.theme.LocalAppColors
import com.example.budgetapptracker.util.toRupeeString
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

// The first week of each month, Home opens with how the last month went. Shows nothing otherwise.
@Composable
fun RecapCard(viewModel: RecapViewModel = viewModel(factory = RecapViewModel.Factory)) {
    val recap by viewModel.recap.collectAsStateWithLifecycle()
    recap?.let { RecapContent(it, onDismiss = viewModel::dismiss, onSetBudget = viewModel::setBudget) }
}

@Composable
private fun RecapContent(
    recap: MonthRecap,
    onDismiss: () -> Unit,
    onSetBudget: (categoryId: String, budgetPaise: Long) -> Unit,
) {
    val colors = LocalAppColors.current
    val monthName = recap.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    // The theme's main colour for good news, its error colour for overspending.
    val good = MaterialTheme.colorScheme.primary
    val bad = MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.panel),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$monthName recap", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Got it") }
            }

            if (recap.allocatedPaise > 0) {
                Text(
                    text = if (recap.savedPaise >= 0) "${recap.savedPaise.toRupeeString()} left unspent"
                    else "${(-recap.savedPaise).toRupeeString()} over budget",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (recap.savedPaise >= 0) good else bad,
                )
            }
            Line("Spent", recap.spentPaise.toRupeeString() +
                if (recap.allocatedPaise > 0) " of ${recap.allocatedPaise.toRupeeString()}" else "")
            if (recap.incomePaise > 0) Line("Income", recap.incomePaise.toRupeeString())
            if (recap.investedPaise > 0) Line("Invested", recap.investedPaise.toRupeeString())
            recap.previousSpentPaise?.let { before ->
                val change = (recap.spentPaise - before) * 100 / before
                val previous = recap.month.minusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                Line(
                    "Compared with $previous",
                    if (change == 0L) "about the same" else "${abs(change)}% ${if (change < 0) "less" else "more"}",
                )
            }
            if (recap.envelopesWithBudget > 0) {
                Line("Envelopes within budget", "${recap.envelopesWithinBudget} of ${recap.envelopesWithBudget}")
            }
            recap.mostOver?.let { (name, paise) -> Line("Most over", "$name by ${paise.toRupeeString()}") }
            recap.topPayee?.let { (name, paise) -> Line("Most paid to", "$name · ${paise.toRupeeString()}") }
            recap.biggest?.let { Line("Biggest expense", "${it.amountPaise.toRupeeString()} at ${it.merchant}") }
            Line("No-spend days", recap.noSpendDays.toString())

            if (recap.budgetSuggestions.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text("Ready for a budget?", style = MaterialTheme.typography.titleSmall)
                Text(
                    "You tracked these without a limit. Set last month's spending as the budget?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                recap.budgetSuggestions.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.name)
                            Text(
                                "Spent ${s.spentPaise.toRupeeString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onSetBudget(s.categoryId, s.suggestedPaise) }) {
                            Text("Set ${s.suggestedPaise.toRupeeString()}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Text(value, maxLines = 2)
    }
}
