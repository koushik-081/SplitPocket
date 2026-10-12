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

// Money owed to you is shown in green, money you owe in the theme's error colour.
private val OwedToYou = Color(0xFF2FBF8F)

private val entryDateFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a")

private fun Long.toEntryDate(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(entryDateFormat)

private fun balanceText(balancePaise: Long): String = when {
    balancePaise > 0 -> "owes you ${balancePaise.toRupeeString()}"
    balancePaise < 0 -> "you owe ${abs(balancePaise).toRupeeString()}"
    else -> "settled up"
}

@Composable
private fun balanceColor(balancePaise: Long): Color = when {
    balancePaise > 0 -> OwedToYou
    balancePaise < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun PeopleScreen(viewModel: PeopleViewModel = viewModel(factory = PeopleViewModel.Factory)) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    val openPerson by viewModel.openPerson.collectAsStateWithLifecycle()
    val entries by viewModel.openPersonEntries.collectAsStateWithLifecycle()

    val person = openPerson
    if (person != null) {
        PersonDetail(
            person = person,
            entries = entries,
            onBack = { viewModel.closePerson() },
            onAddEntry = { signedPaise, note -> viewModel.addEntry(person.id, signedPaise, note) },
            onSettle = { viewModel.settleUp(person) },
            onDeleteEntry = { viewModel.deleteEntry(it) },
            onDeletePerson = { viewModel.deletePerson(person.id) },
        )
    } else {
        PeopleList(
            people = people,
            onOpen = { viewModel.openPerson(it.id) },
            onAdd = { name, phone -> viewModel.addPerson(name, phone) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeopleList(
    people: List<PersonBalance>,
    onOpen: (PersonBalance) -> Unit,
    onAdd: (String, String) -> Unit,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
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
private fun Avatar(name: String) {
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
    onBack: () -> Unit,
    onAddEntry: (Long, String) -> Unit,
    onSettle: () -> Unit,
    onDeleteEntry: (String) -> Unit,
    onDeletePerson: () -> Unit,
) {
    val context = LocalContext.current
    // +1 when adding "they owe me", -1 for "I owe them", null when the dialog is closed.
    var entrySign by remember { mutableStateOf<Int?>(null) }
    var pendingDelete by remember { mutableStateOf<DebtRow?>(null) }
    var confirmDeletePerson by remember { mutableStateOf(false) }

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
                            OutlinedButton(onClick = onSettle, modifier = Modifier.weight(1f)) {
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