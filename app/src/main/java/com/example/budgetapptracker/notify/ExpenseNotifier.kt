package com.example.budgetapptracker.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.budgetapptracker.R
import com.example.budgetapptracker.data.EnvelopeSummary
import com.example.budgetapptracker.data.TransactionEntity
import com.example.budgetapptracker.util.toRupeeString
import com.example.budgetapptracker.MainActivity
import com.example.budgetapptracker.sms.KIND_INVESTMENT
import com.example.budgetapptracker.sms.KIND_TRANSFER
import com.example.budgetapptracker.sms.MerchantCategories

private const val CHANNEL_ID = "captured_expenses"
private const val MAX_BUTTONS = 3

const val EXTRA_TRANSACTION_ID = "transaction_id"
const val EXTRA_MERCHANT = "merchant"
const val EXTRA_CATEGORY_ID = "category_id"
const val EXTRA_NOTIFICATION_ID = "notification_id"
// Set on the "Undo" button of a payment that was filed without asking.
const val EXTRA_UNDO = "undo"

// Shows a notification for an expense that was picked up from a bank SMS.
object ExpenseNotifier {

    @SuppressLint("MissingPermission") // areNotificationsEnabled() is checked first.
    fun show(context: Context, expense: TransactionEntity, envelopes: List<EnvelopeSummary>) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        createChannel(context)

        val notificationId = expense.id.hashCode()
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        // Tapping the notification itself opens the app on this expense, where every
        // envelope can be chosen. The flags reuse the open screen instead of stacking a new one.
        val openExpense = Intent(context, MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            .putExtra(EXTRA_TRANSACTION_ID, expense.id)
        val openApp = PendingIntent.getActivity(context, notificationId, openExpense, flags)

        // A remembered payee or a reliable guess is already filed; anything else needs a choice.
        val assigned = envelopes.firstOrNull { it.id == expense.categoryId }
        val movedNotSpent = when (expense.kind) {
            KIND_INVESTMENT -> "Saved as an investment, not counted as spending"
            KIND_TRANSFER -> "Saved as a transfer to your own account, not spending"
            else -> null
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${expense.amountPaise.toRupeeString()} at ${expense.merchant}")
            .setContentText(
                when {
                    movedNotSpent != null -> movedNotSpent
                    assigned != null -> "Added to ${assigned.name}"
                    else -> "Tap to choose from all your envelopes"
                }
            )
            .setContentIntent(openApp)
            .setAutoCancel(true)

        if (assigned != null || movedNotSpent != null) {
            // Filed without asking: one tap puts it back in the Inbox to sort by hand.
            val undo = Intent(context, NotificationActionReceiver::class.java)
                .putExtra(EXTRA_TRANSACTION_ID, expense.id)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(EXTRA_UNDO, true)
            builder.addAction(
                0,
                "Undo",
                PendingIntent.getBroadcast(context, (expense.id + "undo").hashCode(), undo, flags),
            )
        } else {
            // One button per envelope: the best guess first, then the most-used. Android shows three.
            val guessed = MerchantCategories.guess(expense.merchant, expense.payee, envelopes)?.envelopeId
            envelopes
                .sortedWith(compareByDescending<EnvelopeSummary> { it.id == guessed }.thenByDescending { it.spentPaise })
                .take(MAX_BUTTONS)
                .forEach { envelope ->
                    val choose = Intent(context, NotificationActionReceiver::class.java)
                        .putExtra(EXTRA_TRANSACTION_ID, expense.id)
                        .putExtra(EXTRA_MERCHANT, expense.merchant)
                        .putExtra(EXTRA_CATEGORY_ID, envelope.id)
                        .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    builder.addAction(
                        0,
                        envelope.name,
                        PendingIntent.getBroadcast(
                            context,
                            (expense.id + envelope.id).hashCode(),
                            choose,
                            flags,
                        ),
                    )
                }
        }

        manager.notify(notificationId, builder.build())
    }

    // Android requires every notification to belong to a named channel the user can switch off.
    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Captured expenses",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Expenses picked up from bank messages" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}