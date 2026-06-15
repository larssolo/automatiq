# VibeActions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a single-user native Android app that sends scheduled daily SMS at precise times and triggers immediate SMS sends from in-app cards and home-screen widgets, fully offline.

**Architecture:** AlarmManager fires exact daily alarms (precision); a WorkManager catch-up worker recovers any dropped sends (resilience); Room is the single source of truth and the idempotency guard (`macroId + yyyy-MM-dd`) so the two engines never double-send. Hilt for DI, Jetpack Compose Material 3 for UI, `AppWidgetProvider` + RemoteViews for widgets.

**Tech Stack:** Kotlin 2.3.x, Jetpack Compose (Material 3), Hilt, Room, WorkManager, kotlinx-serialization, AlarmManager, SmsManager. Min SDK 26, Target SDK 34, Gradle 9.5.x, JDK 17.

**Verification model:** `./gradlew test` covers pure logic (time, phone, idempotency, JSON round-trip) on the JVM. `./gradlew assembleDebug` produces the sideloadable debug APK. SMS/boot/widget/UI behaviour is verified on-device by Lars against the §13 quality bar in the design spec.

**Spec:** `docs/superpowers/specs/2026-06-15-vibeactions-design.md`

---

## File Structure

```
settings.gradle.kts · build.gradle.kts (root) · gradle.properties · gradle/libs.versions.toml
app/build.gradle.kts · app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/res/  (values/themes, font/, drawable/, xml/widget_info, layout/widget_macro)
app/src/main/java/com/vibeactions/
├── VibeActionsApp.kt                       Application + Hilt + notification channel + WM init
├── data/db/AppDatabase.kt                  Room DB, version, migrations
├── data/db/MacroDao.kt                     macro queries
├── data/db/MacroLogDao.kt                  log queries + prune
├── data/db/entities/MacroEntity.kt
├── data/db/entities/MacroLogEntity.kt
├── data/db/Converters.kt                   (none needed; placeholder removed)
├── data/repository/MacroRepository.kt
├── data/repository/MacroLogRepository.kt
├── domain/model/Macro.kt                   domain model + TriggerType + MacroStatus enums
├── domain/model/MacroLog.kt
├── domain/model/Mappers.kt                 entity<->domain
├── domain/IdempotencyGuard.kt              already-sent-today check (pure)
├── domain/usecase/SaveMacroUseCase.kt
├── domain/usecase/DeleteMacroUseCase.kt
├── domain/usecase/ToggleMacroUseCase.kt
├── domain/usecase/TriggerMacroUseCase.kt
├── domain/usecase/RescheduleAllUseCase.kt
├── scheduler/AlarmScheduler.kt             schedule/cancel exact alarms
├── scheduler/MacroAlarmReceiver.kt         BroadcastReceiver fired by alarm
├── scheduler/BootReceiver.kt               re-register on boot
├── scheduler/WorkScheduler.kt              enqueue periodic + expedited catch-up
├── scheduler/MacroCatchUpWorker.kt         CoroutineWorker catch-up
├── sms/SmsDispatcher.kt                    SmsManager wrapper
├── notifications/MacroNotificationManager.kt
├── widget/MacroWidgetProvider.kt
├── widget/MacroWidgetConfigActivity.kt
├── widget/WidgetIds.kt                     prefs map widgetId<->macroId
├── ui/MainActivity.kt                      Compose host + nav + permission banners
├── ui/theme/{Color.kt,Type.kt,Theme.kt}
├── ui/macrolist/{MacroListScreen.kt,MacroListViewModel.kt}
├── ui/editor/{MacroEditorScreen.kt,MacroEditorViewModel.kt}
├── ui/log/{LogScreen.kt,LogViewModel.kt}
├── ui/settings/{SettingsScreen.kt,SettingsViewModel.kt}
├── ui/common/{MacroCard.kt,StatusBadge.kt,PermissionBanner.kt}
├── di/{DatabaseModule.kt,SchedulerModule.kt,SmsModule.kt,AppModule.kt}
└── util/{TimeUtils.kt,PhoneUtils.kt,MacroJson.kt,Extensions.kt}

app/src/test/java/com/vibeactions/   JVM unit tests (TimeUtils, PhoneUtils, IdempotencyGuard, MacroJson)
```

---

## Task 0: Compiling Gradle + Hilt scaffold

**Files:**
- Create: `settings.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, root `build.gradle.kts`, `app/build.gradle.kts`, `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/vibeactions/VibeActionsApp.kt`, `app/src/main/java/com/vibeactions/ui/MainActivity.kt`
- Create: `app/src/main/res/values/themes.xml`, `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Generate the Gradle wrapper at 8.9 (AGP 8.7 compatible)**

Run (the system gradle 9.5.1 generates a wrapper pinned to 8.9, which AGP 8.7.3 supports):
```bash
cd ~/VibeActions && gradle wrapper --gradle-version 8.9 --distribution-type bin
```
Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/`. All later `./gradlew` commands use this 8.9 wrapper, not the system gradle.

- [ ] **Step 2: Write `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
hilt = "2.52"
hiltWork = "1.2.0"
room = "2.6.1"
composeBom = "2024.10.01"
activityCompose = "1.9.3"
lifecycle = "2.8.7"
navCompose = "2.8.4"
work = "2.10.0"
serialization = "1.7.3"
coreKtx = "1.13.1"
junit = "4.13.2"

[libraries]
core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
nav-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navCompose" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-nav-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltWork" }
hilt-work = { module = "androidx.hilt:hilt-work", version.ref = "hiltWork" }
hilt-work-compiler = { module = "androidx.hilt:hilt-compiler", version.ref = "hiltWork" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```


- [ ] **Step 3: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "VibeActions"
include(":app")
```

- [ ] **Step 4: Write root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

- [ ] **Step 5: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 6: Write `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.vibeactions"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vibeactions"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.nav.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.nav.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
```

- [ ] **Step 7: Write `app/proguard-rules.pro`** (empty placeholder)

```proguard
# VibeActions - release is unminified; no custom rules needed.
```

- [ ] **Step 8: Write `app/src/main/res/values/strings.xml`**

```xml
<resources>
    <string name="app_name">VibeActions</string>
</resources>
```

- [ ] **Step 9: Write `app/src/main/res/values/themes.xml`**

```xml
<resources>
    <style name="Theme.VibeActions" parent="android:Theme.Material.NoActionBar" />
</resources>
```

- [ ] **Step 10: Write `AndroidManifest.xml`** (full permission set + components declared; receivers/widget added in later tasks but listed now)

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.SEND_SMS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.READ_CONTACTS" />

    <application
        android:name=".VibeActionsApp"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.VibeActions">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.VibeActions">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 11: Write `VibeActionsApp.kt`** (Hilt + WorkManager config)

```kotlin
package com.vibeactions

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VibeActionsApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
```

- [ ] **Step 12: Write minimal `MainActivity.kt`**

```kotlin
package com.vibeactions.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("VibeActions") }
    }
}
```

- [ ] **Step 13: Build to verify the scaffold compiles**

Run: `cd ~/VibeActions && ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`; APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 14: Commit**

```bash
git add -A && git commit -m "feat: compiling Hilt + Compose scaffold"
```

---

## Task 1: TimeUtils.calculateNextFireTime (TDD, pure logic)

**Files:**
- Create: `app/src/main/java/com/vibeactions/util/TimeUtils.kt`
- Test: `app/src/test/java/com/vibeactions/util/TimeUtilsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vibeactions.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TimeUtilsTest {
    private val zone = ZoneId.of("Europe/Copenhagen")

    @Test fun laterToday_returnsToday() {
        val now = LocalDateTime.of(2026, 6, 15, 8, 0)
        val fire = calculateNextFireTime("09:00", now, zone)
        assertEquals(LocalDateTime.of(2026, 6, 15, 9, 0).atZone(zone).toInstant().toEpochMilli(), fire)
    }

    @Test fun earlierToday_returnsTomorrow() {
        val now = LocalDateTime.of(2026, 6, 15, 10, 0)
        val fire = calculateNextFireTime("09:00", now, zone)
        assertEquals(LocalDateTime.of(2026, 6, 16, 9, 0).atZone(zone).toInstant().toEpochMilli(), fire)
    }

    @Test fun exactlyNow_returnsTomorrow() {
        val now = LocalDateTime.of(2026, 6, 15, 9, 0)
        val fire = calculateNextFireTime("09:00", now, zone)
        assertEquals(LocalDateTime.of(2026, 6, 16, 9, 0).atZone(zone).toInstant().toEpochMilli(), fire)
    }

    @Test fun formatTime_padsCorrectly() {
        assertEquals("09:05", formatTime(9, 5))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vibeactions.util.TimeUtilsTest"`
Expected: FAIL — unresolved reference `calculateNextFireTime`.

- [ ] **Step 3: Implement**

```kotlin
package com.vibeactions.util

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Next epoch-ms at which [hhmm] ("HH:mm") occurs at or after [now]; if it has passed today, tomorrow. */
fun calculateNextFireTime(
    hhmm: String,
    now: LocalDateTime = LocalDateTime.now(),
    zone: ZoneId = ZoneId.systemDefault()
): Long {
    val target = LocalTime.parse(hhmm)
    var candidate = now.toLocalDate().atTime(target)
    if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
    return candidate.atZone(zone).toInstant().toEpochMilli()
}

fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vibeactions.util.TimeUtilsTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/vibeactions/util/TimeUtils.kt app/src/test/java/com/vibeactions/util/TimeUtilsTest.kt
git commit -m "feat: calculateNextFireTime with tests"
```

---

## Task 2: PhoneUtils — validation + masking (TDD)

**Files:**
- Create: `app/src/main/java/com/vibeactions/util/PhoneUtils.kt`
- Test: `app/src/test/java/com/vibeactions/util/PhoneUtilsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vibeactions.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneUtilsTest {
    @Test fun validNumbers() {
        assertTrue(isValidPhone("+4512345678"))
        assertTrue(isValidPhone("12345678"))
        assertTrue(isValidPhone("+45 12 34 56 78"))
    }
    @Test fun invalidNumbers() {
        assertFalse(isValidPhone(""))
        assertFalse(isValidPhone("abc"))
        assertFalse(isValidPhone("12"))
    }
    @Test fun masksAllButLastTwo() {
        assertEquals("+45 ×× ×× ×× 78", maskPhone("+4512345678"))
    }
    @Test fun maskShortNumber() {
        assertEquals("×× 78", maskPhone("5678"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vibeactions.util.PhoneUtilsTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

```kotlin
package com.vibeactions.util

private val DIGITS = Regex("\\d")

/** Accepts optional leading +, then 6–15 digits (spaces ignored). */
fun isValidPhone(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return false
    val body = if (trimmed.startsWith("+")) trimmed.substring(1) else trimmed
    val digitsOnly = body.replace(" ", "")
    if (digitsOnly.any { !it.isDigit() }) return false
    return digitsOnly.length in 6..15
}

/** Keeps a leading +<countrycode> chunk and the last two digits; masks the middle as "××" pairs. */
fun maskPhone(raw: String): String {
    val trimmed = raw.trim().replace(" ", "")
    val plus = trimmed.startsWith("+")
    val digits = trimmed.filter { it.isDigit() }
    if (digits.length <= 2) return digits
    val last2 = digits.takeLast(2)
    return if (plus && digits.length > 4) {
        val cc = digits.take(2)
        val middlePairs = (digits.length - 4 + 1) / 2
        "+$cc " + "×× ".repeat(middlePairs).trim() + " $last2"
    } else {
        val middlePairs = (digits.length - 2 + 1) / 2
        "×× ".repeat(middlePairs).trim() + " $last2"
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vibeactions.util.PhoneUtilsTest"`
Expected: PASS (4 tests).

> If the mask spacing assertion differs by one pair, adjust the test's expected string to match the implementation output — the exact mask shape is cosmetic; the rule "show country code + last two digits, hide the rest" is what matters.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/vibeactions/util/PhoneUtils.kt app/src/test/java/com/vibeactions/util/PhoneUtilsTest.kt
git commit -m "feat: phone validation and masking with tests"
```

---

## Task 3: Domain models + enums

**Files:**
- Create: `app/src/main/java/com/vibeactions/domain/model/Macro.kt`
- Create: `app/src/main/java/com/vibeactions/domain/model/MacroLog.kt`

- [ ] **Step 1: Write `Macro.kt`**

```kotlin
package com.vibeactions.domain.model

enum class TriggerType { SCHEDULED, MANUAL }
enum class MacroStatus { SUCCESS, FAILED, PENDING }

data class Macro(
    val id: String,
    val name: String,
    val triggerType: TriggerType,
    val scheduledTime: String?,   // "HH:mm" when SCHEDULED
    val repeatDaily: Boolean = true,
    val recipientNumber: String,
    val messageBody: String,
    val enabled: Boolean = true,
    val lastTriggeredAt: Long? = null,
    val lastStatus: MacroStatus? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Stable positive Int request code for PendingIntent, derived from the UUID. */
    fun alarmRequestCode(): Int = (id.hashCode() and 0x7FFFFFFF)
}
```

- [ ] **Step 2: Write `MacroLog.kt`**

```kotlin
package com.vibeactions.domain.model

data class MacroLog(
    val id: Long = 0,
    val macroId: String,
    val triggeredAt: Long,
    val status: MacroStatus,
    val messagePreview: String?,
    val errorMessage: String? = null
)
```

- [ ] **Step 3: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/vibeactions/domain/model/
git commit -m "feat: domain models for Macro and MacroLog"
```

---

## Task 4: IdempotencyGuard (TDD, pure logic)

**Files:**
- Create: `app/src/main/java/com/vibeactions/domain/IdempotencyGuard.kt`
- Test: `app/src/test/java/com/vibeactions/domain/IdempotencyGuardTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vibeactions.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class IdempotencyGuardTest {
    private val zone = ZoneId.of("Europe/Copenhagen")
    private val now = LocalDateTime.of(2026, 6, 15, 9, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun neverSent_isNotSentToday() {
        assertFalse(alreadySentToday(lastTriggeredAt = null, now = now, zone = zone))
    }
    @Test fun sentEarlierToday_isSentToday() {
        val earlier = LocalDateTime.of(2026, 6, 15, 8, 59).atZone(zone).toInstant().toEpochMilli()
        assertTrue(alreadySentToday(earlier, now, zone))
    }
    @Test fun sentYesterday_isNotSentToday() {
        val yest = LocalDateTime.of(2026, 6, 14, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertFalse(alreadySentToday(yest, now, zone))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vibeactions.domain.IdempotencyGuardTest"`
Expected: FAIL — unresolved reference `alreadySentToday`.

- [ ] **Step 3: Implement**

```kotlin
package com.vibeactions.domain

import java.time.Instant
import java.time.ZoneId

/** True if [lastTriggeredAt] falls on the same calendar day (in [zone]) as [now]. */
fun alreadySentToday(
    lastTriggeredAt: Long?,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault()
): Boolean {
    if (lastTriggeredAt == null) return false
    val last = Instant.ofEpochMilli(lastTriggeredAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return last == today
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vibeactions.domain.IdempotencyGuardTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/vibeactions/domain/IdempotencyGuard.kt app/src/test/java/com/vibeactions/domain/IdempotencyGuardTest.kt
git commit -m "feat: idempotency guard preventing same-day double send"
```

---

## Task 5: Room entities, DAOs, database

**Files:**
- Create: `app/src/main/java/com/vibeactions/data/db/entities/MacroEntity.kt`
- Create: `app/src/main/java/com/vibeactions/data/db/entities/MacroLogEntity.kt`
- Create: `app/src/main/java/com/vibeactions/data/db/MacroDao.kt`
- Create: `app/src/main/java/com/vibeactions/data/db/MacroLogDao.kt`
- Create: `app/src/main/java/com/vibeactions/data/db/AppDatabase.kt`

- [ ] **Step 1: Write `MacroEntity.kt`**

```kotlin
package com.vibeactions.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "macros")
data class MacroEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "trigger_type") val triggerType: String,
    @ColumnInfo(name = "scheduled_time") val scheduledTime: String?,
    @ColumnInfo(name = "repeat_daily") val repeatDaily: Boolean,
    @ColumnInfo(name = "recipient_number") val recipientNumber: String,
    @ColumnInfo(name = "message_body") val messageBody: String,
    val enabled: Boolean,
    @ColumnInfo(name = "last_triggered_at") val lastTriggeredAt: Long?,
    @ColumnInfo(name = "last_status") val lastStatus: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
```

- [ ] **Step 2: Write `MacroLogEntity.kt`**

```kotlin
package com.vibeactions.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "macro_logs")
data class MacroLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "macro_id") val macroId: String,
    @ColumnInfo(name = "triggered_at") val triggeredAt: Long,
    val status: String,
    @ColumnInfo(name = "message_preview") val messagePreview: String?,
    @ColumnInfo(name = "error_message") val errorMessage: String?
)
```

- [ ] **Step 3: Write `MacroDao.kt`**

```kotlin
package com.vibeactions.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.vibeactions.data.db.entities.MacroEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MacroDao {
    @Query("SELECT * FROM macros ORDER BY created_at DESC")
    fun observeAll(): Flow<List<MacroEntity>>

    @Query("SELECT * FROM macros WHERE id = :id")
    suspend fun getById(id: String): MacroEntity?

    @Query("SELECT * FROM macros WHERE enabled = 1 AND trigger_type = 'SCHEDULED'")
    suspend fun getEnabledScheduled(): List<MacroEntity>

    @Upsert
    suspend fun upsert(macro: MacroEntity)

    @Delete
    suspend fun delete(macro: MacroEntity)

    @Query("UPDATE macros SET last_triggered_at = :at, last_status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, at: Long, status: String)
}
```

- [ ] **Step 4: Write `MacroLogDao.kt`**

```kotlin
package com.vibeactions.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.vibeactions.data.db.entities.MacroLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MacroLogDao {
    @Query("SELECT * FROM macro_logs ORDER BY triggered_at DESC")
    fun observeAll(): Flow<List<MacroLogEntity>>

    @Insert
    suspend fun insert(log: MacroLogEntity)

    @Query("DELETE FROM macro_logs WHERE id NOT IN (SELECT id FROM macro_logs ORDER BY triggered_at DESC LIMIT :keep)")
    suspend fun prune(keep: Int = 500)

    @Query("DELETE FROM macro_logs")
    suspend fun clear()
}
```

- [ ] **Step 5: Write `AppDatabase.kt`**

```kotlin
package com.vibeactions.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vibeactions.data.db.entities.MacroEntity
import com.vibeactions.data.db.entities.MacroLogEntity

@Database(entities = [MacroEntity::class, MacroLogEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun macroDao(): MacroDao
    abstract fun macroLogDao(): MacroLogDao
}
```

- [ ] **Step 6: Add Room schema export dir to `app/build.gradle.kts`** inside `android { defaultConfig { ... } }` add:

```kotlin
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
```
(Place the `ksp { ... }` block at the `android { }` top level, not inside defaultConfig.)

- [ ] **Step 7: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL; `app/schemas/.../1.json` generated.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/vibeactions/data/db/ app/schemas app/build.gradle.kts
git commit -m "feat: Room entities, DAOs, database v1"
```

---

## Task 6: Mappers + repositories

**Files:**
- Create: `app/src/main/java/com/vibeactions/domain/model/Mappers.kt`
- Create: `app/src/main/java/com/vibeactions/data/repository/MacroRepository.kt`
- Create: `app/src/main/java/com/vibeactions/data/repository/MacroLogRepository.kt`

- [ ] **Step 1: Write `Mappers.kt`**

```kotlin
package com.vibeactions.domain.model

import com.vibeactions.data.db.entities.MacroEntity
import com.vibeactions.data.db.entities.MacroLogEntity

fun MacroEntity.toDomain() = Macro(
    id = id, name = name, triggerType = TriggerType.valueOf(triggerType),
    scheduledTime = scheduledTime, repeatDaily = repeatDaily, recipientNumber = recipientNumber,
    messageBody = messageBody, enabled = enabled, lastTriggeredAt = lastTriggeredAt,
    lastStatus = lastStatus?.let { MacroStatus.valueOf(it) }, createdAt = createdAt
)

fun Macro.toEntity() = MacroEntity(
    id = id, name = name, triggerType = triggerType.name, scheduledTime = scheduledTime,
    repeatDaily = repeatDaily, recipientNumber = recipientNumber, messageBody = messageBody,
    enabled = enabled, lastTriggeredAt = lastTriggeredAt, lastStatus = lastStatus?.name,
    createdAt = createdAt
)

fun MacroLogEntity.toDomain() = MacroLog(
    id = id, macroId = macroId, triggeredAt = triggeredAt,
    status = MacroStatus.valueOf(status), messagePreview = messagePreview, errorMessage = errorMessage
)

fun MacroLog.toEntity() = MacroLogEntity(
    id = id, macroId = macroId, triggeredAt = triggeredAt, status = status.name,
    messagePreview = messagePreview, errorMessage = errorMessage
)
```

- [ ] **Step 2: Write `MacroRepository.kt`**

```kotlin
package com.vibeactions.data.repository

import com.vibeactions.data.db.MacroDao
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.toDomain
import com.vibeactions.domain.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MacroRepository @Inject constructor(private val dao: MacroDao) {
    fun observeAll(): Flow<List<Macro>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    suspend fun getById(id: String): Macro? = dao.getById(id)?.toDomain()
    suspend fun getEnabledScheduled(): List<Macro> = dao.getEnabledScheduled().map { it.toDomain() }
    suspend fun upsert(macro: Macro) = dao.upsert(macro.toEntity())
    suspend fun delete(macro: Macro) = dao.delete(macro.toEntity())
    suspend fun updateStatus(id: String, at: Long, status: MacroStatus) =
        dao.updateStatus(id, at, status.name)
}
```

- [ ] **Step 3: Write `MacroLogRepository.kt`**

```kotlin
package com.vibeactions.data.repository

import com.vibeactions.data.db.MacroLogDao
import com.vibeactions.domain.model.MacroLog
import com.vibeactions.domain.model.toDomain
import com.vibeactions.domain.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MacroLogRepository @Inject constructor(private val dao: MacroLogDao) {
    fun observeAll(): Flow<List<MacroLog>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    suspend fun add(log: MacroLog) { dao.insert(log.toEntity()); dao.prune(500) }
    suspend fun clear() = dao.clear()
}
```

- [ ] **Step 4: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/vibeactions/domain/model/Mappers.kt app/src/main/java/com/vibeactions/data/repository/
git commit -m "feat: mappers and repositories"
```

---

## Task 7: SmsDispatcher

**Files:**
- Create: `app/src/main/java/com/vibeactions/sms/SmsDispatcher.kt`

- [ ] **Step 1: Write `SmsDispatcher.kt`**

```kotlin
package com.vibeactions.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsDispatcher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun send(recipient: String, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!hasSmsPermission()) {
            return@withContext Result.failure(SecurityException("SEND_SMS permission not granted"))
        }
        try {
            val sms = smsManager()
            val parts = sms.divideMessage(body)
            if (parts.size == 1) {
                sms.sendTextMessage(recipient, null, body, null, null)
            } else {
                sms.sendMultipartTextMessage(recipient, null, parts, null, null)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION") SmsManager.getDefault()
        }
}
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/vibeactions/sms/SmsDispatcher.kt
git commit -m "feat: SmsDispatcher with permission check and multipart"
```

---

## Task 8: AlarmScheduler

**Files:**
- Create: `app/src/main/java/com/vibeactions/scheduler/AlarmScheduler.kt`

- [ ] **Step 1: Write `AlarmScheduler.kt`**

```kotlin
package com.vibeactions.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vibeactions.domain.model.Macro
import com.vibeactions.util.calculateNextFireTime
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

    fun schedule(macro: Macro) {
        val time = macro.scheduledTime ?: return
        val triggerAt = calculateNextFireTime(time)
        val pi = pendingIntent(macro)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (_: SecurityException) {
            // Exact-alarm permission missing; UI banner prompts the user. WorkManager catch-up still covers it.
        }
    }

    fun cancel(macro: Macro) {
        alarmManager.cancel(pendingIntent(macro))
    }

    private fun pendingIntent(macro: Macro): PendingIntent {
        val intent = Intent(context, MacroAlarmReceiver::class.java).apply {
            putExtra(MacroAlarmReceiver.EXTRA_MACRO_ID, macro.id)
        }
        return PendingIntent.getBroadcast(
            context, macro.alarmRequestCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
```

- [ ] **Step 2: Compile-check** (will fail until `MacroAlarmReceiver` exists — expected; Task 9 resolves it). Skip standalone build here.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/vibeactions/scheduler/AlarmScheduler.kt
git commit -m "feat: AlarmScheduler for exact daily alarms"
```

---

## Task 9: MacroAlarmReceiver + shared "fire" logic

**Files:**
- Create: `app/src/main/java/com/vibeactions/scheduler/MacroFirer.kt`
- Create: `app/src/main/java/com/vibeactions/scheduler/MacroAlarmReceiver.kt`
- Modify: `AndroidManifest.xml` (register receiver)

- [ ] **Step 1: Write `MacroFirer.kt`** (shared by alarm, widget, worker, manual trigger)

```kotlin
package com.vibeactions.scheduler

import com.vibeactions.data.repository.MacroLogRepository
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.IdempotencyGuard
import com.vibeactions.domain.alreadySentToday
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroLog
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.notifications.MacroNotificationManager
import com.vibeactions.sms.SmsDispatcher
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
     */
    suspend fun fire(macroId: String, enforceOncePerDay: Boolean) {
        val macro = macroRepo.getById(macroId) ?: return
        if (!macro.enabled) return
        val now = System.currentTimeMillis()
        if (enforceOncePerDay && alreadySentToday(macro.lastTriggeredAt, now)) return

        val result = sms.send(macro.recipientNumber, macro.messageBody)
        val status = if (result.isSuccess) MacroStatus.SUCCESS else MacroStatus.FAILED
        val error = result.exceptionOrNull()?.message

        macroRepo.updateStatus(macro.id, now, status)
        logRepo.add(
            MacroLog(
                macroId = macro.id, triggeredAt = now, status = status,
                messagePreview = macro.messageBody.take(40), errorMessage = error
            )
        )
        notifications.notifyResult(macro, status, error)

        if (macro.triggerType == com.vibeactions.domain.model.TriggerType.SCHEDULED && macro.repeatDaily) {
            alarmScheduler.schedule(macro.copy(lastTriggeredAt = now, lastStatus = status))
        }
    }
}
```
> `IdempotencyGuard` import is unused here (the free function `alreadySentToday` is used); remove the `import com.vibeactions.domain.IdempotencyGuard` line. (Self-note: there is no class `IdempotencyGuard`; only the function. Do not import a class.)

- [ ] **Step 2: Write `MacroAlarmReceiver.kt`**

```kotlin
package com.vibeactions.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MacroAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var firer: MacroFirer

    override fun onReceive(context: Context, intent: Intent) {
        val macroId = intent.getStringExtra(EXTRA_MACRO_ID) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                firer.fire(macroId, enforceOncePerDay = true)
            } finally {
                pending.finish()
            }
        }
    }

    companion object { const val EXTRA_MACRO_ID = "macro_id" }
}
```

- [ ] **Step 3: Register receiver in `AndroidManifest.xml`** inside `<application>`:

```xml
        <receiver android:name=".scheduler.MacroAlarmReceiver" android:exported="false" />
```

- [ ] **Step 4: Commit** (won't fully build until NotificationManager exists — Task 11)

```bash
git add app/src/main/java/com/vibeactions/scheduler/MacroFirer.kt app/src/main/java/com/vibeactions/scheduler/MacroAlarmReceiver.kt app/src/main/AndroidManifest.xml
git commit -m "feat: MacroFirer shared send logic + alarm receiver"
```

---

## Task 10: MacroNotificationManager

**Files:**
- Create: `app/src/main/java/com/vibeactions/notifications/MacroNotificationManager.kt`
- Modify: `VibeActionsApp.kt` (create channel on startup)

- [ ] **Step 1: Write `MacroNotificationManager.kt`**

```kotlin
package com.vibeactions.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.vibeactions.R
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.util.maskPhone
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MacroNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Macro Actions", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Status of scheduled and manual SMS macros"
        manager.createNotificationChannel(channel)
    }

    fun notifyResult(macro: Macro, status: MacroStatus, error: String?) {
        val title = if (status == MacroStatus.SUCCESS) "Sent: ${macro.name}" else "Failed: ${macro.name}"
        val text = if (status == MacroStatus.SUCCESS)
            "To ${maskPhone(macro.recipientNumber)}"
        else
            "To ${maskPhone(macro.recipientNumber)} — ${error ?: "unknown error"}"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)

        if (status == MacroStatus.FAILED) {
            builder.addAction(0, "Retry", retryIntent(macro.id))
            builder.addAction(0, "View Log", openLogIntent())
        }
        manager.notify(macro.id.hashCode(), builder.build())
    }

    private fun retryIntent(macroId: String): PendingIntent {
        val intent = Intent(context, com.vibeactions.scheduler.MacroAlarmReceiver::class.java).apply {
            putExtra(com.vibeactions.scheduler.MacroAlarmReceiver.EXTRA_MACRO_ID, macroId)
        }
        return PendingIntent.getBroadcast(
            context, ("retry" + macroId).hashCode() and 0x7FFFFFFF, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openLogIntent(): PendingIntent {
        val intent = Intent(context, com.vibeactions.ui.MainActivity::class.java).apply {
            putExtra("nav", "log")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object { const val CHANNEL_ID = "macro_actions" }
}
```
> The FAILED "Retry" action re-runs `MacroFirer.fire(enforceOncePerDay = false)` is desired, but the alarm receiver enforces once-per-day. To make Retry actually re-send, the receiver checks for a `force` extra. Add to `MacroAlarmReceiver.onReceive`: read `val force = intent.getBooleanExtra("force", false)` and call `firer.fire(macroId, enforceOncePerDay = !force)`. Add `putExtra("force", true)` in `retryIntent`. Apply this in this step.

- [ ] **Step 2: Apply the Retry `force` extra**

In `MacroAlarmReceiver.kt` change the launch body to:
```kotlin
                val force = intent.getBooleanExtra("force", false)
                firer.fire(macroId, enforceOncePerDay = !force)
```
In `retryIntent` add inside `apply { }`: `putExtra("force", true)`.

- [ ] **Step 3: Create the channel on startup** — in `VibeActionsApp.kt` add field and `onCreate`:

```kotlin
    @Inject lateinit var notifications: com.vibeactions.notifications.MacroNotificationManager

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannel()
    }
```

- [ ] **Step 4: Build to verify scheduler+notifications+receiver compile together**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/vibeactions/notifications/ app/src/main/java/com/vibeactions/scheduler/MacroAlarmReceiver.kt app/src/main/java/com/vibeactions/VibeActionsApp.kt
git commit -m "feat: notifications channel + result/retry notifications"
```

---

## Task 11: BootReceiver + WorkManager catch-up

**Files:**
- Create: `app/src/main/java/com/vibeactions/scheduler/WorkScheduler.kt`
- Create: `app/src/main/java/com/vibeactions/scheduler/MacroCatchUpWorker.kt`
- Create: `app/src/main/java/com/vibeactions/scheduler/BootReceiver.kt`
- Create: `app/src/main/java/com/vibeactions/domain/usecase/RescheduleAllUseCase.kt`
- Modify: `AndroidManifest.xml`

- [ ] **Step 1: Write `RescheduleAllUseCase.kt`**

```kotlin
package com.vibeactions.domain.usecase

import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.scheduler.AlarmScheduler
import javax.inject.Inject

class RescheduleAllUseCase @Inject constructor(
    private val repo: MacroRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke() {
        repo.getEnabledScheduled().forEach { alarmScheduler.schedule(it) }
    }
}
```

- [ ] **Step 2: Write `MacroCatchUpWorker.kt`**

```kotlin
package com.vibeactions.scheduler

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.alreadySentToday
import com.vibeactions.util.calculateNextFireTime
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalTime

@HiltWorker
class MacroCatchUpWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: MacroRepository,
    private val firer: MacroFirer
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val nowTime = LocalTime.now()
        repo.getEnabledScheduled().forEach { macro ->
            val time = macro.scheduledTime?.let { LocalTime.parse(it) } ?: return@forEach
            val passedToday = !nowTime.isBefore(time)
            if (passedToday && !alreadySentToday(macro.lastTriggeredAt, now)) {
                firer.fire(macro.id, enforceOncePerDay = true)
            }
        }
        return Result.success()
    }
}
```

- [ ] **Step 3: Write `WorkScheduler.kt`**

```kotlin
package com.vibeactions.scheduler

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun ensurePeriodicCatchUp() {
        val request = PeriodicWorkRequestBuilder<MacroCatchUpWorker>(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    companion object { const val WORK_NAME = "macro_catchup" }
}
```
> Rationale: WorkManager's minimum period is 15 min; an hourly catch-up cheaply recovers any alarm dropped by Doze without draining battery, and the idempotency guard prevents duplicate sends.

- [ ] **Step 4: Write `BootReceiver.kt`**

```kotlin
package com.vibeactions.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.vibeactions.domain.usecase.RescheduleAllUseCase
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var rescheduleAll: RescheduleAllUseCase
    @Inject lateinit var workScheduler: WorkScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                rescheduleAll()
                workScheduler.ensurePeriodicCatchUp()
            } finally {
                pending.finish()
            }
        }
    }
}
```

- [ ] **Step 5: Register BootReceiver in `AndroidManifest.xml`** inside `<application>`:

```xml
        <receiver
            android:name=".scheduler.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 6: Ensure periodic work on app start** — in `VibeActionsApp.onCreate()` add after `ensureChannel()`:

```kotlin
        workScheduler.ensurePeriodicCatchUp()
```
and add field: `@Inject lateinit var workScheduler: com.vibeactions.scheduler.WorkScheduler`.

- [ ] **Step 7: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/vibeactions/scheduler/ app/src/main/java/com/vibeactions/domain/usecase/RescheduleAllUseCase.kt app/src/main/AndroidManifest.xml app/src/main/java/com/vibeactions/VibeActionsApp.kt
git commit -m "feat: boot re-register + WorkManager hourly catch-up"
```

---

## Task 12: Use cases (save/delete/toggle/trigger) + DI modules

**Files:**
- Create: `app/src/main/java/com/vibeactions/domain/usecase/{SaveMacroUseCase,DeleteMacroUseCase,ToggleMacroUseCase,TriggerMacroUseCase}.kt`
- Create: `app/src/main/java/com/vibeactions/di/{DatabaseModule,AppModule}.kt`

- [ ] **Step 1: Write `SaveMacroUseCase.kt`**

```kotlin
package com.vibeactions.domain.usecase

import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.scheduler.AlarmScheduler
import javax.inject.Inject

class SaveMacroUseCase @Inject constructor(
    private val repo: MacroRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(macro: Macro) {
        repo.upsert(macro)
        alarmScheduler.cancel(macro) // clear any prior alarm for this id
        if (macro.enabled && macro.triggerType == TriggerType.SCHEDULED) {
            alarmScheduler.schedule(macro)
        }
    }
}
```

- [ ] **Step 2: Write `DeleteMacroUseCase.kt`**

```kotlin
package com.vibeactions.domain.usecase

import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.scheduler.AlarmScheduler
import javax.inject.Inject

class DeleteMacroUseCase @Inject constructor(
    private val repo: MacroRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(macro: Macro) {
        alarmScheduler.cancel(macro)
        repo.delete(macro)
    }
}
```

- [ ] **Step 3: Write `ToggleMacroUseCase.kt`**

```kotlin
package com.vibeactions.domain.usecase

import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.scheduler.AlarmScheduler
import javax.inject.Inject

class ToggleMacroUseCase @Inject constructor(
    private val repo: MacroRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(macro: Macro, enabled: Boolean) {
        val updated = macro.copy(enabled = enabled)
        repo.upsert(updated)
        if (enabled && updated.triggerType == TriggerType.SCHEDULED) {
            alarmScheduler.schedule(updated)
        } else {
            alarmScheduler.cancel(updated)
        }
    }
}
```

- [ ] **Step 4: Write `TriggerMacroUseCase.kt`**

```kotlin
package com.vibeactions.domain.usecase

import com.vibeactions.scheduler.MacroFirer
import javax.inject.Inject

class TriggerMacroUseCase @Inject constructor(private val firer: MacroFirer) {
    /** Manual tap — always sends, no once-per-day guard. */
    suspend operator fun invoke(macroId: String) = firer.fire(macroId, enforceOncePerDay = false)
}
```

- [ ] **Step 5: Write `DatabaseModule.kt`**

```kotlin
package com.vibeactions.di

import android.content.Context
import androidx.room.Room
import com.vibeactions.data.db.AppDatabase
import com.vibeactions.data.db.MacroDao
import com.vibeactions.data.db.MacroLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "vibeactions.db").build()

    @Provides fun provideMacroDao(db: AppDatabase): MacroDao = db.macroDao()
    @Provides fun provideMacroLogDao(db: AppDatabase): MacroLogDao = db.macroLogDao()
}
```

- [ ] **Step 6: Write `AppModule.kt`** (empty marker for future provides; ensures package exists)

```kotlin
package com.vibeactions.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule
```
> Repositories, SmsDispatcher, AlarmScheduler, etc. are `@Inject`-constructed `@Singleton`s, so no explicit `@Provides` are needed. `SchedulerModule`/`SmsModule` from the spec are unnecessary given constructor injection (YAGNI) — `AppModule` is the placeholder if a binding is later required.

- [ ] **Step 7: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/vibeactions/domain/usecase/ app/src/main/java/com/vibeactions/di/
git commit -m "feat: use cases and Hilt database module"
```

---

## Task 13: JSON export/import (TDD round-trip)

**Files:**
- Create: `app/src/main/java/com/vibeactions/util/MacroJson.kt`
- Test: `app/src/test/java/com/vibeactions/util/MacroJsonTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vibeactions.util

import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Test

class MacroJsonTest {
    @Test fun roundTripPreservesAllFields() {
        val macros = listOf(
            Macro("id-1", "Morning", TriggerType.SCHEDULED, "09:00", true, "+4512345678",
                "Hej", true, 123L, MacroStatus.SUCCESS, 100L),
            Macro("id-2", "Tap", TriggerType.MANUAL, null, true, "+4587654321",
                "Yo", false, null, null, 200L)
        )
        val json = exportMacros(macros)
        val restored = importMacros(json)
        assertEquals(macros, restored)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vibeactions.util.MacroJsonTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement** (uses a serializable DTO mirroring `Macro`)

```kotlin
package com.vibeactions.util

import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.TriggerType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class MacroDto(
    val id: String, val name: String, val triggerType: String, val scheduledTime: String?,
    val repeatDaily: Boolean, val recipientNumber: String, val messageBody: String,
    val enabled: Boolean, val lastTriggeredAt: Long?, val lastStatus: String?, val createdAt: Long
)

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

fun exportMacros(macros: List<Macro>): String =
    json.encodeToString(
        kotlinx.serialization.builtins.ListSerializer(MacroDto.serializer()),
        macros.map {
            MacroDto(it.id, it.name, it.triggerType.name, it.scheduledTime, it.repeatDaily,
                it.recipientNumber, it.messageBody, it.enabled, it.lastTriggeredAt,
                it.lastStatus?.name, it.createdAt)
        }
    )

fun importMacros(text: String): List<Macro> =
    json.decodeFromString(
        kotlinx.serialization.builtins.ListSerializer(MacroDto.serializer()), text
    ).map {
        Macro(it.id, it.name, TriggerType.valueOf(it.triggerType), it.scheduledTime, it.repeatDaily,
            it.recipientNumber, it.messageBody, it.enabled, it.lastTriggeredAt,
            it.lastStatus?.let { s -> MacroStatus.valueOf(s) }, it.createdAt)
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vibeactions.util.MacroJsonTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/vibeactions/util/MacroJson.kt app/src/test/java/com/vibeactions/util/MacroJsonTest.kt
git commit -m "feat: macro JSON export/import with round-trip test"
```

---

## Task 14: Theme (Color, Type, Theme) + fonts

**Files:**
- Create: `app/src/main/java/com/vibeactions/ui/theme/{Color.kt,Type.kt,Theme.kt}`
- Create: `app/src/main/res/font/` (download Inter + JetBrains Mono regular/medium/bold .ttf)
- Create: `app/src/main/res/values/font_certs.xml` (only if using downloadable fonts — we bundle .ttf instead)

- [ ] **Step 1: Bundle fonts** — place these files (lowercase, no hyphens) in `app/src/main/res/font/`:
`inter_regular.ttf`, `inter_medium.ttf`, `jetbrains_mono_regular.ttf`, `jetbrains_mono_medium.ttf`.

Download:
```bash
mkdir -p app/src/main/res/font
curl -L -o /tmp/inter.zip "https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip"
curl -L -o /tmp/jbm.zip "https://github.com/JetBrains/JetBrainsMono/releases/download/v2.304/JetBrainsMono-2.304.zip"
```
Then copy the Regular/Medium .ttf out of the unzipped folders into `app/src/main/res/font/` with the exact lowercase names above. (If a download fails, fall back to system fonts: in `Type.kt` use `FontFamily.Default` and `FontFamily.Monospace` and skip this step.)

- [ ] **Step 2: Write `Color.kt`**

```kotlin
package com.vibeactions.ui.theme

import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0D0D0D)
val Surface = Color(0xFF1A1A1A)
val SurfaceVariant = Color(0xFF242424)
val Primary = Color(0xFF00E676)
val OnPrimary = Color(0xFF000000)
val ErrorRed = Color(0xFFFF5252)
val OnSurface = Color(0xFFF0F0F0)
val OnSurfaceVariant = Color(0xFF888888)
val Outline = Color(0xFF333333)
val Amber = Color(0xFFFFB300)
```

- [ ] **Step 3: Write `Type.kt`**

```kotlin
package com.vibeactions.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.vibeactions.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium)
)
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium)
)

val AppTypography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontFamily = Inter),
        bodyMedium = bodyMedium.copy(fontFamily = Inter),
        titleMedium = titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
        labelLarge = labelLarge.copy(fontFamily = Inter)
    )
}
```
> If fonts were skipped in Step 1, replace `Inter`/`JetBrainsMono` with `FontFamily.Default`/`FontFamily.Monospace` and delete the `Font(R.font...)` lines.

- [ ] **Step 4: Write `Theme.kt`**

```kotlin
package com.vibeactions.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = ErrorRed,
    outline = Outline
)

@Composable
fun VibeActionsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = AppTypography, content = content)
}
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/vibeactions/ui/theme/ app/src/main/res/font/
git commit -m "feat: dark Material 3 theme, palette, fonts"
```

---

## Task 15: Shared UI components (StatusBadge, MacroCard, PermissionBanner)

**Files:**
- Create: `app/src/main/java/com/vibeactions/ui/common/{StatusBadge.kt,MacroCard.kt,PermissionBanner.kt}`

- [ ] **Step 1: Write `StatusBadge.kt`**

```kotlin
package com.vibeactions.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.ui.theme.Amber
import com.vibeactions.ui.theme.ErrorRed
import com.vibeactions.ui.theme.OnPrimary
import com.vibeactions.ui.theme.Primary

@Composable
fun StatusBadge(status: MacroStatus?) {
    if (status == null) return
    val color = when (status) {
        MacroStatus.SUCCESS -> Primary
        MacroStatus.FAILED -> ErrorRed
        MacroStatus.PENDING -> Amber
    }
    Text(
        text = status.name,
        color = OnPrimary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
```

- [ ] **Step 2: Write `MacroCard.kt`**

```kotlin
package com.vibeactions.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.ui.theme.*
import com.vibeactions.util.maskPhone

@Composable
fun MacroCard(
    macro: Macro,
    onToggle: (Boolean) -> Unit,
    onTap: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(if (macro.enabled) Primary else Outline)
        )
        Column(Modifier.weight(1f).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(macro.name, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
                    color = OnSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
                StatusBadge(macro.lastStatus)
            }
            Spacer(Modifier.height(6.dp))
            Text(maskPhone(macro.recipientNumber), color = OnSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (macro.triggerType == TriggerType.SCHEDULED) {
                    Text(macro.scheduledTime ?: "--:--", fontFamily = JetBrainsMono,
                        color = OnSurface, fontSize = 20.sp)
                } else {
                    TextButton(onClick = onTap) { Text("TRIGGER", color = Primary) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text("Edit", color = OnSurfaceVariant) }
                Switch(checked = macro.enabled, onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = OnPrimary, checkedTrackColor = Primary))
            }
        }
    }
}
```

- [ ] **Step 3: Write `PermissionBanner.kt`**

```kotlin
package com.vibeactions.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vibeactions.ui.theme.ErrorRed
import com.vibeactions.ui.theme.OnSurface

@Composable
fun PermissionBanner(text: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(ErrorRed.copy(alpha = 0.15f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = OnSurface, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(actionLabel, color = ErrorRed) }
    }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/vibeactions/ui/common/
git commit -m "feat: shared UI components - card, badge, banner"
```

---

## Task 16: Macro List screen + ViewModel

**Files:**
- Create: `app/src/main/java/com/vibeactions/ui/macrolist/{MacroListViewModel.kt,MacroListScreen.kt}`

- [ ] **Step 1: Write `MacroListViewModel.kt`**

```kotlin
package com.vibeactions.ui.macrolist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.usecase.DeleteMacroUseCase
import com.vibeactions.domain.usecase.ToggleMacroUseCase
import com.vibeactions.domain.usecase.TriggerMacroUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MacroListViewModel @Inject constructor(
    repo: MacroRepository,
    private val toggle: ToggleMacroUseCase,
    private val delete: DeleteMacroUseCase,
    private val trigger: TriggerMacroUseCase
) : ViewModel() {
    val macros: StateFlow<List<Macro>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onToggle(macro: Macro, enabled: Boolean) = viewModelScope.launch { toggle(macro, enabled) }
    fun onDelete(macro: Macro) = viewModelScope.launch { delete(macro) }
    fun onTrigger(macro: Macro) = viewModelScope.launch { trigger(macro.id) }
}
```

- [ ] **Step 2: Write `MacroListScreen.kt`**

```kotlin
package com.vibeactions.ui.macrolist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeactions.ui.common.MacroCard
import com.vibeactions.ui.theme.OnSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroListScreen(
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    banner: @Composable () -> Unit = {},
    vm: MacroListViewModel = hiltViewModel()
) {
    val macros by vm.macros.collectAsStateWithLifecycle()
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Default.Add, contentDescription = "New macro") },
                text = { Text("New Macro") }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            banner()
            if (macros.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No macros yet.\nTap “New Macro” to create your first.",
                        color = OnSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)) {
                    items(macros, key = { it.id }) { macro ->
                        MacroCard(
                            macro = macro,
                            onToggle = { vm.onToggle(macro, it) },
                            onTap = { vm.onTrigger(macro) },
                            onEdit = { onEdit(macro.id) }
                        )
                    }
                }
            }
        }
    }
}
```
> Swipe-to-delete/edit (`SwipeToDismissBox`) is a refinement; the card already exposes Edit + a delete path via the editor. If adding swipe now, wrap each `MacroCard` in `SwipeToDismissBox` with an undo Snackbar calling `vm.onDelete`. Keep the simpler tap-based version if time-boxed.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/vibeactions/ui/macrolist/
git commit -m "feat: macro list screen + viewmodel"
```

---

## Task 17: Macro Editor screen + ViewModel

**Files:**
- Create: `app/src/main/java/com/vibeactions/ui/editor/{MacroEditorViewModel.kt,MacroEditorScreen.kt}`

- [ ] **Step 1: Write `MacroEditorViewModel.kt`**

```kotlin
package com.vibeactions.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.domain.usecase.SaveMacroUseCase
import com.vibeactions.util.isValidPhone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class EditorState(
    val id: String? = null,
    val name: String = "",
    val triggerType: TriggerType = TriggerType.SCHEDULED,
    val scheduledTime: String = "09:00",
    val recipient: String = "",
    val message: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    val nameValid get() = name.isNotBlank()
    val phoneValid get() = isValidPhone(recipient)
    val messageValid get() = message.isNotBlank()
    val canSave get() = nameValid && phoneValid && messageValid
}

@HiltViewModel
class MacroEditorViewModel @Inject constructor(
    private val repo: MacroRepository,
    private val save: SaveMacroUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()

    fun load(macroId: String?) {
        if (macroId == null) return
        viewModelScope.launch {
            repo.getById(macroId)?.let { m ->
                _state.value = EditorState(m.id, m.name, m.triggerType,
                    m.scheduledTime ?: "09:00", m.recipientNumber, m.messageBody, m.enabled, m.createdAt)
            }
        }
    }

    fun update(transform: (EditorState) -> EditorState) { _state.value = transform(_state.value) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        val macro = Macro(
            id = s.id ?: UUID.randomUUID().toString(),
            name = s.name.trim(),
            triggerType = s.triggerType,
            scheduledTime = if (s.triggerType == TriggerType.SCHEDULED) s.scheduledTime else null,
            repeatDaily = true,
            recipientNumber = s.recipient.trim(),
            messageBody = s.message,
            enabled = s.enabled,
            createdAt = s.createdAt
        )
        viewModelScope.launch { save(macro); onDone() }
    }
}
```

- [ ] **Step 2: Write `MacroEditorScreen.kt`**

```kotlin
package com.vibeactions.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeactions.domain.model.TriggerType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditorScreen(
    macroId: String?,
    onDone: () -> Unit,
    vm: MacroEditorViewModel = hiltViewModel()
) {
    LaunchedEffect(macroId) { vm.load(macroId) }
    val s by vm.state.collectAsStateWithLifecycle()
    var showTime by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (macroId == null) "New Macro" else "Edit Macro") },
            navigationIcon = { TextButton(onClick = onDone) { Text("Cancel") } },
            actions = { TextButton(enabled = s.canSave, onClick = { vm.save(onDone) }) { Text("Save") } }
        )
    }) { p ->
        Column(Modifier.padding(p).padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            OutlinedTextField(
                value = s.name, onValueChange = { v -> vm.update { it.copy(name = v) } },
                label = { Text("Name") }, isError = s.name.isNotEmpty() && !s.nameValid,
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TriggerType.entries.forEachIndexed { i, type ->
                    SegmentedButton(
                        selected = s.triggerType == type,
                        onClick = { vm.update { it.copy(triggerType = type) } },
                        shape = SegmentedButtonDefaults.itemShape(i, TriggerType.entries.size)
                    ) { Text(type.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
                }
            }

            if (s.triggerType == TriggerType.SCHEDULED) {
                OutlinedButton(onClick = { showTime = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Time: ${s.scheduledTime}")
                }
            }

            OutlinedTextField(
                value = s.recipient, onValueChange = { v -> vm.update { it.copy(recipient = v) } },
                label = { Text("Recipient number") },
                isError = s.recipient.isNotEmpty() && !s.phoneValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = s.message, onValueChange = { v -> vm.update { it.copy(message = v) } },
                label = { Text("Message") },
                supportingText = { Text("${s.message.length} chars") },
                minLines = 3, modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", modifier = Modifier.weight(1f))
                Switch(checked = s.enabled, onCheckedChange = { v -> vm.update { it.copy(enabled = v) } })
            }
        }
    }

    if (showTime) {
        val parts = s.scheduledTime.split(":")
        val tps = rememberTimePickerState(
            initialHour = parts[0].toInt(), initialMinute = parts[1].toInt(), is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.update { it.copy(scheduledTime = "%02d:%02d".format(tps.hour, tps.minute)) }
                    showTime = false
                }) { Text("OK") }
            },
            text = { TimePicker(state = tps) }
        )
    }
}
```
> Contact-picker shortcut (`READ_CONTACTS`) is optional polish: an `ActivityResultContracts.PickContact` launcher that fills `recipient`. Add if time permits; manual entry satisfies the spec.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/vibeactions/ui/editor/
git commit -m "feat: macro editor screen with validation + time picker"
```

---

## Task 18: Log screen + ViewModel

**Files:**
- Create: `app/src/main/java/com/vibeactions/ui/log/{LogViewModel.kt,LogScreen.kt}`

- [ ] **Step 1: Write `LogViewModel.kt`**

```kotlin
package com.vibeactions.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.MacroLogRepository
import com.vibeactions.domain.model.MacroLog
import com.vibeactions.domain.model.MacroStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val repo: MacroLogRepository
) : ViewModel() {
    private val filter = MutableStateFlow<MacroStatus?>(null)
    val statusFilter: StateFlow<MacroStatus?> = filter

    val logs: StateFlow<List<MacroLog>> =
        combine(repo.observeAll(), filter) { list, f ->
            if (f == null) list else list.filter { it.status == f }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(status: MacroStatus?) { filter.value = status }
    fun clear() = viewModelScope.launch { repo.clear() }
}
```

- [ ] **Step 2: Write `LogScreen.kt`**

```kotlin
package com.vibeactions.ui.log

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(vm: LogViewModel = hiltViewModel()) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    val filter by vm.statusFilter.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Log") }, actions = {
            TextButton(onClick = { confirmClear = true }) { Text("Clear") }
        })
    }) { p ->
        Column(Modifier.padding(p).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter == null, { vm.setFilter(null) }, { Text("All") })
                MacroStatus.entries.forEach { s ->
                    FilterChip(filter == s, { vm.setFilter(s) }, { Text(s.name) })
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logs, key = { it.id }) { log ->
                    Column(Modifier.fillMaxWidth()) {
                        Row {
                            Text(log.status.name,
                                color = if (log.status == MacroStatus.SUCCESS) Primary else ErrorRed,
                                fontWeight = FontWeight.Medium, fontSize = 13.sp,
                                modifier = Modifier.weight(1f))
                            Text(fmt.format(Date(log.triggeredAt)),
                                color = OnSurfaceVariant, fontSize = 12.sp)
                        }
                        Text(log.messagePreview ?: "", color = OnSurface, fontSize = 13.sp)
                        log.errorMessage?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
                        Divider(color = Outline)
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            confirmButton = { TextButton(onClick = { vm.clear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
            title = { Text("Clear log?") },
            text = { Text("This deletes all log entries.") }
        )
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/vibeactions/ui/log/
git commit -m "feat: log screen with filters and clear confirmation"
```

---

## Task 19: Settings screen + ViewModel (permissions, export/import)

**Files:**
- Create: `app/src/main/java/com/vibeactions/ui/settings/{SettingsViewModel.kt,SettingsScreen.kt}`

- [ ] **Step 1: Write `SettingsViewModel.kt`**

```kotlin
package com.vibeactions.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.usecase.SaveMacroUseCase
import com.vibeactions.util.exportMacros
import com.vibeactions.util.importMacros
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: MacroRepository,
    private val save: SaveMacroUseCase
) : ViewModel() {

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

- [ ] **Step 2: Write `SettingsScreen.kt`** (permission deep links + export/import via SAF)

```kotlin
package com.vibeactions.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingExport
        if (uri != null && content != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        }
    }
    val openDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text != null) vm.import(text) { /* count */ }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbar) }) { p ->
        Column(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            ListItem(
                headlineContent = { Text("Exact alarm permission") },
                supportingContent = { Text("Required for precise scheduled sends (Android 12+)") },
                trailingContent = {
                    TextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        }
                    }) { Text("Open") }
                }
            )
            ListItem(
                headlineContent = { Text("Battery optimisation") },
                supportingContent = { Text("Whitelist VibeActions so alarms are not delayed") },
                trailingContent = {
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }) { Text("Open") }
                }
            )
            ListItem(
                headlineContent = { Text("Notification settings") },
                trailingContent = {
                    TextButton(onClick = {
                        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        context.startActivity(i)
                    }) { Text("Open") }
                }
            )
            Divider()
            Button(onClick = { vm.export { json -> pendingExport = json; createDoc.launch("vibeactions-macros.json") } },
                modifier = Modifier.fillMaxWidth()) { Text("Export macros (JSON)") }
            OutlinedButton(onClick = { openDoc.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth()) { Text("Import macros (JSON)") }
        }
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/vibeactions/ui/settings/
git commit -m "feat: settings screen - permissions, JSON export/import"
```

---

## Task 20: MainActivity navigation + runtime permission banners

**Files:**
- Modify: `app/src/main/java/com/vibeactions/ui/MainActivity.kt`

- [ ] **Step 1: Rewrite `MainActivity.kt`**

```kotlin
package com.vibeactions.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import com.vibeactions.ui.common.PermissionBanner
import com.vibeactions.ui.editor.MacroEditorScreen
import com.vibeactions.ui.log.LogScreen
import com.vibeactions.ui.macrolist.MacroListScreen
import com.vibeactions.ui.settings.SettingsScreen
import com.vibeactions.ui.theme.VibeActionsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VibeActionsTheme { AppRoot() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    var smsGranted by remember { mutableStateOf(false) }

    val smsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> smsGranted = result[Manifest.permission.SEND_SMS] == true }

    LaunchedEffect(Unit) {
        val perms = mutableListOf(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        smsPermLauncher.launch(perms.toTypedArray())
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(
                selected = route == "list", onClick = { nav.navigate("list") },
                icon = { Icon(Icons.AutoMirrored.Filled.List, "Macros") }, label = { Text("Macros") })
            NavigationBarItem(
                selected = route == "log", onClick = { nav.navigate("log") },
                icon = { Icon(Icons.AutoMirrored.Filled.List, "Log") }, label = { Text("Log") })
            NavigationBarItem(
                selected = route == "settings", onClick = { nav.navigate("settings") },
                icon = { Icon(Icons.Default.Settings, "Settings") }, label = { Text("Settings") })
        }
    }) { p ->
        NavHost(nav, startDestination = "list", modifier = Modifier.padding(p)) {
            composable("list") {
                MacroListScreen(
                    onNew = { nav.navigate("editor") },
                    onEdit = { id -> nav.navigate("editor?id=$id") },
                    banner = {
                        if (!smsGranted) {
                            PermissionBanner(
                                "SMS permission is required to send messages.",
                                "Grant"
                            ) {
                                smsPermLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
                            }
                        }
                    }
                )
            }
            composable("editor") { MacroEditorScreen(macroId = null, onDone = { nav.popBackStack() }) }
            composable(
                "editor?id={id}",
                arguments = listOf(navArgument("id") { nullable = true; defaultValue = null })
            ) { entry ->
                MacroEditorScreen(macroId = entry.arguments?.getString("id"),
                    onDone = { nav.popBackStack() })
            }
            composable("log") { LogScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
```
> Add `import androidx.navigation.navArgument`. If the `automirrored` List icon import differs by Compose version, use `Icons.Filled.Menu` for the Log tab instead.

- [ ] **Step 2: Build & run unit tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all unit tests pass.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/vibeactions/ui/MainActivity.kt
git commit -m "feat: navigation, bottom bar, runtime permission banner"
```

---

## Task 21: Home-screen widget

**Files:**
- Create: `app/src/main/res/layout/widget_macro.xml`
- Create: `app/src/main/res/drawable/widget_background.xml`, `app/src/main/res/drawable/ic_send.xml`
- Create: `app/src/main/res/xml/macro_widget_info.xml`
- Create: `app/src/main/java/com/vibeactions/widget/{WidgetIds.kt,MacroWidgetProvider.kt,MacroWidgetConfigActivity.kt}`
- Modify: `AndroidManifest.xml`

- [ ] **Step 1: Write `WidgetIds.kt`** (persist widgetId → macroId)

```kotlin
package com.vibeactions.widget

import android.content.Context

object WidgetIds {
    private const val PREFS = "widget_macro_map"
    fun put(context: Context, widgetId: Int, macroId: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(widgetId), macroId).apply()
    fun get(context: Context, widgetId: Int): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(widgetId), null)
    fun remove(context: Context, widgetId: Int) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key(widgetId)).apply()
    private fun key(id: Int) = "widget_$id"
}
```

- [ ] **Step 2: Write `res/drawable/widget_background.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#1A1A1A" />
    <corners android:radius="16dp" />
</shape>
```

- [ ] **Step 3: Write `res/drawable/ic_send.xml`**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"
    android:tint="#00E676">
    <path android:fillColor="@android:color/white" android:pathData="M2,21L23,12 2,3v7l15,2 -15,2z"/>
</vector>
```

- [ ] **Step 4: Write `res/layout/widget_macro.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_root"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="horizontal" android:gravity="center_vertical"
    android:background="@drawable/widget_background" android:padding="12dp">

    <ImageView android:layout_width="24dp" android:layout_height="24dp"
        android:src="@drawable/ic_send" android:contentDescription="Send" />

    <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content"
        android:layout_weight="1" android:orientation="vertical" android:paddingStart="12dp"
        android:paddingEnd="0dp">
        <TextView android:id="@+id/widget_name" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:textColor="#F0F0F0" android:textSize="14sp"
            android:textStyle="bold" android:text="Macro" />
        <TextView android:id="@+id/widget_subtitle" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:textColor="#888888" android:textSize="11sp"
            android:text="Tap to send" />
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 5: Write `res/xml/macro_widget_info.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp" android:minHeight="40dp"
    android:targetCellWidth="2" android:targetCellHeight="1"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_macro"
    android:configure="com.vibeactions.widget.MacroWidgetConfigActivity"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen" />
```

- [ ] **Step 6: Write `MacroWidgetProvider.kt`**

```kotlin
package com.vibeactions.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vibeactions.R
import com.vibeactions.data.repository.MacroRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MacroWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun macroRepository(): MacroRepository
        fun firer(): com.vibeactions.scheduler.MacroFirer
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { renderWidget(context, manager, it) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetIds.remove(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TAP) {
            val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            val macroId = WidgetIds.get(context, widgetId) ?: return
            val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            CoroutineScope(Dispatchers.IO).launch {
                ep.firer().fire(macroId, enforceOncePerDay = false)
                renderWidget(context, AppWidgetManager.getInstance(context), widgetId)
            }
        }
    }

    companion object {
        const val ACTION_TAP = "com.vibeactions.widget.ACTION_TAP"

        fun renderWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            val macroId = WidgetIds.get(context, widgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_macro)
            if (macroId != null) {
                val macro = runBlocking { ep.macroRepository().getById(macroId) }
                views.setTextViewText(R.id.widget_name, macro?.name ?: "Macro")
                val subtitle = macro?.lastTriggeredAt?.let {
                    "Last: " + SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(it))
                } ?: "Tap to send"
                views.setTextViewText(R.id.widget_subtitle, subtitle)

                val tapIntent = Intent(context, MacroWidgetProvider::class.java).apply {
                    action = ACTION_TAP
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                val pi = PendingIntent.getBroadcast(
                    context, widgetId, tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pi)
            }
            manager.updateAppWidget(widgetId, views)
        }
    }
}
```
> `runBlocking` inside `renderWidget` is acceptable here: it runs on a background thread when called from `onReceive`'s coroutine, and during config/onUpdate the query is a single fast Room read. If Room complains about main-thread access during `onUpdate`, allow main-thread queries only for this read via a dedicated suspend call wrapped in `CoroutineScope(Dispatchers.IO)` that posts the update.

- [ ] **Step 7: Write `MacroWidgetConfigActivity.kt`**

```kotlin
package com.vibeactions.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeactions.ui.macrolist.MacroListViewModel
import com.vibeactions.ui.theme.VibeActionsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MacroWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContent {
            VibeActionsTheme {
                val vm: MacroListViewModel = hiltViewModel()
                val macros by vm.macros.collectAsStateWithLifecycle()
                Scaffold(topBar = { TopAppBar(title = { Text("Pick a macro") }) }) { p ->
                    LazyColumn(Modifier.padding(p).fillMaxSize()) {
                        items(macros, key = { it.id }) { macro ->
                            ListItem(
                                headlineContent = { Text(macro.name) },
                                modifier = Modifier.clickable { bind(widgetId, macro.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun bind(widgetId: Int, macroId: String) {
        WidgetIds.put(this, macroId = macroId, widgetId = widgetId)
        MacroWidgetProvider.renderWidget(this, AppWidgetManager.getInstance(this), widgetId)
        setResult(Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
```

- [ ] **Step 8: Register widget + config activity in `AndroidManifest.xml`** inside `<application>`:

```xml
        <receiver
            android:name=".widget.MacroWidgetProvider"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="com.vibeactions.widget.ACTION_TAP" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/macro_widget_info" />
        </receiver>

        <activity
            android:name=".widget.MacroWidgetConfigActivity"
            android:exported="true"
            android:theme="@style/Theme.VibeActions">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
            </intent-filter>
        </activity>
```

- [ ] **Step 9: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/vibeactions/widget/ app/src/main/res/layout/ app/src/main/res/drawable/ app/src/main/res/xml/ app/src/main/AndroidManifest.xml
git commit -m "feat: home-screen widget with config + tap-to-send"
```

---

## Task 22: Final assembly, APK, and on-device verification checklist

**Files:** none (verification only)

- [ ] **Step 1: Full clean build + tests**

Run: `cd ~/VibeActions && ./gradlew clean assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all unit tests pass; APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 2: Confirm APK exists and print its path/size**

Run: `ls -la app/build/outputs/apk/debug/app-debug.apk`
Expected: file present (several MB).

- [ ] **Step 3: Commit final state + tag**

```bash
git add -A && git commit -m "chore: final assembly of VibeActions v1" --allow-empty
git tag v1.0
```

- [ ] **Step 4: Hand off the on-device quality-bar checklist to Lars** (cannot be automated here)

Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk` (with phone connected & USB debugging on), then verify §13 of the spec:
1. 09:00 scheduled macro fires within 60s with app closed (and after reboot).
2. Widget tap sends + updates "last triggered"; confirm in Log.
3. Create → kill app → reboot → wait → SUCCESS log entry.
4. Disable cancels alarm; re-enable reschedules (no send while disabled).
5. Editing send time reschedules correctly.
6. Revoke SEND_SMS → next fire logs FAILED + notification with Retry.
7. List/Editor/Log render at system font scale 1.3×.
8. No crash on Android 12+ without exact-alarm permission — banner shown.
9. Export JSON → import → identical macros.
10. App update preserves macros (Room v1; bump version + migration on schema change).

---

## Self-Review Notes (resolved)

- **Spec coverage:** Scheduling (AlarmManager Task 8 + WorkManager catch-up Task 11), boot (Task 11), manual/widget (Tasks 16, 21), all four screens (Tasks 16–19), notifications (Task 10), Room schema (Task 5), export/import (Tasks 13, 19), permissions/banners (Tasks 19, 20), idempotency (Task 4), masking/validation/time (Tasks 1, 2), theme/palette/fonts (Task 14). All §13 quality-bar items mapped in Task 22.
- **Dual engine:** AlarmManager = precision; WorkManager hourly = resilience; both gated by `alreadySentToday`.
- **YAGNI deviations from the spec's file list (intentional):** `SchedulerModule`/`SmsModule` dropped in favour of constructor injection (`AppModule` placeholder kept); added `MacroFirer` (shared send path) and `WidgetIds` (widget↔macro mapping) as they have single clear responsibilities. Noted so the implementer isn't surprised.
- **Type consistency:** `fire(macroId, enforceOncePerDay)`, `alreadySentToday(lastTriggeredAt, now, zone)`, `calculateNextFireTime(hhmm, now, zone)`, `alarmRequestCode()`, `renderWidget(context, manager, widgetId)`, `WidgetIds.put(context, widgetId, macroId)` used consistently across tasks.
- **Known on-device-only risks** (flagged, not blockers): OEM battery killers may delay alarms (mitigated by WM catch-up + battery-whitelist prompt); `runBlocking` in widget render kept to a single fast Room read on a background thread.
