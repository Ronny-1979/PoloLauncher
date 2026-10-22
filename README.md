# PoloLauncher v0.31.129 – Launcher für das Polo-Autoradio (Android 13)

Version **0.31.129 / versionCode 210**. Ein Android-Launcher für das Android-Autoradio im VW Polo 6R: Offline-Karte mit Tempolimits und Blitzern, Fahrzeugdaten, DAB-Radio und CarPlay-Übergabe auf einem Bildschirm. Der vollständige Änderungsverlauf steht in [CHANGELOG.md](CHANGELOG.md), der aktuelle Prüfbericht in `TESTBERICHT_v0.31.129.md`.

## Was die App kann

**Offline-Karte Deutschland**
- Vektorkarte (MapLibre, Shortbread-Kacheln) pro Bundesland, komplett ohne Internet nutzbar, mit Neigung und 3D-Gebäuden (einstellbar, Standard 45° / 9 m).
- Einrasten auf die Straße, Zoom getrennt für Innerorts/Außerorts (automatisch erkannt, Standard 18/16).
- Positionsmarke wählbar (Menü Karte → „Positionsmarke“): Standard-Punkt, Halloween-Kürbis oder Weihnachtsschlitten (dreht sich mit der Fahrtrichtung).
- Der letzte Kartenzustand (Position, Fahrtrichtung, Zoom, Ortslage) wird gespeichert und nach Standby, Neustart oder Radio-Abschaltung wiederhergestellt.
- Tempolimits und feste Blitzer aus OpenStreetMap-Daten (Geofabrik), vom Launcher selbst in eine kleine lokale Datenbank gebaut.

**Karten-Updates**
- Menü Offline-Karten: Installation, Update-Prüfung, Fortsetzen nach Abbruch. Die alte Karte bleibt aktiv, bis die neue fertig ist.
- Einmal pro Woche prüft der Launcher selbst auf neue Daten (auch über Mobilfunk). Gibt es welche, fragt ein Popup „Aktualisieren / Später“ – erst, wenn der Launcher nach dem Start komplett hochgefahren ist.
- **Neu: Import vom USB-Stick** (siehe unten) plus Windows-Programm zum bequemen Herunterladen.

**Fahrzeugkachel**
- Tempo, Drehzahl, Aktuell (l/100 km, im Schub l/h), Ø 100 km, Reichweite, Tank, Bordnetz, Außentemperatur, Gurt- und Wasserstatus.
- Türwarnung; orange blinkende Gurtwarnung, wenn man fährt und der Gurt offen ist.
- Werte per CANBOX (Broadcast) und OBD2 über ELM327-Bluetooth-Adapter (Verbrauch, Selbstheilung nach Standby).

**Radio & CarPlay**
- DAB-Kachel mit Senderlogo (DABdream+), Steuerung und Autostart; nach Standby/Neustart zurück zum Launcher, sobald DABdream+ vorn ist und der Sender läuft.
- Optionale CarPlay-Automatik (ZLink): Rückkehr erst, wenn CarPlay wirklich verbunden ist.

**Diagnose**
- Diagnoseprotokoll und Statusseiten im Menü, auch für Fehlersuche per Foto/Log.

## USB-Stick-Import und Windows-Downloader

1. **Windows:** `tools/windows-downloader/PoloMapsDownloader.exe` starten (nichts zu installieren). Zielordner ist automatisch `<Stick>:\PoloMaps`. „Alles herunterladen“ klicken. Das Programm lädt je Bundesland `<land>-shortbread-1.0.mbtiles` und `<land>-latest.osm.pbf` von Geofabrik, setzt Abgebrochenes fort und überspringt Fertiges.
   - Windows-Warnung „Der PC wurde geschützt“: „Weitere Informationen“ → „Trotzdem ausführen“ (die exe ist nicht signiert).
   - Stick mit genug Platz (mehrere GB, am besten ≥ 32 GB, exFAT oder NTFS).
2. **Radio:** Stick einstecken, im Launcher Offline-Karten → „Von USB-Stick importieren“. Der Launcher kopiert nur neuere Dateien und baut Karte und Tempolimit-Daten selbst auf. Findet er den Stick nicht, öffnet sich eine Ordnerauswahl (Ordner `PoloMaps` wählen).
3. Danach kann der Stick abgezogen werden.

Quellcode des Programms: `tools/windows-downloader/` (`MapDownloader.cs` Logik, `MainForm.cs` Oberfläche). Neu bauen: `mcs -target:winexe -r:System.Windows.Forms.dll -r:System.Drawing.dll -out:PoloMapsDownloader.exe MapDownloader.cs MainForm.cs`. Ausführliche Kurzanleitung: `tools/windows-downloader/LIESMICH.txt`.

## Installation und Berechtigungen

- Die APK aktualisiert die vorhandene App; Einstellungen und gelernte Werte bleiben erhalten. Dafür wird immer mit demselben Schlüssel signiert (GitHub-Actions-Secret, siehe `.github/workflows/build-apk.yml`).
- Als Home-App im Radio festlegen. Wichtige Freigaben: Standort, Benachrichtigungszugriff (DAB-Titel), Nutzungszugriff (CarPlay-Automatik), Bluetooth (OBD2).

## Bauen

- **GitHub:** Jeder Push erzeugt über GitHub Actions eine Debug-APK (`PoloLauncher_v<Version>_debug.apk`).
- **Windows lokal:** `BUILD_APK_WINDOWS.bat` (ruft `gradlew assembleDebug`).
- **Technik:** Android 13 (compileSdk 35, targetSdk 33, minSdk 26), Java 17, AGP 8.7.3, Gradle 8.9, MapLibre Android 11.8.0.

## Tests

- `sh tests/run-tests.sh` – JVM-Tests der reinen Logik plus Quelltext-Prüfung (u. a. muss die erste README-Zeile die aktuelle Version nennen).
- `ANDROID_JAR=<android.jar> sh tests/compile-check.sh` – alle Klassen gegen die echte Android-API kompilieren.
- Diese Tests ersetzen keinen Test im Auto: Verhalten am Radio (Standby, CarPlay, OBD2, USB-Erkennung) lässt sich nur dort prüfen.

## Regeln im Projekt

- Bei jeder Änderung Version (`versionName`/`versionCode` in `app/build.gradle`) erhöhen; ZIP-Name `PoloLauncher_vX.Y.Z_android13.zip`.
- Stabile Linie ist v0.31.x; das experimentelle v0.32.0 wird nicht verwendet.
