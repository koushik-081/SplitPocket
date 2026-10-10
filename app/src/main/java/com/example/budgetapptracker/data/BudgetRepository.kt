package com.example.budgetapptracker.data

import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

class BudgetRepository(private val dao: BudgetDao) {

    fun observeEnvelopes(month: YearMonth): Flow<List<EnvelopeSummary>> {
        val (from, until) = month.toEpochRange()
        return dao.observeEnvelopes(from, until)
    }

    fun observeTransactions(month: YearMonth, categoryId: String?): Flow<List<TransactionRow>> {
        val (from, until) = month.toEpochRange()
        return dao.observeTransactions(from, until, categoryId)
    }

    suspend fun addCategory(name: String, budgetPaise: Long) {
        dao.insertCategory(CategoryEntity(name = name.trim(), monthlyBudgetPaise = budgetPaise))
    }

    suspend fun addExpense(categoryId: String, amountPaise: Long, merchant: String) {
        dao.insertTransaction(
            TransactionEntity(
                amountPaise = amountPaise,
                categoryId = categoryId,
                merchant = merchant.trim(),
            )
        )
    }

    suspend fun deleteTransaction(id: String) {
        dao.deleteTransaction(id)
    }

    private fun YearMonth.toEpochRange(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val from = atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val until = plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return from to until
    }
}