package com.vibeactions.util

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ColorMatrixMathTest {
    private val eps = 1e-4f

    @Test fun saturation1_isIdentity() {
        assertArrayEquals(IDENTITY_COLOR_MATRIX, saturationColorMatrix(1f), eps)
    }

    @Test fun hue0_isIdentity() {
        assertArrayEquals(IDENTITY_COLOR_MATRIX, hueRotationColorMatrix(0f), eps)
    }

    @Test fun hue360_isSameAsHue0() {
        // A full rotation must return to the starting matrix (periodicity).
        assertArrayEquals(hueRotationColorMatrix(0f), hueRotationColorMatrix(360f), eps)
    }

    @Test fun combineWithIdentityFirst_returnsSecondUnchanged() {
        val m = saturationColorMatrix(0.4f)
        assertArrayEquals(m, combineColorMatrices(IDENTITY_COLOR_MATRIX, m), eps)
    }

    @Test fun combineWithIdentitySecond_returnsFirstUnchanged() {
        val m = hueRotationColorMatrix(120f)
        assertArrayEquals(m, combineColorMatrices(m, IDENTITY_COLOR_MATRIX), eps)
    }

    @Test fun saturation0_rowsMatchLumaWeights() {
        // Fully desaturated: every output channel is the same Rec. 601 luma mix of R/G/B.
        val m = saturationColorMatrix(0f)
        val expectedRow = floatArrayOf(0.213f, 0.715f, 0.072f, 0f, 0f)
        assertArrayEquals(expectedRow, m.copyOfRange(0, 5), eps)
        assertArrayEquals(expectedRow, m.copyOfRange(5, 10), eps)
        assertArrayEquals(expectedRow, m.copyOfRange(10, 15), eps)
    }
}
