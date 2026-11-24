# iPhone app: start of a new session

Read `docs/ios/PLAN.md` first: it is the plan and the scope.

## State (2026-11-24, evening)

- **Milestone 2 (macros) is built and runs in the simulator.** It has the same look as Android: the fox header with "N of M live", search, folder cards with the raised tab, macro cards with the breathing edge, AI badge and green/amber switch, the blob + button (New macro / New folder), and the bottom bar (Macros, Log, Settings).
  - **List:** long-press menu (Send now, Copy, Move to folder, Enable/Disable, Delete with Undo), swipe actions, and "Sort" mode (drag to reorder; a macro joins the folder above it).
  - **Editor:** Macro, When (Scheduled with time, Repeat/Once, weekdays, next run, more options; Manual; Auto-reply with sender/keyword; Location with map, current location, radius, arrive/leave), Do this (SMS/Reminder/Webhook), Send to with the contact picker, Message with variables and a live preview, AI variation/reply settings, Send now (test), the list of what blocks Save, and a confirmation before discarding changes.
  - **Log** with filters, and **Settings** with quiet hours, backup export/import in the Android format, and About.
- **Storage:** one JSON file (`textfox.json`) in the App Group container, falling back to Documents when the group isn't available (unsigned simulator builds). It holds macros, folders, log and settings. The macros use the Android backup format. Debug builds seed demo macros on first launch, and `-resetData YES` starts over (the UI tests use it).
- **Send now:** SMS opens the system Messages sheet, pre-filled. The simulator can't send texts, so it logs FAILED "This device can't send text messages". Reminders post a notification, and webhooks call the URL.
- **Not yet:** scheduled sending/notifications (milestone 3), App Intents/Shortcuts and the real widget (4), Gemini (5; "AI write" shows a toast), purchases (6), onboarding (7).
- **TextFoxCore** has 37 tests. New since the morning: `Presentation.swift` (accent colour, summaries, "Next …" wording), `FolderLayout.swift` (flattened rows, drop rules, sort orders) and `EditorDraft.swift` (the editor form, validation, and mapping to a macro, ported from Android's EditorState).
- **UI tests** (`TextFoxUITests`, 4 tests) click through the list, folders, search, editor, every trigger, create/delete/undo, send/log and settings. Run `bootstrap.sh --ui`. Screenshots are saved to `$TEST_RUNNER_SHOT_DIR` when it's set.
- **Debug links:** `textfox://new`, `textfox://edit/<id or name>`, `textfox://log`, `textfox://settings`. In the simulator, launch with `xcrun simctl launch <sim> app.kodekid.textfox -open textfox://…` to avoid the "Open in TextFox?" prompt.
- Lars' phone is an **iPhone 8 (iOS 16)**, which can't run the app (iOS 17+). Lars chose to keep iOS 17 and test in the simulator until he has a newer iPhone. The simulator can't send SMS or run Shortcuts automations, so the real-device part of milestone 1 waits for that phone.
- `ios/Config/Signing.xcconfig` (not in git) holds the Team ID F545QS7GV6. The signed device build only needs a registered iPhone: connect it with Developer Mode on, then run `bootstrap.sh --device`.

## First steps

1. Run `ios/scripts/bootstrap.sh`. It checks that Xcode is selected, set up and licensed and that XcodeGen is installed. It then generates the project, runs the core tests and builds the app and widget for the iOS Simulator. Fix whatever it reports before doing anything else.
2. If the simulator runtime is missing, run `xcodebuild -downloadPlatform iOS`. This is a large download on a slow connection, so ask Lars first.
3. Once the Team ID is in `Config/Signing.xcconfig`, run `ios/scripts/bootstrap.sh --device`. Then install it on Lars' iPhone with `xcrun devicectl device install app --device <id> build/DerivedData/Build/Products/Debug-iphoneos/TextFox.app`, using the id from `xcrun devicectl list devices`.
4. Start milestone 1 in PLAN.md: the proof. Build an App Intent that returns the expanded message, and a notification → `MFMessageComposeViewController` flow. Lars then sets up the Shortcuts automation on his phone.

## Rules (the same as the Android work)

- **No AI attribution anywhere.** No Co-Authored-By lines, no "Generated with", and no mention of Claude in code, comments, commits or PRs.
- Commit as `larssolo <larssohl@gmail.com>` using the GIT_AUTHOR_* and GIT_COMMITTER_* env vars. Never change the git config.
- Merge only when Lars says merge, with `gh pr merge N --merge --delete-branch`.
- Lars writes in Danish, and he wants plain Danish without code in chat. The app UI is in English.
- Keep the iPhone's existing data and apps untouched. Never reset the phone or change its settings without asking.
