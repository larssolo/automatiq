package com.vibeactions.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Asymmetric "leaf-cut" corners — the app's organic signature shape (cards, folder cards). */
val LeafShape = RoundedCornerShape(
    topStart = 18.dp, topEnd = 6.dp, bottomEnd = 18.dp, bottomStart = 6.dp
)

/** The "vein": a living accent edge that breathes while the card's subject is active/armed.
 *  DESIGN RULE: every card always carries its own accent tone on this edge — full strength and
 *  breathing while active, faded (35%) but still tinted at rest. Never neutral gray: two idle
 *  cards must still be tellable apart by their edge color. Reads the shared [ambientPulse] instead
 *  of running its own InfiniteTransition per card — with many enabled macros, one per card meant
 *  that many concurrent frame-clock subscriptions redrawing every frame. */
@Composable
fun breathingVeinColor(accent: Color, alive: Boolean): Color {
    if (!alive) return accent.copy(alpha = 0.35f)
    return accent.copy(alpha = ambientPulse().cardBreath)
}
