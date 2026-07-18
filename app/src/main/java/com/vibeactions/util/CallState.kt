package com.vibeactions.util

/** Phone-call watcher state persisted between PHONE_STATE broadcasts — the process may not survive
 *  a long call, so this cannot live in memory. */
data class CallWatchState(
    val ringing: Boolean = false,
    val number: String? = null,
    val answered: Boolean = false
)

/** Result of advancing the state machine: the state to persist plus, when a ring just ended
 *  unanswered, the caller's number to auto-reply to (null when answered, outgoing, or unknown). */
data class CallAdvance(val state: CallWatchState, val missedNumber: String?)

/**
 * Advances the missed-call state machine with one TelephonyManager phone-state broadcast (the
 * EXTRA_STATE strings "RINGING"/"OFFHOOK"/"IDLE", kept as strings so this stays pure JVM).
 * The same ring is often broadcast twice — once with and once without the number — so a null
 * number never clears a known one. OFFHOOK marks the call answered (or outgoing). IDLE ends it:
 * a watched ring that never went OFFHOOK is a missed (or declined) call — declined deliberately
 * counts, since "declined in a meeting" is exactly when an auto-text is wanted.
 */
fun advanceCallState(state: CallWatchState, phoneState: String?, number: String?): CallAdvance =
    when (phoneState) {
        "RINGING" -> CallAdvance(
            CallWatchState(ringing = true, number = number ?: state.number, answered = false), null
        )
        "OFFHOOK" -> CallAdvance(state.copy(answered = true), null)
        "IDLE" -> {
            val missed = if (state.ringing && !state.answered) state.number else null
            CallAdvance(CallWatchState(), missed)
        }
        else -> CallAdvance(state, null)
    }
