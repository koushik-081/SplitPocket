package com.example.budgetapptracker.data

import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

// How far apart an expense and its bank SMS can be and still count as the same payment.
private const val MATCH_BEFORE_MILLIS = 30 * 60 * 1_000L
private const val MATCH_AFTER_MILLIS = 2 * 60 * 1_000L
// A payment app can show the same payment more than once, and the bank SMS may land first.
private const val SAME_PAYMENT_BEFORE_MILLIS = 3 * 60 * 1_000L
private const val SAME_PAYMENT_AFTER_MILLIS = 60 * 1_000L
// How long ago a captured expense can be and still be the payment just made through the app.
private const val JUST_PAID_MILLIS = 10 * 60 * 1_000L
class BudgetRepository(private val dao: BudgetDao) {

    fun observeEnvelopes(month: YearMonth): Flow<List<EnvelopeSummary>> {
        val (from, until) = month.toEpochRange()
        return dao.observeEnvelopes(from, until)
    }

    fun observeTransactions(
        month: YearMonth,
        categoryId: String?,
        uncategorisedOnly: Boolean,
        hidden: Boolean = false,
    ): Flow<List<TransactionRow>> {
        val (from, until) = month.toEpochRange()
        return dao.observeTransactions(from, until, categoryId, uncategorisedOnly, hidden)
    }

    fun observeIncomeTotal(month: YearMonth): Flow<Long> {
        val (from, until) = month.toEpochRange()
        return dao.observeIncomeTotal(from, until)
    }

    fun observeSince(from: Long): Flow<List<TransactionRow>> =
        dao.observeTransactions(from, Long.MAX_VALUE, null, false, false)

    suspend fun addCategory(name: String, budgetPaise: Long) {
        dao.insertCategory(CategoryEntity(name = name.trim(), monthlyBudgetPaise = budgetPaise))
    }

    suspend fun updateBudget(categoryId: String, budgetPaise: Long) {
        dao.updateCategoryBudget(categoryId, budgetPaise)
    }

    suspend fun addIncome(source: String, amountPaise: Long) {
        dao.insertIncome(IncomeEntity(amountPaise = amountPaise, source = source.trim()))
    }

    suspend fun addExpense(
        categoryId: String,
        amountPaise: Long,
        merchant: String,
        source: String = "MANUAL",
    ) {
        if (source == "UPI") {
            // Paid through the app: the bank SMS or the payment app's notification may have
            // recorded this payment already. Put that one in the envelope instead of adding a twin.
            val captured = dao.findCapturedWithoutEnvelope(
                amountPaise = amountPaise,
                from = System.currentTimeMillis() - JUST_PAID_MILLIS,
            )
            if (captured != null) {
                dao.setTransactionCategory(captured, categoryId)
                return
            }
        }
        dao.insertTransaction(
            TransactionEntity(
                amountPaise = amountPaise,
                categoryId = categoryId,
                merchant = merchant.trim(),
                source = source,
            )
        )
    }

    // Returns the saved expense, or null if this SMS was already imported.
    suspend fun addSmsExpense(
        amountPaise: Long,
        merchant: String,
        occurredAt: Long,
        smsKey: String,
    ): TransactionEntity? {
        if (dao.countSmsKey(smsKey) > 0) return null

        // Already recorded by hand or by paying through the app? Attach this SMS to that
        // expense instead of adding the same payment a second time.
        val existing = dao.findUnlinkedExpense(
            amountPaise = amountPaise,
            from = occurredAt - MATCH_BEFORE_MILLIS,
            until = occurredAt + MATCH_AFTER_MILLIS,
        )
        if (existing != null) {
            dao.linkSms(existing, smsKey)
            return null
        }
        val expense = TransactionEntity(
            amountPaise = amountPaise,
            categoryId = dao.findRuleCategory(merchant.uppercase()),
            merchant = merchant,
            occurredAt = occurredAt,
            source = "SMS",
            smsKey = smsKey,
        )
        return expense.takeIf { dao.insertTransactionIfNew(it) != -1L }
    }

    // An expense seen in a payment app's notification. Returns it, or null when the same
    // payment is already recorded (by bank SMS, by hand, or by an earlier notification).
    suspend fun addNotifiedExpense(
        amountPaise: Long,
        merchant: String,
        occurredAt: Long,
    ): TransactionEntity? {
        val existing = dao.findSameAmountNear(
            amountPaise = amountPaise,
            from = occurredAt - SAME_PAYMENT_BEFORE_MILLIS,
            until = occurredAt + SAME_PAYMENT_AFTER_MILLIS,
        )
        if (existing != null) return null

        val expense = TransactionEntity(
            amountPaise = amountPaise,
            categoryId = dao.findRuleCategory(merchant.uppercase()),
            merchant = merchant,
            occurredAt = occurredAt,
            source = "APP",
        )
        dao.insertTransaction(expense)
        return expense
    }
    suspend fun assignCategory(
        transactionId: String,
        merchant: String,
        categoryId: String,
        rememberPayee: Boolean,
    ) {
        dao.setTransactionCategory(transactionId, categoryId)
        val key = merchant.uppercase()
        if (rememberPayee) {
            dao.upsertRule(MerchantRuleEntity(merchant = key, categoryId = categoryId))
            dao.categoriseUncategorised(key, categoryId)
        } else {
            // Saving with the switch off forgets any earlier rule for this payee.
            dao.deleteRule(key)
        }
    }

    suspend fun restoreTransaction(id: String) {
        dao.restoreTransaction(id)
    }

    suspend fun removeTransaction(id: String) {
        dao.excludeSmsTransaction(id)
        dao.deleteManualTransaction(id)
    }
    fun observePeople(): Flow<List<PersonBalance>> = dao.observePeople()

    fun observeDebts(personId: String): Flow<List<DebtRow>> = dao.observeDebts(personId)

    suspend fun addPerson(name: String, phone: String) {
        dao.insertPerson(PersonEntity(name = name.trim(), phone = phone.trim().ifBlank { null }))
    }

    // Positive: they owe you. Negative: you owe them.
    suspend fun addDebt(personId: String, signedPaise: Long, note: String) {
        dao.insertDebt(DebtEntity(personId = personId, amountPaise = signedPaise, note = note))
    }

    suspend fun deleteDebt(id: String) {
        dao.deleteDebt(id)
    }

    suspend fun deletePerson(id: String) {
        dao.deletePerson(id)
    }
    fun observeGroups(): Flow<List<GroupSummary>> = dao.observeGroups()

    fun observeGroupMembers(groupId: String): Flow<List<PersonBalance>> =
        dao.observeGroupMembers(groupId)

    fun observeGroupEntries(groupId: String): Flow<List<GroupEntryRow>> =
        dao.observeGroupEntries(groupId)

    suspend fun addGroup(name: String, memberIds: List<String>) {
        val group = GroupEntity(name = name.trim())
        dao.insertGroup(group)
        dao.insertGroupMembers(memberIds.map { GroupMemberEntity(groupId = group.id, personId = it) })
    }

    suspend fun deleteGroup(id: String) {
        dao.deleteGroup(id)
    }

    // Splits a bill equally between you and the chosen people.
    // payerId is null when you paid; otherwise it is the person who paid.
    // envelopeId is null when your share should not count against any envelope.
    suspend fun addSplit(
        groupId: String,
        description: String,
        totalPaise: Long,
        sharedWithIds: List<String>,
        payerId: String?,
        envelopeId: String?,
    ) {
        val plan = planEqualSplit(totalPaise, otherPeople = sharedWithIds.size)
        val what = description.trim()

        val debts = if (payerId == null) {
            // You paid: each of the others owes you their share.
            sharedWithIds.map {
                DebtEntity(personId = it, groupId = groupId, amountPaise = plan.otherSharePaise, note = what)
            }
        } else {
            // Someone else paid: you owe them your share.
            listOf(DebtEntity(personId = payerId, groupId = groupId, amountPaise = -plan.mySharePaise, note = what))
        }

        // Only your own share is your spending, so only that goes into the envelope.
        val expense = envelopeId?.let {
            TransactionEntity(
                amountPaise = plan.mySharePaise,
                categoryId = it,
                merchant = what,
                note = "Your share of a ${sharedWithIds.size + 1}-way split",
                source = "SPLIT",
                paidPaise = if (payerId == null) totalPaise else null,
            )
        }
        dao.insertSplit(expense, debts)
    }
    // Splits an expense you have already paid. shares says how much each person owes you;
    // whatever is left of the total is your own share, and the expense shrinks to that.
    suspend fun splitExpense(
        transactionId: String,
        merchant: String,
        totalPaise: Long,
        shares: Map<String, Long>,
    ) {
        val othersPaise = shares.values.sum()
        if (shares.isEmpty() || shares.values.any { it <= 0 } || othersPaise > totalPaise) return
        val debts = shares.map { (personId, paise) ->
            DebtEntity(personId = personId, amountPaise = paise, note = merchant, expenseId = transactionId)
        }
        dao.splitExisting(
            id = transactionId,
            sharePaise = totalPaise - othersPaise,
            totalPaise = totalPaise,
            note = "Your share of a split with ${shares.size} " +
                if (shares.size == 1) "person" else "people",
            debts = debts,
        )
    }

    suspend fun undoSplit(transactionId: String) {
        dao.undoSplit(transactionId)
    }

    private fun YearMonth.toEpochRange(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val from = atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val until = plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return from to until
    }
}