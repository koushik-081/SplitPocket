package com.example.budgetapptracker.export

import com.example.budgetapptracker.data.BudgetRepository
import com.example.budgetapptracker.data.EnvelopeSummary
import com.example.budgetapptracker.data.TransactionRow
import java.time.YearMonth
import kotlinx.coroutines.flow.first

// Everything one month's PDF shows, gathered before drawing starts.
data class MonthReport(
    val month: YearMonth,
    val incomePaise: Long,
    val envelopes: List<EnvelopeSummary>,
    // Newest first, hidden expenses left out.
    val expenses: List<TransactionRow>,
) {
    val allocatedPaise: Long get() = envelopes.sumOf { it.monthlyBudgetPaise }
    val spentPaise: Long get() = expenses.sumOf { it.amountPaise }

    // Where the money went, biggest payee first.
    val topPayees: List<Pair<String, Long>>
        get() = expenses
            .groupBy { it.merchant.trim().uppercase() }
            .map { (name, rows) -> name to rows.sumOf { it.amountPaise } }
            .sortedByDescending { it.second }
            .take(8)
}

// Reads the month once from the database; first() takes the current value of each live query.
suspend fun buildMonthReport(repository: BudgetRepository, month: YearMonth) = MonthReport(
    month = month,
    incomePaise = repository.observeIncomeTotal(month).first(),
    envelopes = repository.observeEnvelopes(month).first(),
    expenses = repository.observeTransactions(month, categoryId = null, uncategorisedOnly = false).first(),
)