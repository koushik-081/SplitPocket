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

@Dao
interface BudgetDao {
    @Insert
    suspend fun insertCategory(category: CategoryEntity)

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity)

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
}