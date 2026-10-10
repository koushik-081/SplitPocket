package com.example.budgetapptracker.data

import androidx.room.Dao
import androidx.room.Insert
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
    val categoryId: String?,
    val categoryName: String?,
)

@Dao
interface BudgetDao {
    @Insert
    suspend fun insertCategory(category: CategoryEntity)

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: String)

    @Query(
        """
        SELECT c.id, c.name, c.monthlyBudgetPaise,
               COALESCE(SUM(t.amountPaise), 0) AS spentPaise
        FROM categories c
        LEFT JOIN transactions t
               ON t.categoryId = c.id
              AND t.occurredAt >= :from AND t.occurredAt < :until
        GROUP BY c.id
        ORDER BY c.createdAt
        """
    )
    fun observeEnvelopes(from: Long, until: Long): Flow<List<EnvelopeSummary>>

    @Query(
        """
        SELECT t.id, t.amountPaise, t.merchant, t.occurredAt, t.categoryId,
               c.name AS categoryName
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.occurredAt >= :from AND t.occurredAt < :until
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
        ORDER BY t.occurredAt DESC
        """
    )
    fun observeTransactions(from: Long, until: Long, categoryId: String?): Flow<List<TransactionRow>>
}