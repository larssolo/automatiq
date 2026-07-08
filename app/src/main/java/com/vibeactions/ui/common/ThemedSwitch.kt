package com.vibeactions.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vibeactions.ui.theme.Amber
import com.vibeactions.ui.theme.Primary

/**
 * A standard sliding-thumb toggle: a white thumb travels across a pill-shaped track. The track is
 * [Primary] (green) when checked and [Amber] (yellow) when unchecked, so state reads at a glance.
 */
@Composable
fun ThemedSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = 60.dp,
    height: Dp = 30.dp,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) Primary else Amber,
        animationSpec = tween(durationMillis = 200),
        label = "track",
    )
    val inset = height * 0.1f
    val thumbSize = height - inset * 2
    val travel = width - height
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) travel else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "thumbOffset",
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

    val alpha = if (enabled) 1f else 0.4f

    Box(
        modifier = modifier
            .then(toggleModifier)
            .size(width = width, height = height)
            .clip(RoundedCornerShape(percent = 50))
            .background(trackColor.copy(alpha = trackColor.alpha * alpha)),
    ) {
        Box(
            Modifier
                .padding(inset)
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = alpha)),
        )
    }
}
