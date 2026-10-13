package com.example.budgetapptracker.ui.backup

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.budgetapptracker.BudgetApp
import com.example.budgetapptracker.backup.RestoreResult
import java.time.LocalDate
import kotlinx.coroutines.launch

private const val MIN_PASSWORD_LENGTH = 4

// The Back up and Restore buttons shown in Settings, with the dialogs they need.
@Composable
fun BackupSection() {
    val context = LocalContext.current
    val backupManager = (context.applicationContext as BudgetApp).backupManager
    val scope = rememberCoroutineScope()

    // The file the user picked, held while we ask for the password.
    var backupTarget by remember { mutableStateOf<Uri?>(null) }
    var restoreSource by remember { mutableStateOf<Uri?>(null) }

    fun say(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    // Android's own "save a file" and "open a file" screens. The user chooses where the
    // backup lives (phone storage, Google Drive and so on); the app never sees other files.
    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> backupTarget = uri }
    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> restoreSource = uri }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Backup", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Saves everything to one password-protected file that you choose where to keep.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { createFile.launch("budget-backup-${LocalDate.now()}.bbk") },
                modifier = Modifier.weight(1f),
            ) { Text("Back up") }
            OutlinedButton(
                onClick = { openFile.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f),
            ) { Text("Restore") }
        }
    }

    backupTarget?.let { uri ->
        PasswordDialog(
            title = "Protect this backup",
            message = "Choose a password. You will need it to restore, and it cannot be recovered if forgotten.",
            confirmLabel = "Back up",
            askTwice = true,
            onDismiss = { backupTarget = null },
            onConfirm = { password ->
                backupTarget = null
                scope.launch {
                    try {
                        val count = backupManager.backUp(uri, password)
                        say("Backup saved: $count expenses")
                    } catch (e: Exception) {
                        say("Backup failed. Nothing was changed.")
                    }
                }
            },
        )
    }

    restoreSource?.let { uri ->
        PasswordDialog(
            title = "Restore from backup",
            message = "This replaces everything currently in the app with the backup. Enter the backup's password.",
            confirmLabel = "Restore",
            askTwice = false,
            onDismiss = { restoreSource = null },
            onConfirm = { password ->
                restoreSource = null
                scope.launch {
                    when (val result = backupManager.restore(uri, password)) {
                        is RestoreResult.Restored -> say(
                            "Restored ${result.expenses} expenses, ${result.envelopes} envelopes " +
                                    "and ${result.people} people"
                        )
                        RestoreResult.WrongPassword -> say("Wrong password. Nothing was changed.")
                        RestoreResult.NotABackup -> say("That file is not a backup from this app.")
                    }
                }
            },
        )
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    message: String,
    confirmLabel: String,
    askTwice: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    val valid = password.length >= MIN_PASSWORD_LENGTH && (!askTwice || password == repeat)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (askTwice) {
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = { Text("Password again") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(password) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}