package com.vibeactions.ui.common

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibeactions.BuildConfig
import com.vibeactions.scheduler.AiReportWorker
import com.vibeactions.ui.theme.OnSurface
import com.vibeactions.ui.theme.OnSurfaceVariant
import com.vibeactions.util.AiConsent
import com.vibeactions.util.AiReportReason
import com.vibeactions.util.AiReportSource
import com.vibeactions.util.buildAiReport
import com.vibeactions.util.resolveGeminiModel
import com.vibeactions.util.sendAiReport
import kotlinx.coroutines.launch

/** Prominent disclosure shown before any message text is sent to Gemini. */
@Composable
fun AiConsentDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Send text to Google AI?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Automatiq's AI features use Google's Gemini API with your own API key. To write a " +
                        "message, this text is sent to Google:"
                )
                Text("• AI auto-replies: the incoming SMS you're replying to\n" +
                    "• AI variations: your macro's message\n" +
                    "• AI compose: what you describe")
                Text(
                    "Phone numbers and contact names are not sent. Google processes the text under " +
                        "the Gemini API terms. Nothing is sent unless you use an AI feature, and you can " +
                        "stop any time by removing your key in Settings.",
                    color = OnSurfaceVariant, fontSize = 13.sp
                )
            }
        },
        confirmButton = { Button(onClick = onAccept) { Text("I agree") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Not now") } }
    )
}

/**
 * Returns a gate: call it with an action that sends text to Gemini. Runs the action right away if
 * consent was given; otherwise shows [AiConsentDialog] first and runs it only on "I agree".
 */
@Composable
fun rememberAiConsentGate(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    pending?.let { action ->
        AiConsentDialog(
            onAccept = { AiConsent.give(context); pending = null; action() },
            onDecline = { pending = null }
        )
    }
    return remember { { action -> if (AiConsent.given(context)) action() else pending = action } }
}

/** Small flag button that opens [AiReportDialog] for [aiText]. */
@Composable
fun ReportAiButton(aiText: String, source: AiReportSource, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Outlined.Flag, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Report", fontSize = 13.sp)
    }
    if (open) AiReportDialog(aiText, source, onDismiss = { open = false })
}

/** In-app report of offensive or wrong AI output (Play's AI-Generated Content policy). */
@Composable
fun AiReportDialog(aiText: String, source: AiReportSource, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf<AiReportReason?>(null) }
    var note by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Report AI text") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "“$aiText”",
                    maxLines = 4, overflow = TextOverflow.Ellipsis,
                    color = OnSurface, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text("What's wrong with it?", fontWeight = FontWeight.Medium)
                AiReportReason.entries.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = reason == r, onClick = { reason = r })
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(r.label)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(1000) },
                    label = { Text("Details (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sends this AI text, the reason and your note to the developer to improve the " +
                        "filters. No phone numbers or incoming messages are sent.",
                    color = OnSurfaceVariant, fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                enabled = reason != null && !sending,
                onClick = {
                    val r = reason ?: return@Button
                    sending = true
                    scope.launch {
                        val model = resolveGeminiModel(
                            context.getSharedPreferences("ai_settings", android.content.Context.MODE_PRIVATE)
                                .getString("gemini_model", null)
                        )
                        val report = buildAiReport(r, source, aiText, note, model, BuildConfig.VERSION_NAME)
                        val result = runCatching { sendAiReport(report) }
                        sending = false
                        if (result.isSuccess) {
                            Toast.makeText(context, "Thanks — report sent", Toast.LENGTH_SHORT).show()
                        } else {
                            android.util.Log.w("AiReport", "Send failed, queued for retry", result.exceptionOrNull())
                            AiReportWorker.enqueue(context, report)
                            Toast.makeText(
                                context, "Thanks — report saved, it's sent automatically", Toast.LENGTH_LONG
                            ).show()
                        }
                        onDismiss()
                    }
                }
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Send report")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !sending) { Text("Cancel") } }
    )
}
