package com.example.budgetapptracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.budgetapptracker.BudgetApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SAVE_DELAY_MILLIS = 3_000L
private const val LOOK_BACK_MILLIS = 10 * 60 * 1_000L

// Android calls this whenever an SMS arrives, even if the app is closed.
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val sender = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            .firstOrNull()
            ?.originatingAddress
            ?: return
        if (!isBankSender(sender)) return

        val app = context.applicationContext as BudgetApp
        // goAsync keeps the receiver alive for the few seconds the work takes.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // The phone's messaging app saves the SMS a moment after this broadcast,
                // so wait, then run the same import the Scan SMS button uses.
                delay(SAVE_DELAY_MILLIS)
                app.smsImporter.importSince(System.currentTimeMillis() - LOOK_BACK_MILLIS)
            } catch (e: SecurityException) {
                // SMS permission was not granted or has been removed; nothing to do.
            } finally {
                pending.finish()
            }
        }
    }
}