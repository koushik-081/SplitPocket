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

private const val CHANNEL_ID = "captured_expenses"
private const val MAX_BUTTONS = 3

const val EXTRA_TRANSACTION_ID = "transaction_id"
const val EXTRA_MERCHANT = "merchant"
const val EXTRA_CATEGORY_ID = "category_id"
const val EXTRA_NOTIFICATION_ID = "notification_id"

// Shows a notification for an expense that was picked up from a bank SMS.
object ExpenseNotifier {

    @SuppressLint("MissingPermission") // areNotificationsEnabled() is checked first.
    fun show(context: Context, expense: TransactionEntity, envelopes: List<EnvelopeSummary>) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        createChannel(context)

        val notificationId = expense.id.hashCode()
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        // Tapping the notification itself opens the app.
        val openApp = PendingIntent.getActivity(
            context,
            notificationId,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            flags,
        )

        // A remembered payee is already in its envelope; anything else needs a choice.
        val assigned = envelopes.firstOrNull { it.id == expense.categoryId }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${expense.amountPaise.toRupeeString()} at ${expense.merchant}")
            .setContentText(
                if (assigned != null) "Added to ${assigned.name}" else "Which envelope is this from?"
            )
            .setContentIntent(openApp)
            .setAutoCancel(true)

        if (assigned == null) {
            // One button per envelope, most-used first. Android shows at most three.
            envelopes
                .sortedByDescending { it.spentPaise }
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