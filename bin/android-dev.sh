#!/usr/bin/env bash
# builds the android app for development (talking to a gateway on this machine,
# http://127.0.0.1:1010, which the emulator reaches at 10.0.2.2), and runs it on the
# emulator, starting one if none is running. AVD=<name> picks which (emulator -list-avds).
set -euo pipefail
cd "$(dirname "$0")/.."

sdk="${ANDROID_HOME:-$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null)}"
sdk="${sdk:-$HOME/Library/Android/sdk}"
adb="$sdk/platform-tools/adb"
emulator="$sdk/emulator/emulator"

if ! "$adb" devices | grep -q '^emulator-.*device$'; then
  avd="${AVD:-$("$emulator" -list-avds | head -n 1)}"
  [ -n "$avd" ] || { echo "no emulator to start: make one in android studio's device manager" >&2; exit 1; }
  echo "starting the $avd emulator"
  nohup "$emulator" -avd "$avd" >/dev/null 2>&1 &
  "$adb" wait-for-device
  until [ "$("$adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
fi

serial="$("$adb" devices | awk '/^emulator-.*device$/ { print $1; exit }')"
ANDROID_SERIAL="$serial" ./gradlew :androidApp:installDebug
"$adb" -s "$serial" shell am start -n com.joshlong.mogul/.MainActivity
