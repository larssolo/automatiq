# iPhone app: start of a new session

Read `docs/ios/PLAN.md` first: it is the plan and the scope.

## State (2026-11-24)

- `ios/` holds the XcodeGen spec (`project.yml`), the core package `Packages/TextFoxCore`, a placeholder app (a list showing one sample macro) and a placeholder widget.
- **TextFoxCore is ported from the Android app and tested.** It has 26 tests, and `swift test` passes. It contains:
  - the macro, folder and backup model. It decodes Android backup files, including legacy single-number files, unknown fields and unknown action types.
  - scheduling: weekdays, every N weeks, anchor, end date, random days and jitter. The random choices are deterministic because Swift's `hashValue` changes on every launch, so the core uses a Java-compatible `stableHash` with SplitMix64. The values are not bit-identical to Android's, and they don't need to be.
  - templates, sender and keyword matching, quiet hours and the AI vary prompt.
  - the trial in calendar days, and the early-bird code.
- **The app and widget build for the simulator, and the app runs** (verified 2026-11-24 with Xcode 27.2 and the iOS 27.2 simulator; `bootstrap.sh` passes).
- `ios/Config/Signing.xcconfig` is local and not in git. It holds Lars' Team ID (F545QS7GV6), and the Apple ID is signed in to Xcode.
- The signed device build doesn't work yet only because no iPhone has been registered with the team. Connect Lars' iPhone by cable (Developer Mode on, "Trust"), then run `bootstrap.sh --device` again: Xcode registers the device and creates the profiles itself.

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
