# Testbericht Polo Launcher v0.30.2

Stand: 20.09.2026

## Ergebnis

- 22 vorhandene Java-Testgruppen: bestanden
- Quelltextaudit: 52 Java-Dateien ohne Syntaxfehler geparst
- Neue Prüfungen: begrenzte CAN-/Binder-/Karten-Warteschlangen, eine DAB-Session-Abfrage, asynchrone Kartenvalidierung, abbrechbare Mapsforge-Arbeiter, sichere GPS-Abmeldung und Umschaltung des aktiven Kartenpfads
- Zweiter Detaildurchgang: doppelte GPS-Glättung im Onlinebetrieb entfernt; schwache Mapsforge-Callbacks bleiben für die Lebensdauer des Kartenpanels zuverlässig erhalten
- Downloadrennen abgesichert: Abbruch oder neuer Download während der Kartenprüfung verhindert die spätere Aktivierung; eine langsame Prüfung hält keine alte Activity fest
- Fehlerhafte Hersteller-/ELM-Daten begrenzt: keine unbekannten `toString()`-Aufrufe im Broadcast-Hauptpfad, höchstens vier Bundle-Ebenen/32 Einträge, 512 Hex-Bytes und 16.384 Zeichen pro OBD-Antwort
- Verbrauchs-, Durchschnitts- und Reichweitenlogik: durch die Änderungen nicht verändert; vorhandene Regressionstests bestanden

## Android-Build

`assembleDebug` und `lintDebug` wurden angestoßen, konnten in dieser Umgebung aber nicht beginnen, weil der Gradle-Wrapper Gradle 8.9 von `services.gradle.org` laden müsste und der Host hier nicht erreichbar ist (`UnknownHostException`). Das ist kein gemeldeter Java-/Android-Compilerfehler.

## Noch am Radio prüfen

- Online-/Offline-Umschaltung einschließlich Ortszoom und Straßenkorrektur
- DAB-Tasten und Metadaten bei Wiedergabe sowie Pause
- Standby/Aufwachen und längerer Betrieb mit CAN, OBD2 und GPS
- Abbruch eines gerade fertiggestellten Offline-Kartendownloads während der Prüfphase
- Installation der Debug-APK, sobald sie auf einem Rechner mit verfügbarem Gradle gebaut wurde
