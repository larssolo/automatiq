package com.vibeactions.util

private val DIGITS = Regex("\\d")

/** Accepts optional leading +, then 6–15 digits (spaces ignored). */
fun isValidPhone(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return false
    val body = if (trimmed.startsWith("+")) trimmed.substring(1) else trimmed
    val digitsOnly = body.replace(" ", "")
    if (digitsOnly.any { !it.isDigit() }) return false
    return digitsOnly.length in 6..15
}

/** Keeps a leading +<countrycode> chunk and the last two digits; masks the middle as "××" pairs. */
fun maskPhone(raw: String): String {
    val trimmed = raw.trim().replace(" ", "")
    val plus = trimmed.startsWith("+")
    val digits = trimmed.filter { it.isDigit() }
    if (digits.length <= 2) return digits
    val last2 = digits.takeLast(2)
    return if (plus && digits.length > 4) {
        val cc = digits.take(2)
        val middlePairs = (digits.length - 4 + 1) / 2
        "+$cc " + "×× ".repeat(middlePairs).trim() + " $last2"
    } else {
        val middlePairs = (digits.length - 2 + 1) / 2
        "×× ".repeat(middlePairs).trim() + " $last2"
    }
}
