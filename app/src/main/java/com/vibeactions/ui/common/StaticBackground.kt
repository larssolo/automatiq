package com.vibeactions.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.vibeactions.R
import com.vibeactions.util.combineColorMatrices
import com.vibeactions.util.hueRotationColorMatrix
import com.vibeactions.util.saturationColorMatrix

/**
 * Full-bleed static background image with a hue/saturation filter driven by [BackgroundSetting]
 * (replaces the previous live OpenGL shader gradient).
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
}
