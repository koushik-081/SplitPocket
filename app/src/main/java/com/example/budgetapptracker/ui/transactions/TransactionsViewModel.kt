package com.example.budgetapptracker.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.budgetapptracker.BudgetApp
import com.example.budgetapptracker.data.BudgetRepository
import com.example.budgetapptracker.data.EnvelopeSummary
import com.example.budgetapptracker.data.TransactionRow
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionsViewModel(private val repository: BudgetRepository) : ViewModel() {

    private val month = YearMonth.now()
    private val selectedCategoryId = MutableStateFlow<String?>(null)

    val filter: StateFlow<String?> = selectedCategoryId.asStateFlow()

    val categories: StateFlow<List<EnvelopeSummary>> =
        repository.observeEnvelopes(month)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<TransactionRow>> =
        selectedCategoryId
            .flatMapLatest { repository.observeTransactions(month, it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectCategory(id: String?) {
        selectedCategoryId.value = id
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteTransaction(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BudgetApp
                TransactionsViewModel(app.repository)
            }
        }
    }
}