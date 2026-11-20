#!/usr/bin/env bash
# a production build of the android app, pointed at production mogul: an app bundle for the
# play store and an apk, in androidApp/build/outputs.
#
# to sign them, set these (the upload key; keep it out of the repo):
#   ORG_GRADLE_PROJECT_mogul.android.keystore          path to the keystore
#   ORG_GRADLE_PROJECT_mogul.android.keystorePassword
#   ORG_GRADLE_PROJECT_mogul.android.keyAlias
#   ORG_GRADLE_PROJECT_mogul.android.keyPassword
# without them, both come out unsigned.
set -euo pipefail
cd "$(dirname "$0")/.."

MOGUL_SERVER="${MOGUL_SERVER:-https://studio.mogul.tools}"

./gradlew "-Pmogul.server=$MOGUL_SERVER" clean :androidApp:bundleRelease :androidApp:assembleRelease

echo
echo "built for $MOGUL_SERVER:"
find androidApp/build/outputs -name '*.aab' -o -name '*.apk' | grep -i release
