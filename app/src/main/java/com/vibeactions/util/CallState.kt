package com.vibeactions.util

/** Phone-call watcher state persisted between PHONE_STATE broadcasts — the process may not survive
 *  a long call, so this cannot live in memory. [ringStartedAt] guards against a stale ring whose
 *  IDLE was never delivered (0 = unknown/legacy, treated as fresh). */
data class CallWatchState(
    val ringing: Boolean = false,
    val number: String? = null,
    val answered: Boolean = false,
    val ringStartedAt: Long = 0L
)

/** Result of advancing the state machine: the state to persist plus, when a ring just ended
 *  unanswered, the caller's number to auto-reply to (null when answered, outgoing, or unknown). */
data class CallAdvance(val state: CallWatchState, val missedNumber: String?)

/** A watched ring older than this when IDLE finally arrives is treated as stale (its own IDLE was
 *  dropped), so an unrelated later call can't fire a false missed-call reply to the old number. */
const val CALL_STALE_MS = 10 * 60_000L

/**
 * Advances the missed-call state machine with one TelephonyManager phone-state broadcast (the
 * EXTRA_STATE strings "RINGING"/"OFFHOOK"/"IDLE", kept as strings so this stays pure JVM).
 * The same ring is often broadcast twice — once with and once without the number — so a null OR
 * BLANK number never clears a known one (some OEMs send "" on the duplicate). OFFHOOK marks the
 * call answered (or outgoing). IDLE ends it: a watched, still-fresh ring that never went OFFHOOK is
 * a missed (or declined) call — declined deliberately counts, since "declined in a meeting" is
 * exactly when an auto-text is wanted. [now] stamps the ring start for the staleness guard.
 */
fun advanceCallState(
    state: CallWatchState,
    phoneState: String?,
    number: String?,
    now: Long = 0L
): CallAdvance =
    when (phoneState) {
        "RINGING" -> CallAdvance(
            CallWatchState(
                ringing = true,
                // "" is non-null but useless — keep the number captured from the first broadcast.
                number = number?.takeIf { it.isNotBlank() } ?: state.number,
                answered = false,
                ringStartedAt = if (state.ringing) state.ringStartedAt else now
            ),
            null
        )
        "OFFHOOK" -> CallAdvance(state.copy(answered = true), null)
        "IDLE" -> {
            val fresh = state.ringStartedAt == 0L || now == 0L ||
                now - state.ringStartedAt <= CALL_STALE_MS
            val missed = if (state.ringing && !state.answered && fresh) state.number else null
            CallAdvance(CallWatchState(), missed)
        }
        else -> CallAdvance(state, null)
    }
