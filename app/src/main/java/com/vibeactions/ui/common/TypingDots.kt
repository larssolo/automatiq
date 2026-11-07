package com.vibeactions.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vibeactions.ui.theme.FoxGreen
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The fox's three dots hopping like a "typing" bubble: TextFox's sign that AI is at work.
 *  [dot] is one dot's diameter; the whole row is 4.4 dots wide and 2 dots tall. */
@Composable
fun TypingDots(modifier: Modifier = Modifier, dot: Dp = 6.dp, color: Color = FoxGreen) {
    val phase by rememberInfiniteTransition(label = "typing").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "typingPhase"
    )
    Canvas(modifier.size(dot * 4.4f, dot * 2f)) {
        val r = dot.toPx() / 2f
        repeat(3) { i ->
            // Each dot hops during its own third-ish of the cycle, then rests.
            val t = (phase * 1.6f - i * 0.2f).coerceIn(0f, 1f)
            val lift = abs(sin(t * PI).toFloat()) * r
            drawCircle(color, r, Offset(r + i * r * 3.4f, size.height - r - lift))
        }
    }
}
