package com.example.budgetapptracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.budgetapptracker.BudgetApp
import com.example.budgetapptracker.data.BudgetRepository
import com.example.budgetapptracker.data.EnvelopeSummary
import java.time.YearMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: BudgetRepository) : ViewModel() {

    private val month = YearMonth.now()

    val envelopes: StateFlow<List<EnvelopeSummary>> =
        repository.observeEnvelopes(month)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomePaise: StateFlow<Long> =
        repository.observeIncomeTotal(month)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun addCategory(name: String, budgetPaise: Long) {
        viewModelScope.launch { repository.addCategory(name, budgetPaise) }
    }

    fun updateBudget(categoryId: String, budgetPaise: Long) {
        viewModelScope.launch { repository.updateBudget(categoryId, budgetPaise) }
    }

    fun addIncome(source: String, amountPaise: Long) {
        viewModelScope.launch { repository.addIncome(source, amountPaise) }
    }

    fun addExpense(categoryId: String, amountPaise: Long, merchant: String) {
        viewModelScope.launch { repository.addExpense(categoryId, amountPaise, merchant) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BudgetApp
                HomeViewModel(app.repository)
            }
        }
    }
}