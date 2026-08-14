package com.vibeactions.util

import com.vibeactions.domain.model.TriggerType

/**
 * True when a fire must hand off to [com.vibeactions.scheduler.AiVaryWorker] to generate an
 * AI-written variation of the macro's fixed message before sending.
 *
 * Applies to non-reply macros (scheduled/manual/state/location) with AI enabled, and only to
 * "own" fires: an override body means the text already exists (the worker's own callback, or an
 * approved draft), and an override recipient means a reply fire (auto-reply / missed call), which
 * has its own AI pipeline in [com.vibeactions.scheduler.GeminiReplyWorker].
 */
fun aiVariationApplies(
    triggerType: TriggerType,
    aiReplyEnabled: Boolean,
    hasOverrideBody: Boolean,
    hasOverrideRecipient: Boolean
): Boolean =
    aiReplyEnabled && !hasOverrideBody && !hasOverrideRecipient &&
        triggerType != TriggerType.INCOMING && triggerType != TriggerType.MISSED_CALL

/**
 * System prompt for the variation call. The fixed wrapper pins the contract (one message, same
 * meaning/language/length, nothing but the message text); the macro's optional [instruction]
 * steers tone/style on top. The user's own message travels separately as the user turn.
 */
fun buildVaryPrompt(instruction: String?): String = buildString {
    append("You rewrite SMS messages. The user sends you a message they send out regularly. ")
    append("Write ONE fresh variation of it: same meaning, same language, roughly the same ")
    append("length, natural and human — never a copy of the original. ")
    append("Reply ONLY with the message itself — no preamble, no explanation, no quotes. ")
    if (!instruction.isNullOrBlank()) append("Style guidance: $instruction")
}
