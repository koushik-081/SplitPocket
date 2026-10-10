package com.example.budgetapptracker.data

import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

class BudgetRepository(private val dao: BudgetDao) {

    fun observeEnvelopes(month: YearMonth): Flow<List<EnvelopeSummary>> {
        val (from, until) = month.toEpochRange()
        return dao.observeEnvelopes(from, until)
    }

    fun observeTransactions(
        month: YearMonth,
        categoryId: String?,
        uncategorisedOnly: Boolean,
    ): Flow<List<TransactionRow>> {
        val (from, until) = month.toEpochRange()
        return dao.observeTransactions(from, until, categoryId, uncategorisedOnly)
    }

    fun observeIncomeTotal(month: YearMonth): Flow<Long> {
        val (from, until) = month.toEpochRange()
        return dao.observeIncomeTotal(from, until)
    }

    fun observeSince(from: Long): Flow<List<TransactionRow>> =
        dao.observeTransactions(from, Long.MAX_VALUE, null, false)

    suspend fun addCategory(name: String, budgetPaise: Long) {
        dao.insertCategory(CategoryEntity(name = name.trim(), monthlyBudgetPaise = budgetPaise))
    }

    suspend fun updateBudget(categoryId: String, budgetPaise: Long) {
        dao.updateCategoryBudget(categoryId, budgetPaise)
    }

    suspend fun addIncome(source: String, amountPaise: Long) {
        dao.insertIncome(IncomeEntity(amountPaise = amountPaise, source = source.trim()))
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

    suspend fun addSmsExpense(
        amountPaise: Long,
        merchant: String,
        occurredAt: Long,
        smsKey: String,
    ): Boolean {
        val rowId = dao.insertTransactionIfNew(
            TransactionEntity(
                amountPaise = amountPaise,
                categoryId = dao.findRuleCategory(merchant.uppercase()),
                merchant = merchant,
                occurredAt = occurredAt,
                source = "SMS",
                smsKey = smsKey,
            )
        )
        return rowId != -1L
    }

    suspend fun assignCategory(
        transactionId: String,
        merchant: String,
        categoryId: String,
        rememberPayee: Boolean,
    ) {
        dao.setTransactionCategory(transactionId, categoryId)
        if (rememberPayee) {
            val key = merchant.uppercase()
            dao.upsertRule(MerchantRuleEntity(merchant = key, categoryId = categoryId))
            dao.categoriseUncategorised(key, categoryId)
        }
    }

    suspend fun removeTransaction(id: String) {
        dao.excludeSmsTransaction(id)
        dao.deleteManualTransaction(id)
    }

    private fun YearMonth.toEpochRange(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val from = atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val until = plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return from to until
    }
}