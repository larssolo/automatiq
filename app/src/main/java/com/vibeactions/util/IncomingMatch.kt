package com.vibeactions.util

import com.vibeactions.domain.model.Macro

private fun digits(s: String): String = s.filter { it.isDigit() }

/**
 * Two numbers match if their digit suffixes line up, so "+45 12 34 56 78", "4512345678" and
 * "12345678" are all treated as the same contact regardless of country-code / spacing.
 */
fun senderMatches(filter: String, incoming: String): Boolean {
    val a = digits(filter)
    val b = digits(incoming)
    if (a.isEmpty() || b.isEmpty()) return false
    val len = minOf(a.length, b.length).coerceAtMost(8)
    if (len < 6) return a == b
    return a.takeLast(len) == b.takeLast(len)
}

/**
 * Whether an INCOMING (auto-reply) macro should respond to [body] from [sender].
 * A blank sender or keyword filter is a wildcard; both present filters must pass (AND).
 */
fun incomingMatches(macro: Macro, sender: String, body: String): Boolean {
    val senderOk = macro.matchSender.isNullOrBlank() || senderMatches(macro.matchSender, sender)
    val keywordOk = macro.matchKeyword.isNullOrBlank() ||
        body.contains(macro.matchKeyword.trim(), ignoreCase = true)
    return senderOk && keywordOk
}
