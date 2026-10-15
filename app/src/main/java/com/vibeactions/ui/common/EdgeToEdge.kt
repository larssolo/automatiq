package com.vibeactions.ui.common

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/** Edge-to-edge is mandatory from targetSdk 35. The app is always dark, so system bar icons stay
 *  light regardless of the phone's own light/dark setting. */
fun ComponentActivity.enableDarkEdgeToEdge() = enableEdgeToEdge(
    statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
)
