# Overlevering — Automatiq (VibeActions)

> Kopiér denne fil ind i en ny chattråd som kontekst. Sidst opdateret: 2026-07-18 (fase 4: kodegennemgang + robusthed/sikkerhed).

## Seneste arbejde (fase 4 — fuld kodegennemgang, 2026-07-18)

Fuld gennemgang af appen; rettelser (tests: 68 grønne, 7 nye):

1. **Sikkerhed: widget-tap flyttet til ikke-eksporteret receiver.** `MacroWidgetProvider` (eksporteret, krævet af launcheren) håndterede selv `ACTION_TAP` → enhver co-installeret app kunne fyre en makro (= sende SMS) med et broadcast. Ny `widget/WidgetTapReceiver` (exported=false) håndterer tap; provider renderer kun.
2. **Fremtidig startdato affyrede dagligt:** `calculateNextFireTime` scannede kun `interval*7+7` dage frem fra i dag; et anker længere ude ramte "i morgen"-fallbacken → alarm fyrede hver dag før startdatoen. Scanner nu fra ankeret når det ligger i fremtiden (testdrevet).
3. **Crash-loop via import:** håndredigeret importfil med ugyldig `scheduledTime` crashede `rescheduleAll` ved hver appstart (LocalTime.parse). Import afviser nu ugyldige tider med fejlbesked; `AlarmScheduler`/`MacroCatchUpWorker` parser defensivt (`parseHhMmOrNull`).
4. **Tidszone-/klokkeskift:** `BootReceiver` lytter nu også på `TIMEZONE_CHANGED`, `TIME_SET` og `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` og re-armer.
5. **Ny makro efter dagens tidspunkt:** catch-up-workeren "indhentede" en affyring som aldrig var misset (makroen fandtes ikke på tidspunktet). Nye/duplikerede SCHEDULED-makroer stempler `lastScheduledFireAt` når dagens tid er passeret (`consumedFireStampForNewMacro`, testdrevet).
6. **Radio-FAILED vs. dispatch-SUCCESS race:** `MacroFirer` finaliserer nu loggen først, læser rækken tilbage (FAILED er terminal) og spejler den endelige status på makro + notifikation. `fire()` returnerer `FireResult`.
7. **AI AUTO:** (a) dedup-nøglen bruger nu et event-id mintet pr. indgående SMS — identisk besked nr. 2 samme dag får igen svar, mens worker-retry stadig deduperes; (b) "AI reply sent"-notifikation postes kun ved reel succes, ellers fejlnotifikation.
8. **Dobbelttryk på "Send"** i AI-godkendelsesnotifikationen kunne dobbeltsende → 5s dedup i `AiReplyActionReceiver`.
9. **Alfanumeriske afsendere** (DHL, banker) kan ikke modtage SMS → auto-svar springes over.
10. **Evig PENDING i loggen:** rækker forældreløse efter procesdød markeres FAILED af catch-up-workeren (`failStalePending`, >10 min).
11. **Engangs-makroer (repeatDaily=false, kun via import)** re-armeredes dagligt af appstart/catch-up → filtreres nu efter første affyring.
12. **Widgets opdateres ved omdøb/slet** (`SaveMacroUseCase`/`DeleteMacroUseCase` kalder `WidgetRefresher`).
13. **UI:** bundnav stabler ikke længere destinationer (popUpTo+restoreState); loggen viser makronavn pr. række og PENDING i gul; Settings deep-linker direkte til exact-alarm-toggle og batteridialog; "AI-skriv" → "AI write"; claim af dagens scheduled fire forbruges ikke længere af makroer uden modtagere; `GeminiClient` lækker ikke forbindelsen ved skrivefejl.

**Byggenote:** ændringerne i ren logik er verificeret med 68 grønne JVM-tests; Android-lagene (manifest, receivers, Compose) kunne ikke kompileres i dette miljø (Android-SDK-download blokeret) — byg `assembleDebug` lokalt før release.

## App

**Automatiq** — native Kotlin/Compose Android-app til SMS-automatisering.
- Pakke: `com.vibeactions`
- Repo: `C:\Users\ls\Documents\GitHub\automatiq` (Windows), remote `https://github.com/larssolo/automatiq.git`
- Branch: `main`
- Funktioner: planlagte SMS, AI auto-svar via Gemini, macro-knap-widgets, lokationstriggere.

## Tech stack
- Kotlin + Jetpack Compose (Material 3, mørkt tema)
- Hilt DI (`@HiltViewModel`, `@AndroidEntryPoint`, `@HiltWorker`)
- Room DB **version 10**, additive migrations, `exportSchema=true`
- WorkManager expedited `OneTimeWorkRequest` til Gemini-kald (`GeminiReplyWorker`)
- GLES30-gradient-baggrund (`ShaderGradientBackground`), 200% scale, 60 FPS
- Gemini-kald via `HttpURLConnection` + kotlinx-serialization (`util/GeminiClient.kt`)
- minSdk 26, targetSdk/compileSdk 34. Tests: ren JVM (junit + kotlin-test), ingen mockk/robolectric

## Byggemiljø (Windows, denne maskine)
- Portabel JDK 17: `C:\Users\ls\.jdks\jdk-17.0.19+10` — sæt `JAVA_HOME` dertil før gradlew
- Android SDK: `C:\Users\ls\AppData\Local\Android\Sdk` (cmdline-tools, platform 34, build-tools 34.0.0) — peget på af `local.properties`
- Byg: `JAVA_HOME=C:/Users/ls/.jdks/jdk-17.0.19+10 ./gradlew assembleDebug` / `./gradlew test`

## Aktuel tilstand
**Working tree indeholder fase 1-, 2- og 3-ændringerne (se nedenfor) — ucommittet.** `./gradlew test` er grøn (61 tests); `./gradlew assembleDebug` bygger (APK ~19,5 MB — steg fra det indlejrede baggrundsbillede). README er omskrevet (AI-funktioner, leveringsstatus, Gemini API-nøgle-guide, DB v10, ny baggrundsfeature). **On-device test udestår stadig.**

## Seneste arbejde (fase 3 — statisk baggrund + UI-polish, 2026-07-08)
Brugerønske: fjern den animerede OpenGL-gradient-baggrund og dens presets, erstat med et statisk billede, og giv brugeren kontrol over Hue/Saturation på baggrunden og gennemsigtighed på kortene. Plus tre mindre UI-rettelser fra en tidligere runde (kort fylder skærmbredden, long-press-menu flyttet, "normale" switch-knapper) — se historik nedenfor.

1. **Fjernet:** `GradientBackground.kt` (GradientPreset-enum), `GradientShaders.kt` (GLSL-shaderkilde), `ShaderGradientBackground.kt` (GLES30 `GLSurfaceView`-renderer). Ingen build.gradle-ændring krævet (GLES30 var en del af Android-SDK'et, ikke en separat dependency).
2. **Nyt statisk billede:** `res/drawable-nodpi/bg_static.webp` (bruger leverede filen, ~290 KB). `drawable-nodpi` så billedet ikke skaleres pr. densitet — det er allerede en fuldskærms-baggrund.
3. **`ui/common/StaticBackground.kt`** (nyt) — `Image(painterResource(R.drawable.bg_static), contentScale = Crop, colorFilter = ...)`; farvefilteret bygges af en ren hue-rotation-matrix + en satureringsmatrix, komponeret via `combineColorMatrices` (testdrevet i `util/ColorMatrixMath.kt` / `ColorMatrixMathTest`, 6 tests — matematikken er bevidst holdt fri af Compose-typer så den kan JVM-testes uden Robolectric).
4. **`ui/common/BackgroundSetting.kt`** omskrevet — samme objektnavn/`load(context)`-signatur som før (så `VibeActionsApp.onCreate` ikke skulle ændres), men holder nu `hue: Float` (0–360°), `saturation: Float` (0–2, 1 = uændret) og `cardOpacity: Float` (0–1) som `mutableFloatStateOf`, persisteret i `"ui_settings"` under `bg_hue`/`bg_saturation`/`card_opacity`.
5. **Settings → "Udseende":** preset-chips erstattet af tre `Slider`e (Farvetone, Mætning, Card-gennemsigtighed) der skriver direkte til `BackgroundSetting`.
6. **`MacroCard.kt`:** hardcodet `Surface.copy(alpha = 0.93f)` erstattet af `Surface.copy(alpha = BackgroundSetting.cardOpacity)`.

**TDD-noter:** `ColorMatrixMathTest` (6 tests) verificerer identitets-egenskaber (`combine(IDENTITY, M) == M` og omvendt, `hue(0°) == hue(360°) == identity`, `saturation(1) == identity`, `saturation(0)`-rækker matcher Rec.601-luma-vægte 0.213/0.715/0.072). Skrevet først, sås fejle (`NotImplementedError`), derefter implementeret.

## Tidligere arbejde (UI-polish, samme dag)
- Makro-listens grid gik fra 2 til 1 kolonne (`GridCells.Fixed(1)` i `MacroListScreen.kt`) — kort fylder nu skærmbredden.
- Long-press-menuen på kortet flyttet 45dp mod venstre (`DropdownMenu(offset = DpOffset((-45).dp, 0.dp))` i `MacroCard.kt`).
- `ThemedSwitch.kt` skrevet om fra en Canvas-tegnet squiggle-SVG-animation til en normal glidende toggle: hvid rund thumb, track grøn (`Primary`) når checked, gul (`Amber`) når unchecked. Bruges i editoren til "Enabled" og "AI-svar (Gemini)".

## Tidligere arbejde (fase 2 — leveringsstatus + widget-sync, 2026-07-08)
1. **Radio-niveau afsendelsesstatus:** Hver SMS armeres nu med en sent-kvittering (`PendingIntent` → ny `scheduler/SmsSentReceiver`). Log-rækken indsættes FØR afsendelse (PENDING) så kvitteringen kan adressere den; dispatch-stien finaliserer til SUCCESS/FAILED som før, og en senere radiofejl (ingen dækning, flytilstand, SMS-grænse …) flipper log + makrostatus til FAILED, poster korrigerende notifikation og opdaterer widgets. FAILED er terminal i `MacroLogDao.updateResult` (`AND status != 'FAILED'`), så en sen fejlkvittering ikke overskrives. Fejlkode→tekst-mapping er ren (`util/SmsResult.kt`, testdrevet i `SmsResultTest`).
2. **Widget-sync:** Ny `widget/WidgetRefresher` + `WidgetIds.widgetsFor(macroId)` (omvendt opslag). `MacroFirer.fire()` slutter med `widgets.refreshFor(macroId)`, så widget-undertitlen "Last: …" også opdateres ved planlagte/auto-afsendelser.
3. **README omskrevet:** AI-sektion med guide til gratis Gemini-nøgle (aistudio.google.com), leveringsstatus-pipeline, DB-skema v10, opdateret features/arkitektur/permissions.

## Tidligere arbejde (fase 1 — robusthed + sikkerhed, 2026-07-08)
Udspringer af en frisk kodegennemgang. Ændringer:

1. **Alarmer/geofences overlever app-opdatering:** `BootReceiver` reagerer nu også på `MY_PACKAGE_REPLACED` (Android rydder alarmer + geofences ved hver (gen)installation). Desuden selvhelbredelse: `VibeActionsApp.onCreate` kalder `rescheduleAll()` ved hver app-start (idempotent — samme PendingIntent/geofence-id erstattes).
2. **Stale recipients på auto-svar-makroer:** Skift af trigger-type til INCOMING beholdt den gamle modtagerliste i DB, og Retry-knappen på en fejlet afsendelse kunne så sende den faste besked til forkerte numre. Fix i to lag: `EditorState.toMacro()` (ny ren funktion, testdrevet) nulstiller recipients for INCOMING, og `notifyResult` viser aldrig Retry for INCOMING-makroer (dækker eksisterende DB-rækker).
3. **API-nøgle ude af cloud-backup:** `backup_rules.xml` + `data_extraction_rules.xml` ekskluderer `ai_settings` (og `ai_sent`) fra backup og device-transfer; manifest peger på begge.
4. **API-nøgle i header:** Gemini-kald sender nøglen som `x-goog-api-key`-header i stedet for URL-query-param (URL'er ender i logs/proxier).
5. **Import-fejl vises:** ugyldig JSON gav før tavst "Imported 0 macros"; nu bærer `SettingsViewModel.import` et `Result<Int>` og skærmen viser fejlbeskeden.
6. **Aktivér/Deaktivér i long-press-menuen:** `MacroCard`-menuen har nu en toggle-post (PlayArrow/Pause-ikon) wired til `ToggleMacroUseCase`, som ellers var blevet død kode efter kort-redesignet (kortene har ingen switch længere).

**TDD-noter:** `EditorState.toMacro(id)` er ekstraheret fra `MacroEditorViewModel.save()` som ren funktion og testdrevet (`EditorStateToMacroTest`: INCOMING rydder recipients, trim af modtagere, anker-logik, blanke match-felter → null). Android-glue (receiver, manifest, notifikationer, UI) testes ikke — projektets etablerede mønster.

## Etableret mønster for læsbarhedsfix
Mørk-på-mørk tekst rettes ved at tilføje eksplicit `color = OnSurface` til `Text(...)`-labels og `tint = OnSurface`/`OnSurfaceVariant` til `Icon(...)`. Temafarver ligger i `ui/theme/Color.kt` (`OnSurface`, `OnSurfaceVariant`, `SurfaceVariant`, `Outline`). Switches får `SwitchDefaults.colors(uncheckedTrackColor = SurfaceVariant, uncheckedThumbColor = OnSurfaceVariant, uncheckedBorderColor = OnSurfaceVariant)`.

## Vigtige filer
- `scheduler/MacroFirer.kt` — central afsendelse; atomær claim ved planlagt fyring, sender body til log + notifikation
- `scheduler/GeminiReplyWorker.kt` — genererer AI-svar; per-macro instruktion vinder over global systemprompt; brevity-wrapper + `maxOutputTokens=150`; AUTO-claim mod dobbeltsend
- `scheduler/AlarmScheduler.kt` — `setAlarmClock` (Doze-eksempt) med fallback til `setAndAllowWhileIdle`
- `scheduler/BootReceiver.kt` — re-armer alarmer + geofences efter reboot **og app-opdatering** (`MY_PACKAGE_REPLACED`)
- `scheduler/SmsSentReceiver.kt` — radioens sent-kvitteringer; flipper log/makrostatus til FAILED ved radiofejl
- `sms/SmsDispatcher.kt` — multipart-afsendelse; armerer sent-kvittering per (log, modtager)
- `widget/WidgetRefresher.kt` — re-render af alle widgets bundet til en makro; kaldes fra `MacroFirer`
- `util/SmsResult.kt` — ren fejlkode→tekst-mapping (testdrevet)
- `scheduler/GeofenceManager.kt` / `GeofenceReceiver.kt` — Play Services geofences for LOCATION-makroer
- `domain/usecase/ToggleMacroUseCase.kt` — af-/på (alarm + geofence); bruges nu fra kortets long-press-menu
- `domain/IdempotencyGuard.kt` — `alreadySentToday` + `startOfDayMillis` (claim-tærskel)
- `util/AiReplyDedup.kt` — `aiReplyDedupKey(...)` ren dedup-nøgle
- `notifications/MacroNotificationManager.kt` — kanaler `macro_actions` (privat, redacted public version) + `macro_ai` (high); Retry aldrig for INCOMING
- `ui/MainActivity.kt` — `AiApprovalDialog`, notification-routing via `EXTRA_NAV` + `onNewIntent` + `launchMode="singleTop"`
- `ui/editor/MacroEditorViewModel.kt` — `EditorState` + ren `EditorState.toMacro(id)` (testdrevet)
- `ui/editor/MacroEditorScreen.kt` — Edit/New Macro; AI-skriv-dialog; background-location → settings på API 30+
- `ui/common/MacroCard.kt` — tap = editor, long-press = menu (Slet/Kopiér/Send nu/Aktivér-Deaktivér), ⠿ = træk-håndtag
- `ui/macrolist/MacroListScreen.kt` — grid + reorder; delte `deleteMacro`/`sendMacro`-handlere
- `util/GeminiClient.kt` — `geminiGenerate(...)` og `geminiSuggest(...)` (3 forslag); nøgle i header

## Løste problemer (historik)
- Alarmer/geofences døde ved hver app-opdatering → `MY_PACKAGE_REPLACED` + selvhelbredelse ved app-start
- Retry på fejlet auto-svar kunne ramme forældede numre → recipients ryddes for INCOMING + Retry skjult for INCOMING
- Gemini-nøgle røg i cloud-backup i klartekst → backup-regler ekskluderer `ai_settings`
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
- Gemini API-nøgle logges aldrig; gemt i SharedPreferences `"ai_settings"` (keys: `gemini_api_key`, `gemini_system_prompt`, `gemini_model`); ekskluderet fra backup
- UI-prefs i `"ui_settings"` (`bg_preset`); AUTO-dedup i `"ai_sent"`
- Push/commit kun når brugeren beder om det; arbejdet skal være bygget og verificeret først
- Bruger taler dansk

## Næste skridt
- **Fase 1 + 2 + 3 skal committes** når brugeren siger til.
- **On-device test udestår** — verificér især:
  - Baggrund: Hue/Saturation-sliders i Settings opdaterer billedet live; card-gennemsigtighed-slideren rammer synligt fra transparent til opak
  - Efter en app-opdatering (installér ny build ovenpå): planlagte alarmer fyrer stadig uden reboot
  - AI auto-svar-flowet (APPROVE + AUTO) på rigtig enhed — inkl. at nøglen stadig virker efter header-ændringen ("Test nøgle" i Indstillinger)
  - Leveringsstatus: send med flytilstand slået til lige efter dispatch → log/notifikation skal flippe til FAILED med "radio off"
  - Widget: planlagt/auto-afsendelse opdaterer widget-undertitlen uden tap
  - Notifikation: låseskærm viser kun "Sent: <navn>", fuld besked efter oplåsning, tryk åbner Loggen
  - LOCATION-makro: geofence re-armes ved app-start og opdatering
  - Long-press kort → menu (Slet/Kopiér/Send nu/Aktivér-Deaktivér, nu 45dp mod venstre); træk i ⠿-håndtag omarrangerer
  - Switch-knapperne (Enabled, AI-svar) ligner en normal slider, grøn/gul
- **Fremtidigt:** dependency-bump (Compose BOM, targetSdk 35) + `Result.retry()` for transiente Gemini-fejl
