#!/bin/zsh
# App Store screenshots (6.9-inch iPhone) from the demo data, into docs/ios/screenshots/.
set -euo pipefail
cd "${0:A:h}/.."
OUT="${0:A:h}/../../docs/ios/screenshots"
mkdir -p "$OUT"
SIM=$(xcrun simctl list devices available | awk '/iPhone [0-9]+ Pro Max \(/ {print; exit}' | grep -oE '[0-9A-F-]{36}')
[[ -n "$SIM" ]] || { echo "No iPhone Pro Max simulator"; exit 1; }
xcrun simctl boot "$SIM" 2>/dev/null || true
# A clean status bar: 9:41, full battery and signal.
xcrun simctl status_bar "$SIM" override --time "9:41" --batteryState charged --batteryLevel 100 --cellularBars 4 --wifiBars 3
xcodegen generate --quiet
STORE_SHOTS=1 TEST_RUNNER_STORE_SHOTS=1 TEST_RUNNER_SHOT_DIR="$OUT" xcodebuild test -project TextFox.xcodeproj -scheme TextFox \
  -destination "platform=iOS Simulator,id=$SIM" -derivedDataPath build/DerivedData CODE_SIGN_IDENTITY=- \
  -only-testing:TextFoxUITests/TextFoxUITests/testAppStoreScreenshots -quiet
xcrun simctl status_bar "$SIM" clear
ls "$OUT"
