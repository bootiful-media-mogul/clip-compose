#!/usr/bin/env bash
# builds the ios app for development (talking to the server the apps default to, on this
# machine) and runs it on a simulator, showing its output here until you quit it.
# SIMULATOR=<name> picks which (xcrun simctl list devices available).
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"

project="$(prop ios.project)"
scheme="$(prop ios.scheme)"
simulator="${SIMULATOR:-$(prop ios.simulator)}"
derived="$(dirname "$project")/build/dev"
products="$derived/Build/Products/Debug-iphonesimulator"

xcodebuild build \
  -project "$project" \
  -scheme "$scheme" \
  -configuration Debug \
  -destination "platform=iOS Simulator,name=$simulator" \
  -derivedDataPath "$derived"

# the scheme's app: extensions are inside it, as .appex, so it's the only .app at the top
app="$(find "$products" -maxdepth 1 -name '*.app' | head -n 1)"
[ -n "$app" ] || { echo "the $scheme scheme built no app in $products" >&2; exit 1; }

xcrun simctl boot "$simulator" 2>/dev/null || true
# newer xcodes show simulators in DeviceHub, older ones in Simulator
open -b com.apple.dt.Devices 2>/dev/null || open -a Simulator
xcrun simctl install "$simulator" "$app"
bundle="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$app/Info.plist")"
xcrun simctl launch --console-pty --terminate-running-process "$simulator" "$bundle"
