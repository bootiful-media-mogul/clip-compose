#!/usr/bin/env bash
# builds the ios app (and its share extension) against production mogul, and uploads it to
# testflight, without xcode's ui or an apple id login: it signs and uploads with an app store
# connect api key (app store connect > users and access > integrations > app store connect api).
#
#   ASC_KEY_ID      the key's id
#   ASC_ISSUER_ID   the issuer id, shown above the list of keys
#   ASC_KEY         the contents of the key's .p8 file (or ASC_KEY_PATH, the file itself)
#   MOGUL_SERVER    defaults to https://studio.mogul.tools
#
# on a laptop, `source ~/code/mogul/workspace/bin/env.sh` sets the first three, from the
# mogul-clip-appstore-connect-api item in bitwarden. in ci, they'd come from secrets.
#
# each upload needs a build number higher than the last, so this uses the time: 20261120.2245.
# apple then processes the build for a few minutes before testflight offers it.
set -euo pipefail
cd "$(dirname "$0")/.."

hint="(source ~/code/mogul/workspace/bin/env.sh first)"
: "${ASC_KEY_ID:?set ASC_KEY_ID to the id of the app store connect api key $hint}"
: "${ASC_ISSUER_ID:?set ASC_ISSUER_ID to the app store connect api issuer id $hint}"

# xcodebuild wants the key as a file: write it somewhere only we can read, for this run only
if [ -z "${ASC_KEY_PATH:-}" ]; then
  : "${ASC_KEY:?set ASC_KEY to the contents of the app store connect api key, or ASC_KEY_PATH to the file $hint}"
  ASC_KEY_PATH="$(mktemp -d)/AuthKey_$ASC_KEY_ID.p8"
  trap 'rm -rf "$(dirname "$ASC_KEY_PATH")"' EXIT
  (umask 077 && printf '%s\n' "$ASC_KEY" > "$ASC_KEY_PATH")
fi
[ -f "$ASC_KEY_PATH" ] || { echo "no api key at $ASC_KEY_PATH" >&2; exit 1; }

MOGUL_SERVER="${MOGUL_SERVER:-https://studio.mogul.tools}"
build="$(date -u +%Y%m%d.%H%M)"
out=iosApp/build/testflight
rm -rf "$out"
mkdir -p "$out"

auth=(
  -allowProvisioningUpdates
  -authenticationKeyPath "$ASC_KEY_PATH"
  -authenticationKeyID "$ASC_KEY_ID"
  -authenticationKeyIssuerID "$ASC_ISSUER_ID"
)

# xcode runs gradle to build the kotlin framework, and gradle reads the server from this.
# it has a dot in its name, which the shell can't export, but env can pass along
env "ORG_GRADLE_PROJECT_mogul.server=$MOGUL_SERVER" xcodebuild archive \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$out/MogulClip.xcarchive" \
  CURRENT_PROJECT_VERSION="$build" \
  "${auth[@]}"

# "upload" sends it straight to app store connect, rather than writing an .ipa here
cat > "$out/ExportOptions.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>method</key>
	<string>app-store-connect</string>
	<key>destination</key>
	<string>upload</string>
	<key>signingStyle</key>
	<string>automatic</string>
	<key>manageAppVersionAndBuildNumber</key>
	<false/>
</dict>
</plist>
PLIST

xcodebuild -exportArchive \
  -archivePath "$out/MogulClip.xcarchive" \
  -exportOptionsPlist "$out/ExportOptions.plist" \
  -exportPath "$out" \
  "${auth[@]}"

echo
echo "uploaded build $build (for $MOGUL_SERVER) to app store connect; testflight offers it once apple has processed it"
