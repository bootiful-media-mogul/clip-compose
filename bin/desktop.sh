#!/usr/bin/env bash
# packages the desktop app for the system this runs on, against the production server (APP_SERVER
# names another): a .dmg on macos, an .msi on windows, a .deb on linux. jlink trims a java runtime
# down to what the app needs and jpackage puts it inside, so nobody needs java installed. jpackage
# only packages for the system it runs on, which is why ci runs this on each of them. the
# installer lands in <module>/build/release, named for the version, the system and the cpu.
#
# the runtime is java 27, and the build trains an AOT cache of the app inside the .dmg or .deb
# (not the .msi): it runs the app for a few seconds, so it needs a display (xvfb-run on linux).
# -Pmogul.aot=false, in GRADLE_ARGS, skips that
#
#   DESKTOP_VERSION   the version, like 1.2.3 (desktop.version in app.properties otherwise)
#
# on macos, gatekeeper won't open an app that isn't signed with a developer id and notarized by
# apple. without these, the dmg comes out unsigned, which is fine on the machine that built it:
#
#   MAC_SIGNING_IDENTITY   the developer id application certificate, as keychain access names it
#   MAC_SIGNING_KEYCHAIN   the keychain it's in, if not the default one
#   ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY (or ASC_KEY_PATH)
#                          the app store connect api key bin/ios-testflight.sh uploads with, which
#                          notarizes the signed dmg
#
# secrets.hint in app.properties says how to set these on a laptop. in ci, they'd come from
# secrets.
# shellcheck source-path=SCRIPTDIR source=common.sh
source "$(dirname "$0")/common.sh"

module="$(prop desktop.module)"
version="${DESKTOP_VERSION:-$(prop desktop.version)}"
server_property="$(prop server.property)"
server="$(release_server)"
hint="$(prop secrets.hint '')"
hint="${hint:+ ($hint first)}"

# what jpackage takes on every system: three numbers, the first above 0, none past what an msi takes
if ! [[ "$version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] ||
  [ "${BASH_REMATCH[1]}" -lt 1 ] || [ "${BASH_REMATCH[1]}" -gt 255 ] ||
  [ "${BASH_REMATCH[2]}" -gt 255 ] || [ "${BASH_REMATCH[3]}" -gt 65535 ]; then
  echo "the version must be like 1.2.3, from 1.0.0 to 255.255.65535, not $version" >&2
  exit 1
fi

case "$(uname -s)" in
  Darwin) system=macos format=dmg ;;
  MINGW* | MSYS* | CYGWIN*) system=windows format=msi ;;
  *) system=linux format=deb ;;
esac
case "$(uname -m)" in
  arm64 | aarch64) cpu=arm64 ;;
  *) cpu=x64 ;;
esac

gradle=("-P$server_property=$server" "-Pmogul.version=$version")
sign=""
if [ "$system" = macos ] && [ -n "${MAC_SIGNING_IDENTITY:-}" ]; then
  sign=yes
  gradle+=(-Pcompose.desktop.mac.sign=true "-Pcompose.desktop.mac.signing.identity=$MAC_SIGNING_IDENTITY")
  [ -z "${MAC_SIGNING_KEYCHAIN:-}" ] || gradle+=("-Pcompose.desktop.mac.signing.keychain=$MAC_SIGNING_KEYCHAIN")

  : "${ASC_KEY_ID:?set ASC_KEY_ID to the id of the app store connect api key, to notarize the signed dmg$hint}"
  : "${ASC_ISSUER_ID:?set ASC_ISSUER_ID to the app store connect api issuer id$hint}"
  if [ -z "${ASC_KEY_PATH:-}" ]; then
    : "${ASC_KEY:?set ASC_KEY to the contents of the app store connect api key, or ASC_KEY_PATH to the file$hint}"
    ASC_KEY_PATH="$(mktemp -d)/AuthKey_$ASC_KEY_ID.p8"
    trap 'rm -rf "$(dirname "$ASC_KEY_PATH")"' EXIT
    (umask 077 && printf '%s\n' "$ASC_KEY" >"$ASC_KEY_PATH")
  fi
  [ -f "$ASC_KEY_PATH" ] || { echo "no api key at $ASC_KEY_PATH" >&2; exit 1; }
fi

# shellcheck disable=SC2086
./gradlew "${gradle[@]}" ${GRADLE_ARGS:-} clean ":$module:packageDistributionForCurrentOS"

built="$(find "$module/build/compose/binaries/main/$format" -name "*.$format" | head -n 1)"
[ -f "$built" ] || { echo "the build made no .$format in $module/build/compose/binaries/main/$format" >&2; exit 1; }

if [ -n "$sign" ]; then
  # jpackage signs the app but not the dmg around it: sign that too, so gatekeeper can vouch
  # for the dmg itself before it's opened
  keychain=()
  [ -z "${MAC_SIGNING_KEYCHAIN:-}" ] || keychain=(--keychain "$MAC_SIGNING_KEYCHAIN")
  codesign --force --timestamp --sign "$MAC_SIGNING_IDENTITY" ${keychain[@]+"${keychain[@]}"} "$built"

  # apple checks the app for malware and that it's signed properly, then the dmg carries its
  # ticket, so gatekeeper needn't ask apple when it first opens
  echo "notarizing $built"
  xcrun notarytool submit "$built" --wait \
    --key "$ASC_KEY_PATH" --key-id "$ASC_KEY_ID" --issuer "$ASC_ISSUER_ID"
  xcrun stapler staple "$built"
fi

# jpackage names a .deb name_version_arch, and the others name-version: keep only the name
name="$(tr '[:upper:] ' '[:lower:]-' <<<"$(basename "$built" ".$format" | sed "s/[-_]$version\([-_].*\)\{0,1\}\$//")")"
out="$module/build/release"
rm -rf "$out"
mkdir -p "$out"
cp "$built" "$out/$name-$version-$system-$cpu.$format"

echo
echo "packaged $version for $server, ${sign:+signed and notarized, }in $out:"
ls "$out"
