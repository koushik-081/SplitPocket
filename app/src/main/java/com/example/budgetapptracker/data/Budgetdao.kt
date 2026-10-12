package com.example.budgetapptracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class EnvelopeSummary(
    val id: String,
    val name: String,
    val monthlyBudgetPaise: Long,
    val spentPaise: Long,
) {
    val remainingPaise: Long get() = monthlyBudgetPaise - spentPaise
}

data class TransactionRow(
    val id: String,
    val amountPaise: Long,
    val merchant: String,
    val occurredAt: Long,
    val source: String,
    val categoryId: String?,
    val categoryName: String?,
    // True when "Remember for this payee" is saved for this merchant.
    val remembered: Boolean,
)

data class PersonBalance(
    val id: String,
    val name: String,
    val phone: String?,
    val balancePaise: Long,
)

data class DebtRow(
    val id: String,
    val amountPaise: Long,
    val note: String,
    val createdAt: Long,
    val groupName: String?,
)
@Dao
interface BudgetDao {

@Insert
suspend fun insertPerson(person: PersonEntity)

@Insert
suspend fun insertDebt(debt: DebtEntity)

@Query("DELETE FROM debts WHERE id = :id")
suspend fun deleteDebt(id: String)

@Query("DELETE FROM people WHERE id = :id")
suspend fun deletePerson(id: String)

// One row per person with everything they owe you, minus everything you owe them.
@Query(
    """
        SELECT p.id, p.name, p.phone, COALESCE(SUM(d.amountPaise), 0) AS balancePaise
        FROM people p
        LEFT JOIN debts d ON d.personId = p.id
        GROUP BY p.id
        ORDER BY p.name COLLATE NOCASE
        """
)
fun observePeople(): Flow<List<PersonBalance>>

@Query(
    """
        SELECT d.id, d.amountPaise, d.note, d.createdAt, g.name AS groupName
        FROM debts d
        LEFT JOIN split_groups g ON g.id = d.groupId
        WHERE d.personId = :personId
        ORDER BY d.createdAt DESC
        """
)
fun observeDebts(personId: String): Flow<List<DebtRow>>
    @Insert
    suspend fun insertCategory(category: CategoryEntity)

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactionIfNew(transaction: TransactionEntity): Long

    @Insert
    suspend fun insertIncome(income: IncomeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: MerchantRuleEntity)

    @Query("SELECT categoryId FROM merchant_rules WHERE merchant = :merchant")
    suspend fun findRuleCategory(merchant: String): String?

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id")
    suspend fun setTransactionCategory(id: String, categoryId: String)

    @Query(
        """
        UPDATE transactions SET categoryId = :categoryId
        WHERE UPPER(merchant) = :merchant AND categoryId IS NULL AND excluded = 0
        """
    )
    suspend fun categoriseUncategorised(merchant: String, categoryId: String)

    @Query("DELETE FROM transactions WHERE id = :id AND smsKey IS NULL")
    suspend fun deleteManualTransaction(id: String)

    @Query(
        """
        UPDATE transactions SET excluded = 1, categoryId = NULL
        WHERE id = :id AND smsKey IS NOT NULL
        """
    )
    suspend fun excludeSmsTransaction(id: String)

    @Query("UPDATE transactions SET excluded = 0 WHERE id = :id")
    suspend fun restoreTransaction(id: String)
    @Query("SELECT COUNT(*) FROM transactions WHERE smsKey = :smsKey")
    suspend fun countSmsKey(smsKey: String): Int

    // An expense of the same amount, recorded without an SMS, close to when the SMS arrived.
    @Query(
        """
        SELECT id FROM transactions
        WHERE smsKey IS NULL AND excluded = 0 AND amountPaise = :amountPaise
          AND occurredAt >= :from AND occurredAt <= :until
        ORDER BY occurredAt DESC LIMIT 1
        """
    )
    suspend fun findUnlinkedExpense(amountPaise: Long, from: Long, until: Long): String?

    @Query("UPDATE transactions SET smsKey = :smsKey WHERE id = :id")
    suspend fun linkSms(id: String, smsKey: String)

    @Query("DELETE FROM merchant_rules WHERE merchant = :merchant")
    suspend fun deleteRule(merchant: String)

    @Query("UPDATE categories SET monthlyBudgetPaise = :budgetPaise WHERE id = :id")
    suspend fun updateCategoryBudget(id: String, budgetPaise: Long)

    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0) FROM incomes
        WHERE receivedAt >= :from AND receivedAt < :until
        """
    )
    fun observeIncomeTotal(from: Long, until: Long): Flow<Long>

    @Query(
        """
        SELECT c.id, c.name, c.monthlyBudgetPaise,
               COALESCE(SUM(t.amountPaise), 0) AS spentPaise
        FROM categories c
        LEFT JOIN transactions t
               ON t.categoryId = c.id
              AND t.excluded = 0
              AND t.occurredAt >= :from AND t.occurredAt < :until
        GROUP BY c.id
        ORDER BY c.createdAt
        """
    )
    fun observeEnvelopes(from: Long, until: Long): Flow<List<EnvelopeSummary>>

    @Query(
        """
        SELECT t.id, t.amountPaise, t.merchant, t.occurredAt, t.source, t.categoryId,
               c.name AS categoryName,
               EXISTS(SELECT 1 FROM merchant_rules r WHERE r.merchant = UPPER(t.merchant)) AS remembered
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.occurredAt >= :from AND t.occurredAt < :until
          AND t.excluded = :hidden
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:uncategorisedOnly = 0 OR t.categoryId IS NULL)
        ORDER BY t.occurredAt DESC
        """
    )
    fun observeTransactions(
        from: Long,
        until: Long,
        categoryId: String?,
        uncategorisedOnly: Boolean,
        hidden: Boolean,
    ): Flow<List<TransactionRow>>
}