package com.example.budgetapptracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.budgetapptracker.data.EnvelopeSummary
import com.example.budgetapptracker.util.rupeesToPaise
import com.example.budgetapptracker.util.toRupeeString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val envelopes by viewModel.envelopes.collectAsStateWithLifecycle()
    val incomePaise by viewModel.incomePaise.collectAsStateWithLifecycle()
    val openEnvelope by viewModel.openEnvelope.collectAsStateWithLifecycle()
    val openTransactions by viewModel.openEnvelopeTransactions.collectAsStateWithLifecycle()
    var showAddEnvelope by rememberSaveable { mutableStateOf(false) }
    var showAddIncome by rememberSaveable { mutableStateOf(false) }
    var expenseTarget by remember { mutableStateOf<EnvelopeSummary?>(null) }
    var budgetTarget by remember { mutableStateOf<EnvelopeSummary?>(null) }

    val current = openEnvelope
    if (current != null) {
        EnvelopeDetailScreen(
            envelope = current,
            transactions = openTransactions,
            onBack = { viewModel.closeEnvelope() },
            onAddExpense = { expenseTarget = current },
            onEditBudget = { budgetTarget = current },
            onRemove = { viewModel.removeTransaction(it) },
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("This month") },
                    actions = {
                        TextButton(onClick = { showAddIncome = true }) { Text("Add income") }
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(onClick = { showAddEnvelope = true }) {
                    Text("New envelope")
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SummaryCard(
                        incomePaise = incomePaise,
                        allocatedPaise = envelopes.sumOf { it.monthlyBudgetPaise },
                        spentPaise = envelopes.sumOf { it.spentPaise },
                    )
                }
                if (envelopes.isEmpty()) {
                    item {
                        Text(
                            text = "Create your first envelope to start budgeting",
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(envelopes, key = { it.id }) { envelope ->
                    EnvelopeCard(
                        envelope = envelope,
                        onOpen = { viewModel.openEnvelope(envelope.id) },
                        onEditBudget = { budgetTarget = envelope },
                    )
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }

    if (showAddEnvelope) {
        EntryDialog(
            title = "New envelope",
            textLabel = "Name (e.g. Food)",
            onDismiss = { showAddEnvelope = false },
            onConfirm = { name, paise ->
                viewModel.addCategory(name, paise)
                showAddEnvelope = false
            },
        )
    }

    if (showAddIncome) {
        EntryDialog(
            title = "Add income",
            textLabel = "Source (e.g. Salary)",
            onDismiss = { showAddIncome = false },
            onConfirm = { source, paise ->
                viewModel.addIncome(source, paise)
                showAddIncome = false
            },
        )
    }

    expenseTarget?.let { target ->
        EntryDialog(
            title = "Spend from ${target.name}",
            textLabel = "Where / what",
            onDismiss = { expenseTarget = null },
            onConfirm = { merchant, paise ->
                viewModel.addExpense(target.id, paise, merchant)
                expenseTarget = null
            },
        )
    }

    budgetTarget?.let { target ->
        EntryDialog(
            title = "Budget for ${target.name}",
            textLabel = null,
            onDismiss = { budgetTarget = null },
            onConfirm = { _, paise ->
                viewModel.updateBudget(target.id, paise)
                budgetTarget = null
            },
        )
    }
}

@Composable
private fun SummaryCard(incomePaise: Long, allocatedPaise: Long, spentPaise: Long) {
    val unallocatedPaise = incomePaise - allocatedPaise

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryRow("Income", incomePaise.toRupeeString())
            SummaryRow("Allocated to envelopes", allocatedPaise.toRupeeString())
            if (unallocatedPaise >= 0) {
                SummaryRow("Unallocated", unallocatedPaise.toRupeeString())
            } else {
                SummaryRow(
                    label = "Over-allocated by",
                    value = (-unallocatedPaise).toRupeeString(),
                    valueColor = MaterialTheme.colorScheme.error,
                )
            }
            SummaryRow("Spent so far", spentPaise.toRupeeString())
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = valueColor, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun EnvelopeCard(
    envelope: EnvelopeSummary,
    onOpen: () -> Unit,
    onEditBudget: () -> Unit,
) {
    val overspent = envelope.remainingPaise < 0
    val fraction = if (envelope.monthlyBudgetPaise == 0L) 0f
    else (envelope.spentPaise.toFloat() / envelope.monthlyBudgetPaise).coerceIn(0f, 1f)

    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(envelope.name, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onEditBudget) { Text("Edit budget") }
            }
            Text(
                text = if (overspent) "Over by ${(-envelope.remainingPaise).toRupeeString()}"
                else "${envelope.remainingPaise.toRupeeString()} left of ${envelope.monthlyBudgetPaise.toRupeeString()}",
                color = if (overspent) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                color = if (overspent) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EntryDialog(
    title: String,
    textLabel: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    val paise = rupeesToPaise(amount)
    val textOk = textLabel == null || text.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (textLabel != null) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(textLabel) },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = textOk && paise != null,
                onClick = { paise?.let { onConfirm(text, it) } },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}