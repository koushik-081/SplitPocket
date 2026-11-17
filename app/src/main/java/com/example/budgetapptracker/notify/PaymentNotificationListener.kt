package com.example.budgetapptracker.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.budgetapptracker.BudgetApp
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Android hands this service every notification once the user allows "notification access".
// Only "you paid" notifications from payment apps are looked at; the rest are dropped unread.
class PaymentNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in paymentAppPackages) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val payment = PaymentNotificationParser.parse(title, text) ?: return

        val app = applicationContext as BudgetApp
        scope.launch {
            val expense = app.repository.addNotifiedExpense(
                amountPaise = payment.amountPaise,
                merchant = payment.merchant,
                occurredAt = sbn.postTime,
                payee = payment.payee,
            ) ?: return@launch
            val envelopes = app.repository.observeEnvelopes(YearMonth.now()).first()
            ExpenseNotifier.show(this@PaymentNotificationListener, expense, envelopes)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}