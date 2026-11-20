#!/usr/bin/env bash
# a production build of the ios app (and its share extension), pointed at production mogul:
# an archive, and the .ipa exported from it for app store connect, in iosApp/build/release.
# signs with the team in the xcode project, using the apple account signed in to xcode
# (xcode > settings > accounts), which creates or updates the provisioning profiles it needs.
set -euo pipefail
cd "$(dirname "$0")/.."

MOGUL_SERVER="${MOGUL_SERVER:-https://studio.mogul.tools}"
out=iosApp/build/release
rm -rf "$out"
mkdir -p "$out"

# xcode runs gradle to build the kotlin framework, and gradle reads the server from this.
# it has a dot in its name, which the shell can't export, but env can pass along
env "ORG_GRADLE_PROJECT_mogul.server=$MOGUL_SERVER" xcodebuild archive \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$out/MogulClip.xcarchive" \
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
  -archivePath "$out/MogulClip.xcarchive" \
  -exportOptionsPlist "$out/ExportOptions.plist" \
  -exportPath "$out" \
  -allowProvisioningUpdates

echo
echo "built for $MOGUL_SERVER:"
ls "$out"/*.ipa
