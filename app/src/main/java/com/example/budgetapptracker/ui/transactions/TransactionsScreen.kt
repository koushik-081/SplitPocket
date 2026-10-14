package com.example.budgetapptracker.ui.transactions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.budgetapptracker.data.EnvelopeSummary
import com.example.budgetapptracker.data.PersonBalance
import com.example.budgetapptracker.data.SplitMode
import com.example.budgetapptracker.data.planShares
import com.example.budgetapptracker.data.TransactionRow
import com.example.budgetapptracker.ui.theme.transparentTopBarColors
import com.example.budgetapptracker.util.toRupeeString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val UNKNOWN_MERCHANT = "UNKNOWN"
// Reading old messages and being told about new ones are two separate permissions.
// From Android 13, showing notifications needs the user's permission as well.
private val smsPermissions = buildList {
    add(Manifest.permission.READ_SMS)
    add(Manifest.permission.RECEIVE_SMS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private val dateFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a")

private fun Long.toDateText(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(dateFormat)

// UPI IDs of people usually start with their phone number (e.g. 98xxxxxx10@ybl).
private fun looksLikePerson(merchant: String): Boolean =
    merchant.firstOrNull()?.isDigit() == true

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
expenseToOpen: String? = null,
onExpenseOpened: () -> Unit = {},
viewModel: TransactionsViewModel = viewModel(factory = TransactionsViewModel.Factory),
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selected by viewModel.filter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    var openTransaction by remember { mutableStateOf<TransactionRow?>(null) }
    var splitTransaction by remember { mutableStateOf<TransactionRow?>(null) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.READ_SMS] == true) viewModel.scanSms()
        else viewModel.showMessage("SMS permission is needed to scan for expenses")
    }
// Arrived from a tapped notification: show every expense, then open that one.
    LaunchedEffect(expenseToOpen, transactions) {
        if (expenseToOpen == null) return@LaunchedEffect
        if (selected != null) viewModel.selectFilter(null)
        if (searchQuery.isNotEmpty()) viewModel.search("")
        val target = transactions.firstOrNull { it.id == expenseToOpen }
        if (target != null) {
            openTransaction = target
            onExpenseOpened()
        }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = transparentTopBarColors(),
                title = { Text("Activity") },
                actions = {
                    TextButton(onClick = {
                        val granted = smsPermissions.all {
                            ContextCompat.checkSelfPermission(context, it) ==
                                    PackageManager.PERMISSION_GRANTED
                        }
                        if (granted) viewModel.scanSms()
                        else permissionLauncher.launch(smsPermissions)
                    }) { Text("Scan SMS") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.search(it) },
                placeholder = { Text("Search by place") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.search("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
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
                item {
                    FilterChip(
                        selected = selected == TransactionsViewModel.HIDDEN,
                        onClick = { viewModel.selectFilter(TransactionsViewModel.HIDDEN) },
                        label = { Text("Hidden") },
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
                        when (selected) {
                            TransactionsViewModel.INBOX -> "Inbox is empty. Everything is categorised."
                            TransactionsViewModel.HIDDEN -> "Nothing is hidden."
                            else -> "No spending recorded here yet"
                        }
                    )
                }
            } else {
                LazyColumn {
                    items(transactions, key = { it.id }) { transaction ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
        if (selected == TransactionsViewModel.HIDDEN) {
            AlertDialog(
                onDismissRequest = { openTransaction = null },
                title = { Text("Restore this expense?") },
                text = {
                    Text(
                        "${target.merchant} · ${target.amountPaise.toRupeeString()}\n\n" +
                            "It was marked as not an expense. Restoring puts it back in your Inbox."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.restore(target.id)
                        openTransaction = null
                    }) { Text("Restore") }
                },
                dismissButton = {
                    TextButton(onClick = { openTransaction = null }) { Text("Cancel") }
                },
            )
        } else {
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
                onSplit = {
                    splitTransaction = target
                    openTransaction = null
                },
                onUndoSplit = {
                    viewModel.undoSplit(target)
                    openTransaction = null
                },
            )
        }
    }

    splitTransaction?.let { target ->
        SplitDialog(
            transaction = target,
            people = people,
            onDismiss = { splitTransaction = null },
            onSplit = { shares ->
                viewModel.split(target, shares)
                splitTransaction = null
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
    onSplit: () -> Unit,
    onUndoSplit: () -> Unit,
) {
    val canRemember = transaction.merchant != UNKNOWN_MERCHANT
    var selectedId by remember { mutableStateOf(transaction.categoryId) }
    var rememberPayee by remember {
        mutableStateOf(
            // Already categorised: show what is saved. New: guess from the payee's name.
            if (transaction.categoryId != null) transaction.remembered
            else canRemember && !looksLikePerson(transaction.merchant)
        )
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

                val paidPaise = transaction.paidPaise
                if (paidPaise == null && transaction.source != "SPLIT" && transaction.source != "SETTLE") {
                    TextButton(onClick = onSplit) { Text("Split with people") }
                } else if (paidPaise != null && paidPaise != transaction.amountPaise) {
                    Text(
                        text = "Split: you paid ${paidPaise.toRupeeString()}, and this is your share.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    // Only splits made here can be undone; a group bill is edited in its group.
                    if (transaction.splitCount > 0) {
                        TextButton(onClick = onUndoSplit) { Text("Undo split") }
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

// Choose who shared an expense you have already paid, and how it is divided.
@Composable
private fun SplitDialog(
    transaction: TransactionRow,
    people: List<PersonBalance>,
    onDismiss: () -> Unit,
    onSplit: (Map<String, Long>) -> Unit,
) {
    var mode by remember { mutableStateOf(SplitMode.Equal) }
    var chosen by remember { mutableStateOf(emptySet<String>()) }
    // What is typed next to each person: rupees or a percentage, depending on the mode.
    var entries by remember { mutableStateOf(emptyMap<String, String>()) }

    val chosenIds = people.map { it.id }.filter { it in chosen }
    val shares = planShares(transaction.amountPaise, mode, chosenIds, entries)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split ${transaction.amountPaise.toRupeeString()}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "${transaction.merchant} · ${transaction.occurredAt.toDateText()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SplitMode.entries.forEach { option ->
                        FilterChip(
                            selected = mode == option,
                            onClick = {
                                mode = option
                                entries = emptyMap()
                            },
                            label = { Text(option.label) },
                        )
                    }
                }

                if (people.isEmpty()) {
                    Text("Add people on the People tab first.")
                }
                people.forEach { person ->
                    val isChosen = person.id in chosen
                    val toggle = {
                        chosen = if (isChosen) chosen - person.id else chosen + person.id
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { toggle() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = isChosen, onCheckedChange = { toggle() })
                        Text(person.name, modifier = Modifier.weight(1f))
                        if (isChosen && mode != SplitMode.Equal) {
                            OutlinedTextField(
                                value = entries[person.id].orEmpty(),
                                onValueChange = { typed ->
                                    entries = entries + (person.id to typed.take(9))
                                },
                                prefix = if (mode == SplitMode.Amount) ({ Text("₹") }) else null,
                                suffix = if (mode == SplitMode.Percent) ({ Text("%") }) else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.width(120.dp).padding(vertical = 4.dp),
                            )
                        }
                    }
                }

                if (chosenIds.isNotEmpty()) {
                    Text(
                        text = when {
                            shares == null && mode == SplitMode.Equal -> "This amount is too small to split."
                            shares == null -> "Enter each person's share. Together they cannot be more than the bill."
                            mode == SplitMode.Equal ->
                                "Each of them owes you ${shares.values.first().toRupeeString()}.\n" +
                                    "Your share: ${(transaction.amountPaise - shares.values.sum()).toRupeeString()}"
                            else ->
                                "They owe you ${shares.values.sum().toRupeeString()} in total.\n" +
                                    "Your share: ${(transaction.amountPaise - shares.values.sum()).toRupeeString()}"
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = shares != null,
                onClick = { shares?.let(onSplit) },
            ) { Text("Split") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
