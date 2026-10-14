package com.example.budgetapptracker.ui.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.budgetapptracker.BudgetApp
import com.example.budgetapptracker.data.BudgetRepository
import com.example.budgetapptracker.data.MonthRecap
import com.example.budgetapptracker.data.UserPrefs
import com.example.budgetapptracker.data.buildRecap
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

// How many days into a new month the recap of the last one stays on Home.
private const val SHOW_FOR_DAYS = 7

class RecapViewModel(repository: BudgetRepository, private val userPrefs: UserPrefs) : ViewModel() {

    private val lastMonth = YearMonth.now().minusMonths(1)

    // Last month's recap, or null when it should not be shown: after the first week, once
    // dismissed, or when last month has no spending recorded.
    val recap: StateFlow<MonthRecap?> =
        combine(
            repository.observeIncomeTotal(lastMonth),
            repository.observeEnvelopes(lastMonth),
            repository.observeTransactions(lastMonth, categoryId = null, uncategorisedOnly = false),
            repository.observeTransactions(lastMonth.minusMonths(1), categoryId = null, uncategorisedOnly = false),
            userPrefs.recapDismissedMonth,
        ) { income, envelopes, expenses, before, dismissed ->
            val showNow = LocalDate.now().dayOfMonth <= SHOW_FOR_DAYS
            if (!showNow || dismissed == lastMonth.toString() || expenses.isEmpty()) null
            else buildRecap(lastMonth, income, envelopes, expenses, before.sumOf { it.amountPaise })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismiss() {
        userPrefs.dismissRecap(lastMonth.toString())
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BudgetApp
                RecapViewModel(app.repository, app.userPrefs)
            }
        }
    }
}
