# Overlevering — Automatiq (VibeActions)

> Kopiér denne fil ind i en ny chattråd som kontekst. Sidst opdateret: 2026-06-23.

## App

**Automatiq** — native Kotlin/Compose Android-app til SMS-automatisering.
- Pakke: `com.vibeactions`
- Repo: `~/VibeActions`, remote `https://github.com/larssolo/automatiq.git`
- Branch: `main` (op til dato med origin)
- Funktioner: planlagte SMS, AI auto-svar via Gemini, macro-knap-widgets, lokationstriggere.

## Tech stack
- Kotlin + Jetpack Compose (Material 3, mørkt tema)
- Hilt DI (`@HiltViewModel`, `@AndroidEntryPoint`, `@HiltWorker`)
- Room DB **version 10**, additive migrations, `exportSchema=true`
- WorkManager expedited `OneTimeWorkRequest` til Gemini-kald (`GeminiReplyWorker`)
- GLES30-gradient-baggrund (`ShaderGradientBackground`), 200% scale, 60 FPS
- Gemini-kald via `HttpURLConnection` + kotlinx-serialization (`util/GeminiClient.kt`)

## Aktuel tilstand (uncommitted)
**Én ændret fil, ikke committet endnu:**
`app/src/main/java/com/vibeactions/ui/editor/MacroEditorScreen.kt`

**Hvad blev lavet:** Læsbarhedsfix af mørk-på-mørk tekst på Edit Macro-skærmen (bruger pegede på røde ringe i screenshot):
1. "Enabled"-label → tilføjet `color = OnSurface`
2. Kontakt-ikon ved modtager-felt ("Number 1") → tilføjet `tint = OnSurface`
3. Kontakt-ikon ved "From sender" (auto-svar) → tilføjet `tint = OnSurface`

`./gradlew assembleDebug` kører igennem uden fejl. **Ikke committet/pushet endnu** — afventer brugerens go.

## Etableret mønster for læsbarhedsfix
Mørk-på-mørk tekst rettes ved at tilføje eksplicit `color = OnSurface` til `Text(...)`-labels og `tint = OnSurface`/`OnSurfaceVariant` til `Icon(...)`. Temafarver ligger i `ui/theme/Color.kt` (`OnSurface`, `OnSurfaceVariant`, `SurfaceVariant`, `Outline`). Switches får `SwitchDefaults.colors(uncheckedTrackColor = SurfaceVariant, uncheckedThumbColor = OnSurfaceVariant, uncheckedBorderColor = OnSurfaceVariant)`.

## Vigtige filer
- `ui/editor/MacroEditorScreen.kt` — Edit/New Macro-skærm; AI-skriv-dialog (tilstandsmaskine `AiState`: Idle/Loading/Suggestions/Err), per-macro AI-instruktionsfelt
- `ui/MainActivity.kt` — `AiApprovalDialog` (in-app godkendelse), notification-routing via `onNewIntent` + `launchMode="singleTop"`
- `notifications/MacroNotificationManager.kt` — ét kanal `macro_actions`; `notifyAiApproval` (APPROVE), `notifyAiSent` (AUTO), `notifyResult`
- `scheduler/GeminiReplyWorker.kt` — genererer AI-svar; per-macro instruktion vinder over global systemprompt; hard-coded brevity-wrapper + `maxOutputTokens = 150`
- `scheduler/AiReplyActionReceiver.kt` — håndterer `ACTION_AI_SEND` / `ACTION_AI_DISCARD`
- `util/GeminiClient.kt` — `geminiGenerate(...)` og `geminiSuggest(...)` (3 forslag)

## Løste problemer (historik)
- Switch næsten usynlig på mørk baggrund → `Outline` 0xFF333333 → 0xFF505050 + eksplicitte switch-farver
- 30 FPS gradient stutter ("det hakker") → revertet til 60 FPS, beholdt 200% scale
- MIUI viste ikke Send-knap på AI-godkendelses-notifikation → in-app `AiApprovalDialog` (tap på notifikation åbner app)
- AI auto-svar ramlede → brevity-wrapper-prompt + `maxOutputTokens=150` + per-macro instruktionsfelt
- Læsbarhed på labels "Repeat on", "Recipients", "AI-svar (Gemini)", "AI afsendelse" → eksplicit `OnSurface`

## Konventioner / constraints
- Telefonnumre maskeres altid i notifikationer/log (`maskPhone` / `maskRecipients`)
- Gemini API-nøgle logges aldrig; gemt i SharedPreferences `"ai_settings"` (keys: `gemini_api_key`, `gemini_system_prompt`, `gemini_model`)
- UI-prefs i `"ui_settings"` (`bg_preset`)
- Push/commit kun når brugeren beder om det; arbejdet skal være bygget og verificeret først
- Bruger taler dansk

## Næste skridt
- Få brugerens go til at committe + pushe læsbarhedsfixet (3 ændringer i `MacroEditorScreen.kt`)
- On-device test af AI auto-svar-flowet udestår stadig
