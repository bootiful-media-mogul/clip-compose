#!/usr/bin/env bash
# builds the ios app (and any extensions) against the production server (APP_SERVER names
# another), and uploads it to testflight, without xcode's ui or an apple id login: it signs
# and uploads with an app store connect api key (app store connect > users and access >
# integrations > app store connect api). one key serves every app on the team.
#
#   ASC_KEY_ID      the key's id
#   ASC_ISSUER_ID   the issuer id, shown above the list of keys
#   ASC_KEY         the contents of the key's .p8 file (or ASC_KEY_PATH, the file itself)
#
# secrets.hint in app.properties says how to set the first three on a laptop. in ci, they'd
# come from secrets.
#
# each upload needs a build number higher than the last, so this uses the time: 20261120.2245.
# apple then processes the build for a few minutes before testflight offers it.
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"

project="$(prop ios.project)"
scheme="$(prop ios.scheme)"
archive="$(prop ios.archive)"
server_property="$(prop server.property)"
server="$(release_server)"
hint="$(prop secrets.hint '')"
hint="${hint:+ ($hint first)}"

: "${ASC_KEY_ID:?set ASC_KEY_ID to the id of the app store connect api key$hint}"
: "${ASC_ISSUER_ID:?set ASC_ISSUER_ID to the app store connect api issuer id$hint}"

# xcodebuild wants the key as a file: write it somewhere only we can read, for this run only
if [ -z "${ASC_KEY_PATH:-}" ]; then
  : "${ASC_KEY:?set ASC_KEY to the contents of the app store connect api key, or ASC_KEY_PATH to the file$hint}"
  ASC_KEY_PATH="$(mktemp -d)/AuthKey_$ASC_KEY_ID.p8"
  trap 'rm -rf "$(dirname "$ASC_KEY_PATH")"' EXIT
  (umask 077 && printf '%s\n' "$ASC_KEY" > "$ASC_KEY_PATH")
fi
[ -f "$ASC_KEY_PATH" ] || { echo "no api key at $ASC_KEY_PATH" >&2; exit 1; }

build="$(date -u +%Y%m%d.%H%M)"
out="$(dirname "$project")/build/testflight"
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
env "ORG_GRADLE_PROJECT_$server_property=$server" xcodebuild archive \
  -project "$project" \
  -scheme "$scheme" \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$out/$archive.xcarchive" \
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
  -archivePath "$out/$archive.xcarchive" \
  -exportOptionsPlist "$out/ExportOptions.plist" \
  -exportPath "$out" \
  "${auth[@]}"

echo
echo "uploaded build $build (for $server) to app store connect; testflight offers it once apple has processed it"
