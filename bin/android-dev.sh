#!/usr/bin/env bash
# builds the android app for development (talking to the server the apps default to, on this
# machine), and runs it on the emulator, starting one if none is running. AVD=<name> picks
# which (emulator -list-avds).
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"

module="$(prop android.module)"
application_id="$(prop android.applicationId)"

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
ANDROID_SERIAL="$serial" ./gradlew ":$module:installDebug"

# whichever activity the app's launcher icon opens
activity="$("$adb" -s "$serial" shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$application_id" | tr -d '\r' | tail -n 1)"
"$adb" -s "$serial" shell am start -n "$activity"
