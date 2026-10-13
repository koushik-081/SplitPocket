package com.example.budgetapptracker.ui.bills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.budgetapptracker.data.BillStatus
import com.example.budgetapptracker.data.BillSuggestion
import com.example.budgetapptracker.ui.theme.LocalAppColors
import com.example.budgetapptracker.util.rupeesToPaise
import com.example.budgetapptracker.util.toRupeeString
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val dayFormat = DateTimeFormatter.ofPattern("d MMM")

// The Home card listing rent, EMIs and subscriptions, and what is still due this month.
@Composable
fun BillsCard(viewModel: BillsViewModel = viewModel(factory = BillsViewModel.Factory)) {
    val bills by viewModel.bills.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var openBill by remember { mutableStateOf<BillStatus?>(null) }

    val unpaid = bills.filter { !it.paid }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.panel),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Upcoming bills",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showAdd = true }) { Text("Add bill") }
            }

            if (bills.isEmpty()) {
                Text(
                    text = "Add rent, EMIs and subscriptions to see what is still due each month.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
            } else {
                Text(
                    text = if (unpaid.isEmpty()) "All paid for this month"
                    else "${unpaid.sumOf { it.bill.amountPaise }.toRupeeString()} still due this month",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            bills.forEach { status ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openBill = status }
                        .padding(top = 12.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(status.bill.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = status.describe(LocalDate.now()),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.overdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(status.bill.amountPaise.toRupeeString(), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    if (showAdd) {
        AddBillDialog(
            suggestions = suggestions,
            onDismiss = { showAdd = false },
            onAdd = { name, amountPaise, day ->
                viewModel.add(name, amountPaise, day)
                showAdd = false
            },
        )
    }

    openBill?.let { status ->
        AlertDialog(
            onDismissRequest = { openBill = null },
            title = { Text(status.bill.name) },
            text = {
                Text(
                    "${status.bill.amountPaise.toRupeeString()} on day ${status.bill.dayOfMonth} of every month\n" +
                            status.describe(LocalDate.now())
                )
            },
            confirmButton = {
                if (status.paidTo != null) {
                    // Ticked off by a real payment, so there is nothing to mark by hand.
                    TextButton(onClick = { openBill = null }) { Text("Close") }
                } else {
                    TextButton(onClick = {
                        viewModel.setPaid(status, paid = !status.paid)
                        openBill = null
                    }) { Text(if (status.paid) "Mark as not paid" else "Mark as paid") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.delete(status)
                    openBill = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

private fun BillStatus.describe(today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, dueDate)
    return when {
        paidTo != null -> "Paid to $paidTo · next on ${dueDate.format(dayFormat)}"
        paid -> "Paid · next on ${dueDate.format(dayFormat)}"
        overdue -> "Overdue since ${dueDate.format(dayFormat)}"
        days == 0L -> "Due today"
        days == 1L -> "Due tomorrow"
        else -> "Due ${dueDate.format(dayFormat)} · in $days days"
    }
}

@Composable
private fun AddBillDialog(
    suggestions: List<BillSuggestion>,
    onDismiss: () -> Unit,
    onAdd: (String, Long, Int) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf("") }

    val amountPaise = rupeesToPaise(amount)
    val dayOfMonth = day.toIntOrNull()?.takeIf { it in 1..31 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a monthly bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (suggestions.isNotEmpty()) {
                    Text("Found in your spending", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        suggestions.forEach { suggestion ->
                            AssistChip(
                                onClick = {
                                    name = suggestion.name
                                    amount = (suggestion.amountPaise / 100.0).toString()
                                    day = suggestion.dayOfMonth.toString()
                                },
                                label = { Text(suggestion.name) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Name") },
                    supportingText = { Text("Use the payee's name as it shows in Activity and it is ticked off by itself") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.take(10) },
                    label = { Text("Amount") },
                    prefix = { Text("₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it.filter(Char::isDigit).take(2) },
                    label = { Text("Day of the month (1 to 31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && amountPaise != null && dayOfMonth != null,
                onClick = {
                    if (amountPaise != null && dayOfMonth != null) onAdd(name, amountPaise, dayOfMonth)
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}