# Testbericht v0.24.0

Stand: 17.09.2026. Ausgangspunkt: v0.23.1. Keine Änderung des Layouts vorgesehen.

## Ausgeführte Prüfungen

12 Testsuiten mit Java 17+-kompatiblen Quellen ausgeführt: alle bestanden.
18 ausgewählte Produktionsklassen einschließlich CAN-Binder, Broadcast-Empfänger,
Medienmodell und Seiten-Pager wurden dabei mitkompiliert.
41 Produktions-Java-Dateien mit dem Java-Compilerparser ohne Syntaxfehler gelesen.
Android-Aufrufe werden in diesen lokalen Tests durch Test-Doubles ersetzt.
Eine Syntaxprüfung bestätigt nicht die Auflösung sämtlicher Android-/SDK-Methoden.

| Review-Punkt | Änderung | Prüfung und Ergebnis |
| --- | --- | --- |
| 1. Lade-Timer | Einmaliges Scharfstellen, Rücksetzen bei Pause/Erfolg/Fehler | LoadTimeoutGateTest: 200 wiederholte Start-/Resume-Anforderungen führen nur zu einer Timer-Anforderung; erneutes Scharfstellen nach Reset besteht. Lifecycle-Verdrahtung im Quellcode geprüft. |
| 2. Nachtanken | Baseline folgt nicht mehr jeder kleinen Aufwärtsstufe | RangeEstimatorTest: 12→14→16→18 und 12→13→…→18 erkannt; kein vorzeitiger Reset; kurzzeitiger Anstieg ignoriert; konstanter Tank zählt weiterhin herunter. |
| 3. Seiten-Swipe/GPS | Nachführungszustand bei Gesture-Start merken und bei Seiten-Übernahme wiederherstellen | PagerGestureTest mit echtem Pager und vereinfachten Touch-/View-Doubles: diagonal→horizontal besteht; absichtlich pausierte Nachführung bleibt pausiert; Pinch gehört nicht dem Pager. Beide Karten im Quellcode angeschlossen. |
| 4. Erster GPS-Fix | Genauigkeit und Koordinaten vor Initialisierung prüfen | GpsFollowFilterTest: schlechter erster Fix, NaN und ungültige Koordinaten abgelehnt; guter Fix und Wiederaufnahme nach Lücke bestehen. Null-Behandlung in beiden GPS-Datenwegen geprüft. |
| 5. Logo-Allokationen | Bitmap-Quelle zusätzlich merken | MediaInfoTest mit Bitmap-Double: 100 Wiederholungen derselben Quelle erzeugen nur eine Skalierung. |
| 6. Altes Logo | Fehlendes Artwork explizit weiterreichen und löschen | MediaInfoTest: null löscht Bild; erneutes Bild wird angenommen. Session-/Notification-Verdrahtung geprüft. |
| 7. 0x2E in Nutzdaten | Längenbasierter serieller Parser mit Prüfsummenprüfung | CanFrameTest: Payload-0x2E, Prüfsummen-0x2E, fragmentierte Frames, alle 256 Payloadwerte und Recovery nach falscher Prüfsumme bestehen. |
| 8. Unbestätigte CAN-Zuordnungen | Toyota-Decoder entfernt; nur bestätigte HCT-Normalisierung | SystemCanReceiverTest: unbekannter Transport verändert keine Türwerte; bestätigter serieller/normalisierter HCT-Weg und VW-Türzuordnung bestehen. |
| 9. Repository-Sperre | Broadcast-Schreibzugriffe atomar unter gleicher Sperre | AST-Prüfung aller mutable()-Zugriffe im Empfänger besteht; parallel 10.000 Broadcast-Schreibzugriffe und Snapshots ohne Testfehler. |
| 10. CAN-Service-Bindings | Normalen Disconnect nicht doppelt binden; tote/Null-Bindings freigeben, Retry-Cooldown | CanBindingTest mit Context-/Binder-Doubles: keine doppelte Bindung, Unbind nach Disconnect/Null/Binding-Died, Retry-Cooldown und kein Bind ohne Besitzer bestehen. |
| 11. Kartensicherung | Verbliebene Sicherung vor erneutem Austausch wiederherstellen, nie vorab löschen | MapFileActivationTest: echte temporäre Dateien mit simulierten Disk-Fehlern; Fallback, Rollback, gescheiterte Wiederherstellung und erneuter Versuch behalten die gute Sicherung. Atomarer Erfolg ebenfalls geprüft. |
| 12. Menü nach Download | Verfügbarkeit des Straßenkorrektur-Schalters in onResume aktualisieren | Quellcodeprüfung besteht. Echter Android-Menüwechsel ist hier nicht ausführbar. |
| Code-Leichen | VwPq25Decoder, alter SimpleSoftDecoder, lastMode2Rpm, lastCallbackHex entfernt | SourceAuditTest bestätigt, dass Klassen/Felder fehlen. Bestätigter CAN-Datenweg bleibt getestet. |

Weitere Regressionen: GpsLeadPolicyTest und GpsLeadPredictorTest bestehen
(Vorausberechnung begrenzt, Kurven/Bremsen/Lücken stoppen Vorlauf, rohe Messung unverändert).
Seiten-Pager überspringt Positionskorrektur in onLayout während laufender Animation.
Windows-Buildskript verlässt Erfolgspfad nicht mehr wegen falscher IF-Verknüpfung.
Online-Stile Liberty/Bright/3D und 45°-Neigung bleiben im Quellcode enthalten.

## Nicht erfolgreich ausführbar

`bash ./gradlew :app:assembleDebug` bricht bereits beim Gradle-Download ab:
`java.net.UnknownHostException: services.gradle.org`.
Es wurde daher keine neue APK erzeugt und kein erfolgreicher Android-Build behauptet.
PowerShell-Teststarter wurde hier nicht ausgeführt; ausgeführt wurde der Shell-Teststarter.
Echte MapLibre-/Mapsforge-Darstellung, System-Touch-Dispatch, OEM-Binder und CAN-Eingang
brauchen zusätzlich einen Build in Android Studio und einen Test auf dem Radio.

## Empfohlener kurzer Radiotest

1. Update ohne Deinstallation bauen/installieren; Einstellungen sollten erhalten bleiben.
2. Seitlich und leicht schräg über der Karte wischen; nach Rückkehr muss GPS-Nachführung weiterlaufen.
3. DAB-Sender wechseln, Logo/Interpret und Tasten kontrollieren.
4. Online-/Offline-Wechsel und bei Gelegenheit Kartendownload testen.
5. GPS-Start, Fahrtrichtung und sanften Vorlauf während einer Fahrt beobachten.
6. Nach dem nächsten Nachtanken ca. 30 Sekunden stabiler Tankmeldungen abwarten und Reichweite prüfen.

Die Prüfungen sichern die beschriebenen lokalen Fälle ab, sind jedoch kein Nachweis,
dass auf jedem Radio sämtliche Laufzeitfehler ausgeschlossen sind.
