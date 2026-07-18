package com.vibeactions.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateTriggerTest {
    @Test fun anyTarget_matchesConnectDirection() {
        assertTrue(stateTriggerMatches(macroTarget = null, macroOnConnect = true,
            eventTarget = "AA:BB", eventConnected = true))
        assertTrue(stateTriggerMatches(macroTarget = "", macroOnConnect = true,
            eventTarget = null, eventConnected = true))
    }

    @Test fun wrongDirection_doesNotMatch() {
        assertFalse(stateTriggerMatches(macroTarget = null, macroOnConnect = true,
            eventTarget = "AA:BB", eventConnected = false))
        assertFalse(stateTriggerMatches(macroTarget = null, macroOnConnect = false,
            eventTarget = "AA:BB", eventConnected = true))
    }

    @Test fun specificTarget_matchesCaseInsensitively() {
        assertTrue(stateTriggerMatches("aa:bb:cc:dd:ee:ff", true, "AA:BB:CC:DD:EE:FF", true))
    }

    @Test fun specificTarget_mismatchDoesNotFire() {
        assertFalse(stateTriggerMatches("AA:BB:CC:DD:EE:FF", true, "11:22:33:44:55:66", true))
    }

    @Test fun specificTarget_unknownEventTarget_doesNotFire() {
        // A Wi-Fi drop with no SSID: a macro pinned to a specific SSID must NOT fire.
        assertFalse(stateTriggerMatches("HomeWifi", false, null, false))
    }
}
