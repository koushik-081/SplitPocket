package com.example.budgetapptracker.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.budgetapptracker.data.EnvelopeSummary
import com.example.budgetapptracker.data.TransactionRow
import com.example.budgetapptracker.ui.theme.transparentTopBarColors
import com.example.budgetapptracker.util.toRupeeString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val detailDateFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a")

private fun Long.toDetailDateText(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(detailDateFormat)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvelopeDetailScreen(
    envelope: EnvelopeSummary,
    transactions: List<TransactionRow>,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onEditBudget: () -> Unit,
    onRemove: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var pendingRemove by remember { mutableStateOf<TransactionRow?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val overspent = envelope.remainingPaise < 0
    val fraction = if (envelope.monthlyBudgetPaise == 0L) 0f
    else (envelope.spentPaise.toFloat() / envelope.monthlyBudgetPaise).coerceIn(0f, 1f)

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = transparentTopBarColors(),
                title = { Text(envelope.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onEditBudget) { Text("Edit budget") }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete envelope")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddExpense) { Text("Add expense") }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (overspent) "Over by ${(-envelope.remainingPaise).toRupeeString()}"
                        else "${envelope.remainingPaise.toRupeeString()} left",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (overspent) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Spent ${envelope.spentPaise.toRupeeString()} of ${envelope.monthlyBudgetPaise.toRupeeString()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (overspent) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (transactions.isEmpty()) {
                item {
                    Text(
                        text = "No expenses in this envelope yet",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(transactions, key = { it.id }) { transaction ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(transaction.merchant) },
                    supportingContent = { Text(transaction.occurredAt.toDetailDateText()) },
                    trailingContent = {
                        Text(
                            transaction.amountPaise.toRupeeString(),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    modifier = Modifier.clickable { pendingRemove = transaction },
                )
                HorizontalDivider()
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${envelope.name}?") },
            text = {
                Text(
                    "Its expenses are kept and move to the Inbox in Activity, where you can " +
                            "put them in another envelope. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    pendingRemove?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove this expense?") },
            text = { Text("${target.merchant} · ${target.amountPaise.toRupeeString()}") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(target.id)
                    pendingRemove = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("Cancel") } },
        )
    }
}