package com.vibeactions.util

import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingMatchTest {
    private fun incoming(sender: String? = null, keyword: String? = null) = Macro(
        id = "m", name = "Reply", triggerType = TriggerType.INCOMING, scheduledTime = null,
        recipients = emptyList(), messageBody = "På vej!", matchSender = sender, matchKeyword = keyword
    )

    @Test fun noFilters_matchesAnything() {
        assertTrue(incomingMatches(incoming(), "+4512345678", "hej"))
    }

    @Test fun senderFilter_ignoresFormattingAndCountryCode() {
        val m = incoming(sender = "12345678")
        assertTrue(incomingMatches(m, "+45 12 34 56 78", "noget"))
        assertTrue(incomingMatches(m, "4512345678", "noget"))
        assertFalse(incomingMatches(m, "+4587654321", "noget"))
    }

    @Test fun keywordFilter_isCaseInsensitiveSubstring() {
        val m = incoming(keyword = "hvornår")
        assertTrue(incomingMatches(m, "+4500000000", "Hej, HVORNÅR kommer du?"))
        assertFalse(incomingMatches(m, "+4500000000", "Hej der"))
    }

    @Test fun bothFilters_mustBothPass() {
        val m = incoming(sender = "12345678", keyword = "ok")
        assertTrue(incomingMatches(m, "12345678", "det er OK"))
        assertFalse(incomingMatches(m, "12345678", "nej"))
        assertFalse(incomingMatches(m, "99999999", "det er OK"))
    }
}
