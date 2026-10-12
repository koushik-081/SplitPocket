package com.example.budgetapptracker.ui.pay

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.budgetapptracker.data.EnvelopeSummary
import com.example.budgetapptracker.pay.UpiApp
import com.example.budgetapptracker.pay.UpiLink
import com.example.budgetapptracker.pay.UpiRequest
import com.example.budgetapptracker.pay.installedUpiApps
import com.example.budgetapptracker.ui.home.HomeViewModel
import com.example.budgetapptracker.ui.home.PendingPayment
import com.example.budgetapptracker.util.rupeesToPaise
import com.example.budgetapptracker.util.toRupeeString
import java.math.BigDecimal
import androidx.compose.material3.FilterChip
import androidx.compose.material3.rememberModalBottomSheetState
import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

enum class PayStart { ScanQr, EnterUpiId }

// Everything about paying through the app lives here: scanning, the envelope check,
// handing off to a UPI app, and asking afterwards whether the payment went through.
@Composable
fun PayFlow(
    start: PayStart?,
    onStartHandled: () -> Unit,
    envelopes: List<EnvelopeSummary>,
    viewModel: HomeViewModel,
) {
    val context = LocalContext.current
    val pending by viewModel.pendingPayment.collectAsStateWithLifecycle()
    var showUpiIdDialog by rememberSaveable { mutableStateOf(false) }
    var request by remember { mutableStateOf<UpiRequest?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(start) {
        when (start) {
            PayStart.ScanQr -> scanQr(
                context = context,
                onText = { text ->
                    val scanned = UpiLink.parse(text)
                    if (scanned != null) request = scanned
                    else problem = "That QR code is not a UPI payment code."
                },
                onProblem = { problem = it },
            )
            PayStart.EnterUpiId -> showUpiIdDialog = true
            null -> return@LaunchedEffect
        }
        onStartHandled()
    }

    if (showUpiIdDialog) {
        UpiIdDialog(
            onDismiss = { showUpiIdDialog = false },
            onContinue = {
                showUpiIdDialog = false
                request = it
            },
        )
    }

    request?.let { current ->
        PaySheet(
            request = current,
            envelopes = envelopes,
            onDismiss = { request = null },
            onPay = { envelope, amountPaise, app ->
                val amountRupees = BigDecimal.valueOf(amountPaise, 2).toPlainString()
                val view = Intent(Intent.ACTION_VIEW, UpiLink.build(current, amountRupees))
                request = null
                // Remember what was being paid, so we can ask about it when the user comes back.
                viewModel.startPayment(
                    PendingPayment(envelope.id, envelope.name, amountPaise, current.payeeName)
                )
                if (app != null) {
                    // Open the chosen payment app directly, with the payee and amount filled in.
                    context.startActivity(view.setPackage(app.packageName))
                } else {
                    // No app list available: fall back to Android's own chooser.
                    context.startActivity(Intent.createChooser(view, "Pay with"))
                }
            },
        )
    }

    pending?.let { payment ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Did the payment go through?") },
            text = {
                Text(
                    "${payment.amountPaise.toRupeeString()} to ${payment.payee}, " +
                        "from ${payment.envelopeName}."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.finishPayment(paid = true) }) { Text("Yes, record it") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.finishPayment(paid = false) }) { Text("No") }
            },
        )
    }

    problem?.let { message ->
        AlertDialog(
            onDismissRequest = { problem = null },
            title = { Text("Can't continue") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { problem = null }) { Text("OK") } },
        )
    }
}

// Opens Google's QR scanner. It is the same scanner Google's own apps use, so it reads
// codes quickly, including ones on screens or with a logo in the middle.
private fun scanQr(context: Context, onText: (String) -> Unit, onProblem: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode ->
            val text = barcode.rawValue
            if (text != null) onText(text) else onProblem("That QR code could not be read.")
        }
        .addOnFailureListener {
            onProblem(
                "The scanner could not start. Connect to the internet once so Google Play " +
                        "services can set it up, then try again."
            )
        }
}

@Composable
private fun UpiIdDialog(onDismiss: () -> Unit, onContinue: (UpiRequest) -> Unit) {
    var id by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    val parsed = UpiLink.fromUpiId(id, name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay by UPI ID") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it.trim() },
                    label = { Text("UPI ID (e.g. name@bank)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let(onContinue) }) {
                Text("Continue")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// The envelope check: pick an envelope, see what it would have left, then choose a payment app.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaySheet(
    request: UpiRequest,
    envelopes: List<EnvelopeSummary>,
    onDismiss: () -> Unit,
    onPay: (EnvelopeSummary, Long, UpiApp?) -> Unit,
) {
    val context = LocalContext.current
    val apps = remember { installedUpiApps(context) }
    var amount by rememberSaveable { mutableStateOf(request.fixedAmountRupees.orEmpty()) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val amountPaise = rupeesToPaise(amount)
    val selected = envelopes.firstOrNull { it.id == selectedId }
    val ready = selected != null && amountPaise != null

    // Open at full height, so the payment apps are never hidden below the edge of the screen.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text("Pay ${request.payeeName}", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = request.payeeId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount (₹)") },
                singleLine = true,
                // A QR with a fixed amount cannot be changed without breaking it.
                enabled = request.fixedAmountRupees == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Pay from", style = MaterialTheme.typography.titleSmall)
            if (envelopes.isEmpty()) {
                Text(
                    text = "Create an envelope first, then come back to pay.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Envelopes sit side by side, so a long list does not push the payment apps away.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(envelopes, key = { it.id }) { envelope ->
                        FilterChip(
                            selected = selectedId == envelope.id,
                            onClick = { selectedId = envelope.id },
                            label = { Text(envelope.name) },
                        )
                    }
                }
            }

            val after = if (selected != null && amountPaise != null) {
                selected.remainingPaise - amountPaise
            } else {
                null
            }
            Text(
                text = when {
                    selected == null -> "Pick the envelope this payment comes from."
                    after == null -> "${selected.name} has ${selected.remainingPaise.toRupeeString()} left."
                    after >= 0 -> "${selected.name} has ${selected.remainingPaise.toRupeeString()} left. " +
                        "After this payment: ${after.toRupeeString()}."
                    else -> "${selected.name} has ${selected.remainingPaise.toRupeeString()} left. " +
                        "This payment goes over by ${(-after).toRupeeString()}."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (after != null && after < 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (apps.isEmpty()) {
                Button(
                    onClick = { if (selected != null && amountPaise != null) onPay(selected, amountPaise, null) },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (amountPaise != null) "Pay ${amountPaise.toRupeeString()}" else "Pay")
                }
            } else {
                Text(
                    text = if (amountPaise != null) "Pay ${amountPaise.toRupeeString()} with"
                    else "Pay with",
                    style = MaterialTheme.typography.titleSmall,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        Column(
                            modifier = Modifier
                                .width(76.dp)
                                .clip(MaterialTheme.shapes.small)
                                .alpha(if (ready) 1f else 0.4f)
                                .clickable(enabled = ready) {
                                    if (selected != null && amountPaise != null) {
                                        onPay(selected, amountPaise, app)
                                    }
                                }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Image(
                                bitmap = remember(app.packageName) {
                                    app.icon.toBitmap(144, 144).asImageBitmap()
                                },
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (!ready) {
                    Text(
                        text = "Enter an amount and pick an envelope to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
