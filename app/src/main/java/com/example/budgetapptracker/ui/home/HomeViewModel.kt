package com.example.budgetapptracker.ui.home

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: BudgetRepository) : ViewModel() {

    private val month = YearMonth.now()
    private val openEnvelopeId = MutableStateFlow<String?>(null)

    val envelopes: StateFlow<List<EnvelopeSummary>> =
        repository.observeEnvelopes(month)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomePaise: StateFlow<Long> =
        repository.observeIncomeTotal(month)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val openEnvelope: StateFlow<EnvelopeSummary?> =
        combine(envelopes, openEnvelopeId) { list, id -> list.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val openEnvelopeTransactions: StateFlow<List<TransactionRow>> =
        openEnvelopeId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList())
                else repository.observeTransactions(month, id, uncategorisedOnly = false)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openEnvelope(id: String) {
        openEnvelopeId.value = id
    }

    fun closeEnvelope() {
        openEnvelopeId.value = null
    }

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

    fun removeTransaction(id: String) {
        viewModelScope.launch { repository.removeTransaction(id) }
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