package com.example.budgetapptracker.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.budgetapptracker.backup.BackupDao

@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        IncomeEntity::class,
        MerchantRuleEntity::class,
        PersonEntity::class,
        GroupEntity::class,
        DebtEntity::class,
        GroupMemberEntity::class,
        BillEntity::class,
        PayslipEntity::class,
    ],
    version = 11,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun budgetDao(): BudgetDao
    abstract fun backupDao(): BackupDao

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
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `people` (" +
                            "`id` TEXT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`phone` TEXT, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `split_groups` (" +
                            "`id` TEXT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `debts` (" +
                            "`id` TEXT NOT NULL, " +
                            "`personId` TEXT NOT NULL, " +
                            "`groupId` TEXT, " +
                            "`amountPaise` INTEGER NOT NULL, " +
                            "`note` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`), " +
                            "FOREIGN KEY(`personId`) REFERENCES `people`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                            "FOREIGN KEY(`groupId`) REFERENCES `split_groups`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE SET NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_personId` ON `debts` (`personId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_groupId` ON `debts` (`groupId`)")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `paidPaise` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `group_members` (" +
                            "`groupId` TEXT NOT NULL, " +
                            "`personId` TEXT NOT NULL, " +
                            "PRIMARY KEY(`groupId`, `personId`), " +
                            "FOREIGN KEY(`groupId`) REFERENCES `split_groups`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                            "FOREIGN KEY(`personId`) REFERENCES `people`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_group_members_personId` " +
                            "ON `group_members` (`personId`)"
                )
            }
        }
        // Debts remember which expense they were split from, so a split can be undone.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `debts` ADD COLUMN `expenseId` TEXT")
            }
        }
        // Monthly bills: rent, EMIs and subscriptions.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bills` (" +
                            "`id` TEXT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`amountPaise` INTEGER NOT NULL, " +
                            "`dayOfMonth` INTEGER NOT NULL, " +
                            "`paidMonth` TEXT, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                )
            }
        }

        // Payslips: salary details per month and the attached file's name.
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `payslips` (" +
                            "`id` TEXT NOT NULL, " +
                            "`month` TEXT NOT NULL, " +
                            "`employer` TEXT NOT NULL, " +
                            "`grossPaise` INTEGER NOT NULL, " +
                            "`deductionsPaise` INTEGER NOT NULL, " +
                            "`netPaise` INTEGER NOT NULL, " +
                            "`fileName` TEXT, " +
                            "`fileType` TEXT, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                )
            }
        }

        // What kind of non-spending a hidden payment is, and whether a person or a shop was paid.
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `kind` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `payee` TEXT")
            }
        }

        // Provident fund and tax kept separately on each payslip.
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `payslips` ADD COLUMN `pfPaise` INTEGER")
                db.execSQL("ALTER TABLE `payslips` ADD COLUMN `taxPaise` INTEGER")
            }
        }
    }
}