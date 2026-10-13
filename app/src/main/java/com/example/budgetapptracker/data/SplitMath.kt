package com.example.budgetapptracker.data

// How an amount divides between you and the other people sharing it.
data class SplitPlan(val mySharePaise: Long, val otherSharePaise: Long)

// Equal shares in whole paise. When the amount does not divide evenly, the odd
// paise go to your share, so the others always owe a round, identical figure.
fun planEqualSplit(totalPaise: Long, otherPeople: Int): SplitPlan {
    val everyone = otherPeople + 1
    val base = totalPaise / everyone
    return SplitPlan(mySharePaise = base + totalPaise % everyone, otherSharePaise = base)
}