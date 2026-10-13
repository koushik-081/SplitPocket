package com.example.budgetapptracker.ui.capture

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

// The Settings row for picking up payments from GPay, PhonePe, Paytm and similar apps.
@Composable
fun PaymentCaptureSection() {
    val context = LocalContext.current
    fun isAllowed() =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    var allowed by remember { mutableStateOf(isAllowed()) }
    // The switch lives in the phone's Settings, so look again whenever the user comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = isAllowed() }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Capture from payment apps", style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (allowed) "On. Payments made in GPay, PhonePe, Paytm and similar apps are added."
                else "Catches small payments your bank sends no SMS for. Nothing leaves your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) { Text(if (allowed) "Manage" else "Turn on") }
    }
}