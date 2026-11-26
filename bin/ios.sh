#!/usr/bin/env bash
# a production build of the ios app (and any extensions), pointed at the production server
# (APP_SERVER names another): an archive, and the .ipa exported from it for app store
# connect, in <xcode project's directory>/build/release. signs with the team in the xcode
# project, using the apple account signed in to xcode (xcode > settings > accounts), which
# creates or updates the provisioning profiles it needs.
# shellcheck source-path=SCRIPTDIR source=common.sh
source "$(dirname "$0")/common.sh"

project="$(prop ios.project)"
scheme="$(prop ios.scheme)"
archive="$(prop ios.archive)"
server_property="$(prop server.property)"
server="$(release_server)"
out="$(dirname "$project")/build/release"
rm -rf "$out"
mkdir -p "$out"

# xcode runs gradle to build the kotlin framework, and gradle reads the server from this.
# it has a dot in its name, which the shell can't export, but env can pass along
env "ORG_GRADLE_PROJECT_$server_property=$server" xcodebuild archive \
  -project "$project" \
  -scheme "$scheme" \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$out/$archive.xcarchive" \
  -allowProvisioningUpdates

cat > "$out/ExportOptions.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>method</key>
	<string>app-store-connect</string>
	<key>destination</key>
	<string>export</string>
	<key>signingStyle</key>
	<string>automatic</string>
</dict>
</plist>
PLIST

xcodebuild -exportArchive \
  -archivePath "$out/$archive.xcarchive" \
  -exportOptionsPlist "$out/ExportOptions.plist" \
  -exportPath "$out" \
  -allowProvisioningUpdates

echo
echo "built for $server:"
ls "$out"/*.ipa
