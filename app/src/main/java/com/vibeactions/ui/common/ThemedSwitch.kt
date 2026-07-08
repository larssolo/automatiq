package com.vibeactions.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * An iOS-style capsule toggle: a dark pill that animates to [ThemeSettings.accentColor] with a
 * white knob sliding left -> right. Ported from a styled-components switch and themed
 * to the app's green accent.
 *
 * 300ms ease (FastOutSlowIn ≈ cubic-bezier(0.4, 0, 0.2, 1)) on both the knob position
 * and the track colour.
 */
@Composable
fun ThemedSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // Track 51x29, knob 26 -> knob travels from 2.dp (off) to 24.dp (on).
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 2.dp,
        animationSpec = tween(durationMillis = 300),
        label = "knob",
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) ThemeSettings.accentColor else TrackOff,
        animationSpec = tween(durationMillis = 300),
        label = "track",
    )

    val interaction = remember { MutableInteractionSource() }
    val toggleModifier = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = interaction,
            indication = null,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(toggleModifier)
            .size(width = 51.dp, height = 29.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (enabled) trackColor else trackColor.copy(alpha = 0.4f)),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = knobOffset)
                .size(26.dp)
                .shadow(elevation = 3.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(Color(0xFFE3E3E3)),
        )
    }
}

private val TrackOff = Color(0xFF39393D)
