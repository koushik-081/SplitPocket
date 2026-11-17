package com.example.budgetapptracker.ui.payslips

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.budgetapptracker.data.PayslipEntity
import com.example.budgetapptracker.payslip.PayslipFields
import com.example.budgetapptracker.payslip.PayslipFiles
import com.example.budgetapptracker.payslip.PayslipRead
import com.example.budgetapptracker.util.rupeesToPaise
import com.example.budgetapptracker.util.toRupeeString
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private fun YearMonth.label() = "${month.getDisplayName(TextStyle.FULL, Locale.getDefault())} $year"

// The list of saved payslips, newest month first, opened from the + menu.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayslipsSheet(
    onDismiss: () -> Unit,
    viewModel: PayslipsViewModel = viewModel(factory = PayslipsViewModel.Factory),
) {
    val context = LocalContext.current
    val payslips by viewModel.payslips.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PayslipEntity?>(null) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Payslips", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { adding = true }) { Text("Add payslip") }
            }
            if (payslips.isEmpty()) {
                Text(
                    text = "Keep each month's salary details here, with the payslip itself attached.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            payslips.forEach { payslip ->
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = "${YearMonth.parse(payslip.month).label()} · ${payslip.employer}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("Net pay ${payslip.netPaise.toRupeeString()}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Gross ${payslip.grossPaise.toRupeeString()} · Deductions ${payslip.deductionsPaise.toRupeeString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val parts = listOfNotNull(
                        payslip.pfPaise?.let { "PF ${it.toRupeeString()}" },
                        payslip.taxPaise?.let { "Tax ${it.toRupeeString()}" },
                    )
                    if (parts.isNotEmpty()) {
                        Text(
                            text = parts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row {
                        val name = payslip.fileName
                        val type = payslip.fileType
                        if (name != null && type != null) {
                            TextButton(onClick = { PayslipFiles.open(context, name, type) }) { Text("Open payslip") }
                        }
                        TextButton(onClick = { deleting = payslip }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }

    if (adding) {
        AddPayslipDialog(
            hasSalaryFor = viewModel::hasSalary,
            onRead = viewModel::read,
            onDismiss = { adding = false },
            onSave = { month, employer, gross, deductions, file, addAsIncome, pf, tax ->
                viewModel.add(month, employer, gross, deductions, file, addAsIncome, pf, tax)
                adding = false
            },
        )
    }

    deleting?.let { payslip ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this payslip?") },
            text = {
                Text("${YearMonth.parse(payslip.month).label()} · ${payslip.employer}. Its attached file is deleted too. Income already added stays.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(payslip)
                    deleting = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

// What the form says about the attached file while and after reading it.
private sealed interface ReadState {
    data object Idle : ReadState
    data object Reading : ReadState
    data class Filled(val fields: PayslipFields) : ReadState
    data object Unreadable : ReadState
}

// 7118000 paise -> "71180", 7118050 -> "71180.50": what a person would type in the box.
private fun Long.toTypedRupees(): String =
    if (this % 100 == 0L) (this / 100).toString() else "%d.%02d".format(this / 100, this % 100)

@Composable
private fun AddPayslipDialog(
    hasSalaryFor: (YearMonth) -> Flow<Boolean>,
    onRead: suspend (Uri, String?) -> PayslipRead,
    onDismiss: () -> Unit,
    onSave: (YearMonth, String, Long, Long, Uri?, Boolean, Long?, Long?) -> Unit,
) {
    val thisMonth = YearMonth.now()
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(thisMonth) }
    var employer by remember { mutableStateOf("") }
    var gross by remember { mutableStateOf("") }
    var deductions by remember { mutableStateOf("") }
    var pf by remember { mutableStateOf("") }
    var tax by remember { mutableStateOf("") }
    var file by remember { mutableStateOf<Uri?>(null) }
    var addAsIncome by remember { mutableStateOf(true) }
    var readState by remember { mutableStateOf<ReadState>(ReadState.Idle) }
    // Set while a locked PDF waits for its password; wrongPassword after a failed try.
    var askPassword by remember { mutableStateOf(false) }
    var wrongPassword by remember { mutableStateOf(false) }

    // If the chosen month already has a salary income, start with the box unticked so the
    // same pay is not counted twice. Changing the month checks again.
    val hasSalary by remember(month) { hasSalaryFor(month) }.collectAsState(initial = false)
    LaunchedEffect(hasSalary) { addAsIncome = !hasSalary }

    // Reads the file and puts what it found into the boxes, for the user to check.
    fun readFile(uri: Uri, password: String?) {
        readState = ReadState.Reading
        scope.launch {
            when (val result = onRead(uri, password)) {
                is PayslipRead.Read -> {
                    val f = result.fields
                    f.employer?.let { employer = it }
                    f.month?.let { month = it }
                    f.grossPaise?.let { gross = it.toTypedRupees() }
                    f.deductionsPaise?.let { deductions = it.toTypedRupees() }
                    f.pfPaise?.let { pf = it.toTypedRupees() }
                    f.taxPaise?.let { tax = it.toTypedRupees() }
                    askPassword = false
                    readState = ReadState.Filled(f)
                }
                PayslipRead.NeedsPassword -> { askPassword = true; wrongPassword = false; readState = ReadState.Idle }
                PayslipRead.WrongPassword -> { askPassword = true; wrongPassword = true; readState = ReadState.Idle }
                PayslipRead.Unreadable -> readState = ReadState.Unreadable
            }
        }
    }

    // Android's file picker, limited to PDFs and pictures.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            file = uri
            readFile(uri, null)
        }
    }

    val grossPaise = rupeesToPaise(gross)
    // No deductions is allowed, so an empty box counts as zero.
    val deductionsPaise = if (deductions.isBlank()) 0L else rupeesToPaise(deductions)
    // PF and tax are optional details of the deductions.
    val pfPaise = if (pf.isBlank()) null else rupeesToPaise(pf)
    val taxPaise = if (tax.isBlank()) null else rupeesToPaise(tax)
    val partsOk = (pf.isBlank() || pfPaise != null) && (tax.isBlank() || taxPaise != null) &&
        (deductionsPaise == null || (pfPaise ?: 0) + (taxPaise ?: 0) <= deductionsPaise)
    val valid = employer.isNotBlank() && grossPaise != null && deductionsPaise != null &&
        deductionsPaise < grossPaise && partsOk

    // The month the payslip names may be older than the three quick choices.
    val monthOptions = listOf(thisMonth, thisMonth.minusMonths(1), thisMonth.minusMonths(2))
        .let { if (month in it) it else it + month }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a payslip") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Attaching first is the quick way, so it comes first.
                OutlinedButton(
                    onClick = { pickFile.launch(arrayOf("application/pdf", "image/*")) },
                    enabled = readState != ReadState.Reading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (file == null) "Attach the payslip (PDF or photo)" else "File attached · change")
                }
                when (val state = readState) {
                    ReadState.Idle -> if (file == null) {
                        Text(
                            "The details are read from the file and filled in for you. Nothing leaves your phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ReadState.Reading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Reading the payslip…", style = MaterialTheme.typography.bodySmall)
                    }
                    is ReadState.Filled -> ReadSummary(state.fields)
                    ReadState.Unreadable -> Text(
                        "Could not read the details from this file. It is still attached; please type them below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    monthOptions.forEach { option ->
                        FilterChip(
                            selected = month == option,
                            onClick = { month = option },
                            label = { Text(option.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
                OutlinedTextField(
                    value = employer,
                    onValueChange = { employer = it.take(40) },
                    label = { Text("Employer") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = gross,
                    onValueChange = { gross = it.take(12) },
                    label = { Text("Gross pay") },
                    prefix = { Text("₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = deductions,
                    onValueChange = { deductions = it.take(12) },
                    label = { Text("Total deductions") },
                    prefix = { Text("₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pf,
                        onValueChange = { pf = it.take(12) },
                        label = { Text("of which PF") },
                        prefix = { Text("₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = tax,
                        onValueChange = { tax = it.take(12) },
                        label = { Text("of which tax") },
                        prefix = { Text("₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!partsOk) {
                    Text(
                        "PF and tax are part of the deductions, so together they cannot be more.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (valid) {
                    Text("Net pay: ${(grossPaise!! - deductionsPaise!!).toRupeeString()}")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = addAsIncome, onCheckedChange = { addAsIncome = it })
                    Text("Add the net pay as ${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} income")
                }
                if (hasSalary) {
                    Text(
                        text = "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} already has a salary " +
                            "income, so this is off to avoid counting it twice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && readState != ReadState.Reading,
                onClick = {
                    if (grossPaise != null && deductionsPaise != null) {
                        onSave(month, employer, grossPaise, deductionsPaise, file, addAsIncome, pfPaise, taxPaise)
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (askPassword) {
        PasswordDialog(
            wrong = wrongPassword,
            onDismiss = { askPassword = false },
            onSubmit = { password -> file?.let { readFile(it, password) } },
        )
    }
}

// "Filled in from your payslip. Please check." plus what was found and whether it adds up.
@Composable
private fun ReadSummary(fields: PayslipFields) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Filled in from your payslip. Please check before saving.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (fields.taxParts.isNotEmpty()) {
            Text(
                "Tax: " + fields.taxParts.joinToString(" · ") { (name, paise) -> "$name ${paise.toRupeeString()}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val net = fields.netPaise
        if (net != null && fields.grossPaise != null && fields.deductionsPaise != null && !fields.addsUp) {
            Text(
                "The payslip says net pay is ${net.toRupeeString()}, which does not match gross minus deductions. " +
                    "Check the deductions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        val missing = listOfNotNull(
            "employer".takeIf { fields.employer == null },
            "gross pay".takeIf { fields.grossPaise == null },
            "deductions".takeIf { fields.deductionsPaise == null },
        )
        if (missing.isNotEmpty()) {
            Text(
                "Not found: ${missing.joinToString(", ")}. Please type ${if (missing.size == 1) "it" else "them"}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Many payslip PDFs are locked, often with your PAN or date of birth.
@Composable
private fun PasswordDialog(wrong: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("This payslip is locked") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter its password to read the details. It is often your PAN or date of birth. " +
                        "The password is not saved.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    isError = wrong,
                    supportingText = if (wrong) {
                        { Text("That password did not open it. Try again.") }
                    } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty(), onClick = { onSubmit(password) }) { Text("Open") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Type instead") } },
    )
}
