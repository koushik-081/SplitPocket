package com.example.budgetapptracker

import android.app.Application
import androidx.room.Room
import com.example.budgetapptracker.data.AppDatabase
import com.example.budgetapptracker.data.BudgetRepository
import com.example.budgetapptracker.sms.SmsImporter
import com.example.budgetapptracker.data.UserPrefs

class BudgetApp : Application() {
    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "budget.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
            )
            .build()
    }
    val repository by lazy { BudgetRepository(database.budgetDao()) }
    val smsImporter by lazy { SmsImporter(this, repository) }
    val userPrefs by lazy { UserPrefs(this) }
}