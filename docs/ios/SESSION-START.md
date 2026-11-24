# iPhone app: start of a new session

Read `docs/ios/PLAN.md` first: it is the plan and the scope.

## State (2026-11-25, night)

Milestones 2–7 are built and run in the simulator (see PLAN.md). On PR branch `ios-scheduling`.

- **Macros (2):** list, folders, search, sort mode, context menus, swipe, undo; the full editor; log; settings.
- **Scheduling (3):** `Scheduler.swift` plans local notifications (core `Planner.swift`: 60 soonest sends, every macro gets its next one). Send opens the filled-in Messages sheet, Skip logs it. The catch-up logs what came due while the app was closed (PENDING until sent). Background refresh tops up. Location macros use `UNLocationNotificationTrigger` (shares the 64 budget).
- **Shortcuts + widget (4):** `Intents.swift` (Get Message, Get Recipients, Get Auto-Reply with quiet hours, Run Macro), `Shared/MacroEntity.swift`, the widget (small = chosen macro, medium = first four, tap → `textfox://run/<id>`), and the in-app guide `ShortcutsGuideView`.
- **AI (5):** core `Gemini.swift` (Android models, thinking config, quota fallback); `AI.swift` (key in the Keychain, consent, variation/reply with fixed-text fallback); Settings → AI; AI write and Preview AI variation in the editor. Not tried against Gemini yet (no key here; don't spend Lars' free quota in tests).
- **Purchases (6):** `Purchases.swift` with StoreKit 2: `textfox_trial` (free non-consumable that starts the 30 days — Apple's rule), `textfox_unlock`, `textfox_unlock_earlybird` (after code TEXTFOX50). Trial dates are in the Keychain. Runs, notification taps, planning and intents pause outside the trial. `TextFox/TextFox.storekit` is the local test store (the scheme's Run uses it).
- **Polish (7):** `Shared/Look.swift`: backdrop image (from Android) with hue/saturation/brightness, card opacity, presets, and the **iPhone look** (default on: calmer accents, SF fonts, Apple greens, even corners). `IntroViews.swift`: the fox intro on every cold start and the first-launch guide (replay in Settings). Phone check in Settings. Privacy manifest.
- **Release (8):** `docs/ios/APP-STORE.md` (App Store Connect steps, products, texts, review notes); screenshots in `docs/ios/screenshots/` from `scripts/screenshots.sh`.
- **Tests:** core 45 (`swift test`), UI 8 + the screenshot test (`bootstrap.sh --ui`, ~10 min). Debug launch arguments: `-resetData YES`, `-paywall NO`, `-skipIntro YES`, `-open textfox://…` (new, edit/<name>, run/<id>, log, settings, unlock, widget-preview, test-fire/<name>, test-tap/<name>, test-skip/<name>). Names with apostrophes don't survive XCUITest launch arguments.
- **Simulator builds are signed locally** (`CODE_SIGN_IDENTITY=-`) so the App Group works; unsigned builds fall back to Documents.
- Lars' phone is an **iPhone 8 (iOS 16)**: it can't run the app. He's finding a newer iPhone. The simulator can't send SMS or run Shortcuts automations, so milestone 1's real-device proof is still open.
- `ios/Config/Signing.xcconfig` (not in git) holds the Team ID F545QS7GV6. The signed device build needs a registered iPhone.

**Open / next:** Lars' review of the look (iPhone look vs TextFox look); real-device test of notifications, Shortcuts automations and purchases (TestFlight); a real Gemini call with Lars' key; App Store Connect setup per APP-STORE.md.

## First steps

1. Run `ios/scripts/bootstrap.sh`. It checks that Xcode is selected, set up and licensed and that XcodeGen is installed. It then generates the project, runs the core tests and builds the app and widget for the iOS Simulator. Fix whatever it reports before doing anything else.
2. If the simulator runtime is missing, run `xcodebuild -downloadPlatform iOS`. This is a large download on a slow connection, so ask Lars first.
3. Once the Team ID is in `Config/Signing.xcconfig`, run `ios/scripts/bootstrap.sh --device`. Then install it on Lars' iPhone with `xcrun devicectl device install app --device <id> build/DerivedData/Build/Products/Debug-iphoneos/TextFox.app`, using the id from `xcrun devicectl list devices`.
4. Start milestone 1 in PLAN.md: the proof. Build an App Intent that returns the expanded message, and a notification → `MFMessageComposeViewController` flow. Lars then sets up the Shortcuts automation on his phone.

## Rules (the same as the Android work)

- **No AI attribution anywhere.** No Co-Authored-By lines, no "Generated with", and no mention of AI tools in code, comments, commits or PRs.
- Commit as `larssolo <larssohl@gmail.com>` using the GIT_AUTHOR_* and GIT_COMMITTER_* env vars. Never change the git config.
- Merge only when Lars says merge, with `gh pr merge N --merge --delete-branch`.
- Lars writes in Danish, and he wants plain Danish without code in chat. The app UI is in English.
- Keep the iPhone's existing data and apps untouched. Never reset the phone or change its settings without asking.
