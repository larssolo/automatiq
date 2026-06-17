package com.vibeactions.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class MessageTemplateTest {
    // 2026-06-15 is a Monday.
    private val now = LocalDateTime.of(2026, 6, 15, 9, 5)

    @Test fun expandsAllTokens() {
        val out = expandTemplate("{navn}: i dag er {ugedag} den {dato} kl. {tid}", now, "Morgen")
        assertEquals("Morgen: i dag er mandag den 15-06-2026 kl. 09:05", out)
    }

    @Test fun leavesUnknownTokensUntouched() {
        assertEquals("hej {foo} verden", expandTemplate("hej {foo} verden", now, "X"))
    }

    @Test fun noTokensIsUnchanged() {
        assertEquals("bare tekst", expandTemplate("bare tekst", now, "X"))
    }
}
