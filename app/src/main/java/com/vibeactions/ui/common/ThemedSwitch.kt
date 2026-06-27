package com.vibeactions.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vibeactions.ui.theme.OnPrimary
import com.vibeactions.ui.theme.OnSurface
import com.vibeactions.ui.theme.Primary
import com.vibeactions.ui.theme.SurfaceVariant

/**
 * A capsule toggle styled after the Uiverse "switch" by SelfMadeSystem.
 *
 * The original has no sliding thumb: a stroked line sits inside a fat rounded pill
 * and flips vertically (scaleY(-1)) when toggled. This recreates that in Compose and
 * adapts it to the app theme — gray pill when off, [Primary] green when on — so the
 * colour shift plus the flipping squiggle clearly communicate state.
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
    // 1f = off (squiggle one way), -1f = on (flipped). Springy to echo the bouncy CSS ease.
    val flip by animateFloatAsState(
        targetValue = if (checked) -1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "flip",
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) Primary else SurfaceVariant,
        animationSpec = tween(durationMillis = 450),
        label = "track",
    )
    val iconColor by animateColorAsState(
        targetValue = if (checked) OnPrimary else OnSurface,
        animationSpec = tween(durationMillis = 450),
        label = "icon",
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

    Canvas(
        modifier = modifier
            .then(toggleModifier)
            .size(width = width, height = height)
            .clip(RoundedCornerShape(percent = 50)),
    ) {
        // Fat rounded pill (clip already rounds the corners).
        drawRect(color = trackColor.copy(alpha = trackColor.alpha * alpha))

        // Stroked squiggle, centred, spanning the middle ~55% of the pill.
        val inset = size.width * 0.225f
        val xL = inset
        val xR = size.width - inset
        val span = xR - xL
        val cy = size.height / 2f
        val amp = size.height * 0.20f * flip

        val path = Path().apply {
            moveTo(xL, cy)
            cubicTo(
                xL + span / 3f, cy - amp * 2f,
                xL + span * 2f / 3f, cy + amp * 2f,
                xR, cy,
            )
        }

        drawPath(
            path = path,
            color = iconColor.copy(alpha = iconColor.alpha * alpha),
            style = Stroke(
                width = size.height * 0.16f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
