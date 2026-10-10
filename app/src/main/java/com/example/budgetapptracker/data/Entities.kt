package com.example.budgetapptracker.data

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
    indices = [Index("categoryId"), Index("occurredAt")],
)
data class TransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val amountPaise: Long,
    val categoryId: String?,
    val merchant: String,
    val note: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    val source: String = "MANUAL",
)

@Entity(tableName = "incomes")
data class IncomeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val amountPaise: Long,
    val source: String,
    val receivedAt: Long = System.currentTimeMillis(),
)