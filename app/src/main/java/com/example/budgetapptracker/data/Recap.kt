package com.example.budgetapptracker.data

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

// A finished month in a few numbers, for the recap card at the start of the next month.
data class MonthRecap(
    val month: YearMonth,
    val incomePaise: Long,
    val allocatedPaise: Long,
    val spentPaise: Long,
    val envelopesWithinBudget: Int,
    val envelopesWithBudget: Int,
    // The envelope that went furthest over, and by how much.
    val mostOver: Pair<String, Long>?,
    val biggest: TransactionRow?,
    val topPayee: Pair<String, Long>?,
    val noSpendDays: Int,
    // Spending in the month before, for a comparison; null when there was none.
    val previousSpentPaise: Long?,
) {
    // Positive: money left in the envelopes. Negative: spent more than was set aside.
    val savedPaise: Long get() = allocatedPaise - spentPaise
}

fun buildRecap(
    month: YearMonth,
    incomePaise: Long,
    envelopes: List<EnvelopeSummary>,
    expenses: List<TransactionRow>,
    previousSpentPaise: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): MonthRecap {
    val budgeted = envelopes.filter { it.monthlyBudgetPaise > 0 }
    val spendDays = expenses.map { Instant.ofEpochMilli(it.occurredAt).atZone(zone).dayOfMonth }.toSet()
    return MonthRecap(
        month = month,
        incomePaise = incomePaise,
        allocatedPaise = envelopes.sumOf { it.monthlyBudgetPaise },
        spentPaise = expenses.sumOf { it.amountPaise },
        envelopesWithinBudget = budgeted.count { it.remainingPaise >= 0 },
        envelopesWithBudget = budgeted.size,
        mostOver = budgeted.filter { it.remainingPaise < 0 }
            .minByOrNull { it.remainingPaise }
            ?.let { it.name to -it.remainingPaise },
        biggest = expenses.maxByOrNull { it.amountPaise },
        topPayee = expenses.groupBy { it.merchant.trim().uppercase() }
            .map { (name, rows) -> name to rows.sumOf { it.amountPaise } }
            .maxByOrNull { it.second },
        noSpendDays = month.lengthOfMonth() - spendDays.size,
        previousSpentPaise = previousSpentPaise.takeIf { it > 0 },
    )
}
