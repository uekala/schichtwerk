#!/usr/bin/env bash
# Signiert die Release-APK mit Schlüsselrotation (APK Signature Scheme v3.1).
#
#   bash tools/sign-release.sh <unsignierte.apk> <ziel.apk>
#
# Bis Version 0.1 war die App mit dem Sideload-Schlüssel aus signing/ signiert. Der lag
# öffentlich im Repository und gilt deshalb als bekannt. Seit 0.2 signiert ein neuer Schlüssel,
# der nur in den GitHub-Secrets liegt. Eine Lineage (Herkunftsnachweis) belegt, dass der alte
# Schlüssel den neuen bestätigt hat. So installiert Android das Update über die vorhandene App,
# ohne Deinstallieren. Ab Android 13 gilt danach nur noch der neue Schlüssel: Eine APK, die nur
# mit dem alten signiert ist, nimmt das Gerät nicht mehr an (Rollback-Recht bleibt aus).
# Für ältere Android-Versionen signiert der alte Schlüssel weiter mit.
#
# Umgebungsvariablen (GitHub-Secrets, eingetragen mit tools/setup-android-signing.sh):
#   ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD
#   ANDROID_OLD_KEYSTORE_BASE64, ANDROID_OLD_KEYSTORE_PASSWORD, ANDROID_OLD_KEY_ALIAS
# Passwörter werden nur aus der Umgebung gelesen; die Keystores liegen nur kurz in einem
# privaten Arbeitsordner und werden am Ende gelöscht.
set -euo pipefail

IN="${1:?Pfad zur unsignierten APK fehlt}"
OUT="${2:?Pfad zur signierten APK fehlt}"

for v in ANDROID_KEYSTORE_BASE64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD \
         ANDROID_OLD_KEYSTORE_BASE64 ANDROID_OLD_KEYSTORE_PASSWORD ANDROID_OLD_KEY_ALIAS; do
  if [[ -z "${!v:-}" ]]; then
    echo "::error::Secret $v fehlt. Einmalig tools/setup-android-signing.sh ausführen (siehe README, Abschnitt Signatur)."
    exit 1
  fi
done

BUILD_TOOLS="$(ls -d "${ANDROID_HOME:?ANDROID_HOME fehlt}"/build-tools/*/ | sort -V | tail -n 1)"
APKSIGNER="${BUILD_TOOLS}apksigner"
ZIPALIGN="${BUILD_TOOLS}zipalign"

umask 077
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
printf '%s' "$ANDROID_OLD_KEYSTORE_BASE64" | base64 -d > "$WORK/alt.jks"
printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 -d > "$WORK/neu.jks"

# Lineage: der alte Schlüssel bestätigt den neuen. Rollback bleibt aus (Standard).
"$APKSIGNER" rotate --out "$WORK/lineage" \
  --old-signer --ks "$WORK/alt.jks" --ks-key-alias "$ANDROID_OLD_KEY_ALIAS" \
    --ks-pass env:ANDROID_OLD_KEYSTORE_PASSWORD --key-pass env:ANDROID_OLD_KEYSTORE_PASSWORD \
  --new-signer --ks "$WORK/neu.jks" --ks-key-alias "$ANDROID_KEY_ALIAS" \
    --ks-pass env:ANDROID_KEYSTORE_PASSWORD --key-pass env:ANDROID_KEY_PASSWORD

"$ZIPALIGN" -f -p 4 "$IN" "$WORK/ausgerichtet.apk"

"$APKSIGNER" sign \
  --ks "$WORK/alt.jks" --ks-key-alias "$ANDROID_OLD_KEY_ALIAS" \
    --ks-pass env:ANDROID_OLD_KEYSTORE_PASSWORD --key-pass env:ANDROID_OLD_KEYSTORE_PASSWORD \
  --next-signer --ks "$WORK/neu.jks" --ks-key-alias "$ANDROID_KEY_ALIAS" \
    --ks-pass env:ANDROID_KEYSTORE_PASSWORD --key-pass env:ANDROID_KEY_PASSWORD \
  --lineage "$WORK/lineage" \
  --out "$OUT" "$WORK/ausgerichtet.apk"

echo "Signiert: $OUT"
