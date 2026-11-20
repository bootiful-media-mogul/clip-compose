#!/usr/bin/env bash
# a production build of the android app, pointed at production mogul: an app bundle for the
# play store and an apk, in androidApp/build/outputs.
#
# to sign them, set these (the upload key; keep it out of the repo):
#   MOGUL_ANDROID_KEYSTORE           path to the keystore
#   MOGUL_ANDROID_KEYSTORE_PASSWORD
#   MOGUL_ANDROID_KEY_ALIAS
#   MOGUL_ANDROID_KEY_PASSWORD
# without them, both come out unsigned.
set -euo pipefail
cd "$(dirname "$0")/.."

MOGUL_SERVER="${MOGUL_SERVER:-https://studio.mogul.tools}"

./gradlew "-Pmogul.server=$MOGUL_SERVER" clean :androidApp:bundleRelease :androidApp:assembleRelease

echo
echo "built for $MOGUL_SERVER:"
find androidApp/build/outputs -name '*.aab' -o -name '*.apk' | grep -i release
