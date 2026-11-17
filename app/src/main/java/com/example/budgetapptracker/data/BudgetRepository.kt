package com.example.budgetapptracker.data

import com.example.budgetapptracker.sms.KIND_LENT
import com.example.budgetapptracker.sms.MerchantCategories
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// How far apart an expense and its bank SMS can be and still count as the same payment.
private const val MATCH_BEFORE_MILLIS = 30 * 60 * 1_000L
private const val MATCH_AFTER_MILLIS = 2 * 60 * 1_000L
// A payment app can show the same payment more than once, and the bank SMS may land first.
private const val SAME_PAYMENT_BEFORE_MILLIS = 3 * 60 * 1_000L
private const val SAME_PAYMENT_AFTER_MILLIS = 60 * 1_000L
// How long ago a captured expense can be and still be the payment just made through the app.
private const val JUST_PAID_MILLIS = 10 * 60 * 1_000L
// How long ago a captured payment can be and still be the one that paid back a debt.
private const val SETTLE_LOOK_BACK_MILLIS = 24 * 60 * 60 * 1_000L
private const val BILL_LOOK_BACK_MILLIS = 125L * 24 * 60 * 60 * 1_000
// The two settings that decide whether a new payment is filed without asking.
data class AutoFileSettings(val enabled: Boolean = false, val ownNames: List<String> = emptyList())

class BudgetRepository(
    private val dao: BudgetDao,
    // Read fresh for every captured payment, so a change in Settings applies at once.
    private val autoFileSettings: () -> AutoFileSettings = { AutoFileSettings() },
) {

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

    suspend fun deleteCategory(id: String) {
        dao.deleteCategory(id)
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
        payee: String? = null,
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
        val expense = filed(
            TransactionEntity(
                amountPaise = amountPaise,
                categoryId = null,
                merchant = merchant,
                occurredAt = occurredAt,
                source = "SMS",
                smsKey = smsKey,
                payee = payee,
            )
        )
        return expense.takeIf { dao.insertTransactionIfNew(it) != -1L }
    }

    // An expense seen in a payment app's notification. Returns it, or null when the same
    // payment is already recorded (by bank SMS, by hand, or by an earlier notification).
    suspend fun addNotifiedExpense(
        amountPaise: Long,
        merchant: String,
        occurredAt: Long,
        payee: String? = null,
    ): TransactionEntity? {
        val existing = dao.findSameAmountNear(
            amountPaise = amountPaise,
            from = occurredAt - SAME_PAYMENT_BEFORE_MILLIS,
            until = occurredAt + SAME_PAYMENT_AFTER_MILLIS,
        )
        if (existing != null) return null

        val expense = filed(
            TransactionEntity(
                amountPaise = amountPaise,
                categoryId = null,
                merchant = merchant,
                occurredAt = occurredAt,
                source = "APP",
                payee = payee,
            )
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
        // A loan made from this payment is undone with it.
        if (dao.kindOf(id) == KIND_LENT) dao.deleteSplitDebts(id)
        dao.restoreTransaction(id)
    }

    // Decides where a newly captured payment goes:
    // 1. your own "Remember" rule for this payee, always;
    // 2. otherwise, when auto-filing is on, a reliable guess (a known brand, an investment,
    //    or a transfer to your own name);
    // 3. otherwise no envelope, so it waits in the Inbox for you to sort.
    private suspend fun filed(expense: TransactionEntity): TransactionEntity {
        dao.findRuleCategory(expense.merchant.uppercase())?.let { return expense.copy(categoryId = it) }
        val settings = autoFileSettings()
        if (!settings.enabled) return expense
        val envelopes = observeEnvelopes(YearMonth.now()).first()
        val guess = MerchantCategories.guess(expense.merchant, expense.payee, envelopes, settings.ownNames)
        return when {
            guess == null || !guess.confident -> expense
            guess.kind != null -> expense.copy(kind = guess.kind, excluded = true)
            else -> expense.copy(categoryId = guess.envelopeId)
        }
    }

    // The best guess for a payment already saved, for showing the likely envelope first.
    suspend fun guessFor(merchant: String, payee: String?) =
        MerchantCategories.guess(
            merchant,
            payee,
            observeEnvelopes(YearMonth.now()).first(),
            autoFileSettings().ownNames,
        )

    // Everything still without an envelope from the start of last month, newest first.
    fun observeUnsorted(): Flow<List<TransactionRow>> {
        val from = YearMonth.now().minusMonths(1).toEpochRange().first
        val until = YearMonth.now().toEpochRange().second
        return dao.observeTransactions(from, until, categoryId = null, uncategorisedOnly = true, hidden = false)
    }

    suspend fun countUnsorted(): Int =
        dao.countUnsorted(YearMonth.now().minusMonths(1).toEpochRange().first)

    // Money moved rather than spent: TRANSFER or INVESTMENT. It leaves every total.
    suspend fun markKind(id: String, kind: String) {
        dao.markKind(id, kind)
    }

    // Undo for an automatically filed payment: back to "no envelope".
    suspend fun unfile(id: String) {
        dao.unfile(id)
    }

    // You paid this to lend it: the person owes you the whole amount, and it is not your spending.
    suspend fun lend(transactionId: String, personId: String, amountPaise: Long, merchant: String) {
        dao.insertDebt(
            DebtEntity(personId = personId, amountPaise = amountPaise, note = "Lent · $merchant", expenseId = transactionId)
        )
        dao.markKind(transactionId, KIND_LENT)
    }

    // You paid this to pay back what you owed: your balance with them moves by this amount.
    // The payment itself stays, so it can still go into an envelope like other settle-ups.
    suspend fun paidBack(personId: String, amountPaise: Long) {
        dao.insertDebt(DebtEntity(personId = personId, amountPaise = amountPaise, note = "Paid back"))
    }

    fun observeKindTotal(month: YearMonth, kind: String): Flow<Long> {
        val (from, until) = month.toEpochRange()
        return dao.observeKindTotal(kind, from, until)
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
    // Brings a person's balance to zero. When you owed them, envelopeId is the envelope the
    // money came out of, or null when it is already counted (your share of a group bill).
    suspend fun settleUp(personId: String, personName: String, balancePaise: Long, envelopeId: String?) {
        val settled = DebtEntity(personId = personId, amountPaise = -balancePaise, note = "Settled up")
        if (balancePaise >= 0 || envelopeId == null) {
            dao.insertDebt(settled)
            return
        }
        val paidPaise = -balancePaise
        // Paid through GPay or similar: the bank SMS or the app's notification may have
        // recorded it already. Put that one in the envelope instead of adding a twin.
        val captured = dao.findCapturedWithoutEnvelope(
            amountPaise = paidPaise,
            from = System.currentTimeMillis() - SETTLE_LOOK_BACK_MILLIS,
        )
        if (captured != null) {
            dao.setTransactionCategory(captured, envelopeId)
            dao.insertDebt(settled)
            return
        }
        // Saves the expense and the ledger line together.
        dao.insertSplit(
            TransactionEntity(
                amountPaise = paidPaise,
                categoryId = envelopeId,
                merchant = "Paid $personName",
                note = "Settled up",
                source = "SETTLE",
            ),
            listOf(settled),
        )
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
    fun observeBills(): Flow<List<BillEntity>> = dao.observeBills()

    // Looks back about four months for payments that repeat monthly.
    fun observeBillSuggestions(): Flow<List<BillSuggestion>> =
        dao.observeBillSuggestions(System.currentTimeMillis() - BILL_LOOK_BACK_MILLIS)

    suspend fun addBill(name: String, amountPaise: Long, dayOfMonth: Int) {
        dao.insertBill(BillEntity(name = name.trim(), amountPaise = amountPaise, dayOfMonth = dayOfMonth))
    }

    // paidMonth is written like "2026-10", or null to clear the tick.
    suspend fun setBillPaid(id: String, paidMonth: String?) {
        dao.setBillPaidMonth(id, paidMonth)
    }

    suspend fun deleteBill(id: String) {
        dao.deleteBill(id)
    }
    fun observePayslips(): Flow<List<PayslipEntity>> = dao.observePayslips()

    // True when that month already has an income whose name mentions salary.
    fun observeHasSalary(month: YearMonth): Flow<Boolean> {
        val (from, until) = month.toEpochRange()
        return dao.observeSalaryCount(from, until).map { it > 0 }
    }

    // Saves a payslip and, if asked, records its net pay as that month's income.
    suspend fun addPayslip(payslip: PayslipEntity, addAsIncome: Boolean) {
        dao.insertPayslip(payslip)
        if (addAsIncome) {
            val month = YearMonth.parse(payslip.month)
            // This month: now. An earlier month: its first day, so it counts in that month.
            val receivedAt = if (month == YearMonth.now()) System.currentTimeMillis()
            else month.toEpochRange().first
            dao.insertIncome(
                IncomeEntity(
                    amountPaise = payslip.netPaise,
                    source = "Salary · ${payslip.employer}",
                    receivedAt = receivedAt,
                )
            )
        }
    }

    suspend fun deletePayslip(id: String) {
        dao.deletePayslip(id)
    }

    private fun YearMonth.toEpochRange(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val from = atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val until = plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return from to until
    }
}