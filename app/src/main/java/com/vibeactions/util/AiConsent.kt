package com.vibeactions.util

import android.content.Context

/**
 * The user's explicit OK to send message text to Google's Gemini API (Play's User Data policy:
 * prominent disclosure + affirmative consent before personal data leaves the device). Background
 * AI work checks this and falls back to the fixed text without it. Not part of backups on purpose:
 * consent is given per device.
 */
object AiConsent {
    private const val PREFS = "ai_settings"
    private const val KEY = "ai_consent_v1"

    fun given(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun give(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
}
