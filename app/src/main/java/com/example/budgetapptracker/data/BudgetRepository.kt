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
        hidden: Boolean = false,
    ): Flow<List<TransactionRow>> {
        val (from, until) = month.toEpochRange()
        return dao.observeTransactions(from, until, categoryId, uncategorisedOnly, hidden)
    }

    fun observeIncomeTotal(month: YearMonth): Flow<Long> {
        val (from, until) = month.toEpochRange()
        return dao.observeIncomeTotal(from, until)
    }

    fun observeSince(from: Long): Flow<List<TransactionRow>> =
        dao.observeTransactions(from, Long.MAX_VALUE, null, false, false)

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

    // Returns the saved expense, or null if this SMS was already imported.
    suspend fun addSmsExpense(
        amountPaise: Long,
        merchant: String,
        occurredAt: Long,
        smsKey: String,
    ): TransactionEntity? {
        val expense = TransactionEntity(
            amountPaise = amountPaise,
            categoryId = dao.findRuleCategory(merchant.uppercase()),
            merchant = merchant,
            occurredAt = occurredAt,
            source = "SMS",
            smsKey = smsKey,
        )
        return expense.takeIf { dao.insertTransactionIfNew(it) != -1L }
    }

    suspend fun assignCategory(
        transactionId: String,
        merchant: String,
        categoryId: String,
        rememberPayee: Boolean,
    ) {
        dao.setTransactionCategory(transactionId, categoryId)
        val key = merchant.uppercase()
        if (rememberPayee) {
            dao.upsertRule(MerchantRuleEntity(merchant = key, categoryId = categoryId))
            dao.categoriseUncategorised(key, categoryId)
        } else {
            // Saving with the switch off forgets any earlier rule for this payee.
            dao.deleteRule(key)
        }
    }

    suspend fun restoreTransaction(id: String) {
        dao.restoreTransaction(id)
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