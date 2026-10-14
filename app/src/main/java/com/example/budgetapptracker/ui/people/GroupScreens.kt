package com.example.budgetapptracker.ui.people

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.budgetapptracker.data.EnvelopeSummary
import com.example.budgetapptracker.data.GroupEntryRow
import com.example.budgetapptracker.data.GroupSummary
import com.example.budgetapptracker.data.PersonBalance
import com.example.budgetapptracker.data.planEqualSplit
import com.example.budgetapptracker.ui.theme.LocalAppColors
import com.example.budgetapptracker.ui.theme.transparentTopBarColors
import com.example.budgetapptracker.util.rupeesToPaise
import com.example.budgetapptracker.util.toRupeeString
import kotlin.math.abs

// One group on the People tab.
@Composable
internal fun GroupCard(group: GroupSummary, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAppColors.current.accents[1]
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.panel),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Groups, contentDescription = null, tint = accent)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${group.memberCount} " + if (group.memberCount == 1) "member" else "members",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = groupBalanceText(group.balancePaise),
                style = MaterialTheme.typography.labelLarge,
                color = balanceColor(group.balancePaise),
            )
        }
    }
}

private fun groupBalanceText(balancePaise: Long): String = when {
    balancePaise > 0 -> "+" + balancePaise.toRupeeString()
    balancePaise < 0 -> "−" + abs(balancePaise).toRupeeString()
    else -> "settled"
}

@Composable
internal fun NewGroupDialog(
    people: List<PersonBalance>,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New group") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. Goa trip)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (people.isEmpty()) {
                    Text(
                        text = "Add people first, then put them in a group.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Members", style = MaterialTheme.typography.titleSmall)
                    people.forEach { person ->
                        CheckRow(
                            label = person.name,
                            checked = person.id in selected,
                            onToggle = {
                                selected = if (person.id in selected) selected - person.id else selected + person.id
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && selected.isNotEmpty(),
                onClick = { onCreate(name, selected.toList()) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(label)
    }
}

// One group: who is in it, what each owes inside this group, and its split bills.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupDetail(
    group: GroupSummary,
    members: List<PersonBalance>,
    entries: List<GroupEntryRow>,
    envelopes: List<EnvelopeSummary>,
    onBack: () -> Unit,
    onAddSplit: (description: String, totalPaise: Long, sharedWithIds: List<String>, payerId: String?, envelopeId: String?) -> Unit,
    onDelete: () -> Unit,
) {
    var showSplit by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = transparentTopBarColors(),
                title = { Text(group.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete group")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showSplit = true }) { Text("Split a bill") }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = when {
                            group.balancePaise > 0 -> "In this group you are owed"
                            group.balancePaise < 0 -> "In this group you owe"
                            else -> "This group is settled up"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = abs(group.balancePaise).toRupeeString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = balanceColor(group.balancePaise),
                    )
                }
            }
            item { SectionLabel("Members") }
            items(members, key = { "member-" + it.id }) { member ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(member.name)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = member.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = balanceText(member.balancePaise),
                        style = MaterialTheme.typography.bodySmall,
                        color = balanceColor(member.balancePaise),
                    )
                }
            }
            item { SectionLabel("Bills and entries") }
            if (entries.isEmpty()) {
                item {
                    Text(
                        text = "Nothing yet. Tap Split a bill to add the first one.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(entries, key = { "entry-" + it.id }) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.note.ifBlank { "Entry" },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${entry.personName} · ${entry.createdAt.toEntryDate()}",
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
            item { Spacer(modifier = Modifier.height(96.dp)) }
        }
    }

    if (showSplit) {
        SplitDialog(
            members = members,
            envelopes = envelopes,
            onDismiss = { showSplit = false },
            onSave = { description, totalPaise, sharedWithIds, payerId, envelopeId ->
                onAddSplit(description, totalPaise, sharedWithIds, payerId, envelopeId)
                showSplit = false
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${group.name}?") },
            text = { Text("The group is removed. What each person owes stays on their balance in People.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// Splits one bill equally between you and the ticked members.
@Composable
private fun SplitDialog(
    members: List<PersonBalance>,
    envelopes: List<EnvelopeSummary>,
    onDismiss: () -> Unit,
    onSave: (String, Long, List<String>, String?, String?) -> Unit,
) {
    var description by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    // null means "you" for the payer, and "no envelope" for the envelope.
    var payerId by rememberSaveable { mutableStateOf<String?>(null) }
    var envelopeId by rememberSaveable { mutableStateOf<String?>(null) }
    var sharedWith by remember { mutableStateOf(members.map { it.id }.toSet()) }

    val totalPaise = rupeesToPaise(amount)
    val plan = totalPaise?.takeIf { sharedWith.isNotEmpty() }?.let { planEqualSplit(it, sharedWith.size) }
    val payerName = members.firstOrNull { it.id == payerId }?.name
    val valid = description.isNotBlank() && totalPaise != null && sharedWith.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split a bill") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("What was it for") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Total amount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )

                Text("Paid by", style = MaterialTheme.typography.titleSmall)
                ChipRow {
                    FilterChip(selected = payerId == null, onClick = { payerId = null }, label = { Text("You") })
                    members.forEach { member ->
                        FilterChip(
                            selected = payerId == member.id,
                            onClick = { payerId = member.id },
                            label = { Text(member.name) },
                        )
                    }
                }

                Text("Split between you and", style = MaterialTheme.typography.titleSmall)
                members.forEach { member ->
                    CheckRow(
                        label = member.name,
                        checked = member.id in sharedWith,
                        onToggle = {
                            sharedWith = if (member.id in sharedWith) sharedWith - member.id else sharedWith + member.id
                        },
                    )
                }

                Text("Your share comes from", style = MaterialTheme.typography.titleSmall)
                ChipRow {
                    FilterChip(
                        selected = envelopeId == null,
                        onClick = { envelopeId = null },
                        label = { Text("No envelope") },
                    )
                    envelopes.forEach { envelope ->
                        FilterChip(
                            selected = envelopeId == envelope.id,
                            onClick = { envelopeId = envelope.id },
                            label = { Text(envelope.name) },
                        )
                    }
                }

                // Spell out the result before saving, so nothing about the split is a surprise.
                Text(
                    text = when {
                        plan == null -> "Enter the amount and tick at least one person."
                        payerId == null -> "Your share is ${plan.mySharePaise.toRupeeString()}. " +
                                "Each of the ${sharedWith.size} other${if (sharedWith.size == 1) "" else "s"} " +
                                "owes you ${plan.otherSharePaise.toRupeeString()}."
                        else -> "Your share is ${plan.mySharePaise.toRupeeString()}, " +
                                "which you will owe ${payerName ?: "them"}."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    if (totalPaise != null) {
                        onSave(description, totalPaise, sharedWith.toList(), payerId, envelopeId)
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// A row of chips that scrolls sideways when there are too many to fit.
@Composable
internal fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}