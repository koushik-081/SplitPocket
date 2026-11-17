package com.example.budgetapptracker.ui.sort

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.budgetapptracker.BudgetApp
import com.example.budgetapptracker.notify.SortReminder

// Settings for sorting: auto-filing, your own name for spotting transfers, and the evening reminder.
// Each change is saved straight away, like the theme switch.
@Composable
fun SortSettingsSection() {
    val context = LocalContext.current
    val prefs = (context.applicationContext as BudgetApp).userPrefs
    val autoFile by prefs.autoFile.collectAsStateWithLifecycle()
    val reminder by prefs.eveningReminder.collectAsStateWithLifecycle()
    var ownNames by rememberSaveable { mutableStateOf(prefs.ownNames.value) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sorting", style = MaterialTheme.typography.titleSmall)
        SettingSwitch(
            title = "File obvious payments by itself",
            detail = "Swiggy to Food, petrol pumps to Fuel, mutual funds as investments. " +
                "You can undo from the notification.",
            checked = autoFile,
            onChange = { prefs.setAutoFile(it) },
        )
        SettingSwitch(
            title = "Evening reminder",
            detail = "At about 9 PM, only if something still has no envelope",
            checked = reminder,
            onChange = { on ->
                prefs.setEveningReminder(on)
                if (on) SortReminder.schedule(context) else SortReminder.cancel(context)
            },
        )
        OutlinedTextField(
            value = ownNames,
            onValueChange = {
                ownNames = it.take(120)
                prefs.setOwnNames(ownNames)
            },
            label = { Text("Your name as banks show it") },
            supportingText = {
                Text("Payments to this name count as transfers to your own account. Separate several with commas.")
            },
            singleLine = true,
        )
    }
}

@Composable
private fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
