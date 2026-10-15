package com.vibeactions.util

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiReportTest {

    @Test fun `report is trimmed to the table limits`() {
        val r = buildAiReport(
            AiReportReason.SPAM, AiReportSource.COMPOSE, "x".repeat(5000), "n".repeat(3000),
            "m".repeat(100), "v".repeat(50)
        )
        assertEquals(2000, r.aiText.length)
        assertEquals(1000, r.note!!.length)
        assertEquals(64, r.model!!.length)
        assertEquals(32, r.appVersion!!.length)
    }

    @Test fun `blank note is dropped and ids match the table's allowed values`() {
        val r = buildAiReport(AiReportReason.OFFENSIVE, AiReportSource.AUTO_REPLY, " hi ", "   ", null, null)
        assertNull(r.note)
        assertEquals("hi", r.aiText)
        assertEquals("offensive", r.reason)
        assertEquals("auto_reply", r.source)
    }

    @Test fun `JSON uses the column names and omits empty optionals`() {
        val r = buildAiReport(AiReportReason.OTHER, AiReportSource.PREVIEW, "t", null, null, "1.3")
        val json = Json.encodeToString(AiReportBody.serializer(), r)
        assertTrue(json, "\"ai_text\":\"t\"" in json)
        assertTrue(json, "\"app_version\":\"1.3\"" in json)
        assertFalse(json, "note" in json)
    }
}
