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
}
