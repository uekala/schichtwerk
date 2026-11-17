# Schichtwerk

Android-App für den FlashForge Creator 5 Pro. Sie spricht ausschließlich mit Home Assistant
(REST-API) und nutzt die Entitäten, die dort schon vorhanden sind.

<p>
  <img src="docs/druck.png" alt="Druck-Ansicht mit Fortschritt, Steuerung und Kamera" width="270">
  <img src="docs/einrichtung.png" alt="Einrichtung mit Adresse und Zugriffstoken" width="270">
</p>

## Funktionen

- **Druck:** Fortschritt, Schicht, Restzeit, Druckende, Vorschaubild, Kamera, Temperaturen
- **Steuerung:** Pause, Fortsetzen, Abbrechen (mit Rückfrage), Geschwindigkeit, LED, Luftfilter
- **Material:** Restmengen und Preise je Slot (in der App änderbar), Wartungsintervalle, Kosten und Verbrauch
- **Live-Anzeige:** Dauerbenachrichtigung mit Fortschritt, nur solange ein Druck läuft
- **Alarme:** Fehler, Pause, Druck fertig oder gestoppt
- **Widget:** Status, Fortschritt und Restzeit auf dem Homescreen

## Einrichten

1. APK aus dem neuesten [Release](../../releases) laden und installieren.
2. In Home Assistant ein langlebiges Zugriffstoken anlegen (Profil → Sicherheit).
3. In der App Adresse und Token eintragen.
4. Unter *Einstellungen → Hintergrund* die Akku-Optimierung ausschalten, damit die Live-Anzeige
   auch bei geschlossener App startet.

Das Token wird mit dem Android Keystore verschlüsselt und nur auf dem Gerät gespeichert.

## Bauen

Jeder Push auf `main` baut über GitHub Actions eine signierte APK und legt ein Release an.
Lokal: `./gradlew assembleRelease` (JDK 17, Android SDK 35).

Die Entity-IDs stehen in `app/src/main/java/de/uekala/schichtwerk/data/Snapshot.kt` (`Ids`).

## Signatur

`signing/sideload.jks` ist ein reiner Sideload-Schlüssel, damit Updates sich über die
vorherige Version installieren lassen. Bevor das Repo öffentlich wird, gehört der Schlüssel
in ein GitHub-Secret.
