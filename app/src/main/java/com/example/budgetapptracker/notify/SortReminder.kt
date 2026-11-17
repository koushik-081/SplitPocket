package com.example.budgetapptracker.notify

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.budgetapptracker.BudgetApp
import com.example.budgetapptracker.MainActivity
import com.example.budgetapptracker.R
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "sort_reminder"
private const val NOTIFICATION_ID = 9_000
private val REMIND_AT: LocalTime = LocalTime.of(21, 0)
private const val WINDOW_MILLIS = 30 * 60 * 1_000L

// An evening nudge when expenses are still waiting for an envelope. Off unless switched on in Settings.
object SortReminder {

    // Asks Android to wake the app once, at the next 9 PM. A 30-minute window lets the phone
    // bundle it with other alarms to save battery, and needs no special permission.
    // (A repeating inexact alarm can drift by many hours, so the receiver sets the next one.)
    fun schedule(context: Context) {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        var next = now.with(REMIND_AT)
        if (!next.isAfter(now)) next = next.plusDays(1)
        context.getSystemService(AlarmManager::class.java).setWindow(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            WINDOW_MILLIS,
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID,
            Intent(context, SortReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    @SuppressLint("MissingPermission") // areNotificationsEnabled() is checked first.
    internal fun show(context: Context, count: Int) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Sorting reminder", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "An evening reminder when expenses have no envelope yet" }
        )
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (count == 1) "1 expense needs an envelope" else "$count expenses need an envelope"
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(text)
                .setContentText("Takes a few seconds. Tap to sort them.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}

// Woken by the daily alarm. Counts what is unsorted and only notifies when something is.
class SortReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as BudgetApp
        if (!app.userPrefs.eveningReminder.value) return
        // One alarm at a time: set tomorrow's before doing today's.
        SortReminder.schedule(context)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val count = app.repository.countUnsorted()
                if (count > 0) SortReminder.show(context, count)
            } finally {
                pending.finish()
            }
        }
    }
}
