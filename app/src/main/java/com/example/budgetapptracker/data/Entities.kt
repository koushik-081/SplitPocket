package com.example.budgetapptracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val monthlyBudgetPaise: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("categoryId"),
        Index("occurredAt"),
        Index(value = ["smsKey"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val amountPaise: Long,
    val categoryId: String?,
    val merchant: String,
    val note: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    val source: String = "MANUAL",
    val smsKey: String? = null,
    @ColumnInfo(defaultValue = "0") val excluded: Boolean = false,
    // What actually left your account, when that differs from your own share (a split bill).
    val paidPaise: Long? = null,
)

@Entity(tableName = "incomes")
data class IncomeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val amountPaise: Long,
    val source: String,
    val receivedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey val merchant: String,
    val categoryId: String,
)
// Someone you lend to, borrow from or split expenses with.
@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

// A named group such as "Goa trip" or "Flat". Used from the next step.
@Entity(tableName = "split_groups")
data class GroupEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

// One line in the ledger with a person. Positive: they owe you. Negative: you owe them.
@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("personId"), Index("groupId")],
)
data class DebtEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val personId: String,
    val groupId: String? = null,
    val amountPaise: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
// Which people belong to which group.
@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "personId"],
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("personId")],
)
data class GroupMemberEntity(
    val groupId: String,
    val personId: String,
)
