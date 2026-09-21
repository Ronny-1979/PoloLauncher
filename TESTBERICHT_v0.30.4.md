# Prüfbericht Polo Launcher v0.30.4

Ausgangsstand: v0.30.3 (Code 64). Überarbeiteter Stand: v0.30.4 (Code 65).
Auftrag: alle elf Befunde der vollständigen Quelltextprüfung von v0.30.3 und die kleineren Punkte beheben.

## Die elf Befunde

| Nr. | Fehler | Korrektur | Prüfung |
| --- | --- | --- | --- |
| 1 | Falscher Kilometerstand als Bezugswert sperrte die Reichweite dauerhaft; nach langer Standzeit konnte ein Ausreißer akzeptiert werden und den Tankinhalt auf 0 setzen | Toleranz wächst nur bis 24 h; ein unplausibler Wert, der 30 s konsistent bleibt, wird neuer Bezug; einzelne Ausreißer ändern nichts | RangeEstimatorRecoveryTest: Ausreißer als erster Bezug, Ausreißer nach zwei Wochen Stand, wechselnder Müll, plausible unbeobachtete Fahrt. Mutationsprobe: ohne Rebase und ohne 24-h-Grenze schlägt der Test fehl |
| 2 | OBD-Thread fing nur IOException/RuntimeException; fester 2-s-Takt; keine Pufferleerung, verschobene Antwortströme blieben unbemerkt | `Throwable` im Poll-Loop; Backoff 2/4/8/16/30 s; Puffer vor jedem Befehl leeren; Verschiebung erkennen, nicht veröffentlichen, neu synchronisieren; Meldungen gefaltet | Obd2ResilienceTest: verspätete Antwort im Puffer, komplett verschobener Strom, Backoff-Folge, Log-Flut (50 Meldungen → ≤ 3 Zeilen), `NoSuchMethodError` im Loop (Thread lebt weiter). Mutationsproben: ohne Drain und mit `catch (Exception)` schlägt der Test fehl |
| 3 | `onReceive` ungeschützt, Empfänger exportiert | Gesamter Ablauf in `try/catch (Throwable)`, Registrierung abgesichert, Fehlerlog begrenzt | SystemCanReceiverTest: Broadcast, dessen `getExtras()` wirft; `context == null` |
| 4 | `startForeground()` ohne Schutz gegen Android-12-Hintergrundstart | Ablehnung wird abgefangen, Dienst beendet sich, nächster sichtbarer Launcher startet ihn neu | Quelltextprüfung (kein Android-Test möglich) |
| 5 | Schlüsselnamen-Heuristik lief in Produktion und konnte bestätigte Werte überschreiben | Standardmäßig aus; Schalter „Sniffer-Heuristik“ in System & Diagnose; bestätigte Frames und bekannte Broadcasts unverändert | SystemCanReceiverTest: aus (Standard), an per Feld, an per Einstellung; fremder Broadcast überschreibt weder OBD- noch Bordnetzwerte. Mutationsprobe „immer an“ schlägt fehl |
| 6 | Heller Standarddialog: cremefarbene Texte/Links kaum lesbar | `CopperSkin.dialog()` (dunkles Material-Thema) für alle Dialoge; Linkfarbe Gold | Kompilierung gegen echte Android-API; SourceAuditTest verbietet den rohen `AlertDialog.Builder`. Optik nicht am Gerät geprüft |
| 7 | Tür vorn links fest als „Kontakt defekt“ einkompiliert | Einstellung (Standard an), gespeichert | Kompilierung; SourceAuditTest |
| 8 | Schubabschaltung nicht berücksichtigt, Wirkungsgrad-Annahme (0,78) nicht überprüfbar | Schub-Schätzung (abschaltbar) und Modellkorrektur aus dem CAN-Tankabschnitt (25 % der Abweichung je Abschnitt, Faktor 0,6–1,5, gespeichert, zurücksetzbar) | Obd2CalibrationTest: Verhältnis und Grenzen, Lernabschnitt mit OBD-Summen, Zurücksetzen des OBD-Durchschnitts im Abschnitt, RangeStore → Kalibrierung → Persistenz. Obd2ResilienceTest: Schub zählt 0 l/h, Faktor skaliert Kraftstoff proportional. Mutationsproben bestanden |
| 9 | App-Seiten und -Symbole synchron im UI-Thread; Namen dreifach aufgelöst | Name einmal je Eintrag; Symbole im Hintergrund | Kompilierung; SourceAuditTest. Startzeit nicht gemessen |
| 10 | DAB-Info alle 2 s per Binder im UI-Thread | `DabSessionWatcher` mit `MediaController.Callback`, Wiederanmeldung alle 5 s ohne Zugriff, Nachsynchronisierung alle 20 s | Kompilierung; SourceAuditTest (kein `getActiveSessions` in MainActivity). Kein Test mit echter MediaSession |
| 11 | SourceAuditTest prüfte nur Zeichenketten; UI-Klassen wurden nie kompiliert | Neu geschrieben: Syntax, Sperrdisziplin, Querverweis-Audit (458 statische Aufrufe, 33 Konstruktoraufrufe), whitespace-unabhängige Strukturprüfungen ohne Kosmetik; neues `tests/compile-check.sh` | Mutationsprobe: ein erfundener Aufruf in MainActivity wird gemeldet |

## Kleinere Punkte

Offizieller Gradle-Wrapper 8.9.0 mit `distributionSha256Sum` (lokal geprüft: falsche Prüfsumme wird abgelehnt), `abiFilters`, Sicherungsdatei-Blockade in `MapFileActivation`, monotone Uhr für Aktualität, gecachte DAB-Paketprüfung, abgesicherte Einstellungs-Starts, genaue Standortberechtigung für GPS, `POST_NOTIFICATIONS`, Dateigröße per HEAD-Anfrage, `Throwable` in den Karten-Arbeitern, tote Felder und `onActivityResult` entfernt, `sans-serif`, `CYAN` → `ACCENT`.

## Ergebnis

- `tests/run-tests.sh`: 31 Testprogramme bestanden (27 in v0.30.3; neu: RangeEstimatorRecoveryTest, Obd2CalibrationTest, Obd2ResilienceTest, OfflineMapSizeProbeTest; erweitert: SystemCanReceiverTest, MapFileActivationTest, Zeitbasis in vier bestehenden Tests). 58 Produktionsdateien ohne Syntaxfehler.
- `tests/compile-check.sh`: alle 58 Produktionsklassen, einschließlich aller Activities, kompilieren gegen eine echte `android.jar` (API 33). Mapsforge/MapLibre sind dabei durch handgeschriebene Stubs ersetzt (`tests/compilestubs`), nicht durch die echten Bibliotheken.
- Gradle-Wrapper: `gradlew --version` mit lokaler Distribution erfolgreich; falsche Prüfsumme bricht ab.

## Buildgrenze

Ein vollständiger Gradle-/Android-Build wurde nicht ausgeführt: In dieser Umgebung stehen weder Android-SDK noch das Android-Gradle-Plugin zur Verfügung. Nicht geprüft sind damit Ressourcen- und Manifest-Zusammenführung, Lint, die echten Mapsforge-/MapLibre-Signaturen und alles, was nur auf dem Radio sichtbar ist. Vor dem Einsatz: `BUILD_APK_WINDOWS.bat` oder Android Studio.

## Am Auto zu prüfen

1. **Schubabschaltung**: Die Schwellen (1300/min, 30 kPa, 10 km/h) sind Schätzwerte. Nach einer Fahrt in der OBD-Diagnose „Schubabschaltung geschätzt: n von m Runden“ ansehen. Eine Landstraßenfahrt mit vielen Schubphasen sollte einen kleinen, keinen großen Anteil ergeben; sonst Schätzung abschalten und melden.
2. **Modellkorrektur**: Der Faktor ändert sich erst nach einem CAN-Lernabschnitt (≥ 100 km). Plausibel sind Werte um 0,8–1,2. Läuft er an eine Grenze (0,6 oder 1,5), stimmt etwas anderes nicht (Adapter, Tankgeber): dann „Modellkorrektur zurücksetzen“ und melden.
3. **Sniffer-Heuristik aus**: Alle Anzeigewerte (Drehzahl, Spannung, Außentemperatur, Kilometer, Tank, Türen, Gurt, Wischwasser) kommen aus bestätigten HCT-Frames. Fehlt nach dem Update ein Wert, Sniffer einschalten und die Diagnosezeile melden.
4. **DAB**: Titel, Cover und Tasten nach Start und nach Wechsel der Station beobachten.
5. **Dialoge** (Kartenquellen, Download löschen) auf Lesbarkeit prüfen.
