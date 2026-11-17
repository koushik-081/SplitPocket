package com.example.budgetapptracker.ui.sort

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.budgetapptracker.BudgetApp
import com.example.budgetapptracker.data.BudgetRepository
import com.example.budgetapptracker.data.EnvelopeSummary
import com.example.budgetapptracker.data.PersonBalance
import com.example.budgetapptracker.data.TransactionRow
import com.example.budgetapptracker.data.UserPrefs
import com.example.budgetapptracker.sms.Guess
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Everything the "Sort your spending" sheet and the Home banner need.
class SortViewModel(
    private val repository: BudgetRepository,
    private val userPrefs: UserPrefs,
) : ViewModel() {

    // Expenses with no envelope since the start of last month, newest first.
    // null while loading, so the sheet does not pop up before the list is known.
    val unsorted: StateFlow<List<TransactionRow>?> =
        repository.observeUnsorted()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val envelopes: StateFlow<List<EnvelopeSummary>> =
        repository.observeEnvelopes(YearMonth.now())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val people: StateFlow<List<PersonBalance>> =
        repository.observePeople()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // True once the sheet has opened by itself today.
    val promptedToday: StateFlow<Boolean> =
        userPrefs.sortPromptedOn
            .map { it == today() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, userPrefs.sortPromptedOn.value == today())

    fun markPrompted() = userPrefs.markSortPrompted(today())

    suspend fun guessFor(transaction: TransactionRow): Guess? =
        repository.guessFor(transaction.merchant, transaction.payee)

    fun assign(transaction: TransactionRow, envelopeId: String, rememberPayee: Boolean) {
        viewModelScope.launch {
            repository.assignCategory(transaction.id, transaction.merchant, envelopeId, rememberPayee)
        }
    }

    // A refund, a duplicate, or anything else that is not spending.
    fun notExpense(transaction: TransactionRow) {
        viewModelScope.launch { repository.removeTransaction(transaction.id) }
    }

    // TRANSFER or INVESTMENT: money moved, not spent.
    fun markKind(transaction: TransactionRow, kind: String) {
        viewModelScope.launch { repository.markKind(transaction.id, kind) }
    }

    fun lend(transaction: TransactionRow, personId: String) {
        viewModelScope.launch {
            repository.lend(transaction.id, personId, transaction.amountPaise, transaction.merchant)
        }
    }

    fun paidBack(transaction: TransactionRow, personId: String) {
        viewModelScope.launch { repository.paidBack(personId, transaction.amountPaise) }
    }

    // shares: what each chosen person owes you, in paise. Your own share stays to be sorted.
    fun split(transaction: TransactionRow, shares: Map<String, Long>) {
        viewModelScope.launch {
            repository.splitExpense(
                transactionId = transaction.id,
                merchant = transaction.merchant,
                totalPaise = transaction.amountPaise,
                shares = shares,
            )
        }
    }

    private fun today(): String = LocalDate.now().toString()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BudgetApp
                SortViewModel(app.repository, app.userPrefs)
            }
        }
    }
}
