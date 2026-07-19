<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="Automatiq icon" />

# Automatiq

**On-device SMS automation for Android — scheduled, manual, auto-reply, missed-call, location & device-state triggers, with optional AI-generated replies.**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![License](https://img.shields.io/badge/license-personal-lightgrey)](#)

</div>

---

## What it does

Automatiq lets you define SMS macros — a name, one or more recipients, and a message — and fire them automatically. A macro can trigger on a **schedule**, on a **button tap**, as an **auto-reply** to an incoming SMS, when you **miss (or decline) a call**, when you **arrive at / leave a place**, or on a **device state change** — plugging in the charger, connecting a Bluetooth device or a Wi-Fi network. Macros can be organized into nameable **folders** with a per-folder master switch. Auto-replies can answer with a fixed text or with an **AI-generated reply** (Gemini) — either sent automatically or held for your approval.

Everything runs on-device. No account, no server. Only two optional features reach outside the phone: the **location trigger** (Google Play Services geofencing) and **AI replies** (Google's Gemini API, with your own free key).

```
┌─────────────────────────────────────────────────────────┐
│  Macro: "Morning check-in"                              │
│  → +45 xx xx 78  ·  "Hi {navn}, on my way! ({ugedag})" │
│  → Every Monday + Wednesday · 08:30 · Every 2 weeks    │
│  → Until 2026-08-01                                     │
└─────────────────────────────────────────────────────────┘
         ↓  AlarmManager (precise)
         ↓  WorkManager catch-up (resilience)
         ↓  SmsManager → radio sent receipt → log + status
```

---

## Trigger types

| Type | Fires when… |
|---|---|
| ⏰ **Scheduled** | a set time arrives, on chosen weekdays, every N weeks — with an optional expiry date |
| 👆 **Manual** | you tap the macro in the list or a home-screen widget |
| 💬 **Auto-reply** | an incoming SMS matches an optional sender and/or keyword — replies to the sender with a fixed text or an AI-generated answer |
| 📍 **Location** | you enter or leave a geofenced place (radius of your choosing) |
| 📵 **Missed call** | a call rings out or is declined — texts the caller back, with an optional caller filter |
| 🔌 **Charging / Bluetooth / Wi-Fi** | the phone is plugged in/unplugged, or connects/disconnects a chosen Bluetooth device or Wi-Fi network |

---

## Features

| | Feature |
|---|---|
| 🤖 | **AI auto-replies (Gemini)** — per macro: approve each reply before it goes out, or send automatically and get notified. An optional per-macro instruction steers tone and length |
| ✨ | **AI compose** — describe the message in the editor and pick between three generated suggestions |
| 👥 | **Multiple recipients** — one macro sends to a whole list of numbers |
| 🔤 | **Message variables** — `{dato}` `{tid}` `{ugedag}` `{navn}` are filled in at send time; reply macros also get `{afsender}` (the other party's number) |
| ⏳ | **Expiry date** — a scheduled macro stops firing after a date you pick |
| 💬 | **Auto-reply** — react to incoming SMS by sender/keyword, with a loop guard |
| 📍 | **Location triggers** — arrive/depart a place, re-armed after reboot and app updates |
| 🔁 | **Dual scheduling engine** — AlarmManager for precision + WorkManager catch-up, self-healing on every app start, so nothing is missed after Doze, reboot or an update |
| 📅 | **Per-weekday control** — pick any combination of Mon–Sun per macro |
| 🗓️ | **Every-N-weeks recurrence** — every week, every other week, every 3 or 4 weeks |
| 🏠 | **Home-screen widgets** — one widget per macro, tap to send; the subtitle shows the last send ("Last: 14:32 ✓") and stays in sync with scheduled and auto sends. A separate app-shortcut widget launches any app with one tap |
| 📬 | **Radio-level send status** — a sent receipt from the radio flips the log and notification to *failed* if the network dropped the message after dispatch |
| ✓✓ | **Delivery receipts** — when the carrier reports delivery, the log shows "Delivered ✓✓" (or "Not delivered") |
| 🌙 | **Quiet hours** — auto-replies received in the window aren't dropped but answered right after it ends: one reply per person, to their latest message |
| 📵 | **Missed-call auto-text** — "I'll call you back" to anyone you couldn't pick up for; declined calls count too |
| 🩺 | **Health screen** — exact-alarm, battery and notification status with one-tap fixes, plus each macro's next fire time |
| 🔍 | **Search & log filters** — find macros by name; filter the log by status and by macro |
| 📁 | **Folders** — nameable accordion cards in the list; drag macros in (the drop position decides membership), master enable/disable switch per folder, Undo-friendly delete |
| 🔔 | **Notifications** — result (success / failed) + one-tap retry on failure; message content is kept off the lock screen |
| ⏯️ | **Quick actions** — a one-tap send button on every card; long-press for Delete / Duplicate / Send now / Enable-Disable |
| 🖼️ | **Living dark UI** — a static background with hue/saturation and card-opacity sliders, topped by a slow aurora of drifting accent glows; cards breathe while armed |
| 📋 | **Execution log** — full chronological history per macro, filterable |
| 📤 | **JSON export / import** — full backup of macros *and* settings (API key only if you opt in) via system file picker; legacy macro-only files still import |
| ↕️ | **Drag-and-drop reorder** — visible drag handle on every card |
| 🎨 | **Per-macro colours** — each card gets a random pastel accent |
| 🔋 | **Battery-optimisation prompt** — guides you through the whitelist so alarms survive aggressive OEM killers |

---

## AI replies (Gemini)

Auto-reply macros can hand the incoming message to Google's **Gemini** and reply with a generated answer. Two modes, chosen per macro:

- **Approve before sending** (default) — a heads-up notification (or an in-app dialog) shows the generated reply; you can edit it, send it, or discard it.
- **Send automatically & inform** — the reply goes out on its own and a notification tells you what was sent. A per-day dedup guard ensures a retried background job never sends the same reply twice.

A per-macro instruction (e.g. *"Svar kort og venligt på dansk, maks. 1 sætning"*) steers the reply; otherwise the global system prompt from Settings is used. If the API is unreachable or no key is configured, the macro falls back to its fixed message text — auto-replies never silently fail.

### Getting a free Gemini API key

The AI features use Google's free tier — no credit card or billing account required:

1. Go to **[aistudio.google.com](https://aistudio.google.com)** and sign in with any Google account.
2. Click **"Get API key"** (in the left menu or top bar), then **"Create API key"**. Choose *Create API key in new project* if asked.
3. Copy the key (it starts with `AIza…`).
4. In Automatiq: **Settings → AI (Gemini)** → paste the key → tap **"Test nøgle"** to verify it works → **"Gem"**.

Notes:

- The free tier is rate-limited per model (roughly 10–15 requests/minute — plenty for SMS replies). If the key test reports **quota 0**, that model isn't free-tier-enabled for your project/region: just pick another model in the dropdown (e.g. `gemini-2.5-flash-lite` or `gemini-2.0-flash`).
- The key is stored only on the device, sent to Google exclusively as a request header, never written to logs, and **excluded from Android cloud backups and device transfers**.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                            UI Layer                                  │
│  MacroListScreen · MacroEditorScreen · LogScreen · SettingsScreen    │
│  MacroCard · ThemedSwitch · PermissionBanner  (Jetpack Compose)      │
└────────────────────────────┬─────────────────────────────────────────┘
                             │ ViewModels (Hilt)
┌────────────────────────────▼─────────────────────────────────────────┐
│                          Domain Layer                                │
│  SaveMacroUseCase · DeleteMacroUseCase · ToggleMacroUseCase         │
│  TriggerMacroUseCase · RescheduleAllUseCase · IdempotencyGuard      │
└────────────┬───────────────────────────────────────┬─────────────────┘
             │                                       │
┌────────────▼─────────────────────────┐ ┌───────────▼─────────────────┐
│          Scheduler / Triggers         │ │          Data Layer          │
│  AlarmScheduler · MacroAlarmReceiver  │ │  MacroRepository             │
│  MacroCatchUpWorker · BootReceiver    │ │  MacroLogRepository          │
│  SmsReplyReceiver · IncomingRouter    │ │  Room DB (v13)               │
│  GeminiReplyWorker · DeferredReplyWkr │ │  MacroEntity · MacroLogEntity│
│  CallStateReceiver  (missed calls)    │ │  AppSettings (quiet hours)   │
│  TriggerMonitorService (state trig.)  │ │  Migrations 1→…→13           │
│  GeofenceManager · GeofenceReceiver   │ │                              │
│  SmsSentReceiver · SmsDeliveredRecv   │ │                              │
│  MacroFirer  ←── single send path ────┼─┤                              │
└────────────┬──────────────────────────┘ └─────────────────────────────┘
             │
┌────────────▼─────────────────────────────────────────────────────────┐
│                        System / Android                              │
│  AlarmManager.setAlarmClock()  ·  WorkManager (periodic + expedited) │
│  SmsManager (multipart + sent receipts) · Telephony SMS_RECEIVED     │
│  Play Services Geofencing · Gemini API · AppWidgetProvider           │
└──────────────────────────────────────────────────────────────────────┘
```

`MacroFirer.fire()` is the one path every trigger funnels through — alarm, catch-up worker, manual tap, widget, auto-reply (fixed and AI), missed call, device-state trigger, and geofence all call it.

### Scheduling engine

Scheduled sends use two complementary engines so they're never silently dropped:

1. **`AlarmManager.setAlarmClock()`** — Doze-exempt exact alarm; fires precisely at the scheduled time.
2. **`MacroCatchUpWorker`** — hourly WorkManager job that checks all enabled scheduled macros and sends any that weren't fired yet today.

An atomic claim (`claimScheduledFire`, a check-and-set UPDATE keyed on local midnight) ensures alarm and catch-up can never double-send. A manual tap writes a separate `lastTriggeredAt` field so it never consumes the day's scheduled send slot. Weekday, every-N-weeks recurrence, and the expiry date are all evaluated by the shared `isScheduledDay` rule.

Alarms and geofences are silently cleared by Android on reboot **and on every app update** — `BootReceiver` handles both events (`BOOT_COMPLETED` + `MY_PACKAGE_REPLACED`), and the app additionally re-arms everything on each launch as a self-heal.

### Send pipeline & delivery status

"Handed to `SmsManager`" is not the same as "sent". Every dispatched SMS carries a **sent receipt** (`PendingIntent`) addressed to `SmsSentReceiver` with the log entry's id. The log row is created *before* sending; if the radio later reports a failure (no service, flight mode, SMS limit, …) the entry and the macro's status flip to **FAILED** with the radio's reason, a corrective notification is posted, and bound widgets refresh. Success receipts are no-ops — the dispatch path already finalized the entry.

Each SMS also requests a **delivery report**. When the carrier sends one, the log entry gains a delivery status — shown as *"Delivered ✓✓"* or *"Not delivered"*. Many carriers never report anything; the status then simply stays unknown.

**Quiet hours** pause every auto-reply — fixed, AI, and missed-call texts — during a nightly window you pick in Settings. Held replies are not lost: `DeferredReplyWorker` answers each person's latest message right after the window ends, one reply per person per night.

---

## Tech stack

| Layer | Library | Version |
|---|---|---|
| Language | Kotlin | 2.0.21 |
| UI | Jetpack Compose + Material 3 | BOM 2024.10.01 |
| Fonts | DM Sans · JetBrains Mono | bundled |
| DI | Hilt | 2.52 |
| Database | Room | 2.6.1 |
| Background | WorkManager | 2.9.1 |
| Location | play-services-location | 21.3.0 |
| AI | Gemini API via `HttpURLConnection` | — |
| Serialization | kotlinx-serialization | 1.7.3 |
| Drag-and-drop | sh.calvin.reorderable | 2.4.3 |
| Build tools | AGP 8.7.3 · Gradle 8.9 · KSP 2.0.21-1.0.28 | |
| Min SDK | Android 8.0 (API 26) | |
| Target SDK | Android 14 (API 34) | |

---

## Permissions

| Permission | Why |
|---|---|
| `SEND_SMS` | Send the macro message |
| `RECEIVE_SMS` | Detect incoming SMS for auto-reply macros |
| `INTERNET` | Gemini API calls (AI replies / AI compose) — nothing else leaves the device |
| `RECEIVE_BOOT_COMPLETED` | Re-register alarms and geofences after reboot |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Precise fire time for scheduled macros |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Keep WorkManager running on aggressive OEM ROMs |
| `ACCESS_FINE_LOCATION` / `ACCESS_BACKGROUND_LOCATION` | Geofence (location) triggers |
| `POST_NOTIFICATIONS` | Show send-result, AI-approval and retry notifications |
| `READ_CONTACTS` | Contact picker in the editor |
| `READ_PHONE_STATE` | Detect ringing/ended calls for missed-call macros |
| `READ_CALL_LOG` | Android only reveals the caller's number to apps holding this — required to text a missed caller back |
| `BLUETOOTH_CONNECT` | Match a specific Bluetooth device in state-trigger macros |
| `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | Detect Wi-Fi connect/disconnect (and the SSID) for state triggers |
| `FOREGROUND_SERVICE` (+ `SPECIAL_USE`) | The state-trigger monitor runs as a minimal foreground service, only while a charging/Bluetooth/Wi-Fi macro is enabled |

Privacy: phone numbers are always masked in notifications and the log; result notifications keep the message body off the lock screen (redacted public version); the Gemini key is excluded from backups.

---

## Build & install

**Prerequisites:** JDK 17, Android SDK platform-34 + build-tools 34.0.0.

```bash
# Build debug APK
./gradlew assembleDebug

# Run JVM unit tests
./gradlew test

# Install on connected device / emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app is not distributed via the Play Store — it is sideloaded on a personal device.

---

## Database schema

Room database at version **13**. All migrations are additive (`ADD COLUMN`) — upgrading from any prior install preserves existing macros.

| Migration | Change |
|---|---|
| 1 → 2 | `last_scheduled_fire_at`, `sort_order` |
| 2 → 3 | `days_of_week` (bitmask, default 127 = every day) |
| 3 → 4 | `week_interval` (default 1), `anchor_epoch_day` |
| 4 → 5 | `card_color` (per-macro accent) |
| 5 → 6 | `valid_until_epoch_day` (expiry date) |
| 6 → 7 | `match_sender`, `match_keyword` (auto-reply) |
| 7 → 8 | `latitude`, `longitude`, `radius_meters`, `geofence_transition` |
| 8 → 9 | `ai_reply_enabled`, `ai_send_mode` (AI auto-replies) |
| 9 → 10 | `ai_reply_instruction` (per-macro AI prompt) |
| 10 → 11 | `trigger_on_connect`, `trigger_target`, `trigger_target_label` (state triggers) |
| 11 → 12 | `delivery_status` on `macro_logs` (carrier delivery reports) |
| 12 → 13 | `folders` table + `folder_id` on macros (macro folders) |

---

## Project layout

```
app/src/main/java/com/vibeactions/
├── data/
│   ├── AppSettings    Quiet hours + app prefs (SharedPreferences)
│   ├── db/            Room entities, DAOs, Migrations, AppDatabase
│   └── repository/    MacroRepository, MacroLogRepository
├── domain/
│   ├── model/         Macro, MacroLog, Mappers
│   └── usecase/       Save, Delete, Toggle, Trigger, RescheduleAll
├── scheduler/         AlarmScheduler · MacroFirer · MacroCatchUpWorker
│                      BootReceiver · SmsReplyReceiver · IncomingReplyRouter
│                      GeminiReplyWorker · DeferredReplyWorker (quiet hours)
│                      CallStateReceiver (missed calls) · AiReplyActionReceiver
│                      SmsSentReceiver · SmsDeliveredReceiver (receipts)
│                      TriggerMonitorService · TriggerMonitor (state triggers)
│                      GeofenceManager · GeofenceReceiver (location)
├── sms/               SmsDispatcher (multipart-aware, arms sent receipts)
├── notifications/     MacroNotificationManager
├── widget/            AppWidgetProvider, config activity, WidgetIds, WidgetRefresher
├── ui/
│   ├── macrolist/     List screen + ViewModel
│   ├── editor/        Editor screen + ViewModel (pure EditorState.toMacro)
│   ├── log/           Log screen + ViewModel
│   ├── settings/      Settings screen + ViewModel (AI settings, export/import)
│   ├── health/        Health screen + ViewModel (system status, next fires)
│   ├── common/        MacroCard, FolderCard, CardVisuals, ThemedSwitch, PermissionBanner, StaticBackground
│   └── theme/         Color, Type, Theme
├── di/                Hilt modules
└── util/              TimeUtils · PhoneUtils · MacroJson · BackupJson
                       MessageTemplate · IncomingMatch · GeminiClient
                       AiReplyDedup · SmsResult · QuietHours · StateTrigger
                       CallState · WidgetSubtitle · ColorMatrixMath · CardColors
                       FolderLayout
```

---

<div align="center">

Built with [Claude](https://claude.ai) · [larssohl.dk](https://www.larssohl.dk)

</div>
