package com.vibeactions.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue

/**
 * The app's "breathing" values — the aurora backdrop, every armed macro's card edge, and the
 * status dot all read from this single clock instead of each running their own InfiniteTransition.
 * Compose ties one frame-clock subscription to each InfiniteTransition instance; with a separate
 * one per card, N enabled macros meant N+2 concurrent subscriptions redrawing every frame while
 * the list was on screen. One shared transition still redraws every frame, but only once.
 */
data class AmbientPulse(
    val cardBreath: Float,
    val dotBreath: Float,
    val auroraT1: Float,
    val auroraT2: Float
)

private val defaultAmbientPulse = AmbientPulse(cardBreath = 1f, dotBreath = 1f, auroraT1 = 0f, auroraT2 = 0f)
private val LocalAmbientPulse = compositionLocalOf { defaultAmbientPulse }

@Composable
fun ProvideAmbientPulse(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val cardBreath by transition.animateFloat(
        0.45f, 1f,
        infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cardBreath"
    )
    val dotBreath by transition.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotBreath"
    )
    val auroraT1 by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(52_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "auroraT1"
    )
    val auroraT2 by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(73_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "auroraT2"
    )
    CompositionLocalProvider(
        LocalAmbientPulse provides AmbientPulse(cardBreath, dotBreath, auroraT1, auroraT2)
    ) {
        content()
    }
}

@Composable
fun ambientPulse(): AmbientPulse = LocalAmbientPulse.current
