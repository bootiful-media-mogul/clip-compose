# shellcheck shell=bash
# sourced by the other scripts here: moves to the project root, and reads app.properties,
# which holds everything about the project they need to know
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

[ -f app.properties ] || { echo "no app.properties in $PWD: the scripts in bin read the project's details from it" >&2; exit 1; }

# prop <key> [default]: the value of key in app.properties, the default if it isn't there, or
# an error if there's no default either
prop() {
  local key="${1//./\\.}" value
  value="$(sed -n "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*//p" app.properties | tail -n 1 | sed 's/[[:space:]]*$//')"
  if [ -n "$value" ]; then
    printf '%s\n' "$value"
  elif [ $# -gt 1 ]; then
    printf '%s\n' "$2"
  else
    echo "app.properties has no $1" >&2
    exit 1
  fi
}

# the server a release build points at
release_server() {
  printf '%s\n' "${APP_SERVER:-$(prop server.production)}"
}
