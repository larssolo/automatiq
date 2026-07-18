package com.vibeactions.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.vibeactions.R
import com.vibeactions.ui.theme.Amber
import com.vibeactions.ui.theme.Primary
import com.vibeactions.util.combineColorMatrices
import com.vibeactions.util.hueRotationColorMatrix
import com.vibeactions.util.saturationColorMatrix

/** The brand's robot-teal, used only as ambient light here. */
private val Teal = Color(0xFF42D1CA)

/**
 * Full-bleed static background image with a hue/saturation filter driven by [BackgroundSetting],
 * plus a slow aurora of drifting accent glows layered on top — the app's ambient pulse.
 */
@Composable
fun StaticBackground(modifier: Modifier = Modifier) {
    val hue = BackgroundSetting.hue
    val saturation = BackgroundSetting.saturation
    val colorFilter = remember(hue, saturation) {
        ColorFilter.colorMatrix(
            ColorMatrix(combineColorMatrices(saturationColorMatrix(saturation), hueRotationColorMatrix(hue)))
        )
    }
    Image(
        painter = painterResource(R.drawable.bg_static),
        contentDescription = null,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        colorFilter = colorFilter
    )
    AuroraOverlay(Modifier.fillMaxSize())
}

/**
 * Three radial glows drifting on minute-scale cycles. Alphas stay single-digit so the user's own
 * hue/saturation-tuned image remains the subject — this only adds depth and life. Compose pauses
 * infinite transitions while the UI isn't visible, so there is no background battery cost.
 */
@Composable
private fun AuroraOverlay(modifier: Modifier = Modifier) {
    val drift = rememberInfiniteTransition(label = "aurora")
    val t1 by drift.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(52_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "auroraT1"
    )
    val t2 by drift.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(73_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "auroraT2"
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun glow(color: Color, alpha: Float, center: Offset, radius: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = center, radius = radius
                ),
                radius = radius, center = center
            )
        }
        glow(Primary, 0.09f, Offset(w * (0.12f + 0.22f * t1), h * (0.08f + 0.14f * t2)), size.minDimension * 0.72f)
        glow(Teal, 0.07f, Offset(w * (0.92f - 0.18f * t2), h * (0.80f - 0.12f * t1)), size.minDimension * 0.80f)
        glow(Amber, 0.04f, Offset(w * (0.10f + 0.10f * t2), h * (0.70f + 0.10f * t1)), size.minDimension * 0.45f)
    }
}
