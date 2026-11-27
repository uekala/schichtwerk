#!/usr/bin/env bash
# Probelauf der Release-Signatur mit zwei Wegwerf-Schlüsseln (alt und neu). Läuft in der CI bei
# jedem Build, damit ein Fehler in tools/sign-release.sh oder tools/verify-apk-signature.sh
# auffällt, bevor ein echtes Release ansteht. Die echten Schlüssel braucht er nicht.
#
#   bash tools/signatur-probe.sh <unsignierte.apk>
set -euo pipefail

IN="${1:?Pfad zur unsignierten APK fehlt}"
HIER="$(cd "$(dirname "$0")" && pwd)"
umask 077
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

PROBE_PASS="$(head -c 24 /dev/urandom | base64 | tr -d '/+=')"
export PROBE_PASS
for name in alt neu; do
  keytool -genkeypair -noprompt -keystore "$WORK/$name.jks" -storetype PKCS12 \
    -alias "$name" -keyalg RSA -keysize 2048 -validity 2 -dname "CN=Signatur-Probe $name" \
    -storepass:env PROBE_PASS -keypass:env PROBE_PASS >/dev/null
done
fp() { keytool -list -v -keystore "$WORK/$1.jks" -alias "$1" -storepass:env PROBE_PASS | sed -n 's/^[[:space:]]*SHA256: //p' | head -n 1; }

ANDROID_OLD_KEYSTORE_BASE64="$(base64 -w0 "$WORK/alt.jks")" \
ANDROID_OLD_KEYSTORE_PASSWORD="$PROBE_PASS" ANDROID_OLD_KEY_ALIAS=alt \
ANDROID_KEYSTORE_BASE64="$(base64 -w0 "$WORK/neu.jks")" \
ANDROID_KEYSTORE_PASSWORD="$PROBE_PASS" ANDROID_KEY_ALIAS=neu ANDROID_KEY_PASSWORD="$PROBE_PASS" \
  bash "$HIER/sign-release.sh" "$IN" "$WORK/probe.apk"

ANDROID_SIGNING_CERT_SHA256="$(fp neu)" ALT_CERT_SHA256="$(fp alt)" GITHUB_STEP_SUMMARY="" \
  bash "$HIER/verify-apk-signature.sh" "$WORK/probe.apk"

echo "Signatur-Probe ok"
