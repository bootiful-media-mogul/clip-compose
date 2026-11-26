#!/usr/bin/env bash
# builds the android app's signed release bundle (bin/android.sh, against the production server
# unless APP_SERVER names another) and sends it to a google play track: android.track in
# app.properties, unless ANDROID_TRACK names another. internal testing is play's testflight.
# it talks to the google play developer api itself, as a google cloud service account:
#
#   PLAY_SERVICE_ACCOUNT   the service account's json key (or PLAY_SERVICE_ACCOUNT_PATH, the file)
#
# the service account needs no google cloud roles: invite its email in play console > users and
# permissions, with release permissions for the app. the app has to exist in play console, and
# play takes an app's very first bundle only by hand, in play console.
#
# play only takes signed bundles, so this needs the upload key bin/android.sh signs with, too
# (ANDROID_KEYSTORE and the rest). PLAY_RELEASE_STATUS is completed, or draft while play
# console still has the app itself as a draft.
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"

module="$(prop android.module)"
package="$(prop android.applicationId)"
track="${ANDROID_TRACK:-$(prop android.track internal)}"
status="${PLAY_RELEASE_STATUS:-completed}"
hint="$(prop secrets.hint '')"
hint="${hint:+ ($hint first)}"

: "${ANDROID_KEYSTORE:?set ANDROID_KEYSTORE (and the rest bin/android.sh names): play only takes signed bundles$hint}"
if [ -z "${PLAY_SERVICE_ACCOUNT:-}" ]; then
  : "${PLAY_SERVICE_ACCOUNT_PATH:?set PLAY_SERVICE_ACCOUNT to the google play service account json key, or PLAY_SERVICE_ACCOUNT_PATH to the file$hint}"
  PLAY_SERVICE_ACCOUNT="$(cat "$PLAY_SERVICE_ACCOUNT_PATH")"
fi
jq -e '.type == "service_account"' <<<"$PLAY_SERVICE_ACCOUNT" >/dev/null 2>&1 ||
  { echo "PLAY_SERVICE_ACCOUNT is not a service account's json key" >&2; exit 1; }

bin/android.sh
aab="$(find "$module/build/outputs/bundle/release" -name '*.aab' | head -n 1)"
[ -f "$aab" ] || { echo "bin/android.sh made no bundle in $module/build/outputs/bundle/release" >&2; exit 1; }

# the key, the token and the requests that carry them go through files only we can read, so
# none of them shows up in the process list
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
chmod 700 "$tmp"

# a service account trades a jwt, signed with its key, for an access token
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
jq -r .private_key <<<"$PLAY_SERVICE_ACCOUNT" >"$tmp/key.pem"
now="$(date -u +%s)"
claims="$(jq -cn --arg iss "$(jq -r .client_email <<<"$PLAY_SERVICE_ACCOUNT")" --argjson now "$now" '{
  iss: $iss, scope: "https://www.googleapis.com/auth/androidpublisher",
  aud: "https://oauth2.googleapis.com/token", iat: $now, exp: ($now + 3600)
}')"
unsigned="$(printf '%s' '{"alg":"RS256","typ":"JWT"}' | b64url).$(printf '%s' "$claims" | b64url)"
signature="$(printf '%s' "$unsigned" | openssl dgst -sha256 -sign "$tmp/key.pem" | b64url)"
printf 'grant_type=urn%%3Aietf%%3Aparams%%3Aoauth%%3Agrant-type%%3Ajwt-bearer&assertion=%s' "$unsigned.$signature" >"$tmp/grant"

# call <curl args...>: the response, or what google said and a failure
call() {
  local out
  if ! out="$(curl --silent --show-error --fail-with-body "$@")"; then
    echo "google said: $out" >&2
    return 1
  fi
  printf '%s' "$out"
}

token="$(call --data @"$tmp/grant" https://oauth2.googleapis.com/token)"
printf 'Authorization: Bearer %s\n' "$(jq -er .access_token <<<"$token")" >"$tmp/auth"

# an edit gathers changes to the app, and play applies them all when it's committed
api="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$package"
upload="https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/$package"
edit="$(call -H @"$tmp/auth" -H 'Content-Type: application/json' -X POST --data '{}' "$api/edits")"
edit="$(jq -er .id <<<"$edit")"

echo "uploading $aab to google play"
bundle="$(call -H @"$tmp/auth" -H 'Content-Type: application/octet-stream' -X POST \
  --data-binary @"$aab" "$upload/edits/$edit/bundles?uploadType=media")"
version_code="$(jq -er .versionCode <<<"$bundle")"

release="$(jq -cn --arg track "$track" --arg status "$status" --arg code "$version_code" \
  '{track: $track, releases: [{versionCodes: [$code], status: $status}]}')"
call -H @"$tmp/auth" -H 'Content-Type: application/json' -X PUT --data "$release" \
  "$api/edits/$edit/tracks/$track" >/dev/null
call -H @"$tmp/auth" -X POST --data '' "$api/edits/$edit:commit" >/dev/null

echo
echo "released version code $version_code of $package to the $track track ($status)"
