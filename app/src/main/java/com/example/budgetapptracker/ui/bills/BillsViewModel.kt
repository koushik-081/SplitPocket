package com.example.budgetapptracker.ui.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.budgetapptracker.BudgetApp
import com.example.budgetapptracker.data.BillStatus
import com.example.budgetapptracker.data.BillSuggestion
import com.example.budgetapptracker.data.BudgetRepository
import com.example.budgetapptracker.data.billStatus
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.budgetapptracker.data.Payment

class BillsViewModel(private val repository: BudgetRepository) : ViewModel() {

    // Each bill with its status, unpaid ones first and the soonest at the top.
    val bills: StateFlow<List<BillStatus>> =
        combine(
            repository.observeBills(),
            repository.observeTransactions(YearMonth.now(), categoryId = null, uncategorisedOnly = false),
        ) { bills, expenses ->
            val today = LocalDate.now()
            val payments = expenses.map { Payment(it.merchant, it.paidPaise ?: it.amountPaise) }
            bills.map { billStatus(it, today, payments) }
                .sortedWith(compareBy({ it.paid }, { it.dueDate }))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Payments that turned up once a month for at least two months and are not tracked yet.
    val suggestions: StateFlow<List<BillSuggestion>> =
        combine(repository.observeBillSuggestions(), repository.observeBills()) { found, bills ->
            found.filter { suggestion -> bills.none { it.name.equals(suggestion.name, ignoreCase = true) } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String, amountPaise: Long, dayOfMonth: Int) {
        viewModelScope.launch { repository.addBill(name, amountPaise, dayOfMonth) }
    }

    fun setPaid(status: BillStatus, paid: Boolean) {
        viewModelScope.launch {
            repository.setBillPaid(status.bill.id, if (paid) YearMonth.now().toString() else null)
        }
    }

    fun delete(status: BillStatus) {
        viewModelScope.launch { repository.deleteBill(status.bill.id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { BillsViewModel((this[APPLICATION_KEY] as BudgetApp).repository) }
        }
    }
}