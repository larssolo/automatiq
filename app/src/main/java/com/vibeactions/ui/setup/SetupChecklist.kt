package com.vibeactions.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibeactions.ui.theme.*

private val Leaf = RoundedCornerShape(topStart = 22.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 22.dp)

/** Progress, the one-tap "fix everything" button and a row per step. */
@Composable
fun SetupChecklist(setup: SetupController, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Progress(setup)
        FixAllButton(setup)
        AnimatedVisibility(!setup.allDone) {
            Text(
                "We open each screen for you and tell you what to tap. Just do it and go back — " +
                    "we check it and move on.",
                color = OnSurfaceVariant, fontSize = 12.5.sp, lineHeight = 17.sp
            )
        }
        Spacer(Modifier.height(2.dp))
        setup.steps.forEachIndexed { i, step ->
            StepRow(
                index = i + 1,
                step = step,
                done = setup.done[step.id] == true,
                stuck = setup.stuck == step.id,
                celebrate = setup.justDone == step.id,
                onFix = { setup.fix(step) },
                onAppInfo = setup.openAppInfo
            )
        }
    }
    setup.confirmStep?.let { step ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(step.icon, null, tint = Primary) },
            title = { Text(step.title) },
            text = { Text(step.confirmQuestion.orEmpty()) },
            confirmButton = { Button(onClick = { setup.answerConfirm(true) }) { Text("Yes, done") } },
            dismissButton = { TextButton(onClick = { setup.answerConfirm(false) }) { Text("Not yet") } }
        )
    }
}

@Composable
private fun Progress(setup: SetupController) {
    val fraction by animateFloatAsState(
        setup.doneCount / setup.steps.size.coerceAtLeast(1).toFloat(),
        spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "progress"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (setup.allDone) "ALL SET" else "${setup.doneCount} / ${setup.steps.size} READY",
            color = if (setup.allDone) Primary else OnSurface,
            fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.2.sp
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(50))
                    .background(Primary)
            )
        }
    }
}

@Composable
private fun FixAllButton(setup: SetupController) {
    val container by animateColorAsState(
        if (setup.allDone) Primary.copy(alpha = 0.14f) else Primary, label = "fixAll"
    )
    Button(
        onClick = setup.fixAll,
        enabled = !setup.allDone && !setup.running,
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = Color.Black,
            disabledContainerColor = container,
            disabledContentColor = if (setup.allDone) Primary else Color.Black
        ),
        shape = Leaf,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        AnimatedContent(
            when {
                setup.allDone -> 2
                setup.running -> 1
                else -> 0
            },
            label = "fixAllLabel"
        ) { state ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (state) {
                    2 -> {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Your phone is ready", fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    }
                    1 -> {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Step ${setup.doneCount + 1} of ${setup.steps.size}…",
                            fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 16.sp
                        )
                    }
                    else -> {
                        Icon(Icons.Default.Bolt, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Fix everything for me", fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(
    index: Int,
    step: SetupStep,
    done: Boolean,
    stuck: Boolean,
    celebrate: Boolean,
    onFix: () -> Unit,
    onAppInfo: () -> Unit
) {
    val edge by animateColorAsState(
        when {
            done -> Primary.copy(alpha = 0.55f)
            stuck -> Amber.copy(alpha = 0.7f)
            else -> Color.White.copy(alpha = 0.12f)
        },
        label = "edge"
    )
    // A short pop when this row has just turned green.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(celebrate) {
        if (celebrate) {
            pop.snapTo(1.06f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (done) 0.06f else 0.03f))
            .border(1.dp, edge, RoundedCornerShape(16.dp))
            .clickable(enabled = !done, onClick = onFix)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(if (done) Primary.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(step.icon, null, tint = if (done) Primary else OnSurface, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "$index. ${step.title}", color = Color.White, fontSize = 15.sp,
                    fontFamily = DmSans, fontWeight = FontWeight.Medium
                )
                Text(step.why, color = OnSurfaceVariant, fontSize = 12.5.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.width(8.dp))
            AnimatedContent(done, label = "state") { ok ->
                if (ok) Icon(Icons.Default.CheckCircle, "Done", tint = Primary, modifier = Modifier.size(24.dp))
                else Text(
                    "FIX", color = Primary, fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.sp
                )
            }
        }
        if (!done) {
            Text(
                "→ ${step.howTo}",
                color = Primary.copy(alpha = 0.8f), fontFamily = JetBrainsMono, fontSize = 11.5.sp,
                modifier = Modifier.padding(start = 48.dp, top = 6.dp)
            )
        }
        AnimatedVisibility(stuck && !done, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            StuckHelp(onRetry = onFix, onAppInfo = onAppInfo)
        }
    }
}

@Composable
private fun StuckHelp(onRetry: () -> Unit, onAppInfo: () -> Unit) {
    Column(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Amber.copy(alpha = 0.08f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = Amber, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Not done yet", color = Amber, fontFamily = JetBrainsMono, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Try once more. If the switch is grey or Android says \"Restricted setting\": open App info, " +
                "tap ⋮ (top right) → \"Allow restricted settings\", then come back and tap Fix.",
            color = OnSurface, fontSize = 12.5.sp, lineHeight = 18.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("Try again") }
            OutlinedButton(onClick = onAppInfo, modifier = Modifier.weight(1f)) { Text("App info") }
        }
    }
}

/** Standalone screen, reached from the "setup incomplete" banner and from Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen() {
    val setup = rememberSetupController()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Phone setup") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { p ->
        Column(
            Modifier.padding(p).fillMaxSize().background(Color(0x80000000))
                .verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Text(
                (if (isXiaomi()) "Android and Xiaomi block" else "Android blocks") +
                    " apps from running in the background unless you allow it. " +
                    "Do this once and your macros fire on time.",
                color = OnSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))
            SetupChecklist(setup)
        }
    }
}
