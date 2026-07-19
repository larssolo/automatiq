package com.vibeactions.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vibeactions.ui.theme.Outline

/** Asymmetric "leaf-cut" corners — the app's organic signature shape (cards, folder cards). */
val LeafShape = RoundedCornerShape(
    topStart = 18.dp, topEnd = 6.dp, bottomEnd = 18.dp, bottomStart = 6.dp
)

/** The "vein": a living accent edge that breathes while the card's subject is active/armed;
 *  rests as a static outline otherwise. */
@Composable
fun breathingVeinColor(accent: Color, alive: Boolean): Color {
    if (!alive) return Outline
    val breath = rememberInfiniteTransition(label = "vein")
    val a by breath.animateFloat(
        0.45f, 1f,
        infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "veinAlpha"
    )
    return accent.copy(alpha = a)
}
