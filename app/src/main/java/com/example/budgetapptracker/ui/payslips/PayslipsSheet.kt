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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.budgetapptracker.data.PayslipEntity
import com.example.budgetapptracker.payslip.PayslipFiles
import com.example.budgetapptracker.util.rupeesToPaise
import com.example.budgetapptracker.util.toRupeeString
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.Flow

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
            onDismiss = { adding = false },
            onSave = { month, employer, gross, deductions, file, addAsIncome ->
                viewModel.add(month, employer, gross, deductions, file, addAsIncome)
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

@Composable
private fun AddPayslipDialog(
    hasSalaryFor: (YearMonth) -> Flow<Boolean>,
    onDismiss: () -> Unit,
    onSave: (YearMonth, String, Long, Long, Uri?, Boolean) -> Unit,
) {
    val thisMonth = YearMonth.now()
    var month by remember { mutableStateOf(thisMonth) }
    var employer by remember { mutableStateOf("") }
    var gross by remember { mutableStateOf("") }
    var deductions by remember { mutableStateOf("") }
    var file by remember { mutableStateOf<Uri?>(null) }
    var addAsIncome by remember { mutableStateOf(true) }

    // If the chosen month already has a salary income, start with the box unticked so the
    // same pay is not counted twice. Changing the month checks again.
    val hasSalary by remember(month) { hasSalaryFor(month) }.collectAsState(initial = false)
    LaunchedEffect(hasSalary) { addAsIncome = !hasSalary }

    // Android's file picker, limited to PDFs and pictures.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) file = uri
    }

    val grossPaise = rupeesToPaise(gross)
    // No deductions is allowed, so an empty box counts as zero.
    val deductionsPaise = if (deductions.isBlank()) 0L else rupeesToPaise(deductions)
    val valid = employer.isNotBlank() && grossPaise != null && deductionsPaise != null &&
        deductionsPaise < grossPaise

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a payslip") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(thisMonth, thisMonth.minusMonths(1), thisMonth.minusMonths(2)).forEach { option ->
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
                    label = { Text("Deductions (PF, tax and so on)") },
                    prefix = { Text("₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                if (valid) {
                    Text("Net pay: ${(grossPaise!! - deductionsPaise!!).toRupeeString()}")
                }
                TextButton(onClick = { pickFile.launch(arrayOf("application/pdf", "image/*")) }) {
                    Text(if (file == null) "Attach the payslip (PDF or photo)" else "File attached · change")
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
                enabled = valid,
                onClick = {
                    if (grossPaise != null && deductionsPaise != null) {
                        onSave(month, employer, grossPaise, deductionsPaise, file, addAsIncome)
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
