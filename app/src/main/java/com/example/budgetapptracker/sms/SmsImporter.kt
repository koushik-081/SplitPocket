package com.example.budgetapptracker.sms

import android.content.Context
import android.provider.Telephony
import com.example.budgetapptracker.data.BudgetRepository
import com.example.budgetapptracker.data.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsImporter(
    private val context: Context,
    private val repository: BudgetRepository,
) {
    // Returns the expenses that were newly saved; messages imported before are skipped.
    suspend fun importSince(sinceMillis: Long): List<TransactionEntity> = withContext(Dispatchers.IO) {
        val added = mutableListOf<TransactionEntity>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(sinceMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val address = cursor.getString(0) ?: continue
                val body = cursor.getString(1) ?: continue
                val date = cursor.getLong(2)

                if (!isBankSender(address)) continue

                val parsed = SmsParser.parse(body) ?: continue
                val smsKey = "$address|$date|${body.hashCode()}"
                repository.addSmsExpense(parsed.amountPaise, parsed.merchant, date, smsKey, parsed.payee)
                    ?.let { added += it }
            }
        }
        added
    }
}