package com.example.budgetapptracker.ui.people

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.budgetapptracker.data.DebtRow
import com.example.budgetapptracker.data.PersonBalance
import com.example.budgetapptracker.ui.theme.LocalAppColors
import com.example.budgetapptracker.ui.theme.transparentTopBarColors
import com.example.budgetapptracker.util.rupeesToPaise
import com.example.budgetapptracker.util.toRupeeString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import com.example.budgetapptracker.data.GroupSummary
import androidx.compose.material3.FilterChip
import com.example.budgetapptracker.data.EnvelopeSummary

// Money owed to you is shown in green, money you owe in the theme's error colour.
internal val OwedToYou = Color(0xFF2FBF8F)

private val entryDateFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a")

internal fun Long.toEntryDate(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(entryDateFormat)

internal fun balanceText(balancePaise: Long): String = when {
    balancePaise > 0 -> "owes you ${balancePaise.toRupeeString()}"
    balancePaise < 0 -> "you owe ${abs(balancePaise).toRupeeString()}"
    else -> "settled up"
}

@Composable
internal fun balanceColor(balancePaise: Long): Color = when {
    balancePaise > 0 -> OwedToYou
    balancePaise < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun PeopleScreen(viewModel: PeopleViewModel = viewModel(factory = PeopleViewModel.Factory)) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    val openPerson by viewModel.openPerson.collectAsStateWithLifecycle()
    val entries by viewModel.openPersonEntries.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val openGroup by viewModel.openGroup.collectAsStateWithLifecycle()
    val groupMembers by viewModel.openGroupMembers.collectAsStateWithLifecycle()
    val groupEntries by viewModel.openGroupEntries.collectAsStateWithLifecycle()
    val envelopes by viewModel.envelopes.collectAsStateWithLifecycle()

    val person = openPerson
    val group = openGroup
    if (person != null) {
        PersonDetail(
            person = person,
            entries = entries,
            envelopes = envelopes,
            onBack = { viewModel.closePerson() },
            onAddEntry = { signedPaise, note -> viewModel.addEntry(person.id, signedPaise, note) },
            onSettle = { envelopeId -> viewModel.settleUp(person, envelopeId) },
            onDeleteEntry = { viewModel.deleteEntry(it) },
            onDeletePerson = { viewModel.deletePerson(person.id) },
        )
    } else if (group != null) {
        GroupDetail(
            group = group,
            members = groupMembers,
            entries = groupEntries,
            envelopes = envelopes,
            onBack = { viewModel.closeGroup() },
            onAddSplit = { description, totalPaise, sharedWithIds, payerId, envelopeId ->
                viewModel.addSplit(group.id, description, totalPaise, sharedWithIds, payerId, envelopeId)
            },
            onDelete = { viewModel.deleteGroup(group.id) },
        )
    } else {
        PeopleList(
            people = people,
            groups = groups,
            onOpen = { viewModel.openPerson(it.id) },
            onAdd = { name, phone -> viewModel.addPerson(name, phone) },
            onOpenGroup = { viewModel.openGroup(it.id) },
            onAddGroup = { name, memberIds -> viewModel.addGroup(name, memberIds) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeopleList(
    people: List<PersonBalance>,
    groups: List<GroupSummary>,
    onOpen: (PersonBalance) -> Unit,
    onAdd: (String, String) -> Unit,
    onOpenGroup: (GroupSummary) -> Unit,
    onAddGroup: (String, List<String>) -> Unit,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showNewGroup by rememberSaveable { mutableStateOf(false) }
    val owedToYou = people.filter { it.balancePaise > 0 }.sumOf { it.balancePaise }
    val youOwe = people.filter { it.balancePaise < 0 }.sumOf { -it.balancePaise }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { TopAppBar(colors = transparentTopBarColors(), title = { Text("People") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAdd = true }) { Text("Add person") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TotalTile("You are owed", owedToYou, OwedToYou, Modifier.weight(1f))
                    TotalTile("You owe", youOwe, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                }
            }
            if (people.isEmpty()) {
                item {
                    Text(
                        text = "Add the people you share expenses with. Each one shows a single " +
                                "balance, however many times you lend or split.",
                        modifier = Modifier.padding(4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Groups", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showNewGroup = true }) { Text("New group") }
                }
            }
            if (groups.isEmpty()) {
                item {
                    Text(
                        text = "Make a group for a trip or a shared flat, then split bills in it.",
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(groups, key = { "group-" + it.id }) { group ->
                GroupCard(group = group, onOpen = { onOpenGroup(group) }, modifier = Modifier.animateItem())
            }
            item {
                Text(
                    text = "People",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }
            items(people, key = { it.id }) { person ->
                Card(
                    onClick = { onOpen(person) },
                    modifier = Modifier.fillMaxWidth().animateItem(),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.panel),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(person.name)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = person.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = balanceText(person.balancePaise),
                                style = MaterialTheme.typography.bodySmall,
                                color = balanceColor(person.balancePaise),
                            )
                        }
                    }
                }
            }
        }
    }
    if (showNewGroup) {
        NewGroupDialog(
            people = people,
            onDismiss = { showNewGroup = false },
            onCreate = { name, memberIds ->
                onAddGroup(name, memberIds)
                showNewGroup = false
            },
        )
    }
    if (showAdd) {
        AddPersonDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, phone ->
                onAdd(name, phone)
                showAdd = false
            },
        )
    }
}

@Composable
private fun TotalTile(label: String, amountPaise: Long, color: Color, modifier: Modifier = Modifier) {
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
            Text(
                text = amountPaise.toRupeeString(),
                style = MaterialTheme.typography.titleLarge,
                color = if (amountPaise == 0L) MaterialTheme.colorScheme.onSurface else color,
            )
        }
    }
}

@Composable
internal     fun Avatar(name: String) {
    val accents = LocalAppColors.current.accents
    val accent = accents[(name.lowercase().hashCode() and Int.MAX_VALUE) % accents.size]
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), color = accent, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonDetail(
    person: PersonBalance,
    entries: List<DebtRow>,
    envelopes: List<EnvelopeSummary>,
    onBack: () -> Unit,
    onAddEntry: (Long, String) -> Unit,
    onSettle: (envelopeId: String?) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onDeletePerson: () -> Unit,
) {
    val context = LocalContext.current
    // +1 when adding "they owe me", -1 for "I owe them", null when the dialog is closed.
    var entrySign by remember { mutableStateOf<Int?>(null) }
    var pendingDelete by remember { mutableStateOf<DebtRow?>(null) }
    var confirmDeletePerson by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = transparentTopBarColors(),
                title = { Text(person.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDeletePerson = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete person")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = when {
                            person.balancePaise > 0 -> "${person.name} owes you"
                            person.balancePaise < 0 -> "You owe ${person.name}"
                            else -> "All settled up"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = abs(person.balancePaise).toRupeeString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = balanceColor(person.balancePaise),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { entrySign = 1 }, modifier = Modifier.weight(1f)) {
                            Text("They owe me")
                        }
                        OutlinedButton(onClick = { entrySign = -1 }, modifier = Modifier.weight(1f)) {
                            Text("I owe them")
                        }
                    }
                    if (person.balancePaise != 0L) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                // Paying them back is spending, so ask which envelope it came from.
                                onClick = { if (person.balancePaise < 0) settling = true else onSettle(null) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Settle up")
                            }
                            if (person.balancePaise > 0) {
                                OutlinedButton(
                                    onClick = { sendReminder(context, person) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Remind")
                                }
                            }
                        }
                    }
                }
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        text = "No entries yet.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(entries, key = { it.id }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pendingDelete = entry }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.note.ifBlank { if (entry.amountPaise > 0) "They owe me" else "I owe them" },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = listOfNotNull(entry.groupName, entry.createdAt.toEntryDate()).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = (if (entry.amountPaise > 0) "+" else "−") + abs(entry.amountPaise).toRupeeString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = balanceColor(entry.amountPaise),
                    )
                }
                HorizontalDivider()
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    entrySign?.let { sign ->
        EntryDialog(
            title = if (sign > 0) "${person.name} owes me" else "I owe ${person.name}",
            onDismiss = { entrySign = null },
            onSave = { paise, note ->
                onAddEntry(sign * paise, note)
                entrySign = null
            },
        )
    }
    if (settling) {
        SettleDialog(
            person = person,
            envelopes = envelopes,
            onDismiss = { settling = false },
            onSettle = { envelopeId ->
                onSettle(envelopeId)
                settling = false
            },
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this entry?") },
            text = { Text("${entry.note.ifBlank { "Entry" }} · ${abs(entry.amountPaise).toRupeeString()}") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteEntry(entry.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    if (confirmDeletePerson) {
        AlertDialog(
            onDismissRequest = { confirmDeletePerson = false },
            title = { Text("Delete ${person.name}?") },
            text = { Text("This also deletes every entry with them. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeletePerson = false
                    onDeletePerson()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletePerson = false }) { Text("Cancel") }
            },
        )
    }
}
// Shown when you pay someone back, so the money shows up as spending in an envelope.
@Composable
private fun SettleDialog(
    person: PersonBalance,
    envelopes: List<EnvelopeSummary>,
    onDismiss: () -> Unit,
    onSettle: (envelopeId: String?) -> Unit,
) {
    // Nothing is picked at first, so the choice is never made by accident.
    var picked by rememberSaveable { mutableStateOf(false) }
    var envelopeId by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paid ${person.name} ${abs(person.balancePaise).toRupeeString()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Which envelope did this money come out of?")
                ChipRow {
                    envelopes.forEach { envelope ->
                        FilterChip(
                            selected = picked && envelopeId == envelope.id,
                            onClick = {
                                picked = true
                                envelopeId = envelope.id
                            },
                            label = { Text(envelope.name) },
                        )
                    }
                    FilterChip(
                        selected = picked && envelopeId == null,
                        onClick = {
                            picked = true
                            envelopeId = null
                        },
                        label = { Text("Don't count it") },
                    )
                }
                Text(
                    text = when {
                        !picked -> "Pick \"Don't count it\" if your share is already in an envelope, " +
                                "for example from a group bill."
                        envelopeId == null -> "The balance is cleared, and no spending is added."
                        else -> "It shows in Activity and Insights as spending. If your bank SMS or " +
                                "payment app already caught this payment, that one is used."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = picked, onClick = { onSettle(envelopeId) }) { Text("Settle up") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddPersonDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add person") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("WhatsApp number (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onAdd(name, phone) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EntryDialog(title: String, onDismiss: () -> Unit, onSave: (Long, String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val paise = rupeesToPaise(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("What for (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = paise != null, onClick = { paise?.let { onSave(it, note.trim()) } }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// Opens WhatsApp with a ready-written reminder. Nothing is sent until the user taps send there.
private fun sendReminder(context: Context, person: PersonBalance) {
    val text = "Hi ${person.name}, a quick reminder that ${person.balancePaise.toRupeeString()} " +
            "is pending to me. Thanks!"
    val digits = person.phone.orEmpty().filter { it.isDigit() }
    val intent = if (digits.length >= 10) {
        // A ten-digit number is taken to be Indian, so the country code is added.
        val number = if (digits.length == 10) "91$digits" else digits
        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=${Uri.encode(text)}"))
    } else {
        // No number saved: let the user pick WhatsApp and the chat themselves.
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
            "Send reminder with",
        )
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No app can open the link; nothing more to do.
    }
}