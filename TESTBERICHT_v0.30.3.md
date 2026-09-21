# Prüfbericht Polo Launcher v0.30.3

Ausgangsstand: v0.30.2 (Code 63). Überarbeiteter Stand: v0.30.3 (Code 64).
Auftrag: die sieben Befunde der letzten Quelltextprüfung korrigieren.

## Korrekturen und Nachweise

| Nr. | Fehler | Korrektur | Prüfung |
| --- | --- | --- | --- |
| 1 | Alte CAN-Abmeldung kann neue Anmeldung löschen | Pro Verbindung eigener Callback-Binder; Identitätsprüfung vor/nach Registrierung und bei Frames | CanReconnectTest erzwingt sowohl verspätete Abmeldung als auch verspätete Anmeldung gegen denselben simulierten Dienst; neue Anmeldung bleibt bestehen, alte Frames werden ignoriert |
| 2 | Herstellerobjektprüfung führt unbekannte Methoden aus | Methodenaufrufe vollständig entfernt; nur Datenfelder lesen | SystemCanReceiverTest: weder void reset() noch nicht-void getAndClear() werden ausgeführt; Spannungsfeld bleibt lesbar |
| 3 | CAN überschreibt frische OBD-Verbrauchswerte | Separate CAN-Felder mit eigener Aktualitätsprüfung | SystemCanReceiverTest sendet CAN-Werte 12/20 bei OBD-Werten 7,8/6,2; OBD und kombinierte Reichweitenbasis bleiben unverändert; fehlendes OBD wird nicht durch CAN ersetzt |
| 4 | Kartenabfrage blockiert Positionsweitergabe | 350-ms-Rückfall auf neuesten GPS-Punkt, nicht durch weitere Proben verlängert; alte Ergebnisse werden verworfen | RoadPositionMatcherTest blockiert tatsächlichen Reader-Aufruf über Test-Doubles; mehrere GPS-Punkte, Zeitablauf, Wiederaufnahme, doppelte Ausgabe und Schließen geprüft; PositionResultGateTest prüft Ergebniszuordnung |
| 5 | GPS-Rezentrierung verwirft eigene letzte Position als Duplikat | Frische letzte Anzeige-Position direkt an Kameraweitergabe übergeben | Quelltextprüfung der Verdrahtung; kein Android-Kameratest |
| 6 | Downloadabschluss geht beim Seitenwechsel verloren | Gemeinsame Prüfung mit schwachen Referenzen auf alle interessierten Seiten; zusätzlicher 5-s-Timer auf sichtbarer Hauptseite | OfflineMapAsyncTest schließt erste Activity während blockierter Validierung, meldet neue Activity mehrfach an und prüft genau eine Zustellung plus wirkliche Dateiaktivierung; CompletionListenersTest prüft Wiederholung und nächste Abfragerunde |
| 7 | Downloadseite führt blockierende Speicher-/Dienstabfragen im UI-Thread aus | Hintergrund-Snapshots für Status/Speicher/Regionen; Start, Abbruch, Löschen und Auswahl asynchron; begrenzte Queue und zusammengefasste Anfragen | OfflineMapAsyncTest blockiert DownloadManager-Abfrage und zeigt, dass UI-Ereignisse weiter verarbeitet werden; Arbeitsthread und UI-Rückgabe für Status und Mutation geprüft; SourceAuditTest prüft Verdrahtung der Activity |

## Ergebnis

`bash tests/run-tests.sh` erfolgreich: 27 Testprogramme.

Bestehende Tests für Verbrauch, Durchschnitt, Reichweite, Wiederherstellung, CAN-Decodierung,
OBD-Adapter, DAB-Start, GPS-Filter, Bediengesten, Diagnosegrenzen und Kartenaktivierung
laufen weiterhin durch. 54 Produktions-Java-Dateien wurden ohne Syntaxfehler geparst.
Die neuen Store-/Matcher-Klassen werden zusätzlich gegen die gezielten Android-/Mapsforge-Test-Doubles kompiliert.

Die Test-Doubles befinden sich ausschließlich unter `tests/stubs` und gehören nicht zur APK.
Die Tests ersetzen keinen vollständigen Android-Build, keine echte Mapsforge-Dateiprüfung
und keinen Test der Herstellerdienste am Radio. Die GPS-Schaltfläche und das Layout der
Kartenseite müssen nach dem Android-Build auf dem Gerät geprüft werden.

## Buildgrenze

Versuch: `sh gradlew assembleDebug`.
Ergebnis: fehlgeschlagen beim Laden von Gradle 8.9 mit
`java.net.UnknownHostException: services.gradle.org`.
Es wurde keine neue APK erstellt. Das Projekt kann in Android Studio oder mit
`BUILD_APK_WINDOWS.bat` in einer eingerichteten Android-Buildumgebung gebaut werden.

## Grenzen der Fehlerbehebung

Ein dauerhaft blockierender Hersteller-/Speicherdienst kann weiterhin einen Hintergrundarbeiter
belegen. Die Warteschlangen sind begrenzt; die behobenen Abfragen warten nicht mehr auf der
Download-Bedienoberfläche. Die Straßenkorrektur kann bei langsamer Speicherung entfallen,
während die normale GPS-Position weiter angezeigt wird. 350 ms ist die gesetzte Wartefrist,
keine Echtzeitgarantie des Android-Schedulers.

Eine Ursache des gemeldeten vollständigen Radio-Einfrierens ist hiermit nicht bewiesen.
Die Korrekturen beseitigen die sieben identifizierten Codeprobleme; eine allgemeine
Absturzfreiheit des Radios wird daraus nicht abgeleitet.
