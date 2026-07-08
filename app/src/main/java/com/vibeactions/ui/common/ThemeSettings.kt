package com.vibeactions.ui.common

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.vibeactions.ui.theme.Primary

/**
 * App-wide adjustable theming, observable by Compose and persisted to SharedPreferences.
 *
 * Background: hue rotation in degrees (-180..180, 0 = unchanged) and saturation multiplier
 * (0..2, 1 = unchanged) applied as a colour filter over the static background image.
 *
 * Accent ("card colour"): absolute HSV hue (0..360) and saturation (0..1) picked directly,
 * with value fixed to [Primary]'s original brightness so only hue/saturation are adjustable.
 * Defaults match [Primary] exactly, so nothing changes until a slider is touched.
 */
object ThemeSettings {
    private const val PREFS = "theme_settings"
    private const val KEY_BG_HUE = "bg_hue"
    private const val KEY_BG_SAT = "bg_sat"
    private const val KEY_ACCENT_HUE = "accent_hue"
    private const val KEY_ACCENT_SAT = "accent_sat"

    private val defaultAccentHsv = FloatArray(3).also {
        android.graphics.Color.colorToHSV(Primary.toArgb(), it)
    }
    private val defaultAccentHue = defaultAccentHsv[0]
    private val defaultAccentSaturation = defaultAccentHsv[1]
    private val accentValue = defaultAccentHsv[2]

    var backgroundHue by mutableFloatStateOf(0f)
        private set
    var backgroundSaturation by mutableFloatStateOf(1f)
        private set
    var accentHue by mutableFloatStateOf(defaultAccentHue)
        private set
    var accentSaturation by mutableFloatStateOf(defaultAccentSaturation)
        private set

    /** The app's dynamic accent colour, derived from [accentHue]/[accentSaturation]. */
    val accentColor: Color
        get() = Color.hsv(accentHue, accentSaturation, accentValue)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        backgroundHue = prefs.getFloat(KEY_BG_HUE, 0f)
        backgroundSaturation = prefs.getFloat(KEY_BG_SAT, 1f)
        accentHue = prefs.getFloat(KEY_ACCENT_HUE, defaultAccentHue)
        accentSaturation = prefs.getFloat(KEY_ACCENT_SAT, defaultAccentSaturation)
    }

    fun setBackgroundHue(context: Context, value: Float) {
        backgroundHue = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY_BG_HUE, value).apply()
    }

    fun setBackgroundSaturation(context: Context, value: Float) {
        backgroundSaturation = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY_BG_SAT, value).apply()
    }

    fun setAccentHue(context: Context, value: Float) {
        accentHue = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY_ACCENT_HUE, value).apply()
    }

    fun setAccentSaturation(context: Context, value: Float) {
        accentSaturation = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY_ACCENT_SAT, value).apply()
    }
}
