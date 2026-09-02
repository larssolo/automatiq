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

    @Test fun expandsSenderTokenWhenSenderKnown() {
        assertEquals(
            "Saa ringer jeg til +4512345678",
            expandTemplate("Saa ringer jeg til {afsender}", now, "X", sender = "+4512345678")
        )
    }

    @Test fun leavesSenderTokenWithoutSender() {
        // Scheduled/manual sends have no other party; the token stays visible rather than
        // expanding to something misleading.
        assertEquals("hej {afsender}", expandTemplate("hej {afsender}", now, "X"))
    }

    @Test fun noTokensIsUnchanged() {
        assertEquals("bare tekst", expandTemplate("bare tekst", now, "X"))
    }

    @Test fun expandsRecipientTokenWhenNameKnown() {
        assertEquals(
            "Hej Peter, tid til gymnastik",
            expandTemplate("Hej {modtager}, tid til gymnastik", now, "Workout", recipientName = "Peter")
        )
    }

    @Test fun leavesRecipientTokenWithoutName() {
        // No recipient context (e.g. an AI-varied body) leaves the token untouched rather than
        // expanding to something misleading.
        assertEquals("Hej {modtager}", expandTemplate("Hej {modtager}", now, "X"))
    }

    @Test fun modtagerIsInTemplateTokenHints() {
        // The editor shows TEMPLATE_TOKENS as the available-variables hint, so the new token must
        // be discoverable there.
        assertEquals(true, TEMPLATE_TOKENS.contains("{modtager}"))
    }
}
