package com.example.budgetapptracker.ui.sort

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.budgetapptracker.data.EnvelopeSummary
import com.example.budgetapptracker.data.PersonBalance
import com.example.budgetapptracker.data.TransactionRow
import com.example.budgetapptracker.sms.Guess
import com.example.budgetapptracker.sms.KIND_INVESTMENT
import com.example.budgetapptracker.sms.KIND_TRANSFER
import com.example.budgetapptracker.sms.MerchantCategories
import com.example.budgetapptracker.sms.PAYEE_PERSON
import com.example.budgetapptracker.ui.theme.LocalAppColors
import com.example.budgetapptracker.ui.transactions.SplitDialog
import com.example.budgetapptracker.util.toRupeeString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a")

private fun Long.toDateText(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(dateFormat)

// Someone on the People tab whose name matches the payee, e.g. "Rahul" for "RAHUL KUMAR".
private fun matchPerson(merchant: String, people: List<PersonBalance>): PersonBalance? {
    val payeeWords = MerchantCategories.normalise(merchant).trim().split(" ").toSet()
    return people.firstOrNull { person ->
        val words = MerchantCategories.normalise(person.name).trim().split(" ").filter { it.length > 1 }
        words.isNotEmpty() && words.all { it in payeeWords }
    }
}

// Home banner: "5 expenses not in an envelope · Sort". Shows nothing when everything is sorted.
// Once a day it also opens the sorting sheet by itself, the first time Home appears.
@Composable
fun SortBanner(viewModel: SortViewModel = viewModel(factory = SortViewModel.Factory)) {
    val unsorted by viewModel.unsorted.collectAsStateWithLifecycle()
    val promptedToday by viewModel.promptedToday.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    val waiting = unsorted.orEmpty()

    LaunchedEffect(waiting.isNotEmpty(), promptedToday) {
        if (waiting.isNotEmpty() && !promptedToday) {
            open = true
            viewModel.markPrompted()
        }
    }

    if (waiting.isNotEmpty()) {
        Card(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.panel),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (waiting.size == 1) "1 expense not in an envelope"
                        else "${waiting.size} expenses not in an envelope",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        // Home's "Spent" only adds up envelopes, so these are missing from it.
                        text = "${waiting.sumOf { it.amountPaise }.toRupeeString()} spent outside your envelopes, " +
                            "so \"Left to spend\" looks higher than it is",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { open = true }) { Text("Sort") }
            }
        }
    }

    if (open) SortSheet(viewModel = viewModel, onDismiss = { open = false })
}

// One expense at a time: tap an envelope and the next one appears.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(viewModel: SortViewModel, onDismiss: () -> Unit) {
    val unsorted by viewModel.unsorted.collectAsStateWithLifecycle()
    val envelopes by viewModel.envelopes.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val waiting = unsorted.orEmpty()
    // How many have been skipped. Sorted ones leave the list, so this is also the position.
    var skipped by rememberSaveable { mutableIntStateOf(0) }
    // Sorted in this session, for the progress bar and the closing message.
    var done by rememberSaveable { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val current = waiting.getOrNull(skipped)
            val total = done + waiting.size
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sort your spending", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (current != null) {
                    Text(
                        "${done + skipped + 1} of $total",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { (done + skipped).toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when {
                current != null -> SortCard(
                    transaction = current,
                    viewModel = viewModel,
                    envelopes = envelopes,
                    people = people,
                    onSorted = { done++ },
                    onSkip = { skipped++ },
                )
                waiting.isEmpty() -> {
                    Text("All sorted ✓", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (done > 0) "Every expense is in an envelope now. Your envelopes show the real picture."
                        else "Nothing to sort. Every expense is in an envelope.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
                else -> {
                    Text(
                        "You skipped ${waiting.size}. They stay in Activity › Inbox and on the Home banner.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { skipped = 0 }) { Text("Go through them again") }
                        Button(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortCard(
    transaction: TransactionRow,
    viewModel: SortViewModel,
    envelopes: List<EnvelopeSummary>,
    people: List<PersonBalance>,
    onSorted: () -> Unit,
    onSkip: () -> Unit,
) {
    val guess by produceState<Guess?>(null, transaction.id) {
        value = viewModel.guessFor(transaction)
    }
    val matched = remember(transaction.id, people) { matchPerson(transaction.merchant, people) }
    val isPerson = transaction.payee == PAYEE_PERSON || matched != null ||
        transaction.merchant.firstOrNull()?.isDigit() == true
    val canRemember = transaction.merchant.uppercase() != "UNKNOWN"
    // Remember is on for shops, off for people (you pay the same friend for different things).
    var rememberPayee by remember(transaction.id) { mutableStateOf(canRemember && !isPerson) }
    var paidBackTo by remember(transaction.id) { mutableStateOf<String?>(null) }
    var personAction by remember(transaction.id) { mutableStateOf<PersonAction?>(null) }
    var splitting by remember(transaction.id) { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.panel)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                transaction.amountPaise.toRupeeString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(transaction.merchant, style = MaterialTheme.typography.titleMedium)
            Text(
                "${transaction.occurredAt.toDateText()} · " + when (transaction.source) {
                    "SMS" -> "from a bank SMS"
                    "APP" -> "from a payment app"
                    else -> "added by you"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            guess?.let {
                Text(
                    "Looks like: ${it.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            paidBackTo?.let {
                Text(
                    "Recorded as paying back $it. Now pick an envelope for it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    // A guess that is not an envelope (investment, own account) gets its own one-tap button.
    guess?.kind?.let { kind ->
        Button(
            onClick = { viewModel.markKind(transaction, kind); onSorted() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Yes, ${guess?.label?.lowercase()}") }
    }

    Text("Which envelope?", style = MaterialTheme.typography.titleSmall)
    if (envelopes.isEmpty()) {
        Text("Create an envelope on Home first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        envelopes
            .sortedWith(compareByDescending<EnvelopeSummary> { it.id == guess?.envelopeId }
                .thenByDescending { it.spentPaise })
            .forEach { envelope ->
                FilterChip(
                    selected = envelope.id == guess?.envelopeId,
                    onClick = {
                        viewModel.assign(transaction, envelope.id, canRemember && rememberPayee)
                        onSorted()
                    },
                    label = { Text(envelope.name) },
                )
            }
    }
    if (canRemember) {
        Row(
            modifier = Modifier.clickable { rememberPayee = !rememberPayee },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = rememberPayee, onCheckedChange = { rememberPayee = it })
            Text("Always put ${transaction.merchant} here", style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (isPerson && people.isNotEmpty() && paidBackTo == null) {
        Text(
            "Paid to a person" + (matched?.let { " (${it.name}?)" } ?: "") + ". Was it…",
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { personAction = PersonAction.Lent }) { Text("A loan to them") }
            OutlinedButton(onClick = { personAction = PersonAction.PaidBack }) { Text("Paying them back") }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    Text("Not spending?", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (people.isNotEmpty() && transaction.splitCount == 0) {
            TextButton(onClick = { splitting = true }) { Text("Split with friends") }
        }
        if (guess?.kind != KIND_INVESTMENT) {
            TextButton(onClick = { viewModel.markKind(transaction, KIND_INVESTMENT); onSorted() }) { Text("Investment") }
        }
        if (guess?.kind != KIND_TRANSFER) {
            TextButton(onClick = { viewModel.markKind(transaction, KIND_TRANSFER); onSorted() }) { Text("To my own account") }
        }
        TextButton(onClick = { viewModel.notExpense(transaction); onSorted() }) { Text("Not an expense") }
        TextButton(onClick = onSkip) { Text("Skip for now") }
    }

    personAction?.let { action ->
        PersonPicker(
            title = if (action == PersonAction.Lent) "Who did you lend it to?" else "Who did you pay back?",
            people = people,
            preselected = matched?.id,
            onDismiss = { personAction = null },
            onPick = { person ->
                personAction = null
                if (action == PersonAction.Lent) {
                    viewModel.lend(transaction, person.id)
                    onSorted()
                } else {
                    viewModel.paidBack(transaction, person.id)
                    paidBackTo = person.name
                }
            },
        )
    }

    if (splitting) {
        SplitDialog(
            transaction = transaction,
            people = people,
            onDismiss = { splitting = false },
            onSplit = { shares ->
                // Your own share stays on this card, to pick its envelope next.
                viewModel.split(transaction, shares)
                splitting = false
            },
        )
    }
}

private enum class PersonAction { Lent, PaidBack }

@Composable
private fun PersonPicker(
    title: String,
    people: List<PersonBalance>,
    preselected: String?,
    onDismiss: () -> Unit,
    onPick: (PersonBalance) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                people
                    .sortedByDescending { it.id == preselected }
                    .forEach { person ->
                        Text(
                            text = person.name + if (person.id == preselected) "  ·  likely" else "",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(person) }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
