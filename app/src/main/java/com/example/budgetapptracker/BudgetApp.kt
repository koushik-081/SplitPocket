package com.example.budgetapptracker

import android.app.Application
import androidx.room.Room
import com.example.budgetapptracker.data.AppDatabase
import com.example.budgetapptracker.data.BudgetRepository

class BudgetApp : Application() {
    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "budget.db").build()
    }
    val repository by lazy { BudgetRepository(database.budgetDao()) }
}
