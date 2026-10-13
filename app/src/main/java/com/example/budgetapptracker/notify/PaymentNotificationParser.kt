package com.example.budgetapptracker.notify

import com.example.budgetapptracker.sms.ParsedDebit
import com.example.budgetapptracker.util.rupeesToPaise

// The payment apps whose notifications are read. Every other app's are ignored.
val paymentAppPackages = setOf(
    "com.google.android.apps.nbu.paisa.user", // Google Pay
    "com.phonepe.app",
    "net.one97.paytm",
    "money.super.payments", // super.money
    "com.naviapp",
    "com.dreamplug.androidapp", // CRED
    "in.org.npci.upiapp", // BHIM
)

// Picks the amount and the payee out of a "you paid" notification.
object PaymentNotificationParser {
    private val paidWords =
        Regex("""\b(paid|sent|debited|payment of|payment to)\b""", RegexOption.IGNORE_CASE)

    // Money coming in, requests, failures and adverts are not expenses.
    private val ignoreWords = Regex(
        """\b(paid you|sent you|received|credited|request|requested|failed|declined|pending|""" +
                """cashback|reward|rewards|offer|refund|reminder|due|won|win|scratch)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val amount =
        Regex("""(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

    private val payee = Regex(
        """\bto\s+([A-Za-z0-9][A-Za-z0-9 .&'@_-]{1,39}?)(?=\s+(?:on|via|using|from|with|is|was|has|successfully)\b|[.,;!]|$)""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(title: String, text: String): ParsedDebit? {
        val message = "$title. $text"
        if (!paidWords.containsMatchIn(message) || ignoreWords.containsMatchIn(message)) return null

        val amountText = amount.find(message)?.groupValues?.get(1) ?: return null
        val amountPaise = rupeesToPaise(amountText.replace(",", "")) ?: return null
        if (amountPaise <= 0) return null

        val name = payee.find(message)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Unknown"
        return ParsedDebit(amountPaise, name.uppercase())
    }
}