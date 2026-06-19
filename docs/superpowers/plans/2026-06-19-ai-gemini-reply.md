# AI Gemini Reply Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate Gemini 2.0 Flash into Automatiq so INCOMING macros can generate contextual AI replies (approve-before-send or auto-send-with-notification), and any macro's message body can be AI-composed in the editor.

**Architecture:** A new `util/GeminiClient.kt` handles all HTTP calls to the Gemini REST API using `HttpURLConnection` and the existing `kotlinx-serialization-json` dependency. `SmsReplyReceiver` gains an AI branch: when `macro.aiReplyEnabled`, it calls Gemini and either posts an approval notification (APPROVE mode) or fires immediately and posts an info notification (AUTO mode). A new `AiReplyActionReceiver` handles the approve/discard actions. The editor gets an AI-compose button backed by the same client.

**Tech Stack:** Kotlin coroutines (IO dispatcher), `HttpURLConnection`, `kotlinx-serialization-json` (already in deps), Hilt field injection, Room migration, Jetpack Compose Material 3, NotificationCompat.

---

## File Map

| Action | File |
|--------|------|
| Create | `app/src/main/java/com/vibeactions/util/GeminiClient.kt` |
| Create | `app/src/main/java/com/vibeactions/scheduler/AiReplyActionReceiver.kt` |
| Create | `app/src/test/java/com/vibeactions/util/GeminiClientTest.kt` |
| Modify | `app/src/main/java/com/vibeactions/data/db/Migrations.kt` |
| Modify | `app/src/main/java/com/vibeactions/data/db/AppDatabase.kt` |
| Modify | `app/src/main/java/com/vibeactions/di/DatabaseModule.kt` |
| Modify | `app/src/main/java/com/vibeactions/domain/model/Macro.kt` |
| Modify | `app/src/main/java/com/vibeactions/data/db/entities/MacroEntity.kt` |
| Modify | `app/src/main/java/com/vibeactions/domain/model/Mappers.kt` |
| Modify | `app/src/main/java/com/vibeactions/util/MacroJson.kt` |
| Modify | `app/src/main/java/com/vibeactions/scheduler/MacroFirer.kt` |
| Modify | `app/src/main/java/com/vibeactions/notifications/MacroNotificationManager.kt` |
| Modify | `app/src/main/java/com/vibeactions/scheduler/SmsReplyReceiver.kt` |
| Modify | `app/src/main/java/com/vibeactions/ui/settings/SettingsViewModel.kt` |
| Modify | `app/src/main/java/com/vibeactions/ui/settings/SettingsScreen.kt` |
| Modify | `app/src/main/java/com/vibeactions/ui/editor/MacroEditorViewModel.kt` |
| Modify | `app/src/main/java/com/vibeactions/ui/editor/MacroEditorScreen.kt` |
| Modify | `app/src/main/java/com/vibeactions/ui/common/MacroCard.kt` |
| Modify | `app/src/main/AndroidManifest.xml` |

---

## Task 1: DB Migration 8→9 + Domain Model

Add `ai_reply_enabled` and `ai_send_mode` to the macros table and propagate through the entire model layer.

**Files:**
- Modify: `app/src/main/java/com/vibeactions/data/db/Migrations.kt`
- Modify: `app/src/main/java/com/vibeactions/data/db/AppDatabase.kt`
- Modify: `app/src/main/java/com/vibeactions/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/vibeactions/domain/model/Macro.kt`
- Modify: `app/src/main/java/com/vibeactions/data/db/entities/MacroEntity.kt`
- Modify: `app/src/main/java/com/vibeactions/domain/model/Mappers.kt`
- Modify: `app/src/main/java/com/vibeactions/util/MacroJson.kt`

- [ ] **Step 1: Add MIGRATION_8_9 to Migrations.kt**

Append to the end of `app/src/main/java/com/vibeactions/data/db/Migrations.kt`:

```kotlin
/** v8 → v9: adds AI reply fields for INCOMING macros.
 *  ai_reply_enabled: 0 = off (existing macros unchanged).
 *  ai_send_mode: 'APPROVE' = show notification before sending (safe default). */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE macros ADD COLUMN ai_reply_enabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE macros ADD COLUMN ai_send_mode TEXT NOT NULL DEFAULT 'APPROVE'")
    }
}
```

- [ ] **Step 2: Bump AppDatabase version to 9**

In `AppDatabase.kt`, change the `@Database` annotation:

```kotlin
@Database(entities = [MacroEntity::class, MacroLogEntity::class], version = 9, exportSchema = true)
```

- [ ] **Step 3: Register MIGRATION_8_9 in DatabaseModule**

In `DatabaseModule.kt`, add `MIGRATION_8_9` to the import list and the `.addMigrations(...)` call:

```kotlin
import com.vibeactions.data.db.MIGRATION_8_9
// ...
.addMigrations(
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9
)
```

- [ ] **Step 4: Add AiSendMode enum and fields to Macro.kt**

In `Macro.kt`, add the enum after the existing enums and add two fields to the `Macro` data class:

```kotlin
enum class AiSendMode { APPROVE, AUTO }
```

Add to the end of the `Macro` data class parameter list (after `geofenceTransition`):

```kotlin
/** INCOMING only: when true, replies via Gemini AI instead of the fixed messageBody. */
val aiReplyEnabled: Boolean = false,
/** INCOMING only: APPROVE = notify user to confirm before send; AUTO = send immediately and inform. */
val aiSendMode: AiSendMode = AiSendMode.APPROVE
```

- [ ] **Step 5: Add columns to MacroEntity.kt**

Add two fields at the end of the `MacroEntity` data class (after `geofenceTransition`):

```kotlin
@ColumnInfo(name = "ai_reply_enabled", defaultValue = "0") val aiReplyEnabled: Boolean = false,
@ColumnInfo(name = "ai_send_mode", defaultValue = "APPROVE") val aiSendMode: String = "APPROVE"
```

- [ ] **Step 6: Update Mappers.kt**

In `toDomain()`, add after `geofenceTransition = geofenceTransition`:

```kotlin
aiReplyEnabled = aiReplyEnabled,
aiSendMode = AiSendMode.valueOf(aiSendMode)
```

In `toEntity()`, add after `geofenceTransition = geofenceTransition`:

```kotlin
aiReplyEnabled = aiReplyEnabled,
aiSendMode = aiSendMode.name
```

Add `import com.vibeactions.domain.model.AiSendMode` at the top of `Mappers.kt`.

- [ ] **Step 7: Update MacroJson.kt DTO**

In the `MacroDto` data class, add after `geofenceTransition`:

```kotlin
val aiReplyEnabled: Boolean = false,
val aiSendMode: String = "APPROVE"
```

In `exportMacros`, add after `geofenceTransition = it.geofenceTransition`:

```kotlin
aiReplyEnabled = it.aiReplyEnabled,
aiSendMode = it.aiSendMode.name
```

In `importMacros`, add after `geofenceTransition = it.geofenceTransition`:

```kotlin
aiReplyEnabled = it.aiReplyEnabled,
aiSendMode = AiSendMode.valueOf(it.aiSendMode)
```

Add `import com.vibeactions.domain.model.AiSendMode` at the top of `MacroJson.kt`.

- [ ] **Step 8: Verify build compiles**

```bash
cd ~/VibeActions && ./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: Commit**

```bash
cd ~/VibeActions
git add app/src/main/java/com/vibeactions/data/db/Migrations.kt \
        app/src/main/java/com/vibeactions/data/db/AppDatabase.kt \
        app/src/main/java/com/vibeactions/di/DatabaseModule.kt \
        app/src/main/java/com/vibeactions/domain/model/Macro.kt \
        app/src/main/java/com/vibeactions/data/db/entities/MacroEntity.kt \
        app/src/main/java/com/vibeactions/domain/model/Mappers.kt \
        app/src/main/java/com/vibeactions/util/MacroJson.kt
git commit -m "feat: DB v9 + AiSendMode model (AI reply fields)"
```

---

## Task 2: GeminiClient

Pure HTTP client that calls the Gemini 2.0 Flash REST API. Uses `HttpURLConnection` (no new dependency) and `kotlinx-serialization-json` (already in deps). An internal `parseGeminiResponse()` function is extracted so it can be unit-tested without a network.

**Files:**
- Create: `app/src/main/java/com/vibeactions/util/GeminiClient.kt`
- Create: `app/src/test/java/com/vibeactions/util/GeminiClientTest.kt`

- [ ] **Step 1: Write the failing tests first**

Create `app/src/test/java/com/vibeactions/util/GeminiClientTest.kt`:

```kotlin
package com.vibeactions.util

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class GeminiClientTest {

    @Test fun `parseGeminiResponse extracts text from valid response`() {
        val json = """
            {"candidates":[{"content":{"parts":[{"text":"Hello world"}],"role":"model"}}]}
        """.trimIndent()
        assertEquals("Hello world", parseGeminiResponse(json))
    }

    @Test fun `parseGeminiResponse throws on empty candidates`() {
        val json = """{"candidates":[]}"""
        assertFailsWith<IllegalStateException> { parseGeminiResponse(json) }
    }

    @Test fun `parseGeminiResponse throws on empty parts`() {
        val json = """{"candidates":[{"content":{"parts":[],"role":"model"}}]}"""
        assertFailsWith<IllegalStateException> { parseGeminiResponse(json) }
    }
}
```

- [ ] **Step 2: Run to verify failure**

```bash
cd ~/VibeActions && ./gradlew testDebugUnitTest --tests "com.vibeactions.util.GeminiClientTest" 2>&1 | tail -10
```

Expected: FAILED — `parseGeminiResponse` not yet defined.

- [ ] **Step 3: Create GeminiClient.kt**

Create `app/src/main/java/com/vibeactions/util/GeminiClient.kt`:

```kotlin
package com.vibeactions.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

private const val GEMINI_ENDPOINT =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

@Serializable
private data class GeminiRequest(
    val systemInstruction: GeminiContent? = null,
    val contents: List<GeminiContent>,
    val generationConfig: GenConfig = GenConfig()
)
@Serializable private data class GeminiContent(val parts: List<GeminiPart>, val role: String? = null)
@Serializable private data class GeminiPart(val text: String)
@Serializable private data class GenConfig(val maxOutputTokens: Int = 160)

@Serializable
internal data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())
@Serializable internal data class GeminiCandidate(val content: GeminiContent)

private val geminiJson = Json { ignoreUnknownKeys = true }

/** Extracts the reply text from a raw Gemini JSON response string. Internal so it can be tested. */
internal fun parseGeminiResponse(responseJson: String): String {
    val response = geminiJson.decodeFromString(GeminiResponse.serializer(), responseJson)
    return response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
        ?: error("Empty Gemini response")
}

/**
 * Calls Gemini 2.0 Flash and returns the generated text.
 * Throws on network error or non-2xx HTTP status — callers should wrap in runCatching.
 */
suspend fun geminiGenerate(apiKey: String, systemPrompt: String, userMessage: String): String =
    withContext(Dispatchers.IO) {
        val request = GeminiRequest(
            systemInstruction = if (systemPrompt.isNotBlank())
                GeminiContent(parts = listOf(GeminiPart(systemPrompt))) else null,
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(userMessage))))
        )
        val body = geminiJson.encodeToString(GeminiRequest.serializer(), request)
        val conn = URL("$GEMINI_ENDPOINT?key=$apiKey").openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            outputStream.use { it.write(body.toByteArray()) }
        }
        val responseText = conn.inputStream.bufferedReader().readText()
        parseGeminiResponse(responseText)
    }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd ~/VibeActions && ./gradlew testDebugUnitTest --tests "com.vibeactions.util.GeminiClientTest" 2>&1 | tail -10
```

Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
cd ~/VibeActions
git add app/src/main/java/com/vibeactions/util/GeminiClient.kt \
        app/src/test/java/com/vibeactions/util/GeminiClientTest.kt
git commit -m "feat: GeminiClient — HTTP call + parseGeminiResponse"
```

---

## Task 3: Settings — API Key + System Prompt

Add a "AI (Gemini)" section to SettingsScreen where users can save their API key (masked by default) and an optional system prompt. Stored in SharedPreferences — no Room needed.

**Files:**
- Modify: `app/src/main/java/com/vibeactions/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/vibeactions/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Extend SettingsViewModel**

Replace the full content of `SettingsViewModel.kt`:

```kotlin
package com.vibeactions.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.usecase.SaveMacroUseCase
import com.vibeactions.util.exportMacros
import com.vibeactions.util.importMacros
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: MacroRepository,
    private val save: SaveMacroUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    fun getApiKey(): String = prefs.getString("gemini_api_key", "") ?: ""
    fun saveApiKey(key: String) { prefs.edit().putString("gemini_api_key", key.trim()).apply() }
    fun getSystemPrompt(): String = prefs.getString("gemini_system_prompt", "") ?: ""
    fun saveSystemPrompt(p: String) { prefs.edit().putString("gemini_system_prompt", p.trim()).apply() }

    fun export(onReady: (String) -> Unit) = viewModelScope.launch {
        onReady(exportMacros(repo.observeAll().first()))
    }

    fun import(json: String, onDone: (Int) -> Unit) = viewModelScope.launch {
        val macros = runCatching { importMacros(json) }.getOrDefault(emptyList())
        macros.forEach { save(it) }
        onDone(macros.size)
    }
}
```

- [ ] **Step 2: Add AI section to SettingsScreen**

In `SettingsScreen.kt`, add the following imports at the top of the import block:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
```

Then, in the `Column` of `SettingsScreen`, add this block after the `HorizontalDivider()` that follows the export/import buttons (i.e., just before `Spacer(Modifier.weight(1f))`):

```kotlin
HorizontalDivider()
Text(
    "AI (Gemini 2.0 Flash)",
    style = MaterialTheme.typography.titleSmall,
    modifier = Modifier.padding(vertical = 4.dp)
)

var apiKey by remember { mutableStateOf(vm.getApiKey()) }
var apiKeyVisible by remember { mutableStateOf(false) }
var systemPrompt by remember { mutableStateOf(vm.getSystemPrompt()) }

OutlinedTextField(
    value = apiKey,
    onValueChange = { apiKey = it },
    label = { Text("Gemini API-nøgle") },
    placeholder = { Text("Hent gratis på aistudio.google.com") },
    visualTransformation = if (apiKeyVisible) VisualTransformation.None
        else PasswordVisualTransformation(),
    trailingIcon = {
        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
            Icon(
                if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (apiKeyVisible) "Hide key" else "Show key"
            )
        }
    },
    singleLine = true,
    modifier = Modifier.fillMaxWidth()
)
OutlinedTextField(
    value = systemPrompt,
    onValueChange = { systemPrompt = it },
    label = { Text("Systemprompt (valgfri)") },
    placeholder = { Text("Svar på dansk, hold det kort og venligt") },
    minLines = 2,
    modifier = Modifier.fillMaxWidth()
)
Button(
    onClick = {
        vm.saveApiKey(apiKey)
        vm.saveSystemPrompt(systemPrompt)
        scope.launch { snackbar.showSnackbar("AI-indstillinger gemt") }
    },
    modifier = Modifier.fillMaxWidth()
) { Text("Gem AI-indstillinger") }
```

- [ ] **Step 3: Build to verify**

```bash
cd ~/VibeActions && ./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
cd ~/VibeActions
git add app/src/main/java/com/vibeactions/ui/settings/SettingsViewModel.kt \
        app/src/main/java/com/vibeactions/ui/settings/SettingsScreen.kt
git commit -m "feat: Settings — Gemini API key + system prompt"
```

---

## Task 4: MacroFirer + MacroNotificationManager

Add `overrideBody` and `suppressResultNotification` parameters to `MacroFirer.fire()`. Add `notifyAiApproval()` and `notifyAiSent()` to `MacroNotificationManager`.

**Files:**
- Modify: `app/src/main/java/com/vibeactions/scheduler/MacroFirer.kt`
- Modify: `app/src/main/java/com/vibeactions/notifications/MacroNotificationManager.kt`

- [ ] **Step 1: Update MacroFirer.fire() signature and body**

Replace the full content of `MacroFirer.kt`:

```kotlin
package com.vibeactions.scheduler

import com.vibeactions.data.repository.MacroLogRepository
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.alreadySentToday
import com.vibeactions.domain.model.MacroLog
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.notifications.MacroNotificationManager
import com.vibeactions.sms.SmsDispatcher
import com.vibeactions.util.expandTemplate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MacroFirer @Inject constructor(
    private val macroRepo: MacroRepository,
    private val logRepo: MacroLogRepository,
    private val sms: SmsDispatcher,
    private val notifications: MacroNotificationManager,
    private val alarmScheduler: AlarmScheduler
) {
    /**
     * Sends the macro's SMS, logs, notifies, updates status, and (for scheduled+repeat) re-arms tomorrow.
     * [enforceOncePerDay] guards scheduled fires against alarm+worker double-send; manual taps pass false.
     * [overrideRecipient] (auto-reply) sends to that number instead of the macro's own recipient list.
     * [overrideBody] (AI reply) uses this text directly instead of expanding the macro's messageBody template.
     * [suppressResultNotification] (AI auto-send) skips the standard "Sent/Failed" notification so the
     *   caller can post a richer AI-content notification instead.
     */
    suspend fun fire(
        macroId: String,
        enforceOncePerDay: Boolean,
        overrideRecipient: String? = null,
        overrideBody: String? = null,
        suppressResultNotification: Boolean = false
    ) {
        val macro = macroRepo.getById(macroId) ?: return
        if (!macro.enabled) return
        val now = System.currentTimeMillis()
        if (enforceOncePerDay && alreadySentToday(macro.lastScheduledFireAt, now)) return

        val targets = overrideRecipient?.let { listOf(it) } ?: macro.recipients
        if (targets.isEmpty()) return
        // Use overrideBody directly (AI-generated); otherwise expand template tokens.
        val body = overrideBody ?: expandTemplate(macro.messageBody, LocalDateTime.now(), macro.name)
        val failures = targets.mapNotNull { number ->
            sms.send(number, body).exceptionOrNull()?.let { number to it }
        }
        val status = if (failures.isEmpty()) MacroStatus.SUCCESS else MacroStatus.FAILED
        val error = when {
            failures.isEmpty() -> null
            failures.size == targets.size -> failures.first().second.message ?: "send failed"
            else -> "${failures.size}/${targets.size} failed: " + failures.first().second.message
        }

        macroRepo.updateStatus(macro.id, now, status)
        if (enforceOncePerDay) macroRepo.updateScheduledFireAt(macro.id, now)
        logRepo.add(
            MacroLog(
                macroId = macro.id, triggeredAt = now, status = status,
                messagePreview = body.take(40), errorMessage = error
            )
        )
        if (!suppressResultNotification) notifications.notifyResult(macro, status, error)

        if (macro.triggerType == TriggerType.SCHEDULED && macro.repeatDaily) {
            alarmScheduler.schedule(macro.copy(lastTriggeredAt = now, lastStatus = status))
        }
    }
}
```

- [ ] **Step 2: Add notifyAiApproval and notifyAiSent to MacroNotificationManager**

Add the following imports at the top of `MacroNotificationManager.kt`:

```kotlin
import com.vibeactions.scheduler.AiReplyActionReceiver
import com.vibeactions.util.maskPhone
```

Add these two methods before the companion object in `MacroNotificationManager`:

```kotlin
/** Posts a notification with Send/Discard action buttons for APPROVE-mode AI replies. */
fun notifyAiApproval(macro: Macro, recipient: String, generatedBody: String) {
    val notifId = ("ai_approve" + macro.id + recipient).hashCode() and 0x7FFFFFFF
    val preview = if (generatedBody.length > 200) generatedBody.take(200) + "…" else generatedBody

    val sendIntent = Intent(context, AiReplyActionReceiver::class.java).apply {
        action = AiReplyActionReceiver.ACTION_AI_SEND
        putExtra(AiReplyActionReceiver.EXTRA_MACRO_ID, macro.id)
        putExtra(AiReplyActionReceiver.EXTRA_RECIPIENT, recipient)
        putExtra(AiReplyActionReceiver.EXTRA_BODY, generatedBody)
        putExtra(AiReplyActionReceiver.EXTRA_NOTIF_ID, notifId)
    }
    val sendPi = PendingIntent.getBroadcast(
        context, ("ai_send" + macro.id + recipient).hashCode() and 0x7FFFFFFF, sendIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val discardIntent = Intent(context, AiReplyActionReceiver::class.java).apply {
        action = AiReplyActionReceiver.ACTION_AI_DISCARD
        putExtra(AiReplyActionReceiver.EXTRA_NOTIF_ID, notifId)
    }
    val discardPi = PendingIntent.getBroadcast(
        context, ("ai_discard" + macro.id + recipient).hashCode() and 0x7FFFFFFF, discardIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setContentTitle("AI-svar klar til ${maskPhone(recipient)}")
        .setContentText(preview)
        .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
        .setAutoCancel(false)
        .addAction(0, "Send", sendPi)
        .addAction(0, "Slet", discardPi)
    manager.notify(notifId, builder.build())
}

/** Posts an informational notification after an AUTO-mode AI reply is sent (shows content). */
fun notifyAiSent(macro: Macro, recipient: String, sentBody: String) {
    val preview = if (sentBody.length > 200) sentBody.take(200) + "…" else sentBody
    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setContentTitle("AI-svar sendt til ${maskPhone(recipient)}")
        .setContentText(preview)
        .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
        .setAutoCancel(true)
    manager.notify(("ai_sent" + macro.id + recipient).hashCode() and 0x7FFFFFFF, builder.build())
}
```

- [ ] **Step 3: Build (AiReplyActionReceiver import will fail — that's expected for now)**

```bash
cd ~/VibeActions && ./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

Expected: compile error on `AiReplyActionReceiver` — it doesn't exist yet. Continue to Task 5.

---

## Task 5: AiReplyActionReceiver + Manifest

Handle the Send/Discard tap from the approval notification.

**Files:**
- Create: `app/src/main/java/com/vibeactions/scheduler/AiReplyActionReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create AiReplyActionReceiver.kt**

```kotlin
package com.vibeactions.scheduler

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AiReplyActionReceiver : BroadcastReceiver() {
    @Inject lateinit var firer: MacroFirer

    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        if (notifId != -1) {
            context.getSystemService(NotificationManager::class.java)?.cancel(notifId)
        }
        if (intent.action != ACTION_AI_SEND) return

        val macroId = intent.getStringExtra(EXTRA_MACRO_ID) ?: return
        val recipient = intent.getStringExtra(EXTRA_RECIPIENT) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Standard fire: notifyResult shows "Sent: MacroName" so user knows it went through.
                firer.fire(macroId, enforceOncePerDay = false,
                    overrideRecipient = recipient, overrideBody = body)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_AI_SEND = "com.vibeactions.AI_SEND"
        const val ACTION_AI_DISCARD = "com.vibeactions.AI_DISCARD"
        const val EXTRA_MACRO_ID = "macro_id"
        const val EXTRA_RECIPIENT = "recipient"
        const val EXTRA_BODY = "body"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}
```

- [ ] **Step 2: Register in AndroidManifest.xml**

Inside the `<application>` block, after the existing `<receiver android:name=".scheduler.GeofenceReceiver" .../>` line, add:

```xml
<receiver android:name=".scheduler.AiReplyActionReceiver" android:exported="false" />
```

- [ ] **Step 3: Build — should now compile clean**

```bash
cd ~/VibeActions && ./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit Tasks 4 + 5 together**

```bash
cd ~/VibeActions
git add app/src/main/java/com/vibeactions/scheduler/MacroFirer.kt \
        app/src/main/java/com/vibeactions/notifications/MacroNotificationManager.kt \
        app/src/main/java/com/vibeactions/scheduler/AiReplyActionReceiver.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat: MacroFirer overrideBody, AI notification helpers, AiReplyActionReceiver"
```

---

## Task 6: SmsReplyReceiver — AI Branch

When a matching INCOMING macro has `aiReplyEnabled = true`, call Gemini instead of firing the fixed body. Branch on `aiSendMode`.

**Files:**
- Modify: `app/src/main/java/com/vibeactions/scheduler/SmsReplyReceiver.kt`

- [ ] **Step 1: Replace SmsReplyReceiver.kt**

```kotlin
package com.vibeactions.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.AiSendMode
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.notifications.MacroNotificationManager
import com.vibeactions.util.geminiGenerate
import com.vibeactions.util.incomingMatches
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Listens for incoming SMS and fires any matching INCOMING (auto-reply) macro, replying to the
 * sender. A short per-(macro, sender) throttle prevents two auto-replying phones from ping-ponging.
 * When aiReplyEnabled, calls Gemini to generate the reply; falls back to messageBody on error.
 */
@AndroidEntryPoint
class SmsReplyReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: MacroRepository
    @Inject lateinit var firer: MacroFirer
    @Inject lateinit var notifications: MacroNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }
        if (body.isBlank()) return

        val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                repo.getEnabledByTrigger(TriggerType.INCOMING)
                    .filter { incomingMatches(it, sender, body) }
                    .forEach { macro ->
                        val key = macro.id + "|" + sender.filter { c -> c.isDigit() }
                        val last = lastReply[key]
                        if (last != null && now - last < THROTTLE_MS) return@forEach
                        lastReply[key] = now

                        if (macro.aiReplyEnabled) {
                            val apiKey = prefs.getString("gemini_api_key", "") ?: ""
                            if (apiKey.isBlank()) {
                                // No key configured — fall back to fixed body.
                                firer.fire(macro.id, enforceOncePerDay = false,
                                    overrideRecipient = sender)
                                return@forEach
                            }
                            val systemPrompt = prefs.getString("gemini_system_prompt", "") ?: ""
                            val generated = runCatching {
                                geminiGenerate(apiKey, systemPrompt, body)
                            }.getOrElse { macro.messageBody }

                            when (macro.aiSendMode) {
                                AiSendMode.APPROVE -> {
                                    notifications.notifyAiApproval(macro, sender, generated)
                                }
                                AiSendMode.AUTO -> {
                                    firer.fire(
                                        macro.id, enforceOncePerDay = false,
                                        overrideRecipient = sender,
                                        overrideBody = generated,
                                        suppressResultNotification = true
                                    )
                                    notifications.notifyAiSent(macro, sender, generated)
                                }
                            }
                        } else {
                            firer.fire(macro.id, enforceOncePerDay = false, overrideRecipient = sender)
                        }
                    }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val THROTTLE_MS = 60_000L
        private val lastReply = mutableMapOf<String, Long>()
    }
}
```

- [ ] **Step 2: Build and run all tests**

```bash
cd ~/VibeActions && ./gradlew assembleDebug testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, all tests PASS.

- [ ] **Step 3: Commit**

```bash
cd ~/VibeActions
git add app/src/main/java/com/vibeactions/scheduler/SmsReplyReceiver.kt
git commit -m "feat: SmsReplyReceiver — AI branch with Gemini, APPROVE/AUTO modes"
```

---

## Task 7: Editor UI — AI Toggle + Compose Button

INCOMING macros get an "AI-svar" toggle and send-mode dropdown. All macros get a sparkle "AI-skriv" button next to the message field.

**Files:**
- Modify: `app/src/main/java/com/vibeactions/ui/editor/MacroEditorViewModel.kt`
- Modify: `app/src/main/java/com/vibeactions/ui/editor/MacroEditorScreen.kt`

- [ ] **Step 1: Extend EditorState and MacroEditorViewModel**

In `MacroEditorViewModel.kt`:

1. Add import: `import com.vibeactions.domain.model.AiSendMode`

2. Add two fields to `EditorState` after `cardColor`:

```kotlin
val aiReplyEnabled: Boolean = false,
val aiSendMode: AiSendMode = AiSendMode.APPROVE
```

3. In `load()`, inside the `_state.value = EditorState(...)` call, add after `cardColor = ...`:

```kotlin
aiReplyEnabled = m.aiReplyEnabled,
aiSendMode = m.aiSendMode,
```

4. In `save()`, inside the `Macro(...)` constructor call, add after `cardColor = s.cardColor`:

```kotlin
aiReplyEnabled = if (s.triggerType == TriggerType.INCOMING) s.aiReplyEnabled else false,
aiSendMode = s.aiSendMode,
```

- [ ] **Step 2: Add state variables to MacroEditorScreen**

In `MacroEditorScreen.kt`, add these imports:

```kotlin
import androidx.compose.material.icons.filled.AutoAwesome
import com.vibeactions.domain.model.AiSendMode
```

In the state variable block at the top of `MacroEditorScreen` (after `var triggerExpanded`), add:

```kotlin
var aiModeExpanded by remember { mutableStateOf(false) }
var showAiCompose by remember { mutableStateOf(false) }
```

- [ ] **Step 3: Add AI toggle + send-mode dropdown to INCOMING section**

In `MacroEditorScreen.kt`, in the `if (s.triggerType == TriggerType.INCOMING)` block, add after the `matchKeyword` OutlinedTextField (before the closing brace of the `if`):

```kotlin
HorizontalDivider()
Row(verticalAlignment = Alignment.CenterVertically) {
    Text("AI-svar (Gemini)", modifier = Modifier.weight(1f))
    Switch(
        checked = s.aiReplyEnabled,
        onCheckedChange = { v -> vm.update { it.copy(aiReplyEnabled = v) } }
    )
}
if (s.aiReplyEnabled) {
    ExposedDropdownMenuBox(
        expanded = aiModeExpanded,
        onExpandedChange = { aiModeExpanded = it }
    ) {
        OutlinedTextField(
            value = if (s.aiSendMode == AiSendMode.AUTO)
                "Send automatisk og informer" else "Godkend inden afsendelse",
            onValueChange = {},
            readOnly = true,
            label = { Text("AI afsendelse") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aiModeExpanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = aiModeExpanded,
            onDismissRequest = { aiModeExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Godkend inden afsendelse") },
                onClick = {
                    vm.update { it.copy(aiSendMode = AiSendMode.APPROVE) }
                    aiModeExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Send automatisk og informer") },
                onClick = {
                    vm.update { it.copy(aiSendMode = AiSendMode.AUTO) }
                    aiModeExpanded = false
                }
            )
        }
    }
    Text(
        "Besked-feltet nedenfor bruges som fallback hvis Gemini ikke svarer.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
}
```

- [ ] **Step 4: Add AI-compose button below the message field**

In `MacroEditorScreen.kt`, find the message `OutlinedTextField` block. After its closing `)`, add:

```kotlin
Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
    TextButton(onClick = { showAiCompose = true }) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text("AI-skriv", style = MaterialTheme.typography.labelMedium)
    }
}
```

- [ ] **Step 5: Add AI-compose dialog**

At the end of `MacroEditorScreen`, just before the final closing `}` of the function (alongside the existing `if (showTime)`, `if (showDate)`, `if (showExpiry)` dialogs), add:

```kotlin
if (showAiCompose) {
    var aiPrompt by remember { mutableStateOf("") }
    var aiLoading by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!aiLoading) showAiCompose = false },
        title = { Text("AI-skriv besked") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (aiLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text("Genererer…", style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedTextField(
                        value = aiPrompt,
                        onValueChange = { aiPrompt = it },
                        label = { Text("Beskriv beskeden") },
                        placeholder = { Text("fx: venlig reminder om møde kl. 14") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = aiPrompt.isNotBlank() && !aiLoading,
                onClick = {
                    val prefs = ctx.getSharedPreferences("ai_settings", android.content.Context.MODE_PRIVATE)
                    val key = prefs.getString("gemini_api_key", "") ?: ""
                    if (key.isBlank()) {
                        android.widget.Toast.makeText(
                            ctx, "Tilføj Gemini API-nøgle i Indstillinger", android.widget.Toast.LENGTH_SHORT
                        ).show()
                        showAiCompose = false
                        return@TextButton
                    }
                    aiLoading = true
                    val systemPrompt = prefs.getString("gemini_system_prompt", "") ?: ""
                    scope.launch {
                        val result = runCatching {
                            com.vibeactions.util.geminiGenerate(key, systemPrompt, aiPrompt)
                        }
                        aiLoading = false
                        result.getOrNull()?.let { generated ->
                            vm.update { it.copy(message = generated) }
                        }
                        showAiCompose = false
                    }
                }
            ) { Text("Generer") }
        },
        dismissButton = {
            TextButton(onClick = { if (!aiLoading) showAiCompose = false }) { Text("Annuller") }
        }
    )
}
```

- [ ] **Step 6: Build to verify**

```bash
cd ~/VibeActions && ./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
cd ~/VibeActions
git add app/src/main/java/com/vibeactions/ui/editor/MacroEditorViewModel.kt \
        app/src/main/java/com/vibeactions/ui/editor/MacroEditorScreen.kt
git commit -m "feat: editor — AI-svar toggle, send-mode dropdown, AI-skriv compose button"
```

---

## Task 8: MacroCard AI Badge

Show a small "AI" pill on INCOMING cards when `aiReplyEnabled = true` so the list makes it clear which auto-reply macros use AI.

**Files:**
- Modify: `app/src/main/java/com/vibeactions/ui/common/MacroCard.kt`

- [ ] **Step 1: Update the INCOMING branch in MacroCard**

In `MacroCard.kt`, replace the `TriggerType.INCOMING ->` branch:

```kotlin
TriggerType.INCOMING -> {
    Column(Modifier.weight(1f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Auto-reply", fontFamily = JetBrainsMono,
                color = OnSurface, fontSize = 16.sp)
            if (macro.aiReplyEnabled) {
                Surface(
                    color = accent.copy(alpha = 0.2f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "AI",
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Text(
            autoReplySummary(macro.matchSender, macro.matchKeyword),
            color = OnSurfaceVariant, fontSize = 11.sp
        )
    }
}
```

- [ ] **Step 2: Build and run all tests**

```bash
cd ~/VibeActions && ./gradlew assembleDebug testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, all tests PASS.

- [ ] **Step 3: Commit**

```bash
cd ~/VibeActions
git add app/src/main/java/com/vibeactions/ui/common/MacroCard.kt
git commit -m "feat: MacroCard — AI badge on INCOMING macros with aiReplyEnabled"
```

---

## Task 9: Final verification + push

- [ ] **Step 1: Clean build + full test run**

```bash
cd ~/VibeActions && ./gradlew clean assembleDebug testDebugUnitTest 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`, all tests PASS (existing 22+ plus 3 new GeminiClientTest).

- [ ] **Step 2: Push to remote**

```bash
cd ~/VibeActions && git push
```

- [ ] **Step 3: On-device verification checklist**

Install:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Test sequence:
1. **Settings** → Enter a real Gemini API key from aistudio.google.com → Enter systemprompt "Svar på dansk, vær kort" → tap "Gem AI-indstillinger" → snackbar shows "gemt".
2. **New INCOMING macro** → trigger = Auto-reply → toggle "AI-svar" on → select "Godkend inden afsendelse" → save.
3. Send an SMS to the test device that matches the macro → notification appears with "AI-svar klar til [sender]" + generated preview + "Send"/"Slet" buttons → tap "Send" → SMS delivered to sender.
4. Edit the macro → change to "Send automatisk og informer" → save → send another matching SMS → no approval prompt; reply fires within ~5 s, info notification shows "AI-svar sendt til [sender]: [content]".
5. **Editor AI-compose** → open any macro → tap "AI-skriv" → type "skriv en venlig reminder om møde kl. 14" → tap "Generer" → message field fills with generated text.
6. **No API key test** → clear the key in Settings → tap "AI-skriv" in editor → toast: "Tilføj Gemini API-nøgle i Indstillinger".
7. **MacroCard** → INCOMING macro with AI enabled shows "AI" pill in card list.

---

## Self-Review Notes

- `AiSendMode` enum is defined in `Macro.kt` and imported in `Mappers.kt`, `MacroJson.kt`, `SmsReplyReceiver.kt`, `MacroEditorViewModel.kt`, `MacroEditorScreen.kt` — consistent across all tasks. ✓
- `MacroNotificationManager.notifyAiApproval` references `AiReplyActionReceiver` constants — `AiReplyActionReceiver` is created in Task 5 and `MacroNotificationManager` changes are committed together with it in Task 4+5. ✓
- `MacroFirer.fire()` new parameters are all optional with defaults — all existing callers (AlarmReceiver, CatchUpWorker, widget, etc.) require no changes. ✓
- `SmsReplyReceiver` now injects `MacroNotificationManager` (new) — Hilt will inject it since both are `@Singleton`/`@AndroidEntryPoint`. ✓
- SharedPreferences key `"ai_settings"` with string `"gemini_api_key"` / `"gemini_system_prompt"` is used consistently in `SettingsViewModel`, `SmsReplyReceiver`, and `MacroEditorScreen`. ✓
- `ctx` in `MacroEditorScreen` is declared at the top of the composable (moved from LOCATION block in a prior session) — available to the AI compose dialog. ✓
- `scope` in `MacroEditorScreen` is `rememberCoroutineScope()` declared in an earlier session — available to the AI compose dialog. ✓
