package com.example.budgetapptracker.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.budgetapptracker.data.CategoryEntity
import com.example.budgetapptracker.data.DebtEntity
import com.example.budgetapptracker.data.GroupEntity
import com.example.budgetapptracker.data.GroupMemberEntity
import com.example.budgetapptracker.data.IncomeEntity
import com.example.budgetapptracker.data.MerchantRuleEntity
import com.example.budgetapptracker.data.PersonEntity
import com.example.budgetapptracker.data.TransactionEntity
import com.example.budgetapptracker.data.BillEntity

// Reads and writes whole tables. Only the backup feature uses these.
@Dao
interface BackupDao {
    @Query("SELECT * FROM categories") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM transactions") suspend fun transactions(): List<TransactionEntity>
    @Query("SELECT * FROM incomes") suspend fun incomes(): List<IncomeEntity>
    @Query("SELECT * FROM merchant_rules") suspend fun rules(): List<MerchantRuleEntity>
    @Query("SELECT * FROM people") suspend fun people(): List<PersonEntity>
    @Query("SELECT * FROM split_groups") suspend fun groups(): List<GroupEntity>
    @Query("SELECT * FROM debts") suspend fun debts(): List<DebtEntity>
    @Query("SELECT * FROM group_members") suspend fun groupMembers(): List<GroupMemberEntity>
    @Query("SELECT * FROM bills") suspend fun bills(): List<BillEntity>
    @Insert suspend fun insertCategories(items: List<CategoryEntity>)
    @Insert suspend fun insertBills(items: List<BillEntity>)
    @Insert suspend fun insertTransactions(items: List<TransactionEntity>)
    @Insert suspend fun insertIncomes(items: List<IncomeEntity>)
    @Insert suspend fun insertRules(items: List<MerchantRuleEntity>)
    @Insert suspend fun insertPeople(items: List<PersonEntity>)
    @Insert suspend fun insertGroups(items: List<GroupEntity>)
    @Insert suspend fun insertDebts(items: List<DebtEntity>)
    @Insert suspend fun insertGroupMembers(items: List<GroupMemberEntity>)

    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM bills") suspend fun clearBills()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM incomes") suspend fun clearIncomes()
    @Query("DELETE FROM merchant_rules") suspend fun clearRules()
    @Query("DELETE FROM people") suspend fun clearPeople()
    @Query("DELETE FROM split_groups") suspend fun clearGroups()
    @Query("DELETE FROM debts") suspend fun clearDebts()
    @Query("DELETE FROM group_members") suspend fun clearGroupMembers()
}