# Testbericht v0.31.132 (wöchentliche Karten-Update-Prüfung mit Popup)

Basis v0.31.95 (versionCode 177). Lokaler Kachelserver + Versuchsleiter. run-tests.sh grün; kein Android-Build, nicht am Radio getestet.


## Nachtrag v0.31.105
- OBD2: Timeout-Toleranz (pollRead), Such-Timeout 10 s, Support-Check ohne Verbindungsabbruch; RpmFrozenTest um „Auto fährt“ erweitert. Gesamte Testsuite grün.

## Nachtrag v0.31.105
- Drehzahl 0 bei verstummtem CAN im Stillstand; Testsuite grün.

## Nachtrag v0.31.105
- Diagnose-Log RPM-Verlauf im Stillstand; Testsuite grün.

## Nachtrag v0.31.105
- Aufräumen toter Code; SourceAuditTest-Prüfung der ungenutzten Wetterkachel-Ausrichtung entfernt; Tests von CompletionListeners/LoadTimeoutGate entfernt. Testsuite grün.

## Nachtrag v0.31.105
- Zweite Aufräumrunde (exakte Referenzanalyse); `tests/run-tests.sh` grün; `tests/compile-check.sh` gegen android-33: 108 Klassen, 0 Fehler.

## Nachtrag v0.31.106
- „Ø seit Tanken“ vollständig entfernt (Klasse, Diagnose, Knopf, Feld `avgConsumption`, Refill-Hook); Tests entsprechend angepasst bzw. entfernt (Obd2RefuelAveragerTest). Duplikate zusammengelegt (TrafficDataStore.Window, HttpText, DabNotificationListener.accessGranted). `run-tests.sh` grün; `compile-check.sh` gegen android-33 ohne Fehler. Nicht am Radio getestet.

## Nachtrag v0.31.107
- Ungenutzte Abhängigkeit androidsvg aus build.gradle entfernt (kein Import im gesamten Code). Tests grün.

## Nachtrag v0.31.108
- Glas-Weichzeichnung beim Appstart im Hintergrund vorbereitet; UI-Wächter meldet keinen Leerlauf (nativePollOnce) mehr. Tests und compile-check grün.

## Nachtrag v0.31.109
- Diagnose um GPS-Abstände und Karten-Bilder/Stillstand beim Gleiten erweitert (MapFollowDiagnostics, OnCameraMoveListener). Tests und compile-check grün.

## Nachtrag v0.31.110
- Diagnose: zählt Fixes in Fahrt, bei denen die Kamera schon stillstand, mit Ereigniszeile. Tests und compile-check grün.

## Nachtrag v0.31.111
- Jitter-Filter der Folgekamera nur noch im Stand/Schleichfahrt (CameraMotion.isJitter), Tests ergänzt. Tests und compile-check grün.

## Nachtrag v0.31.112
- OBD2: Verbindungs-Watchdog (20 s), nicht blockierendes Schließen, Adapter-Neustart bei 45 s ohne Daten (Backoff bis 300 s), Neuverbinden beim Standby-Aufwachen. Tests und compile-check grün.

## Nachtrag v0.31.113
- Adapter-Neustart bei leeren Daten nach 30 s statt 45 s. Tests und compile-check grün.

## Nachtrag v0.31.114
- GPS-Taste und Info-Taste auf der Karte ohne Hintergrund (mit leichtem Textschatten); Netztyp zeigt 4G statt LTE. Tests und compile-check grün.

## Nachtrag v0.31.115
- Fahrzeugkachel nach Konzept B: Tempo/Drehzahl groß, Aktuell/Ø 100 KM darunter, Bordnetz/Außen klein in der Kopfzeile, Gurt/Wasser immer sichtbar. Tests und compile-check siehe unten.

## Nachtrag v0.31.116
- DAB-Senderlogo wird mit abgerundeten Ecken beschnitten (RoundedArtworkView). Tests und compile-check grün.

## Nachtrag v0.31.117
- Blinkende orange Gurt-Warnung auf der Karte (SeatbeltAlert + SeatbeltAlertTest). Tests und compile-check grün.

## Nachtrag v0.31.118
- Tempo, Drehzahl, Aktuell, Ø 100 KM: Beschriftung und Wert mittig in der Kachel. Tests und compile-check grün.

## Nachtrag v0.31.119
- DAB-Autostart: Rückkehr erst, wenn DABdream+ vorn ist und der Sender läuft (DabReturnGate). CarPlay-Rückkehr wartet auf Verbindungssignale (Bluetooth/CarPlay-Medien, CarPlayConnection) mit Zeitlimit 90 s. Tests und compile-check grün.

## Nachtrag v0.31.120
- Letzter Kartenzustand (Position, Fahrtrichtung, Zoom, Ortslage) wird alle 5 s, bei Bildschirm-aus und beim Pausieren gespeichert und beim Start wiederhergestellt. Tests und compile-check grün.

## Nachtrag v0.31.121
- Unbekannte Ortslage überschreibt die gespeicherte nicht mehr; ohne erkannte Ortslage/Geschwindigkeit bleibt der zuletzt genutzte Zoom. Tests und compile-check grün.

## Nachtrag v0.31.122
- Einheit bei Aktuell/Ø 100 KM größer (UnitValueView mit eigenem Maßstab); im Schiebebetrieb (l/100 km unter 0,05) zeigt Aktuell l/h (ConsumptionDisplayTest erweitert). Tests und compile-check grün.

## Nachtrag v0.31.123
- Automatische Prüfung auf neue Karten einmal pro Woche (MapUpdateSchedule + MapUpdateScheduleTest), Popup im Launcher mit Aktualisieren/Später. Tests und compile-check grün.

## Nachtrag v0.31.124
- UsbImportLayoutTest, SourceAuditTest und Compile-Check grün.
- Windows-Downloader (mcs, .NET 4.5) gegen lokalen HTTP-Server getestet: Download, HEAD/Last-Modified, Überspringen fertiger Dateien, Fortsetzen per Range, byte-identisch. GUI nur kompiliert, nicht auf Windows gestartet.
- Nicht auf dem Radio getestet (Stick-Erkennung, ggf. SAF-Ordnerauswahl).

## Nachtrag v0.31.125
- Positionsmarke (Standard/Kürbis/Schlitten): Auswahllogik per MarkerStyleTest geprüft; das Zeichnen (Canvas) und das Menü sind nur gegen die Android-API kompiliert, nicht auf dem Radio gesehen.

## Nachtrag v0.31.127
- GPS-Taste als Ring mit Fadenkreuz (GpsRingView). Zeichnung mit einer Java2D-Nachbildung der Canvas-Aufrufe geprüft, Klassen gegen die Android-API kompiliert, auf dem Radio nicht gesehen.

## Nachtrag v0.31.128
- GPS-Taste: Text "◎  GPS" bleibt, bekommt statt des Rings einen ovalen Rahmen. Nur gegen die Android-API kompiliert, auf dem Radio nicht gesehen.

## Nachtrag v0.31.129
- OBD2-Verlauf: eigener kurzer Ereignisspeicher (ObdHistory, getestet), im Diagnoseprotokoll unter OBD2. Ursache des Verbindungsproblems nach Zündung aus/an ist damit NICHT behoben, nur nachvollziehbar gemacht. Kompiliert, im Auto nicht geprüft.

## Nachtrag v0.31.130
- GitHub-Build signierte mit einem eigenen Schlüssel (31:C8:0A:EC…) statt mit dem hinterlegten (D9:46:63:4A…). Jetzt wird die Schlüsseldatei per DEBUG_KEYSTORE_PATH ausdrücklich in signingConfigs.debug eingetragen. Wirkt nur im GitHub-Build; am PC unverändert. Erst der nächste GitHub-Lauf zeigt, ob es greift.

## Nachtrag v0.31.132
- Positionsmarken verkleinert (Kürbis 48x44 -> 32x29 dp, Schlitten 34x56 -> 24x40 dp), nach Foto vom Radio. Standard-Punkt unverändert 20 dp.

## Nachtrag v0.31.132
- Neu: MapResumePolicyTest (Fehlerklassifizierung transient/fatal, Wartezeiten, Range-Fortsetzpunkt). Direkt-Ortsgrenze-Schalter und Reste entfernt, SettlementZoomGateTest/SourceAuditTest angepasst.
- Nicht am Radio getestet: Range-Fortsetzung gegen den echten Server, Alarm-Wiederaufnahme im Standby (Android kann Hintergrundstarts blockieren).

## Nachtrag v0.31.133
- SolarDaylightTest: Nachtmodus 30 min vor Sonnenuntergang, morgens unverändert am Sonnenaufgang. Nicht im Auto getestet.

## Nachtrag v0.31.134
- Download mit fsync/.pos-Sidecar und automatischem Neuladen bei ungültigem MBTiles. Tests/Compile gruen; nicht am Radio getestet.

## Nachtrag v0.31.135
- Defekte OSM-Rohdaten (Parser-RuntimeException) werden verworfen und neu geladen. Tests/Compile gruen; nicht am Radio getestet.

## Nachtrag v0.31.136
- FuelPriceLogicTest (Stationszuordnung, Format, Vergleich). Tankerkönig-Abruf und Wasserwarnung nicht am Radio/gegen die echte API getestet (kein Schlüssel).

## Nachtrag v0.31.137
- Layout/Intervall-Auswahl der Tankstellenfelder; Tests/Compile gruen, nicht am Radio getestet.

## Nachtrag v0.31.138
- Nur Beschriftung der Tankstellenfelder geaendert.

## Nachtrag v0.31.139
- Preisformat zwei Nachkommastellen (abgeschnitten); FuelPriceLogicTest angepasst.

## Nachtrag v0.31.140
- Preis-Schriftgroesse 13 sp wie Name; nur Layout, nicht am Radio getestet.

## Nachtrag v0.31.141
- Layout: eigene Tankstellenkachel. Tests/Compile gruen, Hoehenverhaeltnisse nicht am Radio geprueft.

## Nachtrag v0.31.142
- Layout-Anteile rechts angepasst (DAB nicht mehr abgeschnitten). Nicht am Radio getestet.

## Nachtrag v0.31.143
- Zoom-Gleiten in GermanyOfflinePanel; Tests/Compile gruen. Fluessigkeit nur auf dem Radio pruefbar.

## Nachtrag v0.31.144
- Zoomgeschwindigkeit 0,5 Stufen/s. Tests/Compile gruen, nicht am Radio getestet.
