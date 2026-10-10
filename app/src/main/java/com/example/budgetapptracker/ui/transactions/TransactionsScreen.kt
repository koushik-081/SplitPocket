package com.example.budgetapptracker.ui.transactions

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.budgetapptracker.data.EnvelopeSummary
import com.example.budgetapptracker.data.TransactionRow
import com.example.budgetapptracker.util.toRupeeString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val UNKNOWN_MERCHANT = "UNKNOWN"

private val dateFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a")

private fun Long.toDateText(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(dateFormat)

// UPI IDs of people usually start with their phone number (e.g. 98xxxxxx10@ybl).
private fun looksLikePerson(merchant: String): Boolean =
    merchant.firstOrNull()?.isDigit() == true

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = viewModel(factory = TransactionsViewModel.Factory),
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selected by viewModel.filter.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var openTransaction by remember { mutableStateOf<TransactionRow?>(null) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.scanSms()
        else viewModel.showMessage("SMS permission is needed to scan for expenses")
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions") },
                actions = {
                    TextButton(onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_SMS,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) viewModel.scanSms()
                        else permissionLauncher.launch(Manifest.permission.READ_SMS)
                    }) { Text("Scan SMS") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = selected == null,
                        onClick = { viewModel.selectFilter(null) },
                        label = { Text("All") },
                    )
                }
                item {
                    FilterChip(
                        selected = selected == TransactionsViewModel.INBOX,
                        onClick = { viewModel.selectFilter(TransactionsViewModel.INBOX) },
                        label = { Text("Inbox") },
                    )
                }
                items(categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = selected == category.id,
                        onClick = { viewModel.selectFilter(category.id) },
                        label = { Text(category.name) },
                    )
                }
            }

            Text(
                text = "Total: ${transactions.sumOf { it.amountPaise }.toRupeeString()}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )

            if (transactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (selected == TransactionsViewModel.INBOX) "Inbox is empty. Everything is categorised."
                        else "No spending recorded here yet"
                    )
                }
            } else {
                LazyColumn {
                    items(transactions, key = { it.id }) { transaction ->
                        ListItem(
                            headlineContent = { Text(transaction.merchant) },
                            supportingContent = {
                                Text("${transaction.categoryName ?: "Uncategorised"} · ${transaction.occurredAt.toDateText()}")
                            },
                            trailingContent = {
                                Text(
                                    transaction.amountPaise.toRupeeString(),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            modifier = Modifier.clickable { openTransaction = transaction },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    openTransaction?.let { target ->
        AssignDialog(
            transaction = target,
            categories = categories,
            onDismiss = { openTransaction = null },
            onAssign = { categoryId, rememberPayee ->
                viewModel.assign(target, categoryId, rememberPayee)
                openTransaction = null
            },
            onRemove = {
                viewModel.remove(target.id)
                openTransaction = null
            },
        )
    }
}

@Composable
private fun AssignDialog(
    transaction: TransactionRow,
    categories: List<EnvelopeSummary>,
    onDismiss: () -> Unit,
    onAssign: (String, Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val canRemember = transaction.merchant != UNKNOWN_MERCHANT
    var selectedId by remember { mutableStateOf(transaction.categoryId) }
    var rememberPayee by remember {
        mutableStateOf(canRemember && !looksLikePerson(transaction.merchant))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(transaction.merchant) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "${transaction.amountPaise.toRupeeString()} · ${transaction.occurredAt.toDateText()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                if (categories.isEmpty()) {
                    Text("Create an envelope on the Envelopes tab first.")
                }
                categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedId = category.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedId == category.id,
                            onClick = { selectedId = category.id },
                        )
                        Text(category.name)
                    }
                }

                if (canRemember) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Remember for this payee")
                        Switch(checked = rememberPayee, onCheckedChange = { rememberPayee = it })
                    }
                }

                TextButton(onClick = onRemove) {
                    Text(
                        text = if (transaction.source == "SMS") "Not an expense" else "Delete",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedId != null,
                onClick = { selectedId?.let { onAssign(it, rememberPayee && canRemember) } },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}