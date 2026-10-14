package com.example.budgetapptracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.budgetapptracker.ui.home.HomeAction

private fun HomeAction.icon(): ImageVector = when (this) {
    HomeAction.ScanAndPay -> Icons.Rounded.QrCodeScanner
    HomeAction.PayUpiId -> Icons.Rounded.AlternateEmail
    HomeAction.AddExpense -> Icons.Rounded.ShoppingBag
    HomeAction.AddIncome -> Icons.Rounded.Payments
    HomeAction.NewEnvelope -> Icons.Rounded.AccountBalanceWallet
    HomeAction.Payslips -> Icons.Rounded.Description
}

private fun HomeAction.hint(): String = when (this) {
    HomeAction.ScanAndPay -> "Any UPI QR code"
    HomeAction.PayUpiId -> "Type someone's UPI ID"
    HomeAction.AddExpense -> "Cash or anything else"
    HomeAction.AddIncome -> "Salary, refunds, gifts"
    HomeAction.NewEnvelope -> "Set a monthly amount"
    HomeAction.Payslips -> "Save and attach slips"
}

// What the + button opens: a sheet over a dimmed screen, with one big tile per action.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(onDismiss: () -> Unit, onPick: (HomeAction) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = Color.Black.copy(alpha = 0.6f),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "What would you like to do?",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            // Two tiles a row; paying comes first and stands out.
            HomeAction.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { action ->
                        ActionTile(
                            action = action,
                            highlighted = action == HomeAction.ScanAndPay || action == HomeAction.PayUpiId,
                            onClick = { onPick(action) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionTile(
    action: HomeAction,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (highlighted) colors.primaryContainer else colors.surfaceContainerHighest,
        modifier = modifier.heightIn(min = 104.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = action.icon(),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = action.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = action.hint(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}