#!/usr/bin/env bash
# Gibt den Abschnitt „## <Version>“ aus CHANGELOG.md aus (für die Release-Notizen).
# Bricht ab, wenn es den Abschnitt nicht gibt.
#
#   bash tools/changelog-abschnitt.sh CHANGELOG.md 0.2.0
set -euo pipefail

DATEI="${1:?CHANGELOG-Datei fehlt}"
VERSION="${2:?Version fehlt}"

TEXT="$(awk -v v="$VERSION" '
  /^## / { if (f) exit; f = ($2 == v || $2 == "[" v "]") ; next }
  f
' "$DATEI")"

if [[ -z "${TEXT//[[:space:]]/}" ]]; then
  echo "::error::In $DATEI fehlt der Abschnitt „## $VERSION“" >&2
  exit 1
fi
printf '%s\n' "$TEXT"
