package com.example.budgetapptracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.budgetapptracker.BudgetApp
import com.example.budgetapptracker.data.BudgetRepository
import com.example.budgetapptracker.data.EnvelopeSummary
import com.example.budgetapptracker.data.ThemeMode
import com.example.budgetapptracker.data.TransactionRow
import com.example.budgetapptracker.data.UserPrefs
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow

data class DaySpend(val label: String, val amountPaise: Long)
// A payment handed to a UPI app that we have not yet heard the result of.
data class PendingPayment(
    val envelopeId: String,
    val envelopeName: String,
    val amountPaise: Long,
    val payee: String,
)

class HomeViewModel(
    private val repository: BudgetRepository,
    private val userPrefs: UserPrefs,
) : ViewModel() {

    private val month = YearMonth.now()
    private val openEnvelopeId = MutableStateFlow<String?>(null)

    val userName: StateFlow<String?> = userPrefs.name
    val lockEnabled: StateFlow<Boolean> = userPrefs.lockEnabled
    val themeMode: StateFlow<ThemeMode> = userPrefs.themeMode

    private val _pendingPayment = MutableStateFlow<PendingPayment?>(null)
    val pendingPayment: StateFlow<PendingPayment?> = _pendingPayment.asStateFlow()
    val envelopes: StateFlow<List<EnvelopeSummary>> =
        repository.observeEnvelopes(month)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomePaise: StateFlow<Long> =
        repository.observeIncomeTotal(month)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val lastSevenDays: StateFlow<List<DaySpend>> = run {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val from = days.first().atStartOfDay(zone).toInstant().toEpochMilli()

        repository.observeSince(from)
            .map { rows ->
                val byDay = rows.groupBy {
                    Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
                }
                days.map { day ->
                    DaySpend(
                        label = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        amountPaise = byDay[day].orEmpty().sumOf { it.amountPaise },
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

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

    fun startPayment(payment: PendingPayment) {
        _pendingPayment.value = payment
    }

    fun finishPayment(paid: Boolean) {
        val payment = _pendingPayment.value ?: return
        _pendingPayment.value = null
        if (paid) {
            viewModelScope.launch {
                repository.addExpense(
                    categoryId = payment.envelopeId,
                    amountPaise = payment.amountPaise,
                    merchant = payment.payee,
                    source = "UPI",
                )
            }
        }
    }
    fun saveProfile(name: String, lockEnabled: Boolean) {
        userPrefs.save(name, lockEnabled)
    }

    fun setThemeMode(mode: ThemeMode) {
        userPrefs.setThemeMode(mode)
    }

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
                HomeViewModel(app.repository, app.userPrefs)
            }
        }
    }
}
