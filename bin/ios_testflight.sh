#!/usr/bin/env bash
# builds the ios app (and its share extension) against production mogul, and uploads it to
# testflight, without xcode's ui or an apple id login: it signs and uploads with an app store
# connect api key (app store connect > users and access > integrations > app store connect api).
#
#   ASC_KEY_ID      the key's id
#   ASC_ISSUER_ID   the issuer id, shown above the list of keys
#   ASC_KEY_PATH    the .p8 file; defaults to ~/.appstoreconnect/private_keys/AuthKey_$ASC_KEY_ID.p8
#   MOGUL_SERVER    defaults to https://studio.mogul.tools
#
# each upload needs a build number higher than the last, so this uses the time: 20261120.2245.
# apple then processes the build for a few minutes before testflight offers it.
set -euo pipefail
cd "$(dirname "$0")/.."

: "${ASC_KEY_ID:?set ASC_KEY_ID to the id of the app store connect api key}"
: "${ASC_ISSUER_ID:?set ASC_ISSUER_ID to the app store connect api issuer id}"
ASC_KEY_PATH="${ASC_KEY_PATH:-$HOME/.appstoreconnect/private_keys/AuthKey_$ASC_KEY_ID.p8}"
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
