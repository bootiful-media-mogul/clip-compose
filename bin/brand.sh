#!/usr/bin/env bash
# makes the logo the top bar draws, art/mogul-clip-wordmark.svg, from the mark (art/mogul-clip-logo.svg):
# the mark, then "mogul" and "CLIP" as outlines of the studio's fonts, on one baseline. it's made by
# mogul studio's brand/wordmark.py, so the two apps' logos are one design; that needs mogul studio
# checked out next to this project (client-compose), as the workspace does, and uv. run it after
# changing the mark, and check in what it writes
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"

studio="${STUDIO_HOME:-../client-compose}"
[ -f "$studio/brand/wordmark.py" ] || { echo "no mogul studio at $studio: set STUDIO_HOME to where client-compose is" >&2; exit 1; }
command -v uv >/dev/null || { echo "bin/brand.sh needs uv" >&2; exit 1; }

uv run -q --with fonttools python "$studio/brand/wordmark.py" --mark art/mogul-clip-logo.svg --product CLIP --out art/mogul-clip-wordmark.svg
mkdir -p shared/src/commonMain/composeResources/drawable
cp art/mogul-clip-wordmark.svg shared/src/commonMain/composeResources/drawable/mogul_clip_logo.svg
echo "made the top bar's logo from art/mogul-clip-logo.svg"
