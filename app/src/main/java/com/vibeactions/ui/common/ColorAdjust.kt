package com.vibeactions.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import kotlin.math.cos
import kotlin.math.sin

/**
 * A [ColorFilter] that rotates hue by [hueDegrees] (0 = unchanged) and scales saturation by
 * [saturation] (1 = unchanged). The hue-rotation matrix is the standard RGB-space rotation
 * used by CSS/SVG's `hue-rotate()` filter (rotation around the (1,1,1) luminance axis).
 */
@Composable
fun rememberHueSaturationColorFilter(hueDegrees: Float, saturation: Float): ColorFilter =
    remember(hueDegrees, saturation) {
        val radians = Math.toRadians(hueDegrees.toDouble()).toFloat()
        val cosA = cos(radians)
        val sinA = sin(radians)
        val hueMatrix = ColorMatrix(
            floatArrayOf(
                0.213f + cosA * 0.787f - sinA * 0.213f,
                0.715f - cosA * 0.715f - sinA * 0.715f,
                0.072f - cosA * 0.072f + sinA * 0.928f,
                0f, 0f,

                0.213f - cosA * 0.213f + sinA * 0.143f,
                0.715f + cosA * 0.285f + sinA * 0.140f,
                0.072f - cosA * 0.072f - sinA * 0.283f,
                0f, 0f,

                0.213f - cosA * 0.213f - sinA * 0.787f,
                0.715f - cosA * 0.715f + sinA * 0.715f,
                0.072f + cosA * 0.928f + sinA * 0.072f,
                0f, 0f,

                0f, 0f, 0f, 1f, 0f
            )
        )
        val saturationMatrix = ColorMatrix().apply { setToSaturation(saturation) }
        hueMatrix.timesAssign(saturationMatrix)
        ColorFilter.colorMatrix(hueMatrix)
    }
