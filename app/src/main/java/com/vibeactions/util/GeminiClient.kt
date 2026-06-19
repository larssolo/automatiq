package com.vibeactions.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

private const val GEMINI_ENDPOINT =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

@Serializable
internal data class GeminiRequest(
    val systemInstruction: GeminiContent? = null,
    val contents: List<GeminiContent>,
    val generationConfig: GenConfig = GenConfig()
)

@Serializable internal data class GeminiContent(val parts: List<GeminiPart>, val role: String? = null)
@Serializable internal data class GeminiPart(val text: String)
@Serializable internal data class GenConfig(val maxOutputTokens: Int = 160)

@Serializable
internal data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())

@Serializable internal data class GeminiCandidate(val content: GeminiContent)

private val geminiJson = Json { ignoreUnknownKeys = true }

/** Extracts the reply text from a raw Gemini JSON response string. Internal so it can be tested. */
internal fun parseGeminiResponse(responseJson: String): String {
    val response = geminiJson.decodeFromString(GeminiResponse.serializer(), responseJson)
    return response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
        ?: error("Empty Gemini response")
}

/**
 * Calls Gemini 2.0 Flash and returns the generated text.
 * Throws on network error or non-2xx HTTP status — callers should wrap in runCatching.
 */
suspend fun geminiGenerate(apiKey: String, systemPrompt: String, userMessage: String): String =
    withContext(Dispatchers.IO) {
        val request = GeminiRequest(
            systemInstruction = if (systemPrompt.isNotBlank())
                GeminiContent(parts = listOf(GeminiPart(systemPrompt))) else null,
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(userMessage))))
        )
        val body = geminiJson.encodeToString(GeminiRequest.serializer(), request)
        val conn = URL("$GEMINI_ENDPOINT?key=$apiKey").openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            outputStream.use { it.write(body.toByteArray()) }
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                // The real reason (e.g. "API key not valid") is in the error stream, not inputStream.
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("HTTP $code: ${extractGeminiError(errorText).ifBlank { "ukendt fejl" }}")
            }
            parseGeminiResponse(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

/** Pulls the human-readable message out of a Gemini error JSON body, falling back to the raw text. */
internal fun extractGeminiError(errorJson: String): String {
    if (errorJson.isBlank()) return ""
    return runCatching {
        geminiJson.decodeFromString(GeminiErrorEnvelope.serializer(), errorJson).error?.message
    }.getOrNull() ?: errorJson.take(200)
}

@Serializable internal data class GeminiErrorEnvelope(val error: GeminiErrorBody? = null)
@Serializable internal data class GeminiErrorBody(val message: String? = null, val status: String? = null)
