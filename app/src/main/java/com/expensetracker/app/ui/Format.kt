package com.expensetracker.app.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val rupees = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 2
}

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")

/** "₹20,500" — Indian digit grouping, decimals only when needed. */
fun formatRupees(paise: Long): String = rupees.format(BigDecimal.valueOf(paise, 2))

/** "160" or "12.5" — the bare number, like in the note. */
fun formatAmount(paise: Long): String = BigDecimal.valueOf(paise, 2).stripTrailingZeros().toPlainString()

fun formatMonth(month: YearMonth): String = month.format(monthFormat)

/** Parses input like "45" or "12.50" into paise; null if it isn't a sensible positive amount. */
fun parseAmount(text: String): Long? {
    val value = text.trim().toBigDecimalOrNull() ?: return null
    if (value.signum() <= 0 || value > BigDecimal("10000000")) return null
    return value.setScale(2, RoundingMode.HALF_UP).unscaledValue().toLong().takeIf { it > 0 }
}
