package com.vibeactions.util

/** Accepts optional leading +, then 6–15 digits (spaces ignored). */
fun isValidPhone(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return false
    val body = if (trimmed.startsWith("+")) trimmed.substring(1) else trimmed
    val digitsOnly = body.replace(" ", "")
    if (digitsOnly.any { !it.isDigit() }) return false
    return digitsOnly.length in 6..15
}

/** Masks the first recipient and appends "+N" when there are more, e.g. "+45 ×× 78  +2". */
fun maskRecipients(recipients: List<String>): String {
    if (recipients.isEmpty()) return ""
    val first = maskPhone(recipients.first())
    val extra = recipients.size - 1
    return if (extra > 0) "$first  +$extra" else first
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
