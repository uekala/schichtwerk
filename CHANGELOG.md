# Änderungen

## Unveröffentlicht

- Token und Prüfcode werden bei der Eingabe verdeckt, mit „Zeigen“ zum Prüfen
- „Verbinden“ erst aktiv, wenn Adresse und Token eingetragen sind; Leerzeichen am Rand werden entfernt
- Jeder Reiter beginnt oben; die Zurück-Taste führt erst zum Druck-Reiter
- Schalter: die ganze Zeile ist antippbar
- Hinweis, dass die Geschwindigkeit nur während eines Drucks geht
- Klarere Anzeige, wenn keine Daten kommen
- Verlauf: „Erneut laden“ nach einem Fehler statt eines endlosen „Lade …“; Hinweis, woher der Verlauf kommt

## 0.2.0

- Neuer Signaturschlüssel mit Schlüsselrotation: Das Update installiert sich über die vorhandene App, ab Android 13 gilt danach nur noch der neue Schlüssel
- Releases nur noch gezielt (Tag `mobile-vX.Y.Z`) statt bei jedem Push; Updates am einfachsten über Obtainium
- Update-Hinweis ohne GitHub-Token; ein früher eingetragenes Token wird gelöscht
- Unverschlüsseltes HTTP nur noch zu Adressen im Heimnetz, sonst nur HTTPS
- Testversion „Schichtwerk Test“ (`de.uekala.schichtwerk.test`) läuft neben der echten App

## 0.1

- Versionen 0.1.1 bis 0.1.42, bei jedem Push automatisch veröffentlicht
