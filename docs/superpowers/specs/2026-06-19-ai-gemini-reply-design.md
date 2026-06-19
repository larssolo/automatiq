# AI Gemini Reply — Design Spec
**Date:** 2026-06-19  
**App:** Automatiq (com.vibeactions)  
**DB version after:** 9

---

## Problem / Motivation

Auto-reply macros currently send a fixed message body. The user wants the app to generate contextual SMS replies using AI (Gemini 2.0 Flash) — either requiring approval via notification, or sending automatically with an informational notification after the fact.

---

## Scope

Three connected features:

1. **AI auto-reply** — INCOMING macros can delegate the reply body to Gemini 2.0 Flash. Two send modes per macro: *Approve* (notification with Send/Discard) or *Auto-send* (sends immediately, info notification with content).
2. **AI compose** — A sparkle icon button in the macro editor message field. User describes what they want; AI fills the field. User edits and saves as normal.
3. **Settings** — Gemini API key (masked text field) + optional global system prompt stored in SharedPreferences.

Out of scope: conversation history, on-device Nano fallback, multiple AI providers.

---

## Data Model

### DB Migration 8 → 9

Two new columns on `macros`:

```sql
ALTER TABLE macros ADD COLUMN ai_reply_enabled INTEGER NOT NULL DEFAULT 0;
ALTER TABLE macros ADD COLUMN ai_send_mode TEXT NOT NULL DEFAULT 'APPROVE';
```

`ai_send_mode` values: `APPROVE` | `AUTO`.

### Macro domain model additions

```kotlin
val aiReplyEnabled: Boolean = false
val aiSendMode: AiSendMode = AiSendMode.APPROVE

enum class AiSendMode { APPROVE, AUTO }
```

Propagates through: `MacroEntity`, `Mappers`, `MacroJson` DTO, `EditorState`, `MacroEditorViewModel`.

---

## Architecture

### New files

| File | Responsibility |
|---|---|
| `util/GeminiClient.kt` | Single suspend fun `generate(apiKey, systemPrompt, userMessage): String`. Uses `HttpURLConnection` + `kotlinx-serialization-json` (already in deps). No new library dependency. |
| `scheduler/AiReplyActionReceiver.kt` | `BroadcastReceiver` for notification action intents: `ACTION_AI_SEND` and `ACTION_AI_DISCARD`. On send: calls `MacroFirer.fire()` with pre-generated message as override body. |
| `scheduler/MacroFirer.kt` | Add `overrideBody: String? = null` parameter. When set, skips `expandTemplate` and uses the provided text directly as the SMS body. |

### Modified files

| File | Change |
|---|---|
| `data/db/Migrations.kt` | `MIGRATION_8_9` |
| `data/db/AppDatabase.kt` | version 8 → 9 |
| `di/DatabaseModule.kt` | add `MIGRATION_8_9` |
| `data/db/entities/MacroEntity.kt` | two new `@ColumnInfo` fields |
| `domain/model/Macro.kt` | `aiReplyEnabled`, `aiSendMode`, `AiSendMode` enum |
| `domain/model/Mappers.kt` | map new fields |
| `util/MacroJson.kt` | DTO fields with defaults for import compat |
| `scheduler/SmsReplyReceiver.kt` | after match: if `aiReplyEnabled`, call `GeminiClient`; branch on `aiSendMode` |
| `ui/editor/MacroEditorScreen.kt` | INCOMING section: AI toggle + send-mode dropdown; all macros: AI compose button next to message field |
| `ui/editor/MacroEditorViewModel.kt` | `EditorState` fields + `save()` mapping |
| `ui/common/MacroCard.kt` | INCOMING card: show "AI" badge when `aiReplyEnabled` |
| `ui/settings/SettingsScreen.kt` | API key field + system prompt field |
| `ui/settings/SettingsViewModel.kt` | read/write from SharedPreferences |
| `AndroidManifest.xml` | register `AiReplyActionReceiver` |

---

## Gemini API call

```
POST https://generativelanguage.googleapis.com/v1beta/models/
     gemini-2.0-flash:generateContent?key={API_KEY}
Content-Type: application/json

{
  "systemInstruction": { "parts": [{ "text": "{systemPrompt}" }] },
  "contents": [{ "parts": [{ "text": "{incomingMessage}" }] }],
  "generationConfig": { "maxOutputTokens": 160 }
}
```

Response: `candidates[0].content.parts[0].text`

`maxOutputTokens: 160` keeps replies SMS-sized. If API key is missing or call fails, falls back to the macro's fixed `messageBody`.

---

## Notification flows

### APPROVE mode

1. `SmsReplyReceiver` matches macro → calls `GeminiClient.generate()` (on IO dispatcher via `goAsync()` + coroutine)
2. Posts notification:
   - Title: `"AI-svar klar til [sender]"`
   - Content: first 100 chars of generated text
   - Action 1 **Send** → `AiReplyActionReceiver` with `ACTION_AI_SEND` + extras: `macroId`, `recipient`, `generatedBody`
   - Action 2 **Slet** → `AiReplyActionReceiver` with `ACTION_AI_DISCARD`

### AUTO mode

1. `SmsReplyReceiver` matches → calls `GeminiClient.generate()`
2. Calls `MacroFirer` directly with `overrideBody = generatedText`
3. Posts info-only notification:
   - Title: `"AI-svar sendt til [sender]"`
   - Content: first 100 chars of sent text
   - No action buttons; tap opens app

### Fallback (API error / missing key)

Falls back to `macro.messageBody` in both modes. Logs the fallback reason via existing `MacroLogRepository`.

---

## AI Compose (editor)

A sparkle `✦` `IconButton` placed at the end of the message `OutlinedTextField`'s `trailingIcon`. Tap → `AlertDialog` with a single text field: "Beskriv hvad du vil skrive". Confirm → calls `GeminiClient.generate(apiKey, systemPrompt, userInstruction)` → fills `s.message`. User reviews and saves.

If API key is not set, the button shows a toast: "Tilføj Gemini API-nøgle i indstillinger".

---

## Settings

In `SettingsScreen`, new section "AI (Gemini)":

- **API-nøgle** — `OutlinedTextField`, `visualTransformation = PasswordVisualTransformation()`, trailing eye-toggle to reveal. Stored in `SharedPreferences` key `gemini_api_key`.
- **Systemprompt** — `OutlinedTextField`, multi-line, placeholder: "Svar på dansk, hold det kort og venligt". Stored as `gemini_system_prompt`. Default empty (Gemini answers without a system instruction if blank).

---

## Error handling

| Scenario | Behaviour |
|---|---|
| No API key | AI compose: toast. Auto-reply: fall back to fixed body, no notification. |
| Network error / timeout | Fall back to fixed body; log `FAILED` with reason. |
| Gemini returns empty / blocked | Fall back to fixed body; log with reason. |
| API key invalid (401/403) | Same as network error; consider showing a persistent SettingsBanner. |

---

## Verification

- `./gradlew testDebugUnitTest` — all existing tests pass; new `GeminiClientTest` mocks `HttpURLConnection` and asserts JSON parsing.
- `./gradlew assembleDebug` — clean build, no new warnings.
- On-device:
  - Settings → enter API key → send a test SMS to the device → APPROVE macro: notification appears with AI text; tap Send → SMS delivered. Tap Slet → no SMS.
  - AUTO macro: SMS arrives, reply sent within ~3 s, info notification shows content.
  - Editor → AI compose button → describe message → field fills → save → manual trigger → correct text sent.
  - No API key → auto-reply falls back to fixed body, no crash.
