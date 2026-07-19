package com.vibeactions.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallStateTest {

    @Test fun ringThenIdle_isMissedAndResets() {
        val ring = advanceCallState(CallWatchState(), "RINGING", "+4512345678")
        assertNull(ring.missedNumber)
        val idle = advanceCallState(ring.state, "IDLE", null)
        assertEquals("+4512345678", idle.missedNumber)
        assertEquals(CallWatchState(), idle.state)
    }

    @Test fun answeredCall_isNotMissed() {
        val ring = advanceCallState(CallWatchState(), "RINGING", "+4512345678")
        val offhook = advanceCallState(ring.state, "OFFHOOK", null)
        assertNull(advanceCallState(offhook.state, "IDLE", null).missedNumber)
    }

    @Test fun secondRingBroadcastWithoutNumber_keepsKnownNumber() {
        // The same ring often arrives twice, once with and once without the number extra.
        val first = advanceCallState(CallWatchState(), "RINGING", "+4512345678")
        val second = advanceCallState(first.state, "RINGING", null)
        assertEquals("+4512345678", advanceCallState(second.state, "IDLE", null).missedNumber)
    }

    @Test fun outgoingCall_isNotMissed() {
        val offhook = advanceCallState(CallWatchState(), "OFFHOOK", null)
        assertNull(advanceCallState(offhook.state, "IDLE", null).missedNumber)
    }

    @Test fun unknownNumber_yieldsNoTarget() {
        val ring = advanceCallState(CallWatchState(), "RINGING", null)
        assertNull(advanceCallState(ring.state, "IDLE", null).missedNumber)
    }

    @Test fun secondRingBroadcastWithBlankNumber_keepsKnownNumber() {
        // Many OEMs send an empty string (not null) on the duplicate ring broadcast; it must not
        // wipe the real number captured first, or the missed-call reply is silently dropped.
        val first = advanceCallState(CallWatchState(), "RINGING", "+4512345678")
        val second = advanceCallState(first.state, "RINGING", "")
        assertEquals("+4512345678", advanceCallState(second.state, "IDLE", null).missedNumber)
    }

    @Test fun staleRing_doesNotFireOnLaterUnrelatedIdle() {
        // A ring whose own IDLE was never delivered must not fire a false missed-call when an
        // unrelated IDLE arrives much later.
        val ring = advanceCallState(CallWatchState(), "RINGING", "+4512345678", now = 1_000L)
        val lateIdle = advanceCallState(ring.state, "IDLE", null, now = 1_000L + CALL_STALE_MS + 1)
        assertNull(lateIdle.missedNumber)
    }

    @Test fun freshRing_withTimestamp_stillFires() {
        val ring = advanceCallState(CallWatchState(), "RINGING", "+4512345678", now = 1_000L)
        val idle = advanceCallState(ring.state, "IDLE", null, now = 1_000L + 5_000L)
        assertEquals("+4512345678", idle.missedNumber)
    }
}
