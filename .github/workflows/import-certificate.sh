#!/usr/bin/env bash
# puts a signing certificate (a .p12, base64 encoded, with its private key) in a keychain of its
# own on a macos runner, where codesign and xcodebuild find it, and names that keychain in
# KEYCHAIN for the steps after this one. the runner is thrown away after the job, keychain and all.
set -euo pipefail

: "${CERTIFICATE:?CERTIFICATE is required: the .p12, base64 encoded}"
: "${CERTIFICATE_PASSWORD:?CERTIFICATE_PASSWORD is required}"
: "${RUNNER_TEMP:?RUNNER_TEMP is required: this runs in github actions}"

keychain="$RUNNER_TEMP/signing.keychain-db"
password="$(openssl rand -hex 24)"
p12="$RUNNER_TEMP/certificate.p12"
trap 'rm -f "$p12"' EXIT
(umask 077 && base64 --decode <<<"$CERTIFICATE" >"$p12")

security create-keychain -p "$password" "$keychain"
security set-keychain-settings -lut 21600 "$keychain"
security unlock-keychain -p "$password" "$keychain"
security import "$p12" -k "$keychain" -P "$CERTIFICATE_PASSWORD" -T /usr/bin/codesign -T /usr/bin/security
# let codesign use the key without a dialog nobody is there to answer
security set-key-partition-list -S apple-tool:,apple: -s -k "$password" "$keychain" >/dev/null
# searched first, and still alongside the login keychain
keychains=("$keychain")
while read -r existing; do keychains+=("$(tr -d '"' <<<"$existing")"); done < <(security list-keychains -d user)
security list-keychains -d user -s "${keychains[@]}"

echo "KEYCHAIN=$keychain" >>"$GITHUB_ENV"
security find-identity -v -p codesigning "$keychain"
