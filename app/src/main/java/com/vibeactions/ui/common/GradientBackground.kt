package com.vibeactions.ui.common

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box

/**
 * A living gradient background, recreated natively in Compose (the real shadergradient is a
 * Three.js web library and can't run inside a native app). Each preset is a dark base gradient
 * with two or three slowly drifting colour "blobs" for an animated, flowing look.
 */
enum class GradientPreset(
    val key: String,
    val label: String,
    val base: List<Color>,
    val accent1: Color,
    val accent2: Color
) {
    NightyNight(
        "nighty_night", "Nighty Night",
        base = listOf(Color(0xFF0B1026), Color(0xFF11183A), Color(0xFF0A1F2E)),
        accent1 = Color(0xFF2A6F8F), accent2 = Color(0xFF5B3FA0)
    ),
    Sunset(
        "sunset", "Sunset",
        base = listOf(Color(0xFF2B1055), Color(0xFF50204F), Color(0xFF160A28)),
        accent1 = Color(0xFFE94560), accent2 = Color(0xFFF39C12)
    ),
    Mint(
        "mint", "Mint",
        base = listOf(Color(0xFF0F2027), Color(0xFF15323A), Color(0xFF0A1A1F)),
        accent1 = Color(0xFF2EC4B6), accent2 = Color(0xFF43E97B)
    ),
    Aurora(
        "aurora", "Aurora",
        base = listOf(Color(0xFF06081F), Color(0xFF0E1A3A), Color(0xFF071326)),
        accent1 = Color(0xFF1FA2A6), accent2 = Color(0xFF7B2FF7)
    ),
    Ember(
        "ember", "Ember",
        base = listOf(Color(0xFF1A0606), Color(0xFF3A1010), Color(0xFF12060A)),
        accent1 = Color(0xFFFF6B35), accent2 = Color(0xFFB21F1F)
    );

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
        val k = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        preset = GradientPreset.fromKey(k)
    }

    fun set(context: Context, p: GradientPreset) {
        preset = p
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, p.key).apply()
    }
}

@Composable
fun ShaderGradientBackground(preset: GradientPreset, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bg")
    val t1 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "t1"
    )
    val t2 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(23000, easing = LinearEasing), RepeatMode.Reverse),
        label = "t2"
    )
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Brush.verticalGradient(preset.base))
                val r = size.maxDimension
                val c1 = Offset(size.width * (0.15f + 0.55f * t1), size.height * (0.25f + 0.45f * t2))
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(preset.accent1.copy(alpha = 0.55f), Color.Transparent),
                        center = c1, radius = r * 0.75f
                    )
                )
                val c2 = Offset(size.width * (0.85f - 0.5f * t2), size.height * (0.8f - 0.5f * t1))
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(preset.accent2.copy(alpha = 0.5f), Color.Transparent),
                        center = c2, radius = r * 0.65f
                    )
                )
                val c3 = Offset(size.width * (0.5f + 0.3f * (t1 - t2)), size.height * (0.55f + 0.3f * t2))
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(preset.accent1.copy(alpha = 0.22f), Color.Transparent),
                        center = c3, radius = r * 0.5f
                    )
                )
            }
    )
}
