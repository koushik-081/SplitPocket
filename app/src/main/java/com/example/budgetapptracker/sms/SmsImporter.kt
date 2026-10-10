package com.example.budgetapptracker.sms

import android.content.Context
import android.provider.Telephony
import com.example.budgetapptracker.data.BudgetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsImporter(
    private val context: Context,
    private val repository: BudgetRepository,
) {
    suspend fun importSince(sinceMillis: Long): Int = withContext(Dispatchers.IO) {
        var added = 0
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

                // Banks send from named senders (e.g. VM-HDFCBK); skip personal numbers.
                if (address.none { it.isLetter() }) continue

                val parsed = SmsParser.parse(body) ?: continue
                val smsKey = "$address|$date|${body.hashCode()}"
                if (repository.addSmsExpense(parsed.amountPaise, parsed.merchant, date, smsKey)) {
                    added++
                }
            }
        }
        added
    }
}