package com.example.budgetapptracker.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.budgetapptracker.data.AppDatabase
import java.security.GeneralSecurityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface RestoreResult {
    data class Restored(val expenses: Int, val envelopes: Int, val people: Int) : RestoreResult
    data object WrongPassword : RestoreResult
    data object NotABackup : RestoreResult
}

class BackupManager(private val context: Context, private val database: AppDatabase) {

    private val dao get() = database.backupDao()

    // Writes everything to the file the user picked. Returns how many expenses were saved.
    suspend fun backUp(uri: Uri, password: String, hint: String): Int = withContext(Dispatchers.IO) {
        val data = BackupData(
            categories = dao.categories(),
            transactions = dao.transactions(),
            incomes = dao.incomes(),
            rules = dao.rules(),
            people = dao.people(),
            groups = dao.groups(),
            debts = dao.debts(),
            groupMembers = dao.groupMembers(),
            bills = dao.bills(),
        )
        val file = BackupCrypto.encrypt(BackupCodec.toJson(data).toByteArray(), password, hint)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(file) }
            ?: error("Could not open the chosen file")
        data.transactions.size
    }
    // Looks at a chosen file without unlocking it. Returns its password hint (possibly empty),
    // or null if the file is not one of this app's backups.
    suspend fun hintOf(uri: Uri): String? = withContext(Dispatchers.IO) {
        val file = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext null
        if (BackupCrypto.isBackup(file)) BackupCrypto.hintOf(file) else null
    }
    // Replaces everything in the app with the contents of a backup file.
    suspend fun restore(uri: Uri, password: String): RestoreResult = withContext(Dispatchers.IO) {
        val file = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext RestoreResult.NotABackup
        if (!BackupCrypto.isBackup(file)) return@withContext RestoreResult.NotABackup

        val data = try {
            BackupCodec.fromJson(String(BackupCrypto.decrypt(file, password)))
        } catch (e: GeneralSecurityException) {
            return@withContext RestoreResult.WrongPassword
        } catch (e: Exception) {
            return@withContext RestoreResult.NotABackup
        }

        // One transaction: either the whole backup goes in, or the current data is left untouched.
        database.withTransaction {
            dao.clearBills()
            dao.clearGroupMembers()
            dao.clearDebts()
            dao.clearPeople()
            dao.clearGroups()
            dao.clearTransactions()
            dao.clearRules()
            dao.clearIncomes()
            dao.clearCategories()

            dao.insertCategories(data.categories)
            dao.insertIncomes(data.incomes)
            dao.insertRules(data.rules)
            dao.insertTransactions(data.transactions)
            dao.insertPeople(data.people)
            dao.insertGroups(data.groups)
            dao.insertDebts(data.debts)
            dao.insertGroupMembers(data.groupMembers)
            dao.insertBills(data.bills)
        }
        RestoreResult.Restored(data.transactions.size, data.categories.size, data.people.size)
    }
}