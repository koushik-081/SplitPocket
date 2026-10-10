package com.example.budgetapptracker.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        IncomeEntity::class,
        MerchantRuleEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun budgetDao(): BudgetDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `incomes` (" +
                            "`id` TEXT NOT NULL, " +
                            "`amountPaise` INTEGER NOT NULL, " +
                            "`source` TEXT NOT NULL, " +
                            "`receivedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `smsKey` TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_smsKey` " +
                            "ON `transactions` (`smsKey`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `merchant_rules` (" +
                            "`merchant` TEXT NOT NULL, " +
                            "`categoryId` TEXT NOT NULL, " +
                            "PRIMARY KEY(`merchant`))"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `transactions` ADD COLUMN `excluded` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}