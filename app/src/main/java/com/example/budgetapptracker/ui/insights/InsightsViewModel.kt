package com.example.budgetapptracker.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.budgetapptracker.BudgetApp
import com.example.budgetapptracker.data.BudgetRepository
import com.example.budgetapptracker.data.EnvelopeSummary
import com.example.budgetapptracker.data.TransactionRow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MerchantTotal(val name: String, val amountPaise: Long, val count: Int)

data class EnvelopePace(val name: String, val usedFraction: Float, val overBudget: Boolean)

data class InsightsState(
    val spentPaise: Long = 0,
    val lastMonthSamePeriodPaise: Long = 0,
    val lastMonthName: String = "",
    // One total per day of the month; index 0 is the 1st.
    val dailyPaise: List<Long> = emptyList(),
    val today: Int = 1,
    val daysInMonth: Int = 30,
    val noSpendDays: Int = 0,
    val biggest: TransactionRow? = null,
    val count: Int = 0,
    val topMerchants: List<MerchantTotal> = emptyList(),
    val envelopes: List<EnvelopePace> = emptyList(),
) {
    val dailyAveragePaise: Long get() = spentPaise / today
    val monthFraction: Float get() = today.toFloat() / daysInMonth
}

class InsightsViewModel(repository: BudgetRepository) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val month = YearMonth.from(today)
    private val lastMonth = month.minusMonths(1)

    val state: StateFlow<InsightsState> =
        combine(
            repository.observeTransactions(month, null, uncategorisedOnly = false),
            repository.observeTransactions(lastMonth, null, uncategorisedOnly = false),
            repository.observeEnvelopes(month),
        ) { current, previous, envelopes -> buildState(current, previous, envelopes) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState())

    private fun dayOf(row: TransactionRow): Int =
        Instant.ofEpochMilli(row.occurredAt).atZone(zone).dayOfMonth

    private fun buildState(
        current: List<TransactionRow>,
        previous: List<TransactionRow>,
        envelopes: List<EnvelopeSummary>,
    ): InsightsState {
        val daily = LongArray(month.lengthOfMonth())
        current.forEach { daily[dayOf(it) - 1] += it.amountPaise }

        val topMerchants = current
            .groupBy { it.merchant.trim().uppercase() }
            .map { (_, rows) ->
                MerchantTotal(
                    name = rows.first().merchant.trim(),
                    amountPaise = rows.sumOf { it.amountPaise },
                    count = rows.size,
                )
            }
            .sortedByDescending { it.amountPaise }
            .take(5)

        return InsightsState(
            spentPaise = current.sumOf { it.amountPaise },
            // Compared with the same number of days last month, so the comparison is fair.
            lastMonthSamePeriodPaise = previous
                .filter { dayOf(it) <= today.dayOfMonth }
                .sumOf { it.amountPaise },
            lastMonthName = lastMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
            dailyPaise = daily.toList(),
            today = today.dayOfMonth,
            daysInMonth = month.lengthOfMonth(),
            noSpendDays = (0 until today.dayOfMonth).count { daily[it] == 0L },
            biggest = current.maxByOrNull { it.amountPaise },
            count = current.size,
            topMerchants = topMerchants,
            envelopes = envelopes
                .filter { it.monthlyBudgetPaise > 0 }
                .map {
                    EnvelopePace(
                        name = it.name,
                        usedFraction = it.spentPaise.toFloat() / it.monthlyBudgetPaise,
                        overBudget = it.spentPaise > it.monthlyBudgetPaise,
                    )
                },
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BudgetApp
                InsightsViewModel(app.repository)
            }
        }
    }
}
