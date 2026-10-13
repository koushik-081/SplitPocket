package com.example.budgetapptracker.ui.transactions

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
import com.example.budgetapptracker.sms.SmsImporter
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionsViewModel(
    private val repository: BudgetRepository,
    private val smsImporter: SmsImporter,
) : ViewModel() {

    private val month = YearMonth.now()
    private val selectedFilter = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")
    private val _message = MutableStateFlow<String?>(null)

    val filter: StateFlow<String?> = selectedFilter.asStateFlow()
    val searchQuery: StateFlow<String> = query.asStateFlow()
    val message: StateFlow<String?> = _message.asStateFlow()

    val categories: StateFlow<List<EnvelopeSummary>> =
        repository.observeEnvelopes(month)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Everyone on the People tab, to choose who shared an expense.
    val people: StateFlow<List<PersonBalance>> =
        repository.observePeople()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<TransactionRow>> =
        selectedFilter
            .flatMapLatest { selected ->
                repository.observeTransactions(
                    month = month,
                    categoryId = selected.takeUnless { it == INBOX || it == HIDDEN },
                    uncategorisedOnly = selected == INBOX,
                    hidden = selected == HIDDEN,
                )
            }
            // Narrow the list to places whose name contains what was typed.
            .combine(query) { rows, text ->
                val wanted = text.trim()
                if (wanted.isEmpty()) rows
                else rows.filter { it.merchant.contains(wanted, ignoreCase = true) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun search(text: String) {
        query.value = text
    }

    fun selectFilter(value: String?) {
        selectedFilter.value = value
    }

    fun assign(transaction: TransactionRow, categoryId: String, rememberPayee: Boolean) {
        viewModelScope.launch {
            repository.assignCategory(transaction.id, transaction.merchant, categoryId, rememberPayee)
        }
    }

    // shares: what each chosen person owes you, in paise.
    fun split(transaction: TransactionRow, shares: Map<String, Long>) {
        viewModelScope.launch {
            repository.splitExpense(
                transactionId = transaction.id,
                merchant = transaction.merchant,
                totalPaise = transaction.amountPaise,
                shares = shares,
            )
            _message.value = "Split with ${shares.size} " +
                if (shares.size == 1) "person" else "people"
        }
    }

    fun undoSplit(transaction: TransactionRow) {
        viewModelScope.launch {
            repository.undoSplit(transaction.id)
            _message.value = "Split undone"
        }
    }

    fun restore(id: String) {
        viewModelScope.launch { repository.restoreTransaction(id) }
    }

    fun remove(id: String) {
        viewModelScope.launch { repository.removeTransaction(id) }
    }

    fun scanSms() {
        viewModelScope.launch {
            val monthStart = month.atDay(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val added = smsImporter.importSince(monthStart).size
            _message.value =
                if (added == 0) "No new bank SMS found this month"
                else "$added new expenses imported"
        }
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        const val INBOX = "__inbox__"
        const val HIDDEN = "__hidden__"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BudgetApp
                TransactionsViewModel(app.repository, app.smsImporter)
            }
        }
    }
}