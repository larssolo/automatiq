package com.vibeactions.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibeactions.ui.theme.Background
import com.vibeactions.ui.theme.DmSans
import com.vibeactions.ui.theme.OnSurface
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The launcher icon's green and the fox's dark fill (`drawable/ic_launcher_foreground.xml`). */
private val FoxGreen = Color(0xFF69E382)
private val FoxDark = Color(0xFF0B130E)

/** The fox head from the launcher icon, in its 108-unit adaptive-icon viewport, without the dot
 *  holes: the dots are drawn on top so they can move. */
private const val FOX_HEAD = "M30.09,29.66 L45.82,41.51 L62.18,41.51 L77.91,29.66 L78.93,48.05 " +
    "L82.0,55.41 L58.91,76.05 Q54.0,79.93 49.09,76.05 L26.0,55.41 L29.07,48.05 Z"
private val DOT_X = floatArrayOf(45.21f, 54.0f, 62.79f)
private const val DOT_Y = 53.36f
private const val DOT_R = 2.76f

/**
 * The cold-start brand moment. Its first frame repeats the system splash (the launcher icon in a
 * 160dp circle, centred), so the hand-over is seamless; then the fox's dots bounce like a "typing"
 * bubble, the badge rises and the TextFox wordmark fades in, and the whole layer fades out over
 * the app. About 1.3 s; a tap skips it.
 */
@Composable
fun BrandIntro(onFinished: () -> Unit) {
    val head = remember { PathParser().parsePathString(FOX_HEAD).toPath() }
    val bounce = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val layer = remember { Animatable(1f) }
    val finish by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        coroutineScope {
            listOf(
                async { bounce.animateTo(1f, tween(900, easing = LinearEasing)) },
                async { delay(150); rise.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) },
                async { delay(300); word.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
            ).awaitAll()
        }
        layer.animateTo(0f, tween(300))
        finish()
    }

    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = layer.value }
            .background(Background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                scope.launch { layer.animateTo(0f, tween(150)); finish() }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // Centred on the badge alone at first (like the splash), then the wordmark below
            // takes its place as the badge rises.
            modifier = Modifier.offset(y = 48.dp * (1f - rise.value))
        ) {
            Box(Modifier.size(160.dp).clip(CircleShape).background(FoxGreen)) {
                // 240dp canvas = the 108-unit adaptive icon at splash scale (72 units ↦ 160dp).
                Canvas(Modifier.requiredSize(240.dp).align(Alignment.Center)) {
                    scale(size.width / 108f, Offset.Zero) {
                        drawPath(head, FoxDark)
                        DOT_X.forEachIndexed { i, x ->
                            // Two hops, each dot a beat behind the one before it; all rest at the end.
                            val t = (bounce.value * 2.36f - i * 0.18f).coerceIn(0f, 2f)
                            val lift = abs(sin(t * PI).toFloat()) * 3.2f
                            drawCircle(FoxGreen, DOT_R, Offset(x, DOT_Y - lift))
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = OnSurface)) { append("Text") }
                    withStyle(SpanStyle(color = FoxGreen)) { append("Fox") }
                },
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 40.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = word.value
                    translationY = (1f - word.value) * 16.dp.toPx()
                }
            )
        }
    }
}
