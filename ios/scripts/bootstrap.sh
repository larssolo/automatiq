#!/bin/zsh
# Checks the Mac is ready, generates the Xcode project, runs the core tests and builds the app.
# Run from anywhere: ios/scripts/bootstrap.sh   (add --device to also build signed for a real iPhone)
set -euo pipefail
cd "${0:A:h}/.."

ok()   { print -P "%F{green}✓%f $1"; }
fail() { print -P "%F{red}✗%f $1"; exit 1; }

[[ "$(xcode-select -p)" == *Xcode*.app* ]] || fail "Xcode isn't selected. Run: sudo xcode-select -s /Applications/Xcode.app"
xcodebuild -checkFirstLaunchStatus >/dev/null 2>&1 || fail "Xcode isn't set up yet. Open Xcode once (accept the license, install components), or run: sudo xcodebuild -license accept && sudo xcodebuild -runFirstLaunch"
ok "$(xcodebuild -version | head -1)"

command -v xcodegen >/dev/null || fail "XcodeGen is missing. Run: brew install xcodegen"
[[ -f Config/Signing.xcconfig ]] || cp Config/Signing.example.xcconfig Config/Signing.xcconfig
TEAM=$(sed -n 's/^DEVELOPMENT_TEAM *= *//p' Config/Signing.xcconfig)

xcodegen generate --quiet && ok "TextFox.xcodeproj generated"

(cd Packages/TextFoxCore && swift test --quiet >/dev/null 2>&1) && ok "Core tests pass" || fail "Core tests fail: cd ios/Packages/TextFoxCore && swift test"

xcodebuild -project TextFox.xcodeproj -scheme TextFox -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/DerivedData CODE_SIGNING_ALLOWED=NO -quiet build \
  && ok "App and widget build for the simulator" || fail "Simulator build failed (see the errors above)"

if [[ "${1:-}" == "--device" ]]; then
  [[ "$TEAM" =~ ^[A-Z0-9]{10}$ ]] || fail "Put the Team ID in ios/Config/Signing.xcconfig first"
  xcodebuild -project TextFox.xcodeproj -scheme TextFox -destination 'generic/platform=iOS' \
    -derivedDataPath build/DerivedData -allowProvisioningUpdates -quiet build \
    && ok "Signed build for iPhone (team $TEAM)" || fail "Device build failed: is the Apple ID added in Xcode → Settings → Accounts?"
fi
