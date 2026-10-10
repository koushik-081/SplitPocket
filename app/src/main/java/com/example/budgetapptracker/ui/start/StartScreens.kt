package com.example.budgetapptracker.ui.start

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.budgetapptracker.R
import com.example.budgetapptracker.ui.theme.Mango
import com.example.budgetapptracker.ui.theme.Pink
import com.example.budgetapptracker.ui.theme.Violet

private const val MAX_NAME_LENGTH = 24

// Shown once, the first time the app is opened.
@Composable
fun WelcomeScreen(lockAvailable: Boolean, onDone: (name: String, lockEnabled: Boolean) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var lockEnabled by rememberSaveable { mutableStateOf(lockAvailable) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BrandRing(modifier = Modifier.size(72.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Give every rupee a job", style = MaterialTheme.typography.displaySmall)
                Text(
                    text = "Split your income into envelopes, spend from each one, and always know what is left.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Feature(
                icon = Icons.Rounded.AccountBalanceWallet,
                tint = Mango,
                title = "Envelopes for your money",
                text = "Set a monthly amount for food, fuel, shopping and anything else.",
            )
            Feature(
                icon = Icons.Rounded.Sms,
                tint = Pink,
                title = "Expenses from bank SMS",
                text = "Scan your messages and the app finds your spending. Nothing leaves your phone.",
            )
            Feature(
                icon = Icons.Rounded.PieChart,
                tint = Violet,
                title = "See where it went",
                text = "Charts show your spending by envelope and by day.",
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = { Text("What should we call you?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
            )

            if (lockAvailable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lock the app", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Ask for fingerprint, face or screen lock when opening",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(checked = lockEnabled, onCheckedChange = { lockEnabled = it })
                }
            }

            Button(
                onClick = { onDone(name, lockEnabled && lockAvailable) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Get started")
            }
        }
    }
}

// Shown when the app lock is on and the app has not been unlocked yet.
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    // Ask straight away the first time this screen appears; after that, wait for the button.
    var askedAutomatically by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!askedAutomatically) {
            askedAutomatically = true
            onUnlock()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandRing(modifier = Modifier.size(96.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Locked to keep your finances private",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onUnlock, modifier = Modifier.height(52.dp)) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Unlock")
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, tint: Color, title: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// The three-colour ring from the app icon.
@Composable
private fun BrandRing(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.2f
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val style = Stroke(width = stroke, cap = StrokeCap.Round)
        drawArc(Mango, startAngle = -82f, sweepAngle = 184f, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
        drawArc(Pink, startAngle = 118f, sweepAngle = 84f, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
        drawArc(Violet, startAngle = 218f, sweepAngle = 44f, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
    }
}
