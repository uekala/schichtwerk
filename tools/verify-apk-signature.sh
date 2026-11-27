#!/usr/bin/env bash
# Prüft die Signatur einer Release-APK und bricht ab, wenn etwas nicht stimmt:
#   - Signatur gültig, mit APK Signature Scheme v3.1 (Schlüsselrotation)
#   - der neue Schlüssel ist der in ANDROID_SIGNING_CERT_SHA256 (Repository-Variable)
#   - die Lineage führt vom alten Schlüssel ALT_CERT_SHA256 zum neuen
#   - kein Debug-Schlüssel
# So nimmt Android jedes Release als Update der installierten App an.
#
#   bash tools/verify-apk-signature.sh <app.apk>
set -euo pipefail

APK="${1:?Pfad zur APK fehlt}"
norm() { tr -d ': \r' | tr '[:lower:]' '[:upper:]'; }
NEU="$(printf '%s' "${ANDROID_SIGNING_CERT_SHA256:-}" | norm)"
ALT="$(printf '%s' "${ALT_CERT_SHA256:-}" | norm)"
[[ -n "$NEU" ]] || { echo "::error::Repository-Variable ANDROID_SIGNING_CERT_SHA256 fehlt (setzt tools/setup-android-signing.sh)"; exit 1; }
[[ -n "$ALT" ]] || { echo "::error::ALT_CERT_SHA256 fehlt"; exit 1; }

BUILD_TOOLS="$(ls -d "${ANDROID_HOME:?ANDROID_HOME fehlt}"/build-tools/*/ | sort -V | tail -n 1)"
APKSIGNER="${BUILD_TOOLS}apksigner"

PRUEFUNG="$("$APKSIGNER" verify --verbose --print-certs "$APK")"
fps() { sed -n 's/.*certificate SHA-256 digest: //p' | norm | sort -u; }

if ! grep -q "Verified using v3.1 scheme (APK Signature Scheme v3.1): true" <<<"$PRUEFUNG"; then
  echo "$PRUEFUNG"
  echo "::error::Keine gültige v3.1-Signatur – die Schlüsselrotation fehlt"
  exit 1
fi
if grep -q "certificate DN: .*Android Debug" <<<"$PRUEFUNG"; then
  echo "::error::Die APK ist mit einem Debug-Schlüssel signiert"
  exit 1
fi
SIGNIERER="$(fps <<<"$PRUEFUNG")"
grep -qx "$NEU" <<<"$SIGNIERER" || { echo "$PRUEFUNG"; echo "::error::Neuer Schlüssel $NEU signiert die APK nicht"; exit 1; }
grep -qx "$ALT" <<<"$SIGNIERER" || { echo "$PRUEFUNG"; echo "::error::Alter Schlüssel $ALT signiert die APK nicht mit"; exit 1; }

HERKUNFT="$("$APKSIGNER" lineage --in "$APK" --print-certs)"
KETTE="$(fps <<<"$HERKUNFT")"
grep -qx "$NEU" <<<"$KETTE" && grep -qx "$ALT" <<<"$KETTE" \
  || { echo "$HERKUNFT"; echo "::error::Die Lineage in der APK führt nicht von $ALT zu $NEU"; exit 1; }

echo "Signatur ok: v3.1, alt $ALT → neu $NEU"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "### Android-Signatur"
    echo ""
    echo "- Schema v3.1 mit Lineage: ok"
    echo "- neuer Schlüssel (SHA-256): \`$NEU\`"
    echo "- alter Schlüssel (SHA-256): \`$ALT\`"
  } >> "$GITHUB_STEP_SUMMARY"
fi
