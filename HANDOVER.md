# Overlevering — Automatiq (VibeActions)

> Kopiér denne fil ind i en ny chattråd som kontekst. Sidst opdateret: 2026-07-18 (fase 5: 8 nye funktioner).

## Seneste arbejde (fase 8 — review-rettelser af fase 6+7, 2026-07-19)

Adversarial kodegennemgang (to review-agenter) af mapper- + mistet-opkald/deferral-arbejdet fandt 2 reelle fejl + flere mellem/lav. Rettet på gren `claude/app-review-improvements-5fide7` (frisk fra main, da PR #3 er merget). **124 grønne JVM-tests** (fra 119). `versionCode` bumpet 1→2, `versionName` "1.0"→"1.1".

- **HØJ – crash under mappe-træk:** `MacroListScreen` nulstillede den lokale rækkeliste ved *enhver* vm-emission, også midt i et træk → gemte mappe-medlemmer blev gendannet, og `dropped()` gen-indsatte dem → dublerede LazyGrid-nøgler → crash. Fix: `LaunchedEffect(vmRows) { if (draggedKey == null) rows = vmRows }` + idempotent gen-indsættelse (kun medlemmer der ikke allerede er i listen). `draggedKey`/`hiddenMembers` flyttet før effekten.
- **HØJ – mistet-opkald-svar droppet på mange OEM'er:** `advanceCallState` beskyttede kun mod `null`-nummer, ikke `""` (tom streng på dublet-broadcast) → rigtige nummer overskrevet. Fix: `number?.takeIf { it.isNotBlank() } ?: state.number` (+ test).
- **Mellem – AI-fallback sendte tokens bogstaveligt:** `GeminiReplyWorker` faldt tilbage til rå `messageBody` (uekspanderet, da `MacroFirer` aldrig ekspanderer `overrideBody`). Fix: ekspandér fallback med `expandTemplate(..., sender)`.
- **Mellem – dobbelt-svar muligt:** throttle i `IncomingReplyRouter` gjort atomisk (`ConcurrentHashMap.compute`); `DeferredReplyWorker` fik persistent one-shot-claim (prefs "deferred_sent", pruned pr. dag) mod WorkManager-genkørsel efter procesdød.
- **Mellem – sletning af mappe smed medlemmer til toppen:** `deleteFolder`/`onMoveToFolder` renumererer nu frigivne makroer til halen af destinationens sort-space.
- **Lav:** stale-RINGING-expiry (`ringStartedAt` + `CALL_STALE_MS`, forhindrer falsk mistet-opkald til forkert nummer efter tabt IDLE); `goAsync()` om deferral-enqueue i SMS-/opkalds-receiver; `onMove`-fallback bail'er ved ukendt nøgle; `resolveDrop` ignorerer orphan "ghost"-folderId; editor skjuler "Send now (test)" for reply-makroer og ekspanderer `{afsender}` i preview.

**UI-ønsker fra brugeren (efter on-device test):** X-ikoner (fjern modtager / ryd udløb / ryd søgning) fik eksplicit `tint = OnSurface` (var sort-på-sort). **FolderCard lavet om:** switchen åbner/lukker nu mappen (samme callback som tap på kortet), chevronen er fjernet, og "Enable all"/"Disable all" (den gamle master-switch-funktion) ligger nu i long-press-menuen sammen med Rename/Delete. FolderCard-signaturen er uændret. For ikke at fejllæses som tænd/sluk bruger mappe-switchen grå (0xFF5A5A5A, åben) / mørkegrå (0xFF2C2C2C, lukket) spor og FIRKANTET knop (RoundedCornerShape 4dp, spor 8dp) — `ThemedSwitch` fik valgfri `checkedTrackColor`/`uncheckedTrackColor`/`trackShape`/`thumbShape` med defaults, så alle andre switches er uændrede (grøn/gul, rund).

## Seneste arbejde (fase 8f — "Open location settings" gjorde ingenting, 2026-07-19)

Rodårsag (verificeret på enheden via dumpsys): knappen havde `enabled = fineGranted`, og ACCESS_FINE_LOCATION var ikke givet → deaktiveret knap, der ligner en aktiv på mørkt tema og lover handling. Intent'en (ACTION_APPLICATION_DETAILS_SETTINGS) resolver fint på MIUI (bevist med `am start` → com.miui.appmanager.ApplicationsDetailsActivity). Fix: knappen er altid aktiv og kæder flowet: mangler foreground-lokation → RequestMultiplePermissions(FINE+COARSE), og ved accept på API 30+ åbnes app-Settings DIREKTE i callbacken; ellers åbner den Settings (API 30+) / bg-dialog (API 29). Label skifter: "Allow location" → "Open location settings". Hjælpeteksten opdateret.

## Seneste arbejde (fase 8e — ikon-justering på kortene, 2026-07-19)

Fra brugerens screenshot: højresidens action-ikoner (mappe-chevron, send-pil, AI-badge) flugtede ikke og klæbede til trækhåndtaget. Nu: alle action-marks ligger i en fast 44dp-slot (fælles bagkant på tværs af korttyper), er ~20 % større (send 19→23dp, chevron-cirkel 30→36dp/ikon 15→18dp, AI-badge 9→11sp) og har 10dp Spacer-luft før håndtagets streger. Både MacroCard og FolderCard.

## Seneste arbejde (fase 8d — søgning flyttet i headerbjælken, 2026-07-19)

Det store pilleformede søgefelt under headeren er fjernet. I stedet: et diskret søgeikon (32dp) yderst til højre i wordmark-bjælken (kun når der findes makroer); tryk morfer bjælken til et kompakt inline-BasicTextField (mono 15sp, grøn cursor, "Find a macro"-hint, auto-fokus via FocusRequester) med X der rydder + lukker. Samme søgelogik som før (searching = query ikke blank → flad liste uden drag).

## Seneste arbejde (fase 8c — kant-farveregel + mappe-fane, 2026-07-19)

1. **Designregel (CardVisuals.breathingVeinColor):** ALLE kort bærer altid deres egen accentfarve på kant-"åren" — fuld og åndende når aktiv, svag (alpha 0.35) i hvile. Aldrig neutral grå (før fik alle inaktive kort samme grå kant).
2. **Mappekortet har nu fysisk mappe-silhuet** (brugervalg blandt 3 forslag): custom `FolderTabShape` i FolderCard.kt — hævet fane øverst til venstre (38 % bredde, 12dp høj, afrundet top-venstre 8dp, skrå højrekant 16dp) fyldt med accent alpha 0.45; kroppen beholder leaf-cut-hjørnerne (6/18/6). Kortet er nu 88dp (12 fane + 76 krop); struktur = Column(fane-strip, Row(indhold)) klippet af shapen. Fanens bredde-fraktion deles mellem shape og strip via TAB_WIDTH_FRACTION.

## Seneste arbejde (fase 8b — chevron-knap, 2026-07-19)

Brugeren fandt åbn/luk-skyderen for stor og klodset → erstattet af en lille rund chevron-knap (30dp cirkel i mappens accentfarve, alpha 0.16) med eget vektorikon `res/drawable/ic_chevron.xml` (rounded-stroke, hvid stroke så Compose-tint farver den). Peger højre = lukket, roterer 90° ned = åben (tween 200ms). Samme onClick-callback som kortet. `ThemedSwitch`s valgfri farve/form-parametre fra fase 8 er nu ubrugte men bevaret (generisk API, defaults uændrede).

## Seneste arbejde (fase 7 — mapper, 2026-07-19)

**Makro-mapper:** navngivne accordion-kort i listen. DB **12 → 13** (MIGRATION_12_13: ny `folders`-tabel + `folder_id` på macros; additiv). Motoren (scheduler/sms/widget) rører aldrig folder_id.

1. **Ren logik i `util/FolderLayout.kt`** (flattenRows/hideMembers/resolveDrop/layoutOrders/folderSwitchOn), testdrevet — **119 grønne JVM-tests** (fra 100). Slip-reglen: rækken over landingspositionen afgør medlemskab (udfoldet mappe-header eller medlem ⇒ ind i mappen; alt andet ⇒ roden). Kendt hjørne: slip lige under en udfoldet mappes sidste medlem lander I mappen — menuen "Move to root" er escape.
2. **UI:** `ui/common/FolderCard.kt` (chevron, master-ThemedSwitch = alle aktive, åndende åre via ny delt `ui/common/CardVisuals.kt` — LeafShape + breathingVeinColor udtrukket fra MacroCard), medlemmer indrykket 16dp med mappefarvet åre, FAB-menu (New macro/New folder), "Move to folder…"/"Move to root" i long-press-menu, delete-dialog med Undo der genopretter medlemskaber.
3. **ViewModel:** `rows`-flow (flattenRows), onDrop persisterer via layoutOrders; `ToggleFolderUseCase` looper medlemmer gennem ToggleMacroUseCase. `MacroRepository.persistOrder`/`onReorder` fjernet.
4. **Backup:** `folders`-array + `folderId` på MacroDto; orphan-guard på BEGGE parse-grene (også legacy bare-array); bagudkompatibel.
5. **Editor:** `EditorState.folderId` passthrough så redigering ikke smider makroen ud af mappen.

**Review-fixes undervejs (subagent-drevet eksekvering):** drop-rule testdækning, legacy-array orphan-guard, og et StateFlow-conflation-bug hvor et no-op mappe-træk permanent skjulte medlemmer (fix: stash + splice i MacroListScreen).

**Spec/plan:** `docs/superpowers/specs/2026-07-19-macro-folders-design.md`; `docs/superpowers/plans/2026-07-19-macro-folders.md`.

## Seneste arbejde (fase 6 — quick wins + ubesvaret opkald + organisk UI, 2026-07-18)

Fuld kodegennemgang (dom: solid; 5 småfejl fundet, se nedenfor) fulgt af implementering. Byggemiljø: brugerens Mac (denne fase er lavet i `automatiq-main`-mappen, en zip-kopi af main uden git). **100 grønne JVM-tests** (fra 85). `assembleDebug` bygger.

**Nye funktioner:**
1. **Widget-undertekst "Last: 14:32 ✓"** — `util/WidgetSubtitle.kt` (ren, testdrevet); viser tid (i dag) eller kort dato, ✓/✗ efter status. Genopretter featuren som var regresseret til statisk "Tap to send".
2. **Synlig send-knap på makrokortet** — accent-farvet send-ikon før drag-håndtaget; skjult for reply-typer (INCOMING/MISSED_CALL, ingen modtagerliste). Long-press-menuen har stadig "Send now".
3. **Quiet hours udskyder i stedet for at droppe** — `scheduler/DeferredReplyWorker` (unik pr. (kind, modpart), REPLACE = én besked ved vinduets slutning, svarer på det seneste); `util/QuietHours.minutesUntilQuietEnd` (testdrevet). Matching/throttle/AI-dispatch udtrukket fra SmsReplyReceiver til **`scheduler/IncomingReplyRouter`** (delt af SMS-receiver, opkalds-receiver og deferred worker).
4. **`{afsender}`-token** — `SENDER_TOKEN` i MessageTemplate; expandTemplate tager valgfri sender (= overrideRecipient i MacroFirer, dvs. modparten ved auto-svar/mistet opkald); uudfyldt ved scheduled/manual. Editor-hint viser tokenet kun for reply-typer.
5. **Ubesvaret opkald-trigger (MISSED_CALL)** — nyt TriggerType (ingen DB-migration nødvendig, kolonnen er tekst). `util/CallState.kt`: ren state-machine (RINGING→IDLE uden OFFHOOK = mistet; afvist tæller med — bevidst; nummer huskes på tværs af dobbelt-broadcast; state persisteres i prefs da processen kan dø under lange opkald). `scheduler/CallStateReceiver` (manifest-receiver — PHONE_STATE er protected broadcast og undtaget implicit-broadcast-reglerne). Genbruger matchSender som opkaldsfilter (`callerMatches`). Nye permissions: READ_PHONE_STATE + READ_CALL_LOG (uden call log redacter Android nummeret → intet at svare til; editor beder om begge når triggeren vælges). Quiet hours udskyder også disse.

**Organisk UI-pass (fase B, /frontend-design):** retning "bio-terminal" — terminal-DNA'et (mono, matrix-grøn) + liv:
- `StaticBackground` + `AuroraOverlay`: tre drivende radiale glows (Primary/teal `#42D1CA`/Amber, alpha 4-9%, 52-73s cyklusser) — pauses automatisk når UI ikke er synligt.
- `MacroCard`: asymmetriske "leaf-cut"-hjørner (`LeafShape` 18/6/18/6), accent-gradient i stedet for flad tint, fjeder-skala ved tryk (spring 0.965), "vein"-kanten ånder (alpha 0.45→1, 3.6s) når makroen er aktiv.
- `MacroListScreen`: wordmark-header "automatiq" med pulserende status-dot (+ "N of M live"), pill-formet søgefelt, blob-FAB (26/14/26/14), varmere tomtilstande ("Nothing automated yet." / "Tap + and teach your phone its first trick.").
- `MainActivity`: bundnav med leaf-klippede tophjørner, egne ikoner pr. fane (Bolt/History/Tune — før brugte Macros og Log samme ikon), grønne selected-farver.
- `LogScreen`: tomtilstand ("All quiet."), mono statuslinje+timestamps, blødere divider (Outline 45% i stedet for lys OutlineVariant), varmere clear-dialog.
- `Type.kt`: titleSmall → JetBrains Mono (sektionsoverskrifter i Settings/Health får terminal-accent).

**Kendte småfejl fra gennemgangen som IKKE er rettet endnu:** (a) `GeofenceManager.addOnFailureListener` notificerer altid, ignorerer notifyOnFailure → kan spamme ved app-start med OS-lokation slået fra; (b) alfanumerisk afsender-check (`none { isDigit }`) slipper "DHL2u" igennem; (c) versionCode/-Name bumpes aldrig (stadig 1/"1.0"); (d) `MacroEditorScreen.kt` er ~950 linjer i én composable — bør splittes; (e) ~110 hardcodede engelske UI-strenge (kun app_name i strings.xml) — ingen lokalisering mulig.

## Seneste arbejde (fase 5 — 8 nye funktioner, 2026-07-18)

Bygger på brugerønske om 8 funktionsforslag. DB bumpet **10 → 12** (MIGRATION_10_11: `trigger_on_connect`/`trigger_target`/`trigger_target_label` på macros; MIGRATION_11_12: `delivery_status` på macro_logs). Ren logik testdækket: **85 grønne JVM-tests** (QuietHours, BackupJson, StateTrigger, delivery-status-mapping, nye felter). Android-lagene (service, receivers, Compose-skærme) kunne ikke kompileres her — **byg `assembleDebug` lokalt før release.**

1. **Leveringskvitteringer** — `SmsDispatcher` armerer nu også en delivery-`PendingIntent` → ny `scheduler/SmsDeliveredReceiver` sætter `delivery_status` (DELIVERED/FAILED, FAILED terminal for multipart). Loggen viser "Delivered ✓✓" / "Not delivered". GSM TP-Status→outcome er ren (`util/SmsResult.deliveryStatusForReport`, testdrevet). Mange operatører sender aldrig rapport → status forbliver ukendt (vises ikke).
2. **Health-skærm** — `ui/health/HealthScreen` + `HealthViewModel`. System-status (exact alarms, batteri-optimering, notifikationer, sidste catch-up) med "Fix"-genveje + næste-affyringstid pr. aktiv makro. Nås fra Settings → "Health check" (nav-rute `health`). Catch-up-workeren skriver `AppSettings.lastCatchUpAt`.
3. **Quiet hours** — global (`data/AppSettings`, prefs "app_settings"). `SmsReplyReceiver` springer ALLE auto-svar over (inkl. AI-godkendelse) i vinduet. Vindue-logik med midnats-wrap er ren (`util/QuietHours.isWithinQuietHours`, testdrevet). UI i Settings med tidsvælgere.
4. **State-triggere (CHARGING/BLUETOOTH/WIFI)** — nye `TriggerType`-værdier + felter (`triggerOnConnect`, `triggerTarget`, `triggerTargetLabel`). Forgrundstjeneste `scheduler/TriggerMonitorService` (foregroundServiceType=specialUse) registrerer runtime-receivers (power/BT ACL) + `ConnectivityManager.NetworkCallback` (Wi-Fi). `scheduler/TriggerMonitor.sync()` starter/stopper tjenesten når enabled state-makroer findes; kaldes fra Save/Delete/Toggle/RescheduleAll. Matchning ren (`util/StateTrigger.stateTriggerMatches`, testdrevet). Editor: connect/disconnect-vælger, BT paired-device-dropdown (kræver BLUETOOTH_CONNECT), Wi-Fi SSID-felt. Nye tilladelser i manifest.
5. **Beskedforhåndsvisning** — editoren viser `expandTemplate(...)` (tokens udfyldt "lige nu") når beskeden indeholder en `{token}`.
6. **Geofence-fejl synlige** — `GeofenceManager.register(macro, notifyOnFailure)` poster `notifyGeofenceError`-notifikation ved permission-manglende/Play-Services-fejl (kun ved eksplicit save/toggle, ikke ved app-start-masse-registrering).
7. **Fuld backup** — `util/BackupJson` (macros + settings-map, bagudkompatibel med gammelt macro-only array). Settings samles/genskabes i `SettingsViewModel` (system prompt, model, udseende, quiet hours; API-nøgle kun ved opt-in). Round-trip testdrevet.
8. **Søgning/filter** — søgefelt i makrolisten (drag deaktiveret under søgning); per-makro-dropdown-filter i loggen (nu 4-vejs combine i `LogViewModel`).

**Vigtige nye/ændrede filer:** `data/AppSettings.kt`, `scheduler/{SmsDeliveredReceiver,TriggerMonitorService,TriggerMonitor}.kt`, `ui/health/*`, `util/{QuietHours,BackupJson,StateTrigger}.kt`, `data/db/Migrations.kt` (v10→12), `sms/SmsDispatcher.kt` (delivery), alle 4 use-cases (triggerMonitor.sync), `MacroFirer.fire()` returnerer nu `FireResult?`.


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
