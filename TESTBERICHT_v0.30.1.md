# Testbericht Polo Launcher v0.30.1

## Ergebnis

- 22 von 22 lokalen Testgruppen bestanden.
- 52 Java-Quelldateien ohne Syntaxfehler geparst.
- Strukturprüfung für Einstellungen, Karten, CAN/OBD, Verbrauch, Reichweite, DAB-Autostart und Hintergrunddienst bestanden.
- Neue Regressionstests bestätigen maximal 1.200 interne Diagnosezeilen, maximal 600 sichtbare Diagnosezeilen und die Kürzung überlanger Einzelmeldungen.
- Quelltextprüfung bestätigt, dass HCT-Verbindungs- und Abmeldeabfragen nicht mehr direkt auf dem Android-Hauptthread ausgeführt werden.
- Quelltextprüfung bestätigt, dass unterdrückte CAN-Mode2-Meldungen vor dem Aufbau des Diagnose-Textes erkannt werden.
- DAB-Bildtest bestätigt weiterhin eine maximale sichtbare Kantenlänge von 256 Pixeln und verhindert wiederholtes Skalieren derselben Quelle.

## Nicht möglich

Ein vollständiger Android-Debug-Build war in dieser Umgebung nicht möglich, weil die Gradle-8.9-Distribution nicht von `services.gradle.org` geladen werden konnte (`UnknownHostException`). Deshalb wurden weder APK noch Verhalten auf dem Radio selbst getestet.

## Unverändert

Die Berechnung und Speicherung von Momentanverbrauch, Durchschnitt, Tank und Reichweite sowie Kartenansicht und DAB-Autostart wurden durch die Stabilitätsänderungen nicht verändert.
