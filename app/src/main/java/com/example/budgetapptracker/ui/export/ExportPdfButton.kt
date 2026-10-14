package com.example.budgetapptracker.ui.export

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.budgetapptracker.BudgetApp
import com.example.budgetapptracker.export.buildMonthReport
import com.example.budgetapptracker.export.writeMonthPdf
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// "Export PDF" in the Insights top bar: pick a month, pick where to save, and the report is written.
@Composable
fun ExportPdfButton() {
    val context = LocalContext.current
    val repository = (context.applicationContext as BudgetApp).repository
    val scope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(YearMonth.now()) }

    // Android's own "save a file" screen; the user chooses the folder or Google Drive.
    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val report = buildMonthReport(repository, month)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { writeMonthPdf(report, it) }
                        ?: error("Could not open the file")
                }.isSuccess
            }
            Toast.makeText(
                context,
                if (saved) "PDF saved" else "Could not save the PDF",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    TextButton(onClick = { choosing = true }) { Text("Export PDF") }

    if (choosing) {
        val thisMonth = YearMonth.now()
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Export a month as PDF") },
            text = { Text("A summary, your envelopes, where the money went and every expense.") },
            confirmButton = {
                Column {
                    listOf(thisMonth, thisMonth.minusMonths(1)).forEach { option ->
                        TextButton(onClick = {
                            choosing = false
                            month = option
                            createFile.launch("splitpocket-$option.pdf")
                        }) { Text(option.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " ${option.year}") }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } },
        )
    }
}