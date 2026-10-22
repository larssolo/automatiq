package com.vibeactions.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibeactions.BuildConfig
import com.vibeactions.data.AppSettings
import com.vibeactions.ui.common.ProvideAmbientPulse
import com.vibeactions.ui.common.StaticBackground
import com.vibeactions.ui.common.AiConsentDialog
import com.vibeactions.ui.common.rememberAiConsentGate
import com.vibeactions.ui.setup.SetupChecklist
import com.vibeactions.util.AiConsent
import com.vibeactions.ui.setup.SetupController
import com.vibeactions.ui.setup.rememberSetupController
import com.vibeactions.ui.theme.*
import com.vibeactions.util.GEMINI_API_KEY_URL
import com.vibeactions.util.geminiGenerateWithFallback
import com.vibeactions.util.resolveGeminiModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PAGES = 5

private data class Trigger(val icon: ImageVector, val name: String, val example: String)

private val TRIGGERS = listOf(
    Trigger(Icons.Default.Schedule, "Schedule", "08:30 every weekday — or every 2nd week, until a date you pick."),
    Trigger(Icons.Default.TouchApp, "One tap", "Fire it from the list or a home-screen widget."),
    Trigger(Icons.Default.Sms, "Auto-reply", "Answer incoming texts — with a fixed reply or an AI-written one."),
    Trigger(Icons.AutoMirrored.Filled.PhoneMissed, "Missed call", "Text people back when you can't pick up."),
    Trigger(Icons.Default.LocationOn, "Location", "Arriving at or leaving a place you choose."),
    Trigger(Icons.Default.BatteryChargingFull, "Device", "Charger plugged in, Bluetooth or Wi-Fi connects.")
).filter {
    (it.name != "Location" || BuildConfig.LOCATION_TRIGGER) && (it.name != "Device" || BuildConfig.DEVICE_TRIGGERS)
}

private val NUMBER_WORDS = listOf("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven")

/**
 * First-run guide. One live [SignalCore] sits above a pager and morphs with the swipe; each page
 * explains a slice of the app and lets the user act on it right there (grant access, add a key).
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val pos = pager.currentPage + pager.currentPageOffsetFraction

    val clock = rememberInfiniteTransition(label = "onboarding")
    val t by clock.animateFloat(
        0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "t"
    )
    val rotation by clock.animateFloat(
        0f, 360f, infiniteRepeatable(tween(30_000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )
    val activeTrigger = (((360f - rotation) / (360f / TRIGGERS.size)).roundToInt()) % TRIGGERS.size

    val setup = rememberSetupController()

    // AI key state lives here so the final page can report it.
    val aiPrefs = remember { context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(aiPrefs.getString("gemini_api_key", "").orEmpty()) }
    var keyWorks by remember { mutableStateOf<Boolean?>(null) }
    // A key switches AI on, so it is only stored once the user agreed to text going to Google.
    fun saveKey() {
        if (AiConsent.given(context)) aiPrefs.edit().putString("gemini_api_key", apiKey.trim()).apply()
    }
    var askConsentThenNext by remember { mutableStateOf(false) }

    val burst = remember { Animatable(0f) }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage == PAGES - 1) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(1700))
        }
    }

    fun finish() {
        if (apiKey.isNotBlank()) saveKey()
        AppSettings.setOnboardingDone(context)
        onFinish()
    }

    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    if (askConsentThenNext) {
        // "Not now" still moves on — the key just isn't stored, so AI stays off.
        AiConsentDialog(
            onAccept = {
                AiConsent.give(context)
                askConsentThenNext = false
                saveKey()
                scope.launch { pager.animateScrollToPage(4) }
            },
            onDecline = {
                askConsentThenNext = false
                scope.launch { pager.animateScrollToPage(4) }
            }
        )
    }

    // The core shrinks on the two content-heavy pages to make room for their controls.
    val compact = presence(pos, 2) + presence(pos, 3)
    val coreHeight = (290f - 120f * compact.coerceIn(0f, 1f)).dp

    ProvideAmbientPulse {
        Box(Modifier.fillMaxSize().background(Background)) {
            StaticBackground(Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0x66000000), Color(0xCC000000), Color(0xF0000000)))
                )
            )
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                TopRow(
                    page = pager.currentPage,
                    onSkip = { scope.launch { pager.animateScrollToPage(PAGES - 1) } }
                )
                SignalCore(
                    pos = pos,
                    t = t,
                    rotation = rotation,
                    triggerIcons = TRIGGERS.map { it.icon },
                    activeTrigger = activeTrigger,
                    permsGranted = setup.doneCount,
                    permsTotal = setup.steps.size,
                    burst = burst.value,
                    modifier = Modifier.fillMaxWidth().height(coreHeight)
                )
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top
                ) { page ->
                    val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                    Column(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Text drifts slower than the page and fades: cheap parallax depth.
                                translationX = offset * size.width * 0.35f
                                alpha = 1f - abs(offset).coerceIn(0f, 1f)
                            }
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                    ) {
                        when (page) {
                            0 -> WelcomePage(active = pager.settledPage == 0)
                            1 -> TriggersPage(activeTrigger)
                            2 -> PermissionsPage(setup)
                            3 -> AiPage(
                                apiKey = apiKey,
                                onKeyChange = { apiKey = it; keyWorks = null },
                                keyWorks = keyWorks,
                                onTested = { ok -> keyWorks = ok; if (ok) saveKey() }
                            )
                            else -> DonePage(
                                active = pager.settledPage == PAGES - 1,
                                smsOk = setup.done["sms"] == true,
                                permsOk = setup.allDone,
                                aiState = when {
                                    keyWorks == true -> "online"
                                    apiKey.isNotBlank() && AiConsent.given(context) -> "key saved"
                                    else -> "skipped"
                                }
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
                BottomBar(
                    pos = pos,
                    isLast = pager.currentPage == PAGES - 1,
                    onNext = {
                        if (pager.currentPage == PAGES - 1) finish()
                        else {
                            if (pager.currentPage == 3 && apiKey.isNotBlank() && !AiConsent.given(context)) {
                                askConsentThenNext = true
                                return@BottomBar
                            }
                            if (pager.currentPage == 3 && apiKey.isNotBlank()) saveKey()
                            scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TopRow(page: Int, onSkip: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "automatiq",
            fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
            color = OnSurface, fontSize = 15.sp, modifier = Modifier.weight(1f)
        )
        if (page < PAGES - 1) {
            TextButton(onClick = onSkip) {
                Text("Skip", color = OnSurfaceVariant, fontFamily = JetBrainsMono, fontSize = 13.sp)
            }
        } else Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun Kicker(text: String, color: Color) {
    Text(
        text,
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, letterSpacing = 1.5.sp, color = color
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Title(text: String) {
    Text(
        text,
        fontFamily = DmSans, fontWeight = FontWeight.Medium,
        fontSize = 32.sp, lineHeight = 36.sp, color = Color.White, letterSpacing = (-0.5).sp
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Body(text: String) {
    Text(text, fontFamily = DmSans, fontSize = 15.sp, lineHeight = 22.sp, color = OnSurfaceVariant)
}

/** Types [full] out character by character with a blinking block cursor. */
@Composable
private fun Typewriter(full: String, active: Boolean, color: Color) {
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        shown = 0
        delay(250)
        while (shown < full.length) { shown++; delay(55) }
    }
    val blink = rememberInfiniteTransition(label = "cursor")
    val on by blink.animateFloat(
        0f, 1f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "blink"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            full.take(shown),
            fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
            fontSize = 13.sp, letterSpacing = 1.sp, color = color
        )
        Box(
            Modifier.padding(start = 2.dp).size(width = 8.dp, height = 15.dp)
                .background(color.copy(alpha = if (on > 0.5f) 1f else 0f))
        )
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun WelcomePage(active: Boolean) {
    Typewriter("> automatiq.init()", active, Primary)
    Title("Your phone,\non autopilot.")
    Body(
        "Automatiq sends text messages for you — on a schedule, as a reply" +
            (if (BuildConfig.LOCATION_TRIGGER) ", when you miss a call or when you arrive somewhere. "
            else " or when you miss a call. ") +
            "Set it up once, and it just runs."
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(Icons.Default.PhoneAndroid, "On-device")
        Pill(Icons.Default.PersonOff, "No account")
        Pill(Icons.Default.CloudOff, "No server")
    }
}

@Composable
private fun Pill(icon: ImageVector, text: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = OnSurface, fontFamily = DmSans)
    }
}

@Composable
private fun TriggersPage(index: Int) {
    Kicker("// 02 · TRIGGERS", Amber)
    Title("${NUMBER_WORDS[TRIGGERS.size]} ways\nto fire a macro.")
    // Ticker follows whichever trigger the orbit above has brought to the top.
    AnimatedContent(
        targetState = index,
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn(tween(300))) togetherWith
                (slideOutVertically { -it / 2 } + fadeOut(tween(200)))
        },
        label = "ticker"
    ) { i ->
        val tr = TRIGGERS[i]
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 22.dp))
                .background(Amber.copy(alpha = 0.08f))
                .border(
                    1.dp, Amber.copy(alpha = 0.35f),
                    RoundedCornerShape(topStart = 22.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 22.dp)
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(tr.icon, null, tint = Amber, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(tr.name, color = Color.White, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(tr.example, color = OnSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Body(
        "And not just texts: a macro can also switch your sound mode or Do Not Disturb, show a " +
            "reminder, or call a web address for your smart home."
    )
}

@Composable
private fun PermissionsPage(setup: SetupController) {
    Kicker("// 03 · ACCESS", Primary)
    Title("Hand it the keys.")
    Body(
        "Your phone blocks background apps until you say yes. One tap below walks you through " +
            "every screen — each step lights a segment of the ring above."
    )
    Spacer(Modifier.height(16.dp))
    SetupChecklist(setup)
}

@Composable
private fun AiPage(
    apiKey: String,
    onKeyChange: (String) -> Unit,
    keyWorks: Boolean?,
    onTested: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    val aiConsentGate = rememberAiConsentGate()
    var error by remember { mutableStateOf<String?>(null) }
    val model = remember {
        resolveGeminiModel(
            context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE).getString("gemini_model", null)
        )
    }

    Kicker("// 04 · AI  (OPTIONAL)", Teal)
    Title("Teach it\nto write.")
    Body(
        "Let Google Gemini write auto-replies and fresh variations of your scheduled texts. " +
            "It needs your own free API key — takes about a minute."
    )
    Spacer(Modifier.height(16.dp))

    Step(1, "Open Google AI Studio and sign in with your Google account.")
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GEMINI_API_KEY_URL))) },
        colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.Black),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 18.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) {
        Icon(Icons.Default.Key, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Get my free key", fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
    }
    Spacer(Modifier.height(14.dp))
    Step(2, "Tap \"Create API key\", then copy the key.")
    Spacer(Modifier.height(14.dp))
    Step(3, "Come back here, paste it and test it.")
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = apiKey,
        onValueChange = onKeyChange,
        singleLine = true,
        placeholder = { Text("Paste your API key") },
        visualTransformation = PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { clipboard.getText()?.text?.trim()?.let(onKeyChange) }) {
                Icon(Icons.Default.ContentPaste, "Paste")
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Teal, cursorColor = Teal, unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
    OutlinedButton(
        onClick = {
            aiConsentGate {
                testing = true; error = null
                scope.launch {
                    val r = runCatching { geminiGenerateWithFallback(apiKey.trim(), "", "Reply with only the word OK", model) }
                    testing = false
                    error = r.exceptionOrNull()?.message
                    onTested(r.isSuccess)
                }
            }
        },
        enabled = apiKey.isNotBlank() && !testing,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Teal)
        else Text("Test key", fontFamily = DmSans)
    }
    AnimatedContent(keyWorks, label = "result") { ok ->
        when (ok) {
            true -> Text(
                "● ONLINE — Gemini answered.", color = Primary, fontFamily = JetBrainsMono,
                fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
            )
            false -> Text(
                "● ${error?.take(140) ?: "Didn't work"}", color = ErrorRed, fontFamily = JetBrainsMono,
                fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
            )
            null -> Spacer(Modifier.height(0.dp))
        }
    }
    Spacer(Modifier.height(16.dp))
    InfoCard(
        "Model: $model",
        "Fast and reliable, and it's the default. If it's busy or its free daily quota runs out, " +
            "Automatiq automatically switches to another Gemini model. Your key stays on this phone."
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "No key? Skip it — everything except AI works without one.",
        color = OnSurfaceVariant, fontSize = 12.5.sp
    )
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).border(1.dp, Teal, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("$n", color = Teal, fontFamily = JetBrainsMono, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = OnSurface, fontSize = 14.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun InfoCard(title: String, text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Teal.copy(alpha = 0.07f))
            .border(1.dp, Teal.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = Teal, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = Color.White, fontFamily = JetBrainsMono, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(6.dp))
        Text(text, color = OnSurfaceVariant, fontSize = 12.5.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun DonePage(active: Boolean, smsOk: Boolean, permsOk: Boolean, aiState: String) {
    val lines = listOf(
        (if (smsOk) "✓" else "!") + " sms access ......... " + if (smsOk) "granted" else "missing",
        (if (permsOk) "✓" else "·") + " background ......... " + if (permsOk) "reliable" else "partial",
        (if (aiState == "skipped") "·" else "✓") + " gemini ............. " + aiState,
        "> system armed"
    )
    var visible by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        visible = 0
        delay(500)
        while (visible < lines.size) { visible++; delay(260) }
    }
    Kicker("// 05 · READY", Primary)
    Title("You're live.")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Primary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        lines.forEachIndexed { i, line ->
            val color = when {
                i == lines.lastIndex -> Primary
                line.startsWith("!") -> ErrorRed
                line.startsWith("✓") -> OnSurface
                else -> OnSurfaceVariant
            }
            Text(
                line, fontFamily = JetBrainsMono, fontSize = 12.5.sp, color = color,
                modifier = Modifier.graphicsLayer { alpha = if (i < visible) 1f else 0f }
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    Body(
        "Tap + on the next screen to build your first macro. " +
            "You can replay this guide any time from Settings."
    )
}

@Composable
private fun BottomBar(pos: Float, isLast: Boolean, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Segments stretch with the live drag position, so progress feels physically attached.
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(PAGES) { i ->
                val p = presence(pos, i)
                val passed = pos >= i
                Box(
                    Modifier
                        .height(6.dp)
                        .width((8 + 22 * p).dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (passed || p > 0f) accentAt(pos).copy(alpha = 0.4f + 0.6f * p)
                            else Color.White.copy(alpha = 0.18f)
                        )
                )
            }
        }
        val accent = accentAt(pos)
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 22.dp),
            contentPadding = PaddingValues(horizontal = 22.dp),
            modifier = Modifier.height(54.dp).animateContentSize()
        ) {
            Text(
                if (isLast) "Start automating" else "Next",
                fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 16.sp
            )
            Spacer(Modifier.width(8.dp))
            Icon(if (isLast) Icons.Default.Bolt else Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
        }
    }
}
