package com.example.budgetapptracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import androidx.room.Transaction

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
    // The full amount paid, when this expense has been split and amountPaise is only your share.
    val paidPaise: Long?,
    // How many people owe you for this expense. Above zero means the split can be undone.
    val splitCount: Int,
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
data class GroupSummary(
    val id: String,
    val name: String,
    val memberCount: Int,
    val balancePaise: Long,
)

data class GroupEntryRow(
    val id: String,
    val personName: String,
    val amountPaise: Long,
    val note: String,
    val createdAt: Long,
)
@Dao
interface BudgetDao {
    @Insert
    suspend fun insertGroup(group: GroupEntity)

    @Insert
    suspend fun insertGroupMembers(members: List<GroupMemberEntity>)

    @Insert
    suspend fun insertDebts(debts: List<DebtEntity>)

    @Query("DELETE FROM split_groups WHERE id = :id")
    suspend fun deleteGroup(id: String)

    // Saves a split bill in one step: your share as an expense, and what the others owe.
    @Transaction
    suspend fun insertSplit(expense: TransactionEntity?, debts: List<DebtEntity>) {
        if (expense != null) insertTransaction(expense)
        insertDebts(debts)
    }

    @Query(
        """
        SELECT g.id, g.name,
               (SELECT COUNT(*) FROM group_members m WHERE m.groupId = g.id) AS memberCount,
               (SELECT COALESCE(SUM(d.amountPaise), 0) FROM debts d WHERE d.groupId = g.id) AS balancePaise
        FROM split_groups g
        ORDER BY g.createdAt DESC
        """
    )
    fun observeGroups(): Flow<List<GroupSummary>>

    // Each member with their balance inside this one group.
    @Query(
        """
        SELECT p.id, p.name, p.phone, COALESCE(SUM(d.amountPaise), 0) AS balancePaise
        FROM group_members m
        JOIN people p ON p.id = m.personId
        LEFT JOIN debts d ON d.personId = p.id AND d.groupId = m.groupId
        WHERE m.groupId = :groupId
        GROUP BY p.id
        ORDER BY p.name COLLATE NOCASE
        """
    )
    fun observeGroupMembers(groupId: String): Flow<List<PersonBalance>>

    @Query(
        """
        SELECT d.id, p.name AS personName, d.amountPaise, d.note, d.createdAt
        FROM debts d
        JOIN people p ON p.id = d.personId
        WHERE d.groupId = :groupId
        ORDER BY d.createdAt DESC
        """
    )
    fun observeGroupEntries(groupId: String): Flow<List<GroupEntryRow>>

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
    // Shrinks an expense to your own share and remembers the full amount that was paid.
    // Does nothing if the expense has been split before. Returns how many rows changed.
    @Query(
        """
        UPDATE transactions SET amountPaise = :sharePaise, paidPaise = :totalPaise, note = :note
        WHERE id = :id AND paidPaise IS NULL
        """
    )
    suspend fun shrinkToShare(id: String, sharePaise: Long, totalPaise: Long, note: String): Int

    // Splits an expense that is already recorded: one step, so the expense and the
    // debts can never disagree.
    @Transaction
    suspend fun splitExisting(
        id: String,
        sharePaise: Long,
        totalPaise: Long,
        note: String,
        debts: List<DebtEntity>,
    ) {
        if (shrinkToShare(id, sharePaise, totalPaise, note) == 1) insertDebts(debts)
    }

    // Puts a split expense back to the full amount that was paid.
    @Query(
        """
        UPDATE transactions SET amountPaise = paidPaise, paidPaise = NULL, note = ''
        WHERE id = :id AND paidPaise IS NOT NULL
        """
    )
    suspend fun restoreFullAmount(id: String): Int

    @Query("DELETE FROM debts WHERE expenseId = :id")
    suspend fun deleteSplitDebts(id: String)

    // Undoes a split in one step: the expense is whole again and nobody owes for it.
    @Transaction
    suspend fun undoSplit(id: String) {
        if (restoreFullAmount(id) == 1) deleteSplitDebts(id)
    }
    @Query("SELECT COUNT(*) FROM transactions WHERE smsKey = :smsKey")
    suspend fun countSmsKey(smsKey: String): Int

    // An expense of the same amount, recorded without an SMS, close to when the SMS arrived.
    @Query(
        """
        SELECT id FROM transactions
        WHERE smsKey IS NULL AND excluded = 0
          AND (amountPaise = :amountPaise OR paidPaise = :amountPaise)
          AND occurredAt >= :from AND occurredAt <= :until
        ORDER BY occurredAt DESC LIMIT 1
        """
    )
    suspend fun findUnlinkedExpense(amountPaise: Long, from: Long, until: Long): String?

    // Any expense of this amount around this time, however it was recorded.
    @Query(
        """
        SELECT id FROM transactions
        WHERE (amountPaise = :amountPaise OR paidPaise = :amountPaise)
          AND occurredAt >= :from AND occurredAt <= :until
        LIMIT 1
        """
    )
    suspend fun findSameAmountNear(amountPaise: Long, from: Long, until: Long): String?

    // An expense picked up automatically (bank SMS or payment app) that has no envelope yet.
    @Query(
        """
        SELECT id FROM transactions
        WHERE source IN ('SMS', 'APP') AND categoryId IS NULL AND excluded = 0
          AND amountPaise = :amountPaise AND occurredAt >= :from
        ORDER BY occurredAt DESC LIMIT 1
        """
    )
    suspend fun findCapturedWithoutEnvelope(amountPaise: Long, from: Long): String?
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
               EXISTS(SELECT 1 FROM merchant_rules r WHERE r.merchant = UPPER(t.merchant)) AS remembered,
               t.paidPaise,
               (SELECT COUNT(*) FROM debts d WHERE d.expenseId = t.id) AS splitCount
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