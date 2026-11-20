#!/usr/bin/env bash
# builds the ios app for development (talking to a gateway on this machine,
# http://127.0.0.1:1010) and runs it on a simulator, showing its output here until you quit
# it. SIMULATOR=<name> picks which (xcrun simctl list devices available).
set -euo pipefail
cd "$(dirname "$0")/.."

simulator="${SIMULATOR:-iPhone 17}"
derived=iosApp/build/dev
app="$derived/Build/Products/Debug-iphonesimulator/mogul-clip-ios.app"

xcodebuild build \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -destination "platform=iOS Simulator,name=$simulator" \
  -derivedDataPath "$derived"

xcrun simctl boot "$simulator" 2>/dev/null || true
# newer xcodes show simulators in DeviceHub, older ones in Simulator
open -b com.apple.dt.Devices 2>/dev/null || open -a Simulator
xcrun simctl install "$simulator" "$app"
bundle="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$app/Info.plist")"
xcrun simctl launch --console-pty --terminate-running-process "$simulator" "$bundle"
