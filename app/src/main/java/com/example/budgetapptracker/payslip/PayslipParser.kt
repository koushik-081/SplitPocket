package com.example.budgetapptracker.payslip

import com.example.budgetapptracker.util.rupeesToPaise
import java.time.YearMonth

// What could be read from a payslip. Anything not found is null, and the form leaves it empty.
data class PayslipFields(
    val employer: String? = null,
    val month: YearMonth? = null,
    val grossPaise: Long? = null,
    val deductionsPaise: Long? = null,
    val netPaise: Long? = null,
    // Provident fund taken from your pay this month.
    val pfPaise: Long? = null,
    // Income tax (TDS) and professional tax together.
    val taxPaise: Long? = null,
    // Each tax line found, for showing "Professional tax ₹200 · TDS ₹5,000".
    val taxParts: List<Pair<String, Long>> = emptyList(),
) {
    val foundAnything: Boolean
        get() = employer != null || month != null || grossPaise != null || deductionsPaise != null

    // Gross minus deductions equals net (to within a rupee), so the three numbers agree.
    val addsUp: Boolean
        get() {
            val g = grossPaise ?: return false
            val d = deductionsPaise ?: return false
            val n = netPaise ?: return false
            return kotlin.math.abs(g - d - n) <= 100
        }
}

// Reads the usual Indian payslip labels from the payslip's text. Works on text from a PDF or
// from a photo. Layouts differ between companies, so this looks for many spellings of each
// label and always takes the first amount right after the label (the one after it is
// usually the year-to-date total).
object PayslipParser {

    // 1,45,835 (Indian grouping), 145,835, 145835, 145835.50. The last group after a comma is
    // always three digits, so a misread "71,18" is rejected rather than read as 71: nothing
    // may follow the amount except a non-digit, non-comma character.
    private const val AMOUNT = """(\d{1,3}(?:,\d{2,3})*,\d{3}(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)(?![\d,])"""

    private val grossLabels = listOf(
        "Gross Pay", "Gross Earnings", "Total Earnings", "Gross Salary", "Total Gross",
        "Gross Total", "Earnings Total", "Gross",
    )
    private val deductionLabels = listOf(
        "Gross Deductions", "Total Deductions", "Total Deduction", "Deductions Total", "Deduction Total",
    )
    private val netLabels = listOf(
        "Net Pay", "Net Salary", "Net Payable", "Net Amount Payable", "Take Home", "Take-Home",
        "Amount Payable", "Net Amount", "Total Pay",
    )
    private val pfLabels = listOf(
        "Employee Provident Fund", "Employer Provident Fund", "Provident Fund", "Employee PF",
        "PF Contribution", "EPF", "PF",
    )
    // Each group is one tax; the first spelling found in a group counts, so it is not added twice.
    private val taxGroups = listOf(
        "TDS" to listOf("Income Tax", "Tax Deducted at Source", "TDS"),
        "Professional tax" to listOf("Professional Tax", "Profession Tax", "Prof. Tax", "Prof Tax", "P. Tax", "P Tax"),
    )

    private val companySuffix = Regex(
        """\b(Limited|Ltd\.?|LLP|Pvt\.?|Private|Inc\.?|Corporation|Corp\.?|Technologies|Solutions|Services|Bank)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val slipTitle = Regex("""\b(pay\s*slip|salary\s*slip|pay\s*statement|salary\s*statement)\b""", RegexOption.IGNORE_CASE)
    // Tax-planning tables further down repeat words like "Gross Salary" with yearly totals.
    // Loose on purpose: text read from a photo may say "Compuțation" or "Computatlon".
    private val taxSheetStart = Regex(
        """income\s+tax\s+comp|tax\s+comp\S*tion|tax\s+calculation|tax\s+projection|form\s*16""",
        RegexOption.IGNORE_CASE,
    )
    // Photos often turn "3,000" into "3.000". Rupee amounts have two decimals at most, so a
    // dot followed by exactly three digits is a thousands comma.
    private val dotAsComma = Regex("""(?<=\d)\.(?=\d{3}(?!\d))""")

    private val months = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )
    private val monthName = Regex(
        """\b(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\b[\s,.'\-]*(\d{4}|'?\d{2})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val monthNumber = Regex("""\b(0?[1-9]|1[0-2])\s*[/\-]\s*(20\d{2})\b""")
    private val monthHint = Regex("""\b(month|period|pay\s*slip|salary\s*slip|for\s+the)\b""", RegexOption.IGNORE_CASE)
    private val notPayMonth = Regex("""\b(joining|birth|dob|doj|leaving|exit|issued|generated)\b""", RegexOption.IGNORE_CASE)

    fun parse(rawText: String): PayslipFields {
        val text = dotAsComma.replace(rawText, ",")
        val payPart = taxSheetStart.find(text)?.let { text.substring(0, it.range.first) } ?: text
        val lines = payPart.lines().map { it.trim() }.filter { it.isNotEmpty() }

        var gross = firstAmount(payPart, grossLabels)
        var deductions = firstAmount(payPart, deductionLabels)
        var net = firstAmount(payPart, netLabels)
        // Fill the one missing number from the other two.
        if (net == null && gross != null && deductions != null) net = gross - deductions
        if (deductions == null && gross != null && net != null && net <= gross) deductions = gross - net
        if (gross == null && deductions != null && net != null) gross = net + deductions

        val taxParts = taxGroups.mapNotNull { (name, labels) ->
            firstAmount(payPart, labels)?.takeIf { it > 0 }?.let { name to it }
        }

        return PayslipFields(
            employer = employer(lines),
            month = month(lines),
            grossPaise = gross,
            deductionsPaise = deductions,
            netPaise = net,
            pfPaise = firstAmount(payPart, pfLabels)?.takeIf { it > 0 },
            taxPaise = taxParts.sumOf { it.second }.takeIf { taxParts.isNotEmpty() },
            taxParts = taxParts,
        )
    }

    // The amount straight after the first label that has one. "Straight after" allows a
    // marker like "(A)" or "(A-B)", a colon, and Rs/INR/₹, but nothing else, so
    // "PF number : N.A  UAN 1023..." does not count as a PF amount.
    internal fun firstAmount(text: String, labels: List<String>): Long? {
        for (label in labels) {
            val pattern = Regex(
                """(?<![A-Za-z])""" + Regex.escape(label).replace(" ", """\E[ \t]+\Q""") +
                    // Only spaces between label and amount, never a line break: a label at the
                    // end of one line must not take the number on the next.
                    """(?![A-Za-z])[ \t]*(?:\([A-Za-z0-9+\- ]{1,8}\)[ \t]*)?(?:\+[ \t]*[A-Z][ \t]*)?[:\-]?[ \t]*(?:Rs\.?|INR|₹)?[ \t]*""" + AMOUNT,
                RegexOption.IGNORE_CASE,
            )
            val match = pattern.find(text) ?: continue
            return rupeesToPaise(match.groupValues[1].replace(",", "")) ?: continue
        }
        return null
    }

    private fun employer(lines: List<String>): String? {
        // Only the top of the payslip, above the "Salary Slip" title (or the first few lines).
        val titleAt = lines.indexOfFirst { slipTitle.containsMatchIn(it) }
        val head = lines.take(if (titleAt > 0) titleAt else 4)
        val line = head.firstOrNull { companySuffix.containsMatchIn(it) && !slipTitle.containsMatchIn(it) }
            ?: head.firstOrNull { l -> l.count { it.isLetter() } >= 3 && !slipTitle.containsMatchIn(l) && ':' !in l }
            ?: return null
        // "Acme Cloud Services Limited Plot No. 1..." -> keep up to the company suffix.
        val cut = companySuffix.findAll(line).lastOrNull()?.let { line.substring(0, it.range.last + 1) } ?: line
        return cut.trim().trimEnd(',', '.', '-').take(40).ifBlank { null }
    }

    private fun month(lines: List<String>): YearMonth? {
        val preferred = lines.filter { monthHint.containsMatchIn(it) && !notPayMonth.containsMatchIn(it) }
        val others = lines.filter { !notPayMonth.containsMatchIn(it) }
        for (line in preferred + others) {
            monthName.find(line)?.let { m ->
                val month = months[m.groupValues[1].take(3).lowercase()] ?: return@let
                val y = m.groupValues[2].trimStart('\'')
                val year = if (y.length == 2) 2000 + y.toInt() else y.toInt()
                if (year in 2000..2100) return YearMonth.of(year, month)
            }
            if (line in preferred) {
                monthNumber.find(line)?.let { m -> return YearMonth.of(m.groupValues[2].toInt(), m.groupValues[1].toInt()) }
            }
        }
        return null
    }
}
