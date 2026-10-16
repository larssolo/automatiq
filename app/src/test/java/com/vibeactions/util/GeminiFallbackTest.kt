package com.vibeactions.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException
import kotlin.test.assertFailsWith

class GeminiFallbackTest {

    @Before fun reset() = GeminiCooldown.clear()

    @Test fun `chain starts with the chosen model and has no duplicates`() {
        assertEquals(
            listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite"),
            fallbackChain("gemini-3.8-flash")
        )
        assertEquals(GEMINI_FALLBACK_MODELS, fallbackChain("gemini-3.6-flash"))
    }

    @Test fun `quota, overload, missing model and timeouts are worth another model`() {
        assertTrue(isRetryableOnOtherModel(GeminiHttpException(429, "quota")))
        assertTrue(isRetryableOnOtherModel(GeminiHttpException(503, "high demand")))
        assertTrue(isRetryableOnOtherModel(GeminiHttpException(404, "not found")))
        assertTrue(isRetryableOnOtherModel(SocketTimeoutException()))
    }

    @Test fun `a bad key or request is not`() {
        assertFalse(isRetryableOnOtherModel(GeminiHttpException(400, "bad request")))
        assertFalse(isRetryableOnOtherModel(GeminiHttpException(401, "API key not valid")))
        assertFalse(isRetryableOnOtherModel(GeminiHttpException(403, "forbidden")))
        assertFalse(isRetryableOnOtherModel(java.net.UnknownHostException()))
    }

    @Test fun `falls back on quota and reports the model that answered`() = runBlocking {
        val tried = mutableListOf<String>()
        val result = generateWithFallback(listOf("a", "b", "c"), now = { 1_000L }) { m ->
            tried += m
            if (m == "a") throw GeminiHttpException(429, "quota") else "hi from $m"
        }
        assertEquals(GeminiResult("hi from b", "b"), result)
        assertEquals(listOf("a", "b"), tried)
    }

    @Test fun `stops at once on a bad key`() {
        val tried = mutableListOf<String>()
        assertFailsWith<GeminiHttpException> {
            runBlocking {
                generateWithFallback(listOf("a", "b")) { m -> tried += m; throw GeminiHttpException(401, "bad key") }
            }
        }
        assertEquals(listOf("a"), tried)
    }

    @Test fun `throws the last error when every model fails`() {
        val e = assertFailsWith<GeminiHttpException> {
            runBlocking {
                generateWithFallback(listOf("a", "b")) { m ->
                    throw GeminiHttpException(if (m == "a") 429 else 503, m)
                }
            }
        }
        assertEquals(503, e.code)
    }

    @Test fun `a model that just hit its quota is tried last next time`() = runBlocking {
        generateWithFallback(listOf("a", "b"), now = { 0L }) { m ->
            if (m == "a") throw GeminiHttpException(429, "quota") else "ok"
        }
        val tried = mutableListOf<String>()
        generateWithFallback(listOf("a", "b"), now = { 60_000L }) { m -> tried += m; "ok" }
        assertEquals(listOf("b"), tried)
        // After the cooldown it's first again.
        tried.clear()
        generateWithFallback(listOf("a", "b"), now = { 2 * 60 * 60_000L }) { m -> tried += m; "ok" }
        assertEquals(listOf("a"), tried)
    }
}
