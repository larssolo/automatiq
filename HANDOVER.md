# Overlevering — Automatiq (VibeActions)

> Kopiér denne fil ind i en ny chattråd som kontekst. Sidst opdateret: 2026-06-24.

## App

**Automatiq** — native Kotlin/Compose Android-app til SMS-automatisering.
- Pakke: `com.vibeactions`
- Repo: `~/VibeActions`, remote `https://github.com/larssolo/automatiq.git`
- Branch: `main` (op til dato med origin; HEAD = `e7e4f5d`)
- Funktioner: planlagte SMS, AI auto-svar via Gemini, macro-knap-widgets, lokationstriggere.

## Tech stack
- Kotlin + Jetpack Compose (Material 3, mørkt tema)
- Hilt DI (`@HiltViewModel`, `@AndroidEntryPoint`, `@HiltWorker`)
- Room DB **version 10**, additive migrations, `exportSchema=true`
- WorkManager expedited `OneTimeWorkRequest` til Gemini-kald (`GeminiReplyWorker`)
- GLES30-gradient-baggrund (`ShaderGradientBackground`), 200% scale, 60 FPS
- Gemini-kald via `HttpURLConnection` + kotlinx-serialization (`util/GeminiClient.kt`)
- minSdk 26, targetSdk/compileSdk 34. Tests: ren JVM (junit + kotlin-test), ingen mockk/robolectric

## Aktuel tilstand
**Working tree er rent — alt committet og pushet.** Seneste commit `e7e4f5d` indeholder 6 bug-fixes + en notifikations-feature (se nedenfor). `./gradlew assembleDebug` bygger; alle unit-tests grønne. **On-device test udestår stadig.**

## Seneste arbejde (commit e7e4f5d)
**6 bug-fixes fra kodegennemgang:**
1. Toggle af LOCATION-makro (af-/på i listen) registrerer nu geofencen korrekt — før blev den aldrig gen-armet ved gen-aktivering og lækkede ved deaktivering (`ToggleMacroUseCase` injicerer nu `GeofenceManager`).
2. Background-location-flowet router til app-settings på Android 11+ (runtime-dialogen kan ikke give "Tillad altid" der) — `MacroEditorScreen`.
3. Planlagt fyring claimes nu **atomært** (`MacroDao.claimScheduledFire`), så samtidig alarm + catch-up-worker ikke kan dobbeltsende. Erstatter read-then-write på `lastScheduledFireAt`.
4. AUTO AI-svar claimer en per-(makro,afsender,besked,dag)-nøgle før afsendelse (`GeminiReplyWorker` + `util/AiReplyDedup.kt`), så en WorkManager-retry ikke sender svaret to gange.
5. AI-godkendelses-notifikation bruger nu en high-importance-kanal `macro_ai` (heads-up).
6. "Retry" skjules på auto-svar-fejl, hvor den alligevel intet gjorde (tom recipients).

**Notifikations-feature — "se hvad der blev sendt":**
- Resultat-notifikationen bærer nu hele beskeden (`BigTextStyle`), og loggen gemmer **fuld** tekst (før: `body.take(40)`).
- Kanalen `macro_actions` er `VISIBILITY_PRIVATE` med en redacted public version: låseskærmen viser kun "Sent: <makronavn>"; beskeden vises først efter oplåsning.
- Tryk på resultat-notifikation åbner Loggen — `nav`-extra'en er wired i `MainActivity` (fiksede samtidig den døde "View Log"-knap).

**TDD-noter:** Ny ren logik er testdrevet: `startOfDayMillis` (`IdempotencyGuardTest`) og `aiReplyDedupKey` (`AiReplyDedupTest`). Android-glue (use-cases, notifikationer, permission-UI) testes ikke — følger projektets etablerede mønster.

## Etableret mønster for læsbarhedsfix
Mørk-på-mørk tekst rettes ved at tilføje eksplicit `color = OnSurface` til `Text(...)`-labels og `tint = OnSurface`/`OnSurfaceVariant` til `Icon(...)`. Temafarver ligger i `ui/theme/Color.kt` (`OnSurface`, `OnSurfaceVariant`, `SurfaceVariant`, `Outline`). Switches får `SwitchDefaults.colors(uncheckedTrackColor = SurfaceVariant, uncheckedThumbColor = OnSurfaceVariant, uncheckedBorderColor = OnSurfaceVariant)`.

## Vigtige filer
- `scheduler/MacroFirer.kt` — central afsendelse; atomær claim ved planlagt fyring, sender body til log + notifikation
- `scheduler/GeminiReplyWorker.kt` — genererer AI-svar; per-macro instruktion vinder over global systemprompt; brevity-wrapper + `maxOutputTokens=150`; AUTO-claim mod dobbeltsend
- `scheduler/AlarmScheduler.kt` — `setAlarmClock` (Doze-eksempt) med fallback til `setAndAllowWhileIdle`
- `scheduler/GeofenceManager.kt` / `GeofenceReceiver.kt` — Play Services geofences for LOCATION-makroer
- `domain/usecase/ToggleMacroUseCase.kt` — af-/på spejler nu `SaveMacroUseCase` (alarm + geofence)
- `domain/IdempotencyGuard.kt` — `alreadySentToday` + `startOfDayMillis` (claim-tærskel)
- `util/AiReplyDedup.kt` — `aiReplyDedupKey(...)` ren dedup-nøgle
- `notifications/MacroNotificationManager.kt` — kanaler `macro_actions` (privat, redacted public version) + `macro_ai` (high); `notifyResult`/`notifyAiApproval`/`notifyAiSent`
- `ui/MainActivity.kt` — `AiApprovalDialog`, notification-routing via `EXTRA_NAV` + `onNewIntent` + `launchMode="singleTop"`
- `ui/editor/MacroEditorScreen.kt` — Edit/New Macro; AI-skriv-dialog; background-location → settings på API 30+
- `util/GeminiClient.kt` — `geminiGenerate(...)` og `geminiSuggest(...)` (3 forslag)

## Løste problemer (historik)
- Geofence-toggle registrerede/fjernede ikke fencen → spejlet `SaveMacroUseCase`
- Alarm+catch-up read-then-write race → atomær `claimScheduledFire`
- AUTO AI-svar kunne dobbeltsende ved worker-retry → claim-før-send
- "View Log"/resultat-tryk åbnede ikke loggen → `EXTRA_NAV` wired i `MainActivity`
- Switch næsten usynlig på mørk baggrund → `Outline` 0xFF333333 → 0xFF505050 + eksplicitte switch-farver
- 30 FPS gradient stutter ("det hakker") → revertet til 60 FPS, beholdt 200% scale
- MIUI viste ikke Send-knap på AI-godkendelses-notifikation → in-app `AiApprovalDialog`
- AI auto-svar ramlede → brevity-wrapper-prompt + `maxOutputTokens=150` + per-macro instruktionsfelt
- Læsbarhed på labels → eksplicit `OnSurface`

## Konventioner / constraints
- Telefonnumre maskeres altid i notifikationer/log (`maskPhone` / `maskRecipients`)
- Beskedindhold holdes af låseskærmen (kanal `VISIBILITY_PRIVATE` + redacted public version); afhænger dog også af telefonens "vis følsomt indhold"-indstilling
- Gemini API-nøgle logges aldrig; gemt i SharedPreferences `"ai_settings"` (keys: `gemini_api_key`, `gemini_system_prompt`, `gemini_model`)
- UI-prefs i `"ui_settings"` (`bg_preset`); AUTO-dedup i `"ai_sent"`
- Push/commit kun når brugeren beder om det; arbejdet skal være bygget og verificeret først
- Bruger taler dansk

## Næste skridt
- **On-device test udestår** — verificér især:
  - AI auto-svar-flowet (APPROVE + AUTO) på rigtig enhed
  - Notifikation: låseskærm viser kun "Sent: <navn>", fuld besked efter oplåsning, tryk åbner Loggen
  - LOCATION-makro: toggle af/på i listen registrerer/fjerner geofence; background-location via settings på Android 11+
