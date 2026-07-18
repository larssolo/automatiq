package com.vibeactions.util

import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

private val DANISH = Locale("da", "DK")

/** Tokens the user can put in a message body; shown as a hint in the editor. */
val TEMPLATE_TOKENS = listOf("{dato}", "{tid}", "{ugedag}", "{navn}")

/** Extra token for reply macros (auto-reply / missed call): the other party's number. */
const val SENDER_TOKEN = "{afsender}"

/**
 * Expands template tokens in [body] using [now] and [macroName]:
 *   {dato}   -> dd-MM-yyyy
 *   {tid}    -> HH:mm
 *   {ugedag} -> Danish weekday (e.g. "mandag")
 *   {navn}   -> the macro's name
 *   {afsender} -> [sender] (the other party's number); left untouched when no sender applies
 *                 (scheduled/manual sends).
 * Unknown {tokens} are left untouched.
 */
fun expandTemplate(body: String, now: LocalDateTime, macroName: String, sender: String? = null): String {
    val date = "%02d-%02d-%04d".format(now.dayOfMonth, now.monthValue, now.year)
    val time = "%02d:%02d".format(now.hour, now.minute)
    val weekday = now.dayOfWeek.getDisplayName(TextStyle.FULL, DANISH)
    val expanded = body
        .replace("{dato}", date)
        .replace("{tid}", time)
        .replace("{ugedag}", weekday)
        .replace("{navn}", macroName)
    return if (sender != null) expanded.replace(SENDER_TOKEN, sender) else expanded
}
