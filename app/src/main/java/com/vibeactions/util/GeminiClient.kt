package com.vibeactions.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/** Default model. Configurable in Settings because free-tier availability varies by model/region. */
const val DEFAULT_GEMINI_MODEL = "gemini-3.6-flash"

/** Where users create a Gemini API key. */
const val GEMINI_API_KEY_URL = "https://aistudio.google.com/apikey"

/** Models offered in the Settings picker — all normally free-tier eligible (some may show quota 0
 *  depending on the project/region, so the user can switch to one that works for their key). */
val GEMINI_MODELS = listOf(
    "gemini-3.6-flash",
    "gemini-3.8-flash",
    "gemini-3.5-flash-lite",
    "gemini-3.1-flash-lite"
)

/** Earlier picker entries: retired or access-limited by Google, so a saved one is upgraded. */
private val LEGACY_GEMINI_MODELS = setOf(
    "gemini-2.5-flash",
    "gemini-2.5-flash-lite",
    "gemini-2.0-flash",
    "gemini-2.0-flash-lite",
    "gemini-1.5-flash"
)

/** The model to call for a saved setting: blank or legacy → default; a custom model is kept. */
fun resolveGeminiModel(saved: String?): String {
    val m = saved?.trim().orEmpty()
    return if (m.isEmpty() || m in LEGACY_GEMINI_MODELS) DEFAULT_GEMINI_MODEL else m
}

private fun endpointFor(model: String) =
    "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

@Serializable
internal data class GeminiRequest(
    val systemInstruction: GeminiContent? = null,
    val contents: List<GeminiContent>,
    val generationConfig: GenConfig = GenConfig()
)

@Serializable internal data class GeminiContent(val parts: List<GeminiPart>, val role: String? = null)
@Serializable internal data class GeminiPart(val text: String)
@Serializable internal data class ThinkingConfig(
    val thinkingBudget: Int? = null,
    val thinkingLevel: String? = null
)
@Serializable internal data class GenConfig(
    val maxOutputTokens: Int = 400,
    val thinkingConfig: ThinkingConfig? = null
)

/** Thinking models reason before writing the reply, and that reasoning shares maxOutputTokens
 *  with the visible text — on a short SMS budget it can eat the whole cap and leave the reply
 *  truncated. 2.5 takes a budget (0 = off); 3.x only takes a level, and "low" is the lowest one
 *  every 3.x Flash accepts (3.8 rejects "minimal"). Older models reject thinkingConfig entirely. */
internal fun thinkingConfigFor(model: String): ThinkingConfig? = when {
    model.startsWith("gemini-3") -> ThinkingConfig(thinkingLevel = "low")
    model.startsWith("gemini-2.5") -> ThinkingConfig(thinkingBudget = 0)
    else -> null
}

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
 * Calls the given Gemini model and returns the generated text.
 * Throws on network error or non-2xx HTTP status — callers should wrap in runCatching.
 */
suspend fun geminiGenerate(
    apiKey: String,
    systemPrompt: String,
    userMessage: String,
    model: String = DEFAULT_GEMINI_MODEL,
    maxOutputTokens: Int = 400
): String =
    withContext(Dispatchers.IO) {
        val request = GeminiRequest(
            systemInstruction = if (systemPrompt.isNotBlank())
                GeminiContent(parts = listOf(GeminiPart(systemPrompt))) else null,
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(userMessage)))),
            generationConfig = GenConfig(
                maxOutputTokens = maxOutputTokens,
                thinkingConfig = thinkingConfigFor(model)
            )
        )
        val body = geminiJson.encodeToString(GeminiRequest.serializer(), request)
        // Key travels as a header, not a query param — URLs end up in logs and proxies.
        val conn = URL(endpointFor(model)).openConnection() as HttpURLConnection
        // The finally must also cover the write phase: a connect/write failure would otherwise
        // leak the connection.
        try {
            conn.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                // The real reason (e.g. "API key not valid") is in the error stream, not inputStream.
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("HTTP $code: ${extractGeminiError(errorText).ifBlank { "unknown error" }}")
            }
            parseGeminiResponse(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

/**
 * Asks Gemini to generate exactly 3 short SMS variations and returns them as a list.
 * A fixed built-in system prompt enforces the numbered format; [styleHint] is appended
 * to guide tone (e.g. "short and casual").
 */
suspend fun geminiSuggest(
    apiKey: String,
    userMessage: String,
    styleHint: String = "",
    model: String = DEFAULT_GEMINI_MODEL
): List<String> {
    val style = if (styleHint.isNotBlank()) " Style/tone: $styleHint." else ""
    val systemPrompt = "You are an SMS assistant. The user describes a message they want to send. " +
        "Generate EXACTLY 3 short SMS variations in English (unless another language is specified).$style " +
        "Reply ONLY in this format — no introduction, no explanation:\n" +
        "1. [message]\n2. [message]\n3. [message]"
    val raw = geminiGenerate(apiKey, systemPrompt, userMessage, model)
    return parseSuggestions(raw)
}

/** Splits a "1. …\n2. …\n3. …" response into individual strings. Falls back to a single-item list. */
internal fun parseSuggestions(raw: String): List<String> {
    val pattern = Regex("""^[1-9]\.\s*(.+)""")
    val items = raw.lines().mapNotNull { line ->
        pattern.find(line.trim())?.groupValues?.get(1)?.trim()
    }
    return items.ifEmpty { listOf(raw.trim()) }
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
