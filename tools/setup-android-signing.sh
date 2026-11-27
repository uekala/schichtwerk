#!/usr/bin/env bash
# Einmalig AUF DEINEM RECHNER ausführen, nie in der CI: legt den neuen Signaturschlüssel an und
# trägt ihn zusammen mit dem alten Sideload-Schlüssel als GitHub-Secrets ein.
#
#   bash setup-android-signing.sh [keystore-datei] [alias]
#
# Braucht: keytool (aus einem JDK, z. B. dem von Android Studio), gh (angemeldet), base64.
# Unter Windows Git Bash oder WSL nutzen.
#
# Was passiert:
#   1. Der alte Sideload-Schlüssel wird aus der Git-Historie geholt (Stand ALT_REF, vor dem
#      Entfernen) und als ANDROID_OLD_*-Secrets eingetragen. Er ist öffentlich bekannt, wird aber
#      für die Lineage und für ältere Android-Versionen weiter gebraucht.
#   2. Ein neuer Schlüssel wird erzeugt (oder ein vorhandener Keystore übernommen) und als
#      ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS,
#      ANDROID_KEY_PASSWORD eingetragen; sein Fingerabdruck als Variable
#      ANDROID_SIGNING_CERT_SHA256.
# Keystore und Passwort verlassen deinen Rechner nur als verschlüsselte GitHub-Secrets.
# Danach Keystore-Datei und Passwort in den Passwortmanager legen und die Datei vom Rechner löschen.
set -euo pipefail

REPO="uekala/schichtwerk"
ALT_REF="91a96fd"
ALT_CERT_SHA256="C28E04FE11690E4E6D5C2C7D4B63248BEFE4B162556DFEEB706265C182E348AB"
KEYSTORE="${1:-${REPO##*/}-release.jks}"
ALIAS="${2:-upload}"

fail() { echo "Fehler: $*" >&2; exit 1; }
for tool in keytool gh base64; do
  command -v "$tool" >/dev/null 2>&1 || fail "$tool nicht gefunden"
done
gh auth status >/dev/null 2>&1 || fail "gh ist nicht angemeldet (gh auth login)"

# Ein API-Fehler darf nicht wie eine leere Liste aussehen, sonst würde ein vorhandener
# Schlüssel überschrieben.
SECRET_LIST="$(gh secret list --repo "$REPO")" || fail "Secrets von $REPO nicht abfragbar; abgebrochen."
if printf '%s\n' "$SECRET_LIST" | awk '{print $1}' | grep -qx ANDROID_KEYSTORE_BASE64; then
  fail "$REPO hat schon einen neuen Signaturschlüssel. Ein weiterer würde Updates verhindern; abgebrochen."
fi

umask 077
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# 1. Alter Schlüssel aus der Historie
gh api "repos/$REPO/contents/signing/sideload.jks?ref=$ALT_REF" --jq .content | tr -d '\r\n' > "$WORK/alt.b64"
gh api "repos/$REPO/contents/signing/sideload.properties?ref=$ALT_REF" --jq .content | tr -d '\r\n' \
  | base64 -d > "$WORK/alt.properties"
base64 -d < "$WORK/alt.b64" > "$WORK/alt.jks"
OLD_PASS="$(sed -n 's/^storePassword=//p' "$WORK/alt.properties" | tr -d '\r')"
OLD_ALIAS="$(sed -n 's/^keyAlias=//p' "$WORK/alt.properties" | tr -d '\r')"
[[ -n "$OLD_PASS" && -n "$OLD_ALIAS" ]] || fail "alter Schlüssel: Passwort oder Alias nicht lesbar"
export OLD_PASS
OLD_FP="$(keytool -list -v -keystore "$WORK/alt.jks" -alias "$OLD_ALIAS" -storepass:env OLD_PASS 2>/dev/null \
  | sed -n 's/^[[:space:]]*SHA256: //p' | head -n 1 | tr -d ':')"
[[ "$OLD_FP" == "$ALT_CERT_SHA256" ]] || fail "alter Schlüssel hat nicht den erwarteten Fingerabdruck ($OLD_FP)"

# 2. Neuer Schlüssel
read -r -s -p "Passwort für den NEUEN Keystore (mindestens 12 Zeichen): " STORE_PASS; echo
[[ ${#STORE_PASS} -ge 12 ]] || fail "Passwort zu kurz"
export STORE_PASS
if [[ -f "$KEYSTORE" ]]; then
  echo "Vorhandener Keystore $KEYSTORE wird verwendet."
  keytool -list -keystore "$KEYSTORE" -alias "$ALIAS" -storepass:env STORE_PASS >/dev/null 2>&1 \
    || fail "Keystore lässt sich mit diesem Passwort und Alias $ALIAS nicht öffnen"
else
  read -r -s -p "Passwort wiederholen: " CONFIRM; echo
  [[ "$STORE_PASS" == "$CONFIRM" ]] || fail "Passwörter stimmen nicht überein"
  keytool -genkeypair -noprompt \
    -keystore "$KEYSTORE" -storetype PKCS12 \
    -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
    -dname "CN=${REPO##*/} Release" \
    -storepass:env STORE_PASS -keypass:env STORE_PASS >/dev/null 2>&1 \
    || fail "keytool konnte den Schlüssel nicht erzeugen"
  echo "Neuer Keystore: $KEYSTORE"
fi
FINGERPRINT="$(keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" -storepass:env STORE_PASS 2>/dev/null \
  | sed -n 's/^[[:space:]]*SHA256: //p' | head -n 1)"
[[ -n "$FINGERPRINT" ]] || fail "Fingerabdruck nicht lesbar"
[[ "$(tr -d ':' <<<"$FINGERPRINT")" != "$ALT_CERT_SHA256" ]] || fail "das ist der alte Schlüssel"

# Secrets eintragen
gh secret set ANDROID_OLD_KEYSTORE_BASE64 --repo "$REPO" < "$WORK/alt.b64"
printf '%s' "$OLD_PASS" | gh secret set ANDROID_OLD_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$OLD_ALIAS" | gh secret set ANDROID_OLD_KEY_ALIAS --repo "$REPO"
base64 < "$KEYSTORE" | tr -d '\r\n' | gh secret set ANDROID_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$STORE_PASS" | gh secret set ANDROID_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" | gh secret set ANDROID_KEY_ALIAS --repo "$REPO"
# PKCS12-Keystores nutzen ein Passwort für Speicher und Schlüssel.
printf '%s' "$STORE_PASS" | gh secret set ANDROID_KEY_PASSWORD --repo "$REPO"
gh variable set ANDROID_SIGNING_CERT_SHA256 --repo "$REPO" --body "$FINGERPRINT"
unset STORE_PASS CONFIRM OLD_PASS

cat <<EOF

Fertig für $REPO:
  Secrets: ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD,
           ANDROID_OLD_KEYSTORE_BASE64, ANDROID_OLD_KEYSTORE_PASSWORD, ANDROID_OLD_KEY_ALIAS
  Variable: ANDROID_SIGNING_CERT_SHA256 = $FINGERPRINT

Jetzt sichern (ohne beides sind keine Updates mehr möglich):
  - $KEYSTORE (Passwortmanager, dazu eine Offline-Kopie), danach hier löschen
  - das Keystore-Passwort
Den Keystore nie ins Repository legen und nie in einen Chat kopieren.
EOF
