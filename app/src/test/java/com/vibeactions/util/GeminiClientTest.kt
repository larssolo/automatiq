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

    @Test fun `parseSuggestions extracts three numbered items`() {
        val raw = "1. Hej, husk mødet kl. 14!\n2. Reminder: møde kl. 14 i dag.\n3. Møde kl. 14 – på lørdag?"
        val result = parseSuggestions(raw)
        assertEquals(3, result.size)
        assertEquals("Hej, husk mødet kl. 14!", result[0])
        assertEquals("Reminder: møde kl. 14 i dag.", result[1])
        assertEquals("Møde kl. 14 – på lørdag?", result[2])
    }

    @Test fun `parseSuggestions handles leading whitespace on lines`() {
        val raw = "  1. Første forslag\n  2. Andet forslag\n  3. Tredje forslag"
        val result = parseSuggestions(raw)
        assertEquals(3, result.size)
        assertEquals("Første forslag", result[0])
    }

    @Test fun `parseSuggestions falls back to full text when format is unexpected`() {
        val raw = "Her er en besked til dig."
        val result = parseSuggestions(raw)
        assertEquals(1, result.size)
        assertEquals("Her er en besked til dig.", result[0])
    }

    @Test fun `resolveGeminiModel upgrades blank and legacy models to the default`() {
        assertEquals(DEFAULT_GEMINI_MODEL, resolveGeminiModel(null))
        assertEquals(DEFAULT_GEMINI_MODEL, resolveGeminiModel("  "))
        assertEquals(DEFAULT_GEMINI_MODEL, resolveGeminiModel("gemini-2.5-flash"))
        assertEquals(DEFAULT_GEMINI_MODEL, resolveGeminiModel("gemini-1.5-flash"))
    }

    @Test fun `resolveGeminiModel keeps current and custom models`() {
        assertEquals("gemini-3.5-flash-lite", resolveGeminiModel("gemini-3.5-flash-lite"))
        assertEquals("gemini-4-argon", resolveGeminiModel(" gemini-4-argon "))
    }

    @Test fun `thinking is off where the model allows it, low plus headroom elsewhere`() {
        assertEquals(ThinkingConfig(thinkingLevel = "minimal"), thinkingConfigFor(DEFAULT_GEMINI_MODEL))
        assertEquals(0, thinkingHeadroomFor(DEFAULT_GEMINI_MODEL))
        assertEquals(ThinkingConfig(thinkingLevel = "minimal"), thinkingConfigFor("gemini-3.5-flash-lite"))
        assertEquals(ThinkingConfig(thinkingLevel = "low"), thinkingConfigFor("gemini-3.8-flash"))
        assertEquals(1024, thinkingHeadroomFor("gemini-3.8-flash"))
        assertEquals(ThinkingConfig(thinkingBudget = 0), thinkingConfigFor("gemini-2.5-flash"))
        assertEquals(null, thinkingConfigFor("gemini-2.0-flash"))
    }

    @Test fun `every model in the picker gets a thinking config`() {
        GEMINI_MODELS.forEach { assert(thinkingConfigFor(it) != null) { it } }
    }

    @Test fun `request JSON for 3x carries only thinkingLevel`() {
        val req = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart("hi")))),
            generationConfig = GenConfig(thinkingConfig = thinkingConfigFor("gemini-3.6-flash"))
        )
        val json = kotlinx.serialization.json.Json.encodeToString(GeminiRequest.serializer(), req)
        assert("\"thinkingConfig\":{\"thinkingLevel\":\"minimal\"}" in json) { json }
    }

    @Test fun `parseSuggestions ignores preamble lines before numbered items`() {
        val raw = "Her er tre forslag:\n1. SMS en\n2. SMS to\n3. SMS tre"
        val result = parseSuggestions(raw)
        assertEquals(3, result.size)
        assertEquals("SMS en", result[0])
    }
}
