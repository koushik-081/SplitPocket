package com.example.budgetapptracker

import android.app.Application
import androidx.room.Room
import com.example.budgetapptracker.data.AppDatabase
import com.example.budgetapptracker.data.AutoFileSettings
import com.example.budgetapptracker.notify.SortReminder
import com.example.budgetapptracker.data.BudgetRepository
import com.example.budgetapptracker.sms.SmsImporter
import com.example.budgetapptracker.data.UserPrefs
import com.example.budgetapptracker.backup.BackupManager
import com.example.budgetapptracker.widget.BudgetWidget
import androidx.glance.appwidget.updateAll
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class BudgetApp : Application() {
    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "budget.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
            )
            .build()
    }
    val repository by lazy {
        BudgetRepository(database.budgetDao()) {
            AutoFileSettings(enabled = userPrefs.autoFile.value, ownNames = userPrefs.ownNameList)
        }
    }
    val smsImporter by lazy { SmsImporter(this, repository) }
    val userPrefs by lazy { UserPrefs(this) }
    val backupManager by lazy { BackupManager(this, database) }
    // Work that lives as long as the app's process, such as keeping the widget current.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Alarms are lost when the phone restarts, so set the evening reminder again on every start.
        if (userPrefs.eveningReminder.value) SortReminder.schedule(this)
        // Redraw the home-screen widget whenever spending or the lock setting changes.
        // drop(1) skips the first value, which is just the current state.
        appScope.launch {
            combine(repository.observeEnvelopes(YearMonth.now()), userPrefs.lockEnabled) { _, _ -> }
                .drop(1)
                .collect { BudgetWidget().updateAll(this@BudgetApp) }
        }
    }
}