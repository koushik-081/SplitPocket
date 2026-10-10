package com.example.budgetapptracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.budgetapptracker.ui.AppRoot
import com.example.budgetapptracker.ui.theme.BudgetAppTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BudgetAppTrackerTheme {
                AppRoot()
            }
        }
    }
}