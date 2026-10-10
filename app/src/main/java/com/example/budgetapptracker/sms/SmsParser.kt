package com.example.budgetapptracker.sms

import com.example.budgetapptracker.util.rupeesToPaise

data class ParsedDebit(val amountPaise: Long, val merchant: String)

object SmsParser {
    private val debitWords =
        Regex("""\b(debited|spent|sent|paid|withdrawn|purchase)\b""", RegexOption.IGNORE_CASE)

    private val ignoreWords = Regex(
        """\b(otp|will be debited|due on|due by|requested|failed|declined|reversed|refund)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val amountWithCurrency =
        Regex("""(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

    private val amountAfterDebited =
        Regex("""debited (?:by|for|with)\s+([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

    private val merchantPatterns = listOf(
        Regex("""UPI/P2[MA]/\d+/([^./\n]{2,40})""", RegexOption.IGNORE_CASE),
        Regex(
            """\b(?:trf to|transfer to|paid to|to VPA|at|to)\s+([A-Za-z0-9][A-Za-z0-9 .&'@_-]{1,39}?)(?=\s+(?:on|ref|refno|upi|via|avl|bal)\b|[.,;]|$)""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""；?;\s*([A-Za-z0-9][A-Za-z0-9 .&'@_-]{1,39}?)\s+credited""", RegexOption.IGNORE_CASE),
    )

    fun parse(body: String): ParsedDebit? {
        if (!debitWords.containsMatchIn(body) || ignoreWords.containsMatchIn(body)) return null

        val amountText = (amountWithCurrency.find(body) ?: amountAfterDebited.find(body))
            ?.groupValues?.get(1)
            ?: return null
        val amountPaise = rupeesToPaise(amountText.replace(",", "")) ?: return null

        val merchant = merchantPatterns
            .firstNotNullOfOrNull { it.find(body)?.groupValues?.get(1)?.trim() }
            ?.takeIf { it.isNotBlank() }
            ?: "Unknown"

        return ParsedDebit(amountPaise, merchant.uppercase())
    }
}