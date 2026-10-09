package com.example.budgetapptracker.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

fun rupeesToPaise(input: String): Long? =
    input.trim().toBigDecimalOrNull()
        ?.takeIf { it.signum() > 0 }
        ?.movePointRight(2)
        ?.setScale(0, RoundingMode.HALF_UP)
        ?.toLong()

fun Long.toRupeeString(): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
        .format(BigDecimal.valueOf(this, 2))