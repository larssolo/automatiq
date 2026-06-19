package com.vibeactions.util

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class GeminiClientTest {

    @Test fun `parseGeminiResponse extracts text from valid response`() {
        val json = """
            {"candidates":[{"content":{"parts":[{"text":"Hello world"}],"role":"model"}}]}
        """.trimIndent()
        assertEquals("Hello world", parseGeminiResponse(json))
    }

    @Test fun `parseGeminiResponse throws on empty candidates`() {
        val json = """{"candidates":[]}"""
        assertFailsWith<IllegalStateException> { parseGeminiResponse(json) }
    }

    @Test fun `parseGeminiResponse throws on empty parts`() {
        val json = """{"candidates":[{"content":{"parts":[],"role":"model"}}]}"""
        assertFailsWith<IllegalStateException> { parseGeminiResponse(json) }
    }
}
