package com.vibeactions.util

/**
 * Row-major 4x5 color matrices (RGBA rows, translation column), matching the layout
 * androidx.compose.ui.graphics.ColorMatrix expects. Kept as plain FloatArrays with no Compose
 * dependency so the math is testable on plain JVM.
 */
val IDENTITY_COLOR_MATRIX: FloatArray = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f
)

private const val LUM_R = 0.213f
private const val LUM_G = 0.715f
private const val LUM_B = 0.072f

/** 0 = grayscale (Rec. 601 luma weights), 1 = unchanged, >1 = oversaturated. */
fun saturationColorMatrix(saturation: Float): FloatArray {
    val invSat = 1 - saturation
    val sr = invSat * LUM_R
    val sg = invSat * LUM_G
    val sb = invSat * LUM_B
    return floatArrayOf(
        sr + saturation, sg, sb, 0f, 0f,
        sr, sg + saturation, sb, 0f, 0f,
        sr, sg, sb + saturation, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}

/** Luminance-preserving hue rotation, [degrees] clockwise around the color wheel. */
fun hueRotationColorMatrix(degrees: Float): FloatArray {
    val radians = degrees * kotlin.math.PI.toFloat() / 180f
    val c = kotlin.math.cos(radians)
    val s = kotlin.math.sin(radians)
    return floatArrayOf(
        LUM_R + c * (1 - LUM_R) + s * (-LUM_R), LUM_G + c * -LUM_G + s * -LUM_G, LUM_B + c * -LUM_B + s * (1 - LUM_B), 0f, 0f,
        LUM_R + c * -LUM_R + s * 0.143f, LUM_G + c * (1 - LUM_G) + s * 0.140f, LUM_B + c * -LUM_B + s * -0.283f, 0f, 0f,
        LUM_R + c * -LUM_R + s * -(1 - LUM_R), LUM_G + c * -LUM_G + s * LUM_G, LUM_B + c * (1 - LUM_B) + s * LUM_B, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}

/**
 * Composes two 4x5 affine color matrices so that applying the result to a color is equivalent to
 * applying [a] then [b] (matches androidx ColorMatrix.timesAssign's composition order).
 */
fun combineColorMatrices(a: FloatArray, b: FloatArray): FloatArray {
    val result = FloatArray(20)
    for (row in 0 until 4) {
        for (col in 0 until 5) {
            var sum = 0f
            for (k in 0 until 4) sum += b[row * 5 + k] * a[k * 5 + col]
            if (col == 4) sum += b[row * 5 + 4]
            result[row * 5 + col] = sum
        }
    }
    return result
}
