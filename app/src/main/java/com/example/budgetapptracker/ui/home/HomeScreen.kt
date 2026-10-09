package com.example.budgetapptracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
    var showAddEnvelope by rememberSaveable { mutableStateOf(false) }
    var expenseTarget by remember { mutableStateOf<EnvelopeSummary?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("This month") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAddEnvelope = true }) {
                Text("New envelope")
            }
        },
    ) { padding ->
        if (envelopes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Create your first envelope to start budgeting")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(envelopes, key = { it.id }) { envelope ->
                    EnvelopeCard(envelope, onClick = { expenseTarget = envelope })
                }
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
}

@Composable
private fun EnvelopeCard(envelope: EnvelopeSummary, onClick: () -> Unit) {
    val overspent = envelope.remainingPaise < 0
    val fraction = if (envelope.monthlyBudgetPaise == 0L) 0f
    else (envelope.spentPaise.toFloat() / envelope.monthlyBudgetPaise).coerceIn(0f, 1f)

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(envelope.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (overspent) "Over by ${(-envelope.remainingPaise).toRupeeString()}"
                else "${envelope.remainingPaise.toRupeeString()} left of ${envelope.monthlyBudgetPaise.toRupeeString()}",
                color = if (overspent) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = if (overspent) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EntryDialog(
    title: String,
    textLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    val paise = rupeesToPaise(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(textLabel) },
                    singleLine = true,
                )
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
                enabled = text.isNotBlank() && paise != null,
                onClick = { paise?.let { onConfirm(text, it) } },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}