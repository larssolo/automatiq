package com.vibeactions.util

import kotlinx.coroutines.CancellationException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

/** Tried in this order after the user's chosen model. Each has its own free-tier quota, so when one
 *  is exhausted or overloaded another usually still answers on the same key. 3.8 is left out: it
 *  was slow and often overloaded. */
internal val GEMINI_FALLBACK_MODELS = listOf("gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite")

internal fun fallbackChain(preferred: String): List<String> =
    (listOf(preferred) + GEMINI_FALLBACK_MODELS).distinct()

/** Errors another model may not have: quota (429), overload/server trouble (5xx), a model that's
 *  gone (404) or a timeout. A bad key or request (400/401/403) fails the same on every model. */
internal fun isRetryableOnOtherModel(t: Throwable): Boolean = when (t) {
    is GeminiHttpException -> t.code == 404 || t.code == 429 || t.code >= 500
    is SocketTimeoutException -> true
    else -> false
}

/** How long to skip a model after it failed: quotas reset slowly, overload passes quickly. */
internal fun cooldownMillisFor(t: Throwable): Long = when {
    t is GeminiHttpException && t.code == 429 -> 60 * 60_000L
    t is GeminiHttpException && t.code == 404 -> 24 * 60 * 60_000L
    else -> 2 * 60_000L
}

/** Models that failed recently (process-wide), so the next request doesn't spend its time — and
 *  the user's quota — on them first. */
internal object GeminiCooldown {
    private val until = ConcurrentHashMap<String, Long>()
    fun isCooling(model: String, now: Long) = (until[model] ?: 0L) > now
    fun mark(model: String, untilMillis: Long) { until[model] = untilMillis }
    fun clear() = until.clear()
}

/** A generated text and the model that actually wrote it. */
data class GeminiResult(val text: String, val model: String)

/**
 * Calls [call] for each model in turn until one succeeds. Models cooling down after a recent
 * failure are tried last rather than skipped — a likely failure beats not trying at all.
 * Non-retryable errors (e.g. an invalid key) are thrown right away.
 */
/** Every model in the chain failed with a retryable error; [attempts] says how each one failed. */
class GeminiAllModelsFailedException(val attempts: List<Pair<String, Throwable>>) : Exception(
    "All Gemini models failed: " + attempts.joinToString { (m, t) -> "$m ${t.message?.take(60) ?: t.javaClass.simpleName}" }
)

/** A short, user-facing reason for why AI couldn't write the text (shown in the Log). */
fun aiFailureReason(t: Throwable): String = when (t) {
    is GeminiAllModelsFailedException ->
        if (t.attempts.all { (it.second as? GeminiHttpException)?.code == 429 }) "free daily quota used up on all models"
        else "all Gemini models busy or unavailable"
    is GeminiHttpException -> when (t.code) {
        400, 401, 403 -> "API key rejected (HTTP ${t.code})"
        429 -> "free quota used up"
        else -> "Gemini error (HTTP ${t.code})"
    }
    is SocketTimeoutException -> "Gemini didn't answer in time"
    is java.io.IOException -> "no internet connection"
    else -> t.javaClass.simpleName
}

internal suspend fun generateWithFallback(
    models: List<String>,
    now: () -> Long = System::currentTimeMillis,
    call: suspend (model: String) -> String
): GeminiResult {
    val (cooling, ready) = models.partition { GeminiCooldown.isCooling(it, now()) }
    val attempts = mutableListOf<Pair<String, Throwable>>()
    for (model in ready + cooling) {
        try {
            return GeminiResult(call(model), model)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            if (!isRetryableOnOtherModel(t)) throw t
            attempts += model to t
            GeminiCooldown.mark(model, now() + cooldownMillisFor(t))
        }
    }
    throw GeminiAllModelsFailedException(attempts)
}

/** [geminiGenerate] with automatic fallback to other models on quota, overload or timeouts. */
suspend fun geminiGenerateWithFallback(
    apiKey: String,
    systemPrompt: String,
    userMessage: String,
    model: String = DEFAULT_GEMINI_MODEL,
    maxOutputTokens: Int = 400
): GeminiResult = generateWithFallback(fallbackChain(model)) { m ->
    geminiGenerate(apiKey, systemPrompt, userMessage, m, maxOutputTokens)
}
