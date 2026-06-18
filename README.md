<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="Automatiq icon" />

# Automatiq

**On-device SMS automation for Android — scheduled, manual, auto-reply & location triggers.**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![License](https://img.shields.io/badge/license-personal-lightgrey)](#)

</div>

---

## What it does

Automatiq lets you define SMS macros — a name, one or more recipients, and a message — and fire them automatically. A macro can trigger on a **schedule**, on a **button tap**, as an **auto-reply** to an incoming SMS, or when you **arrive at / leave a place**.

Everything runs on-device. No account, no server. The only feature that reaches outside the phone is the optional **location trigger**, which uses Google Play Services geofencing.

```
┌─────────────────────────────────────────────────────────┐
│  Macro: "Morning check-in"                              │
│  → +45 xx xx 78  ·  "Hi {navn}, on my way! ({ugedag})" │
│  → Every Monday + Wednesday · 08:30 · Every 2 weeks    │
│  → Until 2026-08-01                                     │
└─────────────────────────────────────────────────────────┘
         ↓  AlarmManager (precise)
         ↓  WorkManager catch-up (resilience)
         ↓  SmsManager → SMS delivered
```

---

## Trigger types

| Type | Fires when… |
|---|---|
| ⏰ **Scheduled** | a set time arrives, on chosen weekdays, every N weeks — with an optional expiry date |
| 👆 **Manual** | you tap the macro in the list or a home-screen widget |
| 💬 **Auto-reply** | an incoming SMS matches an optional sender and/or keyword — replies to the sender |
| 📍 **Location** | you enter or leave a geofenced place (radius of your choosing) |

---

## Features

| | Feature |
|---|---|
| 👥 | **Multiple recipients** — one macro sends to a whole list of numbers |
| 🔤 | **Message variables** — `{dato}` `{tid}` `{ugedag}` `{navn}` are filled in at send time |
| ⏳ | **Expiry date** — a scheduled macro stops firing after a date you pick |
| 💬 | **Auto-reply** — react to incoming SMS by sender/keyword, with a loop guard |
| 📍 | **Location triggers** — arrive/depart a place, re-armed after reboot |
| 🔁 | **Dual scheduling engine** — AlarmManager for precision + WorkManager catch-up so nothing is missed after Doze or reboot |
| 📅 | **Per-weekday control** — pick any combination of Mon–Sun per macro |
| 🗓️ | **Every-N-weeks recurrence** — every week, every other week, every 3 or 4 weeks |
| 🏠 | **Home-screen widget** — one widget per macro, tap to send, shows last status |
| 🔔 | **Notifications** — result (success / failed) + one-tap retry on failure |
| 📋 | **Execution log** — full chronological history per macro, filterable |
| 📤 | **JSON export / import** — full backup and restore via system file picker |
| ↕️ | **Drag-and-drop reorder** — visible drag handle on every card |
| 🎨 | **Per-macro colours** — each card gets a random pastel accent |
| 🔋 | **Battery-optimisation prompt** — guides you through the whitelist so alarms survive aggressive OEM killers |

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                            UI Layer                                  │
│  MacroListScreen · MacroEditorScreen · LogScreen · SettingsScreen    │
│  MacroCard · StatusBadge · PermissionBanner  (Jetpack Compose)      │
└────────────────────────────┬─────────────────────────────────────────┘
                             │ ViewModels (Hilt)
┌────────────────────────────▼─────────────────────────────────────────┐
│                          Domain Layer                                │
│  SaveMacroUseCase · DeleteMacroUseCase · ToggleMacroUseCase         │
│  TriggerMacroUseCase · RescheduleAllUseCase · IdempotencyGuard      │
└────────────┬───────────────────────────────────────┬─────────────────┘
             │                                       │
┌────────────▼─────────────────────────┐ ┌───────────▼─────────────────┐
│            Scheduler / Triggers       │ │         Data Layer           │
│  AlarmScheduler · MacroAlarmReceiver  │ │  MacroRepository             │
│  MacroCatchUpWorker · BootReceiver    │ │  MacroLogRepository          │
│  SmsReplyReceiver  (auto-reply)       │ │  Room DB (v8)               │
│  GeofenceManager · GeofenceReceiver   │ │  MacroEntity · MacroLogEntity│
│  MacroFirer  ←── single send path ────┼─┤  Migrations 1→…→8           │
└────────────┬──────────────────────────┘ └─────────────────────────────┘
             │
┌────────────▼─────────────────────────────────────────────────────────┐
│                        System / Android                              │
│  AlarmManager.setAlarmClock()  ·  WorkManager (periodic)            │
│  SmsManager (multipart)  ·  Telephony SMS_RECEIVED                  │
│  Play Services Geofencing  ·  AppWidgetProvider  ·  Notifications   │
└──────────────────────────────────────────────────────────────────────┘
```

`MacroFirer.fire()` is the one path every trigger funnels through — alarm, catch-up worker, manual tap, widget, auto-reply, and geofence all call it.

### Scheduling engine

Scheduled sends use two complementary engines so they're never silently dropped:

1. **`AlarmManager.setAlarmClock()`** — Doze-exempt exact alarm; fires precisely at the scheduled time.
2. **`MacroCatchUpWorker`** — hourly WorkManager job that checks all enabled scheduled macros and sends any that weren't fired yet today.

An idempotency guard (`alreadySentToday`, keyed on `macroId + yyyy-MM-dd`) ensures neither engine double-sends. A manual tap writes a separate `lastTriggeredAt` field so it never consumes the day's scheduled send slot. Weekday, every-N-weeks recurrence, and the expiry date are all evaluated by the shared `isScheduledDay` rule.

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
| `RECEIVE_BOOT_COMPLETED` | Re-register alarms and geofences after reboot |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Precise fire time for scheduled macros |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Keep WorkManager running on aggressive OEM ROMs |
| `ACCESS_FINE_LOCATION` / `ACCESS_BACKGROUND_LOCATION` | Geofence (location) triggers |
| `POST_NOTIFICATIONS` | Show send-result and retry notifications |
| `READ_CONTACTS` | Contact picker in the editor |

---

## Build & install

**Prerequisites:** JDK 17, Android SDK platform-34 + build-tools 34.0.0.

```bash
# Build debug APK
./gradlew assembleDebug

# Run JVM unit tests
./gradlew testDebugUnitTest

# Install on connected device / emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app is not distributed via the Play Store — it is sideloaded on a personal device.

---

## Database schema

Room database at version **8**. All migrations are additive (`ADD COLUMN`) — upgrading from any prior install preserves existing macros.

| Migration | Change |
|---|---|
| 1 → 2 | `last_scheduled_fire_at`, `sort_order` |
| 2 → 3 | `days_of_week` (bitmask, default 127 = every day) |
| 3 → 4 | `week_interval` (default 1), `anchor_epoch_day` |
| 4 → 5 | `card_color` (per-macro accent) |
| 5 → 6 | `valid_until_epoch_day` (expiry date) |
| 6 → 7 | `match_sender`, `match_keyword` (auto-reply) |
| 7 → 8 | `latitude`, `longitude`, `radius_meters`, `geofence_transition` |

---

## Project layout

```
app/src/main/java/com/vibeactions/
├── data/
│   ├── db/            Room entities, DAOs, Migrations, AppDatabase
│   └── repository/    MacroRepository, MacroLogRepository
├── domain/
│   ├── model/         Macro, MacroLog, Mappers
│   └── usecase/       Save, Delete, Toggle, Trigger, RescheduleAll
├── scheduler/         AlarmScheduler · MacroFirer · MacroCatchUpWorker
│                      BootReceiver · SmsReplyReceiver (auto-reply)
│                      GeofenceManager · GeofenceReceiver (location)
├── sms/               SmsDispatcher (multipart-aware)
├── notifications/     MacroNotificationManager
├── widget/            AppWidgetProvider, config activity, WidgetIds
├── ui/
│   ├── macrolist/     List screen + ViewModel
│   ├── editor/        Editor screen + ViewModel
│   ├── log/           Log screen + ViewModel
│   ├── settings/      Settings screen + ViewModel
│   ├── common/        MacroCard, StatusBadge, PermissionBanner
│   └── theme/         Color, Type, Theme
├── di/                Hilt modules
└── util/              TimeUtils · PhoneUtils · MacroJson · IdempotencyGuard
                       MessageTemplate · IncomingMatch · CardColors
```

---

<div align="center">

Built with [Claude](https://claude.ai) · [larssohl.dk](https://www.larssohl.dk)

</div>
