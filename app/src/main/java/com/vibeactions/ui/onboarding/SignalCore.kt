package com.vibeactions.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.vibeactions.ui.theme.Amber
import com.vibeactions.ui.theme.Primary
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal val Teal = Color(0xFF42D1CA)

/** Accent per page; the core blends between them as the pager is dragged. */
internal val PageAccents = listOf(Primary, Amber, Primary, Teal, Primary)

internal fun accentAt(pos: Float): Color {
    val i = pos.toInt().coerceIn(0, PageAccents.lastIndex)
    val next = (i + 1).coerceAtMost(PageAccents.lastIndex)
    return lerp(PageAccents[i], PageAccents[next], (pos - i).coerceIn(0f, 1f))
}

/** How much page [page] is "present" at pager position [pos]: 1 when settled on it, 0 a page away. */
internal fun presence(pos: Float, page: Int) = (1f - abs(pos - page)).coerceIn(0f, 1f)

private class Particle(val angle: Float, val speed: Float, val size: Float, val color: Color, val spin: Float)

/**
 * The onboarding's one continuous hero: a breathing core with radar rings that morphs per page —
 * SMS bubbles (welcome), orbiting triggers, a permission ring, a neural mesh (AI) and a final burst.
 * Every layer is weighted by [presence] of the live pager position, so the morph is scrubbed by
 * the user's finger rather than played as a canned transition.
 */
@Composable
internal fun SignalCore(
    pos: Float,
    t: Float,
    rotation: Float,
    triggerIcons: List<ImageVector>,
    activeTrigger: Int,
    permsGranted: Int,
    permsTotal: Int,
    burst: Float,
    modifier: Modifier = Modifier
) {
    val painters = triggerIcons.map { rememberVectorPainter(it) }
    val particles = remember {
        val rnd = Random(7)
        val colors = listOf(Primary, Teal, Amber, Color.White)
        List(70) {
            Particle(
                angle = rnd.nextFloat() * 2f * PI.toFloat(),
                speed = 0.45f + rnd.nextFloat() * 0.75f,
                size = 3f + rnd.nextFloat() * 7f,
                color = colors[rnd.nextInt(colors.size)],
                spin = rnd.nextFloat() * 6f
            )
        }
    }
    val accent = accentAt(pos)

    Canvas(modifier) {
        val c = center
        val unit = size.minDimension
        val r0 = unit * 0.13f
        val wWelcome = presence(pos, 0)
        val wTriggers = presence(pos, 1)
        val wPerms = presence(pos, 2)
        val wAi = presence(pos, 3)
        val wDone = presence(pos, 4)

        // Radar rings — always on, the app's heartbeat.
        for (i in 0 until 3) {
            val phase = (t + i / 3f) % 1f
            drawCircle(
                color = accent.copy(alpha = (1f - phase) * 0.45f),
                radius = r0 + phase * unit * 0.36f,
                center = c,
                style = Stroke(width = 2.5f * (1f - phase) + 0.5f)
            )
        }

        // Halo + core.
        val breath = 0.92f + 0.08f * sin(t * 2f * PI.toFloat())
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent), c, r0 * 2.6f),
            radius = r0 * 2.6f, center = c
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, accent, accent.copy(alpha = 0.6f)), c, r0 * 0.75f * breath
            ),
            radius = r0 * 0.62f * breath, center = c
        )

        if (wWelcome > 0f) drawBubbles(c, unit, t, accent, wWelcome)
        if (wTriggers > 0f) drawOrbit(c, unit, rotation, painters, activeTrigger, wTriggers)
        if (wPerms > 0f) drawPermRing(c, r0, permsGranted, permsTotal, wPerms)
        if (wAi > 0f) drawMesh(c, unit, t, wAi)
        if (wDone > 0f) {
            drawCheck(c, r0, wDone)
            drawBurst(c, unit, burst, particles, wDone)
        }
    }
}

/** Recipients on a ring; a message bubble travels out to each in turn. */
private fun DrawScope.drawBubbles(c: Offset, unit: Float, t: Float, accent: Color, w: Float) {
    val n = 5
    val ring = unit * 0.38f
    for (i in 0 until n) {
        val a = (-PI / 2 + i * 2 * PI / n).toFloat()
        val target = Offset(c.x + cos(a) * ring, c.y + sin(a) * ring)
        val p = ((t * 1.6f + i / n.toFloat()) % 1f)
        val eased = 1f - (1f - p) * (1f - p)
        // A slight arc so the flights don't look like spokes.
        val bend = sin(eased * PI.toFloat()) * unit * 0.06f
        val pos = Offset(
            c.x + (target.x - c.x) * eased - sin(a) * bend,
            c.y + (target.y - c.y) * eased + cos(a) * bend
        )
        val arrive = if (p > 0.85f) (p - 0.85f) / 0.15f else 0f
        drawCircle(accent.copy(alpha = (0.35f + 0.65f * arrive) * w), unit * 0.022f, target)
        drawCircle(accent.copy(alpha = 0.18f * w), unit * 0.045f, target, style = Stroke(1.5f))
        val alpha = (if (p < 0.85f) 1f else 1f - arrive) * w
        val bw = unit * 0.075f
        val bh = unit * 0.048f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.92f * alpha),
            topLeft = Offset(pos.x - bw / 2, pos.y - bh / 2),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(bh / 2.2f)
        )
        for (d in 0 until 3) {
            drawCircle(
                accent.copy(alpha = alpha),
                radius = bh * 0.1f,
                center = Offset(pos.x - bw * 0.22f + d * bw * 0.22f, pos.y)
            )
        }
    }
}

private fun DrawScope.drawOrbit(
    c: Offset,
    unit: Float,
    rotation: Float,
    painters: List<androidx.compose.ui.graphics.painter.Painter>,
    active: Int,
    w: Float
) {
    val ring = unit * 0.36f
    drawCircle(Amber.copy(alpha = 0.18f * w), ring, c, style = Stroke(1.5f))
    painters.forEachIndexed { i, painter ->
        val a = Math.toRadians((-90.0 + rotation + i * 360.0 / painters.size)).toFloat()
        val p = Offset(c.x + cos(a) * ring, c.y + sin(a) * ring)
        val isActive = i == active
        val chip = unit * (if (isActive) 0.075f else 0.055f)
        val tint = if (isActive) Amber else Color.White
        if (isActive) {
            drawLine(Amber.copy(alpha = 0.5f * w), c, p, strokeWidth = 2f, cap = StrokeCap.Round)
            drawCircle(
                Brush.radialGradient(listOf(Amber.copy(alpha = 0.45f * w), Color.Transparent), p, chip * 2.4f),
                chip * 2.4f, p
            )
        }
        drawCircle(Color(0xFF141414).copy(alpha = 0.92f * w), chip, p)
        drawCircle(tint.copy(alpha = (if (isActive) 0.9f else 0.35f) * w), chip, p, style = Stroke(2f))
        val s = chip * 1.1f
        translate(p.x - s / 2, p.y - s / 2) {
            with(painter) {
                draw(Size(s, s), alpha = w, colorFilter = ColorFilter.tint(tint))
            }
        }
    }
}

/** One arc segment per permission; granted ones light up. */
private fun DrawScope.drawPermRing(c: Offset, r0: Float, granted: Int, total: Int, w: Float) {
    if (total <= 0) return
    val r = r0 * 1.75f
    val gap = 10f
    val sweep = 360f / total - gap
    for (i in 0 until total) {
        val on = i < granted
        drawArc(
            color = (if (on) Primary else Color.White.copy(alpha = 0.22f)).copy(
                alpha = (if (on) 1f else 0.22f) * w
            ),
            startAngle = -90f + i * (360f / total) + gap / 2,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = r0 * 0.28f, cap = StrokeCap.Round)
        )
    }
}

/** Nodes on two rings wired to the core, with pulses running along the wires. */
private fun DrawScope.drawMesh(c: Offset, unit: Float, t: Float, w: Float) {
    val nodes = (0 until 9).map { i ->
        val ring = if (i % 2 == 0) unit * 0.38f else unit * 0.27f
        val a = (i * 2 * PI / 9 + 0.3).toFloat()
        Offset(c.x + cos(a) * ring, c.y + sin(a) * ring)
    }
    nodes.forEachIndexed { i, n ->
        val next = nodes[(i + 1) % nodes.size]
        drawLine(Teal.copy(alpha = 0.16f * w), n, next, 1.5f)
        drawLine(Teal.copy(alpha = 0.28f * w), c, n, 1.5f)
        val p = (t * 1.3f + i * 0.17f) % 1f
        val pulse = Offset(c.x + (n.x - c.x) * p, c.y + (n.y - c.y) * p)
        drawCircle(Color.White.copy(alpha = (1f - p) * w), unit * 0.008f, pulse)
        val glow = 0.5f + 0.5f * sin((t + i * 0.11f) * 2f * PI.toFloat())
        drawCircle(Teal.copy(alpha = (0.35f + 0.65f * glow) * w), unit * 0.016f, n)
    }
}

private fun DrawScope.drawCheck(c: Offset, r0: Float, w: Float) {
    val path = Path().apply {
        moveTo(c.x - r0 * 0.28f, c.y + r0 * 0.02f)
        lineTo(c.x - r0 * 0.06f, c.y + r0 * 0.24f)
        lineTo(c.x + r0 * 0.32f, c.y - r0 * 0.22f)
    }
    drawPath(path, Color.Black.copy(alpha = w), style = Stroke(r0 * 0.14f, cap = StrokeCap.Round))
}

private fun DrawScope.drawBurst(c: Offset, unit: Float, p: Float, particles: List<Particle>, w: Float) {
    if (p <= 0f || p >= 1f) return
    val eased = 1f - (1f - p) * (1f - p) * (1f - p)
    drawCircle(
        Primary.copy(alpha = (1f - p) * 0.8f * w),
        radius = unit * 0.1f + eased * unit * 0.5f,
        center = c,
        style = Stroke(width = 6f * (1f - p))
    )
    particles.forEach { pt ->
        val dist = eased * pt.speed * unit * 0.48f
        val gravity = p * p * unit * 0.12f
        val pos = Offset(c.x + cos(pt.angle) * dist, c.y + sin(pt.angle) * dist + gravity)
        val alpha = ((1f - p) * w).coerceIn(0f, 1f)
        val s = pt.size * (1f - p * 0.5f)
        translate(pos.x, pos.y) {
            val a = pt.spin * p * 6f
            drawLine(
                pt.color.copy(alpha = alpha),
                Offset(-cos(a) * s, -sin(a) * s),
                Offset(cos(a) * s, sin(a) * s),
                strokeWidth = s * 0.55f,
                cap = StrokeCap.Round
            )
        }
    }
}
