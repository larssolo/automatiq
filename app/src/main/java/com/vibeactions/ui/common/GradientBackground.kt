package com.vibeactions.ui.common

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Background presets for the live OpenGL shader gradient. Each preset is three colours — a deep
 * base, a mid tone and a highlight — fed to the fragment shader as uniforms (see GradientShaders).
 */
enum class GradientPreset(
    val key: String,
    val label: String,
    val c1: Color,
    val c2: Color,
    val c3: Color
) {
    NightyNight("nighty_night", "Nighty Night", Color(0xFF0B1026), Color(0xFF2A6F8F), Color(0xFF5B3FA0)),
    Sunset("sunset", "Sunset", Color(0xFF160A28), Color(0xFFE94560), Color(0xFFF39C12)),
    Mint("mint", "Mint", Color(0xFF0A1A1F), Color(0xFF2EC4B6), Color(0xFF43E97B)),
    Aurora("aurora", "Aurora", Color(0xFF071326), Color(0xFF1FA2A6), Color(0xFF7B2FF7)),
    Ember("ember", "Ember", Color(0xFF12060A), Color(0xFFB21F1F), Color(0xFFFF6B35));

    /** Nine floats: three RGB triplets in 0..1 for the shader's colour uniforms. */
    val shaderColors: FloatArray
        get() = floatArrayOf(
            c1.red, c1.green, c1.blue,
            c2.red, c2.green, c2.blue,
            c3.red, c3.green, c3.blue
        )

    companion object {
        fun fromKey(k: String?): GradientPreset = entries.firstOrNull { it.key == k } ?: NightyNight
    }
}

/** App-wide selected background, observable by Compose and persisted to SharedPreferences. */
object BackgroundSetting {
    private const val PREFS = "ui_settings"
    private const val KEY = "bg_preset"

    var preset by mutableStateOf(GradientPreset.NightyNight)
        private set

    fun load(context: Context) {
        preset = GradientPreset.fromKey(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        )
    }

    fun set(context: Context, p: GradientPreset) {
        preset = p
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, p.key).apply()
    }
}
