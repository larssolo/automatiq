<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="Automatiq icon" />

# Automatiq

**Scheduled & manual SMS macros for Android — fully offline, no cloud.**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![License](https://img.shields.io/badge/license-personal-lightgrey)](#)

</div>

---

## What it does

Automatiq lets you define SMS macros — a name, a recipient, and a message — and fire them on a schedule or at the tap of a button. Everything runs on-device. No account, no server, no internet.

```
┌─────────────────────────────────────────────────────────┐
│  Macro: "Morning check-in"                              │
│  → +45 xx xx 78  ·  "Hey, on my way!"                  │
│  → Every Monday + Wednesday · 08:30 · Every 2 weeks    │
└─────────────────────────────────────────────────────────┘
         ↓  AlarmManager (precise)
         ↓  WorkManager catch-up (resilience)
         ↓  SmsManager → SMS delivered
```

---

## Features

| | Feature |
|---|---|
| ⏰ | **Scheduled macros** — fire at a set time, on chosen weekdays, every N weeks |
| 👆 | **Manual macros** — one-tap send from the list or a home-screen widget |
| 🔁 | **Dual scheduling engine** — AlarmManager for precision + WorkManager catch-up so nothing is missed after Doze or reboot |
| 📅 | **Per-weekday control** — pick any combination of Mon–Sun per macro |
| 🗓️ | **Every-N-weeks recurrence** — every week, every other week, every 3 or 4 weeks |
| 🏠 | **Home-screen widget** — one widget per macro, tap to send, shows last status |
| 🔔 | **Notifications** — result (success / failed) + one-tap retry on failure |
| 📋 | **Execution log** — full chronological history per macro, filterable |
| 📤 | **JSON export / import** — full backup and restore via system file picker |
| ↕️ | **Drag-and-drop reorder** — visible drag handle on every card |
| 🔋 | **Battery-optimisation prompt** — guides you through the whitelist so alarms survive aggressive OEM killers |

---

## Screenshots

> _Sideloaded personal app — screenshots from the device once on-device testing is complete._

The four screens:

```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  Macro List  │  │    Editor    │  │     Log      │  │   Settings   │
│              │  │              │  │              │  │              │
│ ⠿ Morning… ▶ │  │ Name         │  │ ✓ 08:30      │  │ Permissions  │
│ ⠿ Weekly …   │  │ Recipient    │  │ ✗ 08:30 (-1) │  │ Export JSON  │
│ ⠿ Reminder ▶ │  │ Message      │  │ ✓ 08:30 (-2) │  │ Import JSON  │
│              │  │ Schedule ▾   │  │              │  │              │
│       ＋      │  │ [Save]       │  │ [Clear log]  │  │ Claude & … ↗ │
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
```

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
┌────────────▼─────────────┐           ┌─────────────▼───────────────┐
│      Scheduler           │           │         Data Layer           │
│  AlarmScheduler          │           │  MacroRepository             │
│  MacroAlarmReceiver      │           │  MacroLogRepository          │
│  MacroFirer ←────────────┼───────────┤  Room DB (v4)               │
│  MacroCatchUpWorker      │           │  MacroEntity · MacroLogEntity│
│  BootReceiver            │           │  Migrations 1→2→3→4         │
└────────────┬─────────────┘           └─────────────────────────────┘
             │
┌────────────▼─────────────────────────────────────────────────────────┐
│                        System / Android                              │
│  AlarmManager.setAlarmClock()  ·  WorkManager (periodic)            │
│  SmsManager (multipart)  ·  AppWidgetProvider  ·  NotificationManager│
└──────────────────────────────────────────────────────────────────────┘
```

### Scheduling engine

Automatiq uses two complementary engines so a scheduled send is never silently dropped:

1. **`AlarmManager.setAlarmClock()`** — Doze-exempt exact alarm; fires precisely at the scheduled time.
2. **`MacroCatchUpWorker`** — hourly WorkManager job that checks all enabled macros and sends any that weren't fired yet today.

An idempotency guard (`alreadySentToday`, keyed on `macroId + yyyy-MM-dd`) ensures neither engine ever double-sends. A manual tap from the list or widget writes a separate `lastTriggeredAt` field so it never consumes the day's scheduled send slot.

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
| `RECEIVE_BOOT_COMPLETED` | Re-register alarms after reboot |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Precise fire time for scheduled macros |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Keep WorkManager running on aggressive OEM ROMs |
| `POST_NOTIFICATIONS` | Show send-result and retry notifications |
| `READ_CONTACTS` | Contact picker in the editor |

---

## Build & install

**Prerequisites:** JDK 17, Android SDK platform-34 + build-tools 34.0.0.

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests (22 tests)
./gradlew testDebugUnitTest

# Install on connected device / emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app is not distributed via the Play Store — it is sideloaded on a personal device.

---

## Database schema

Room database at version **4**. All migrations are additive (`ADD COLUMN`) — upgrading from any prior install preserves existing macros.

| Migration | Change |
|---|---|
| 1 → 2 | Added `last_scheduled_fire_at`, `sort_order` |
| 2 → 3 | Added `days_of_week` (bitmask, default 127 = every day) |
| 3 → 4 | Added `week_interval` (default 1), `anchor_epoch_day` (default null) |

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
├── scheduler/         AlarmScheduler, MacroFirer, receivers, CatchUpWorker
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
└── util/              TimeUtils, PhoneUtils, MacroJson, IdempotencyGuard
```

---

<div align="center">

Built with [Claude](https://claude.ai) · [larssohl.dk](https://www.larssohl.dk)

</div>
