package com.example.budgetapptracker.ui.backup

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.budgetapptracker.BudgetApp
import com.example.budgetapptracker.backup.BackupCrypto
import com.example.budgetapptracker.backup.RestoreResult
import java.time.LocalDate
import kotlinx.coroutines.launch

private const val MIN_PASSWORD_LENGTH = 4

// A backup file chosen for restoring, with the hint that was saved in it.
private data class RestoreSource(val uri: Uri, val hint: String)

// The Back up and Restore buttons shown in Settings, with the dialogs they need.
@Composable
fun BackupSection() {
    val context = LocalContext.current
    val backupManager = (context.applicationContext as BudgetApp).backupManager
    val scope = rememberCoroutineScope()

    // The file the user picked, held while we ask for the password.
    var backupTarget by remember { mutableStateOf<Uri?>(null) }
    var restoreSource by remember { mutableStateOf<RestoreSource?>(null) }

    fun say(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    // Android's own "save a file" and "open a file" screens. The user chooses where the
    // backup lives (phone storage, Google Drive and so on); the app never sees other files.
    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> backupTarget = uri }
    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                // Read the hint first, so it can be shown beside the password box.
                val hint = backupManager.hintOf(uri)
                if (hint == null) say("That file is not a backup from this app.")
                else restoreSource = RestoreSource(uri, hint)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Backup", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Saves everything to one password-protected file that you choose where to keep.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { createFile.launch("splitpocket-backup-${LocalDate.now()}.bbk") },
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
            settingPassword = true,
            savedHint = "",
            onDismiss = { backupTarget = null },
            onConfirm = { password, hint ->
                backupTarget = null
                scope.launch {
                    try {
                        val count = backupManager.backUp(uri, password, hint)
                        say("Backup saved: $count expenses")
                    } catch (e: Exception) {
                        say("Backup failed. Nothing was changed.")
                    }
                }
            },
        )
    }

    restoreSource?.let { source ->
        PasswordDialog(
            title = "Restore from backup",
            message = "This replaces everything currently in the app with the backup. Enter the backup's password.",
            confirmLabel = "Restore",
            settingPassword = false,
            savedHint = source.hint,
            onDismiss = { restoreSource = null },
            onConfirm = { password, _ ->
                restoreSource = null
                scope.launch {
                    when (val result = backupManager.restore(source.uri, password)) {
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

// Used for both actions. When setting a password it asks twice and offers a hint;
// when restoring it shows the hint that was saved with the file.
@Composable
private fun PasswordDialog(
    title: String,
    message: String,
    confirmLabel: String,
    settingPassword: Boolean,
    savedHint: String,
    onDismiss: () -> Unit,
    onConfirm: (password: String, hint: String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    var hint by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }

    // A hint that contains the password would give it away to anyone holding the file.
    val hintRevealsPassword = settingPassword && password.isNotEmpty() &&
            hint.contains(password, ignoreCase = true)
    val valid = password.length >= MIN_PASSWORD_LENGTH &&
            (!settingPassword || (password == repeat && !hintRevealsPassword))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (savedHint.isNotBlank()) {
                    Text(
                        text = "Hint: $savedHint",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    visible = visible,
                    onToggleVisible = { visible = !visible },
                )
                if (settingPassword) {
                    PasswordField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = "Password again",
                        visible = visible,
                        onToggleVisible = { visible = !visible },
                    )
                    OutlinedTextField(
                        value = hint,
                        onValueChange = { hint = it.take(BackupCrypto.MAX_HINT_LENGTH) },
                        label = { Text("Hint (optional)") },
                        singleLine = true,
                        isError = hintRevealsPassword,
                        supportingText = {
                            Text(
                                if (hintRevealsPassword) "The hint must not contain the password."
                                else "Shown when restoring. Anyone with the file can read it."
                            )
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(password, hint.trim()) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// A password box with an eye button to show or hide what was typed.
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
    )
}