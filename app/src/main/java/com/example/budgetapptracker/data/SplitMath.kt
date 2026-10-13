package com.example.budgetapptracker.data

import com.example.budgetapptracker.util.rupeesToPaise

// How an amount divides between you and the other people sharing it.
data class SplitPlan(val mySharePaise: Long, val otherSharePaise: Long)

// Equal shares in whole paise. When the amount does not divide evenly, the odd
// paise go to your share, so the others always owe a round, identical figure.
fun planEqualSplit(totalPaise: Long, otherPeople: Int): SplitPlan {
    val everyone = otherPeople + 1
    val base = totalPaise / everyone
    return SplitPlan(mySharePaise = base + totalPaise % everyone, otherSharePaise = base)
}

enum class SplitMode(val label: String) {
    Equal("Equally"),
    Amount("By amount"),
    Percent("By percent"),
}

// Works out how much each chosen person owes you. entries holds what was typed next to
// each person: rupees for Amount, a percentage for Percent, and nothing for Equal.
// Returns null while an entry is missing or the shares come to more than the bill.
fun planShares(
    totalPaise: Long,
    mode: SplitMode,
    chosenIds: List<String>,
    entries: Map<String, String>,
): Map<String, Long>? {
    if (chosenIds.isEmpty()) return null
    val shares = when (mode) {
        SplitMode.Equal -> {
            val each = planEqualSplit(totalPaise, otherPeople = chosenIds.size).otherSharePaise
            chosenIds.associateWith { each }
        }
        SplitMode.Amount -> chosenIds.associateWith {
            rupeesToPaise(entries[it].orEmpty()) ?: return null
        }
        SplitMode.Percent -> chosenIds.associateWith {
            val percent = entries[it].orEmpty().trim().toBigDecimalOrNull() ?: return null
            if (percent.signum() <= 0) return null
            // Round down to whole paise, so the shares can never add up to more than the bill.
            (totalPaise.toBigDecimal() * percent / 100.toBigDecimal()).toLong()
        }
    }
    return shares.takeIf { it.values.all { paise -> paise > 0 } && it.values.sum() <= totalPaise }
}
