package com.example.budgetapptracker.data

import java.time.LocalDate
import java.time.YearMonth

// Where one repeating bill stands today.
data class BillStatus(
    val bill: BillEntity,
    val paid: Boolean,
    // When it is next due: this month while unpaid, next month once paid.
    val dueDate: LocalDate,
    val overdue: Boolean,
    // The payee of the expense that ticked this bill off, or null when it was ticked by hand.
    val paidTo: String? = null,
)

// One of this month's expenses: who was paid, and the full amount that left your account.
data class Payment(val payee: String, val amountPaise: Long)

// A bill counts as paid this month when it was ticked off by hand, or when one of this
// month's expenses went to a matching name for about the bill's amount (within 20%).
// The amount check stops a small payment to the same person from ticking off the rent.
fun billStatus(bill: BillEntity, today: LocalDate, paymentsThisMonth: List<Payment>): BillStatus {
    val month = YearMonth.from(today)
    val name = bill.name.trim()
    val match = paymentsThisMonth.firstOrNull { payment ->
        val sameName = name.length >= 3 && (
                payment.payee.contains(name, ignoreCase = true) ||
                        (payment.payee.length >= 3 && name.contains(payment.payee, ignoreCase = true))
                )
        val similarAmount = payment.amountPaise * 10 in bill.amountPaise * 8..bill.amountPaise * 12
        sameName && similarAmount
    }
    val paid = bill.paidMonth == month.toString() || match != null

    // A bill due on the 31st falls on the last day of a shorter month.
    fun dueIn(m: YearMonth) = m.atDay(minOf(bill.dayOfMonth, m.lengthOfMonth()))
    val dueDate = if (paid) dueIn(month.plusMonths(1)) else dueIn(month)
    return BillStatus(
        bill = bill,
        paid = paid,
        dueDate = dueDate,
        overdue = !paid && dueDate.isBefore(today),
        paidTo = match?.payee,
    )
}