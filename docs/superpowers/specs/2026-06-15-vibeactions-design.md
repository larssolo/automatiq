# VibeActions — Design Spec

**Date:** 2026-06-15
**Owner:** Lars Sohl
**Status:** Approved (design), pending implementation plan

A single-user native Android app that acts as a personal macro/automation engine: send
scheduled daily SMS at precise times, and trigger immediate SMS sends from in-app cards or
home-screen widgets. Fully offline, no backend, no account.

---

## 1. Decisions taken during brainstorming

| Decision | Choice |
|----------|--------|
| Scheduling engine | **Dual engine** — AlarmManager for precision + WorkManager as resilience/catch-up net, reconciled through Room (see §3). |
| Verification target | **Physical phone**, sideloaded **debug APK**. |
| Session scope | **Full app** — all screens, widget, notifications, scheduling, Room, boot receiver — building to an APK. |

---

## 2. Stack

- **Language:** Kotlin · **UI:** Jetpack Compose, Material 3 · **DI:** Hilt
- **Persistence:** Room (macros, logs) · **Serialization:** kotlinx-serialization (JSON export/import)
- **SMS:** `SmsManager` direct send (no chooser) · **Widget:** `AppWidgetProvider` + RemoteViews
- **Min SDK 26 · Target SDK 34** · Gradle 9.5.x · Kotlin 2.3.x · JDK 17
- **Build environment present:** `build-tools;34.0.0`, `platforms;android-34`, gradle, sdkmanager, adb all installed under `ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`.

---

## 3. Scheduling architecture (the dual engine, reconciled)

The two engines are complementary, not competing. **Room is the single source of truth** and
the idempotency guard.

### AlarmManager — the precise trigger
- Per macro: `setExactAndAllowWhileIdle(RTC_WAKEUP, triggerAt, pendingIntent)`.
- `pendingIntent` uses a **stable request code derived from the macro UUID hash** so edits/cancels
  match the existing alarm. Flags: `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`.
- `MacroAlarmReceiver` (BroadcastReceiver) on fire: send SMS → write `MacroLog` → update
  `lastTriggeredAt`/`lastStatus` → post notification → if `repeatDaily`, reschedule next day →
  update any bound widget.
- `calculateNextFireTime(HH:mm)`: if today's time has passed, schedule for tomorrow.

### WorkManager — the resilience / catch-up net
- A single **daily `PeriodicWorkRequest`** (WM's closest-to-daily primitive) plus an
  **expedited one-shot** enqueued around fire time.
- Worker job = **catch-up**: find every enabled SCHEDULED macro whose `scheduledTime` has
  already passed *today* but whose `lastTriggeredAt` is not today, and send it. This recovers
  sends dropped by Doze / OEM battery-killing, which WM survives better than exact alarms.

### Idempotency guard
- A macro is never sent twice for the same calendar day. Guard key = `macroId + yyyy-MM-dd`,
  evaluated against `lastTriggeredAt`. Both engines check it before sending, so AlarmManager and
  WorkManager cannot double-fire the same macro.

### Boot
- `BootReceiver` on `ACTION_BOOT_COMPLETED` (via `goAsync` + coroutine) re-registers exact alarms
  for all enabled SCHEDULED macros and re-enqueues the WorkManager periodic work. Silent — no
  notification.

---

## 4. Data model (Room)

**macros**
```
id TEXT PK · name TEXT · trigger_type TEXT ("SCHEDULED"|"MANUAL")
scheduled_time TEXT? ("HH:mm") · repeat_daily INTEGER(0/1)
recipient_number TEXT · message_body TEXT · enabled INTEGER DEFAULT 1
last_triggered_at INTEGER? (epoch ms) · last_status TEXT? ("SUCCESS"|"FAILED"|"PENDING")
created_at INTEGER
```

**macro_logs**
```
id INTEGER PK AUTOINCREMENT · macro_id TEXT · triggered_at INTEGER
status TEXT · message_preview TEXT? · error_message TEXT?
```
- Pruned to the most recent **500 rows on each insert**.
- Room schema versioned with a migration path so updates lose no macro data.

---

## 5. Module / file structure

Mirrors the agreed tree:
`data/{db,repository}` · `domain/{model,usecase}` · `scheduler` (AlarmScheduler,
MacroAlarmReceiver, BootReceiver) · `sms/SmsDispatcher` · `notifications` · `widget`
(MacroWidgetProvider, MacroWidgetConfigActivity) · `ui/{theme,macrolist,editor,log,settings}` ·
`di/{DatabaseModule,SchedulerModule,SmsModule}` · `util/{TimeUtils,PhoneUtils,Extensions}`.

WorkManager additions: `scheduler/MacroCatchUpWorker.kt`, `scheduler/WorkScheduler.kt`.

---

## 6. SMS send

```
suspend sendSms(recipient, body): Result<Unit>  // Dispatchers.IO
  parts = smsManager.divideMessage(body)
  parts.size == 1 ? sendTextMessage(...) : sendMultipartTextMessage(...)
  catch Exception -> Result.failure   // never crash the receiver
```
- Permission check before every send; on revoked `SEND_SMS`, log FAILED + actionable notification.
- `>160 chars` auto-split via `divideMessage` → multipart.

---

## 7. UI

Material 3, dark-first. Exact palette: bg `#0D0D0D`, surface `#1A1A1A`, surface-variant `#242424`,
primary `#00E676`, on-primary `#000000`, error `#FF5252`, text `#F0F0F0`, text-secondary `#888888`,
outline `#333333`. **JetBrains Mono** for macro names / message previews / scheduled times;
**Inter** for chrome and body.

- **Macro List:** cards with 3dp left bar (green enabled / `#333` disabled), status pill
  (SUCCESS green / FAILED red / PENDING amber), masked recipient (`+45 ×× ×× 78`), enable toggle
  (haptic `CONTEXT_CLICK`), scheduled time in mono 20sp right-aligned. Swipe-left delete (+undo
  Snackbar), swipe-right edit. Extended FAB "New Macro" collapsing on scroll. Empty state.
- **Editor:** name, trigger-type segmented control, time picker (Scheduled only), recipient
  (phone input + contact-picker shortcut), message body (multiline + char counter), enable toggle.
  Validation: non-empty name, valid phone, non-empty message.
- **Log:** chronological events (macro name, timestamp, status, first 40 chars), filter by
  macro/status, clear-with-confirmation.
- **Settings:** battery-optimisation deep link, exact-alarm settings deep link (Android 12+),
  notification-channel status, export macros JSON (share intent), import macros JSON.
- **Accessibility:** ≥48dp targets, content descriptions, honour system font scale (verified at 1.3×).

## 8. Widget

`AppWidgetProvider` + RemoteViews, one instance bound to one macro via `MacroWidgetConfigActivity`.
Dark `#1A1A1A`, rounded, macro name (Inter Medium, white), green `send` icon, last-triggered
timestamp (`#888888`). Tap → immediate SMS on IO coroutine → Toast result → widget label updates.

## 9. Notifications

Channel "Macro Actions". Sent → status notification (macro, recipient, timestamp, status).
Failure → actionable notification with **Retry** + **View Log**. Reboot re-registration → silent.

---

## 10. Permissions

Manifest: `SEND_SMS`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`,
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, `READ_CONTACTS`.
Runtime grants (`SEND_SMS`, `POST_NOTIFICATIONS`, exact-alarm, battery whitelist) are **granted on
the device** — the app builds the request flows + banners; the user taps to grant. `canScheduleExactAlarms()`
checked on foreground; banner shown if false (no crash on Android 12+ without the permission).

---

## 11. Constraints & honesty

- **Debug build only**, signed with the auto-generated debug keystore — sideload / `adb install`,
  not Play-Store-distributable (direct-SMS apps are restricted there regardless; fine for personal use).
- **Real SMS cannot be tested from this Mac** (no SIM, no attached device). On-device confirmation
  against the quality bar is the user's step.

## 12. Verification

- **In this environment:** `./gradlew assembleDebug` → APK; `./gradlew test` → JVM unit tests for
  pure logic: `calculateNextFireTime`, phone validation/masking, JSON export/import round-trip,
  idempotency guard (`macroId + date`).
- **On device (user):** the §13 quality bar — exact-time send incl. after reboot, widget tap send +
  timestamp update, disable cancels / re-enable reschedules, edit-time reschedules, FAILED on revoked
  `SEND_SMS`, font-scale 1.3× rendering, no crash without exact-alarm permission, JSON export/import
  fidelity, no data loss across updates.

## 13. Quality bar (acceptance)

1. 09:00 daily macro fires within 60s, app closed, including after reboot.
2. Widget tap sends SMS and updates last-triggered timestamp, confirmed in log.
3. Create → kill → reboot → wait → SUCCESS log entry.
4. Disable cancels alarm; re-enable reschedules (no send while disabled).
5. Editing send time cancels old alarm, registers new.
6. Revoked `SEND_SMS` before fire → FAILED status + notification.
7. List/Editor/Log render correctly at system font scale 1.3×.
8. No crash on Android 12+ without `SCHEDULE_EXACT_ALARM` — banner instead.
9. JSON export then re-import recreates all macros identically.
10. No macro data lost across app updates (Room migration / version check).
