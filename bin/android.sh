#!/usr/bin/env bash
# a production build of the android app, pointed at the production server (APP_SERVER names
# another): an app bundle for the play store and an apk, in <module>/build/outputs.
#
# to sign them, set these (the upload key; keep it out of the repo):
#   ANDROID_KEYSTORE           path to the keystore
#   ANDROID_KEYSTORE_PASSWORD
#   ANDROID_KEY_ALIAS
#   ANDROID_KEY_PASSWORD
# without them, both come out unsigned. they reach the android gradle plugin as its injected
# signing properties, so the build needs no signing configuration of its own
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"

module="$(prop android.module)"
server_property="$(prop server.property)"
server="$(release_server)"

signing=()
if [ -n "${ANDROID_KEYSTORE:-}" ]; then
  [ -f "$ANDROID_KEYSTORE" ] || { echo "no keystore at $ANDROID_KEYSTORE" >&2; exit 1; }
  : "${ANDROID_KEYSTORE_PASSWORD:?set ANDROID_KEYSTORE_PASSWORD too}"
  : "${ANDROID_KEY_ALIAS:?set ANDROID_KEY_ALIAS too}"
  : "${ANDROID_KEY_PASSWORD:?set ANDROID_KEY_PASSWORD too}"
  # through the environment, not -P, so the passwords stay out of the process list. the names
  # have dots in them, which the shell can't export, but env can pass along
  signing=(
    "ORG_GRADLE_PROJECT_android.injected.signing.store.file=$(cd "$(dirname "$ANDROID_KEYSTORE")" && pwd)/$(basename "$ANDROID_KEYSTORE")"
    "ORG_GRADLE_PROJECT_android.injected.signing.store.password=$ANDROID_KEYSTORE_PASSWORD"
    "ORG_GRADLE_PROJECT_android.injected.signing.key.alias=$ANDROID_KEY_ALIAS"
    "ORG_GRADLE_PROJECT_android.injected.signing.key.password=$ANDROID_KEY_PASSWORD"
  )
fi

env ${signing[@]+"${signing[@]}"} ./gradlew "-P$server_property=$server" \
  clean ":$module:bundleRelease" ":$module:assembleRelease"

echo
echo "built for $server:"
find "$module/build/outputs" \( -name '*.aab' -o -name '*.apk' \) | grep -i release
