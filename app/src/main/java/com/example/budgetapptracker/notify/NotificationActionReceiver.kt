package com.example.budgetapptracker.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.budgetapptracker.BudgetApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Runs when an envelope button on a notification is tapped. The app does not need to be open.
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID) ?: return
        val merchant = intent.getStringExtra(EXTRA_MERCHANT) ?: return
        val categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        val app = context.applicationContext as BudgetApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                app.repository.assignCategory(
                    transactionId = transactionId,
                    merchant = merchant,
                    categoryId = categoryId,
                    rememberPayee = false,
                )
                NotificationManagerCompat.from(context).cancel(notificationId)
            } finally {
                pending.finish()
            }
        }
    }
}