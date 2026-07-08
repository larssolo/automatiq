package com.vibeactions.ui.common

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide background/card appearance, observable by Compose and persisted to SharedPreferences.
 * The background is a static image (see [StaticBackground]); hue and saturation are applied to it
 * as a color-matrix filter, and card opacity controls how see-through macro cards are.
 */
object BackgroundSetting {
    private const val PREFS = "ui_settings"
    private const val KEY_HUE = "bg_hue"
    private const val KEY_SATURATION = "bg_saturation"
    private const val KEY_CARD_OPACITY = "card_opacity"

    private const val DEFAULT_HUE = 0f
    private const val DEFAULT_SATURATION = 1f
    private const val DEFAULT_CARD_OPACITY = 0.93f

    /** Degrees, 0..360. */
    var hue by mutableFloatStateOf(DEFAULT_HUE)
        private set

    /** 0 = grayscale, 1 = unchanged, up to 2 = oversaturated. */
    var saturation by mutableFloatStateOf(DEFAULT_SATURATION)
        private set

    /** 0 = fully transparent card background, 1 = fully opaque. */
    var cardOpacity by mutableFloatStateOf(DEFAULT_CARD_OPACITY)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        hue = prefs.getFloat(KEY_HUE, DEFAULT_HUE)
        saturation = prefs.getFloat(KEY_SATURATION, DEFAULT_SATURATION)
        cardOpacity = prefs.getFloat(KEY_CARD_OPACITY, DEFAULT_CARD_OPACITY)
    }

    fun setHue(context: Context, value: Float) {
        hue = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY_HUE, value).apply()
    }

    fun setSaturation(context: Context, value: Float) {
        saturation = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_SATURATION, value).apply()
    }

    fun setCardOpacity(context: Context, value: Float) {
        cardOpacity = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_CARD_OPACITY, value).apply()
    }
}
