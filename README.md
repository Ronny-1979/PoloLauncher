# Polo Launcher v0.30.4 – Elf Fehlerkorrekturen und Aufräumarbeiten

## Neu in v0.30.4

Grundlage war eine vollständige Quelltextprüfung von v0.30.3. Alle elf Befunde und die kleineren Punkte sind behoben.

**Reichweite und Verbrauch**

- Ein falscher Kilometerstand als Bezugswert (Ausreißer im ersten Frame, gespeichert) sperrte die Reichweite dauerhaft. Bleibt ein unplausibler Wert 30 s lang konsistent, wird er als neuer Bezug übernommen; einzelne Ausreißer ändern weiterhin nichts. Die erlaubte Sprungweite wächst nach Standzeiten nur noch bis 24 h (vorher unbegrenzt: nach zwei Wochen Standzeit konnte ein Ausreißer akzeptiert werden und den Tankinhalt auf 0 setzen).
- Schubabschaltung: Das OBD-Modell zählte im Schub Kraftstoff, obwohl die Einspritzung aus ist. Neu wird Schub aus Drehzahl (ab 1300/min), Saugrohrdruck (bis 30 kPa) und Geschwindigkeit (ab 10 km/h) geschätzt und als 0 l/h gewertet. **Die Schwellen sind nicht am Auto verifiziert.** Die OBD-Diagnose zeigt „Schubabschaltung geschätzt: n von m Runden“; in den OBD-Einstellungen lässt sich die Schätzung abschalten.
- Modellkorrektur: Nach jedem bestätigten CAN-Lernabschnitt (≥ 100 km, ≥ 5 L echte Tankabnahme) werden die im selben Abschnitt vom OBD-Modell gezählten Liter mit der echten Tankabnahme verglichen und der angenommene volumetrische Wirkungsgrad um ein Viertel der Abweichung nachgeführt (Faktor 0,6 bis 1,5, gespeichert). Abschnitte, in denen der Adapter nicht mindestens 85 % der Strecke erfasst hat oder der OBD-Durchschnitt zurückgesetzt wurde, zählen nicht. Anzeige und „Zurücksetzen“ in den OBD-Einstellungen.
- Alle Aktualitätsprüfungen der Fahrzeugdaten laufen auf einer monotonen Uhr (`VehicleRepository.now()`). Der Sprung der Systemzeit kurz nach dem Start (GPS/NTP) lässt frische Werte nicht mehr veraltet erscheinen.

**OBD-Verbindung**

- Der OBD-Thread fängt jetzt alle `Throwable` (auch `Error`) ab und läuft weiter; ein still gestorbener Thread war der Fehler aus v0.7.0.
- Wiederverbinden mit Backoff 2, 4, 8, 16, 30 s (nur bei echten Bluetooth-Versuchen); ein neu gewählter Adapter verbindet sofort. Gleiche Fehlermeldungen erscheinen höchstens einmal pro Minute mit Zähler.
- Vor jedem Befehl wird der Eingangspuffer geleert. Antworten, die zu einem früheren Befehl gehören (verspätete Antwort nach einem Timeout), verschoben den ganzen Strom, ohne dass je ein Fehler auftrat. Verschobene Runden werden erkannt, nicht veröffentlicht und der Puffer neu synchronisiert.

**Stabilität und Sicherheit**

- `SystemCanReceiver.onReceive` ist vollständig abgesichert. Der Empfänger ist exportiert (jede App kann ihm Broadcasts senden); eine fehlerhafte Zusatzinformation konnte den Launcher abstürzen lassen.
- Die Schlüsselnamen-Heuristik für unbekannte Broadcasts ist standardmäßig **aus** (Einstellungen → System & Diagnose → „Sniffer-Heuristik“). Bestätigte HCT-Frames und bekannte Broadcasts wirken immer. Vorher konnte z. B. ein Lüfter-„speed“ bestätigte CAN-Werte überschreiben.
- `RangeTrackingService`: `startForeground()` ist gegen die Android-12-Beschränkung für Hintergrundstarts abgesichert. Der Dienst beendet sich dann und wird beim nächsten sichtbaren Launcher wieder gestartet.
- Zusätzlich `POST_NOTIFICATIONS` (Android 13) mit Statusknopf in den Einstellungen.
- GPS: `GPS_PROVIDER` braucht die genaue Standortberechtigung. Bei nur „ungefährer“ Freigabe warf jeder Anfrageversuch eine `SecurityException`. Jetzt läuft in diesem Fall nur der Netzwerkanbieter; die Einstellungen zeigen „Nur ungefähr“.
- Einstellungs- und Systemseiten werden über einen abgesicherten Start geöffnet (Radio-Firmware entfernt sie oft); mit Rückfallwegen und Hinweis.

**Oberfläche**

- Dialoge (Kartenquellen, Downloads, Zurücksetzen) im dunklen Cockpit-Stil; im hellen Standarddialog waren die cremefarbenen Texte und Links kaum lesbar.
- „Tür vorn links: Kontakt defekt“ ist jetzt eine Einstellung (Standard an, wie bisher) statt einer fest eingebauten Konstante.
- Start: App-Namen werden einmal pro Aktualisierung ermittelt (vorher dreimal: Sortierung, Signatur, Kachel); die Symbole laden im Hintergrund, sodass das Dashboard nicht auf 18 × n Symbolabfragen wartet.
- DAB-Titel, Cover und Tastenzustand kommen ereignisgesteuert per `MediaController.Callback` (neue Klasse `DabSessionWatcher`). Der bisherige Binder-Aufruf alle 2 s im UI-Thread und das ständige Neuskalieren desselben Covers entfallen. `DabNotificationListener` merkt sich Paketprüfungen.

**Karten**

- `MapFileActivation`: Eine alte `.backup`-Datei neben einer plausiblen neuen Karte blockierte jedes künftige Update der Region. Sie wird jetzt entfernt; gelingt das nicht, gilt die Karte trotzdem als installiert (Warnung statt Fehler).
- `OfflineMapStore`: Der freie Speicher wird gegen die vom Server gemeldete Dateigröße (HEAD-Anfrage) geprüft, nicht mehr nur gegen die veraltbare Größentabelle. Die Abschlussarbeiter fangen `Throwable`, sodass kein Eintrag „ausstehend“ hängen bleibt.

**Build und Aufräumen**

- Der handgeschriebene 3-KB-`gradle-wrapper.jar` (lud ohne Prüfsumme, ohne Schutz gegen manipulierte ZIP-Pfade) wurde durch den offiziellen Wrapper 8.9.0 ersetzt; `distributionSha256Sum` prüft die Distribution.
- `abiFilters 'arm64-v8a', 'armeabi-v7a'`: keine x86-Bibliotheken mehr im APK.
- Entfernt: nur dekodierte, nie angezeigte Felder (CAN-Geschwindigkeit, Kühlmittel, Herstellerreichweite, Handbremse, Motorhaube, CAN-Verbrauch, Blinker), toter `onActivityResult`. Die Decoder protokollieren die Werte weiter in der Diagnose. Die CAN-Verbrauchsfelder aus v0.30.3 entfallen damit; der Schutz der OBD-Werte gilt nun dadurch, dass unbestätigte Broadcasts nichts mehr schreiben.
- `Typeface.create("sans", …)` → `"sans-serif"`; `CYAN` heißt `ACCENT` (enthielt schon lange Gold).

**Tests**

- `tests/SourceAuditTest.java` ist neu geschrieben: Syntax, Sperrdisziplin und ein Querverweis-Audit (jeder `Klasse.methode(...)`- und `new Klasse(...)`-Aufruf im Projekt muss zu einer echten Methode/Konstruktoranzahl passen). Die Struktur-Prüfungen sind whitespace-unabhängig; kosmetische Layout-Prüfungen entfielen.
- Neu: `tests/compile-check.sh` kompiliert alle Produktionsklassen (auch die UI-Klassen) gegen eine echte `android.jar`. Aufruf: `ANDROID_JAR=…/android.jar sh tests/compile-check.sh`. Karten-Bibliotheken werden dabei durch Stubs in `tests/compilestubs` ersetzt.
- Neu: `RangeEstimatorRecoveryTest`, `Obd2CalibrationTest`, `Obd2ResilienceTest`, `OfflineMapSizeProbeTest`; `SystemCanReceiverTest` und `MapFileActivationTest` erweitert.
- Version 0.30.4, Code 65.

## Neu in v0.30.3

- Jede HCT-Verbindung verwendet eine eigene Callback-Binder-Identität. Verspätete An-/Abmeldungen entfernen keine neue Anmeldung; alte Rückmeldungen werden ignoriert.
- Herstellerobjekte werden ausschließlich über ihre Datenfelder untersucht. Es werden keine unbekannten Methoden aufgerufen, auch keine vermeintlichen Getter.
- CAN-Broadcast-Verbrauchswerte besitzen eigene Felder und überschreiben weder den OBD-Momentanverbrauch noch den OBD-Durchschnitt für die kombinierte Reichweite.
- Straßenkorrektur: nach spätestens 350 ms Wartefrist (zuzüglich Android-UI-Scheduling) wird die neueste unveränderte GPS-Position weitergegeben. Neue GPS-Proben verlängern diese Frist nicht. Verspätete Korrekturen ersetzen keine bereits angezeigten oder neueren Positionen.
- Der GPS-Knopf verwendet die letzte frische Anzeige-Position unmittelbar, statt sie erneut durch die Prüfung auf doppelte GPS-Proben zu schicken.
- Alle wartenden Seiten erhalten den Downloadabschluss. Die sichtbare Hauptseite prüft einen laufenden Download zusätzlich alle fünf Sekunden; beim Verlassen wird dieser Timer entfernt.
- Speicherabfragen, Downloadstatus und Dateiaktionen der Offline-Kartenseite laufen auf begrenzten Hintergrundarbeitern. Wiederholte Statusabfragen werden zusammengefasst; veraltete Ergebnisse überschreiben keine neuere Aktion.
- Version 0.30.3, Code 64. 27 Testprogramme bestanden, darunter simulierte Binder-Rennen, blockierte Kartenleser und langsame Downloadabfragen. Details: `TESTBERICHT_v0.30.3.md`.
- Keine fertige APK enthalten: Der Buildversuch scheiterte am nicht erreichbaren Gradle-Download (`services.gradle.org`). Kein Test am Radio. Berechnungsformeln, 1-km-Anzeigeschwelle, Speicherung, Glättung und DAB-Autostart bleiben erhalten.

## Neu in v0.30.2

- Alle acht Punkte der vollständigen Quelltextprüfung umgesetzt: unbekannte Herstellerobjekte werden außerhalb des Android-Hauptthreads untersucht; die Warteschlange ist begrenzt.
- DAB-Metadaten und Tastenstatus stammen pro Aktualisierung aus derselben MediaSession-Abfrage statt aus zwei direkt aufeinanderfolgenden Abfragen.
- DownloadManager-Abfrage, Mapsforge-Dateiprüfung und Kartenaktivierung laufen auf einem eigenen Hintergrundthread. Große Kartendateien blockieren dadurch nicht mehr die Oberfläche.
- Straßenkorrektur und Ortszoom verwenden benannte Daemon-Threads, schwache UI-Referenzen und eine sofortige, unabhängige Dateischließung beim Beenden. Festhängende Karten-I/O hält keine Activity mehr fest.
- Das Entfernen von GPS-Updates ist gegen eine während des Betriebs entzogene Standortberechtigung abgesichert.
- Der HCT-Binder besitzt zwei Daemon-Arbeiter und eine feste Warteschlangengrenze. So bleiben Abmeldung/Neuaufbau bei einem einzelnen festhängenden Herstelleraufruf möglich; volle Warteschlangen werden protokolliert statt unbegrenzt Speicher anzufordern.
- Hochfrequente CAN-Mode2-Daten erzeugen nur noch dann Hex-Zeichenketten, wenn tatsächlich eine Diagnosezeile geschrieben wird.
- Bei sichtbarer Online-Karte schläft der Mapsforge-GPS-/Renderpfad; eine leichte GPS-Quelle versorgt weiterhin Straßenkorrektur, Ortszoom und MapLibre. Offline wird automatisch wieder Mapsforge aktiviert.
- Nach einem zweiten Detailaudit wird die Online-GPS-Position nicht mehr versehentlich zweimal geglättet; Straßenkorrektur und Ortszoom behalten ihre Callbacks zuverlässig bis zum Zerstören des Kartenpanels.
- Ein während der Dateiprüfung abgebrochener Offline-Download kann nicht nachträglich aktiviert werden. Die begrenzte Prüfung hält außerdem keine beendete Activity fest.
- Unbekannte Herstellerobjekte werden im Broadcast-Hauptpfad nicht per `toString()` ausgeführt. Bundle-Tiefe, Hex-Dumps und ELM327-Antwortgrößen besitzen feste Obergrenzen gegen fehlerhafte Datenströme.
- Version auf 0.30.2 (Code 63) erhöht. Verbrauchs-, Durchschnitts- und Reichweitenberechnung bleiben fachlich unverändert.

## Vorherige Version v0.30.1

## Neu in v0.30.1

- HCT-Verbindungsaufbau, Callback-Anmeldung, Serviceinformationen und Callback-Abmeldung laufen über einen eigenen Binder-Arbeitsthread. Ein langsamer oder hängender Herstellerdienst blockiert dadurch nicht mehr den Android-Hauptthread des Launchers.
- Diagnosepuffer von 4.000 auf 1.200 Zeilen begrenzt; Oberfläche, Kopieren und Teilen verwenden höchstens die letzten 600 Zeilen. Einzelne Logzeilen werden bei 1.200 Zeichen gekürzt.
- Unterdrückte, hochfrequente CAN-Mode2-Broadcasts werden weiterhin vollständig ausgewertet, erzeugen aber nicht mehr vorher unnötig Hex-/Diagnosetexte.
- Vom DAB-Senderbild wird nur die auf höchstens 256 Pixel verkleinerte sichtbare Fassung stark gehalten. Das möglicherweise große Originalbild wird lediglich schwach referenziert und kann vom Speicherbereiniger freigegeben werden.
- 22 Testgruppen einschließlich neuer Grenzen für Diagnosepuffer und Zeilenlänge bestanden. Kein Radiotest; ein festhängender Herstellerdienst kann weiterhin seinen eigenen CAN-Arbeitsthread blockieren, aber nicht mehr direkt die Launcher-Oberfläche.
- Verbrauch, Reichweite, Karten, DAB-Autostart sowie alle Änderungen aus v0.30.0 bleiben unverändert.

## Neu in v0.30.0

- Die Kachel „Aktuell“ zeigt hinter dem Zahlenwert nur noch `l`, genau wie die Durchschnittskachel. Die zugrunde liegende Berechnung und die Umschaltung zwischen Stand- und Fahrwert bleiben unverändert; geändert wurde ausschließlich der sichtbare Einheitentext.
- Alle Änderungen aus v0.29.9 bleiben erhalten.

## Neu in v0.29.9

- Gültige langsame OBD-Runden bis 10 Sekunden werden mitgezählt (vier Antworten bis je 1,5 Sekunden plus Poll-Pause). Größere Pausen bleiben ausgeschlossen; nach NO DATA beginnt die Zeitbasis neu, ohne fehlende Daten rückwirkend zu erfinden.
- Neueres frisches CAN-Drehzahlsignal 0 blendet den Momentanverbrauch aus, auch wenn OBD noch den letzten Standgaswert enthält. Ohne neues Motor-aus-Signal bleibt nur der bisherige Daten-Timeout als Absicherung. Durchschnitt und Reichweite bleiben erhalten.
- SCREEN_OFF gibt den DAB-Autostart für den nächsten Bildschirm-/Standby-Zyklus wieder frei. SCREEN_ON bzw. Rückkehr zum Launcher prüft erneut Internet, startet einmal und kehrt nach drei Sekunden zurück. Der Hintergrunddienst beobachtet die Bildschirmereignisse auch bei zerstörter Hauptseite. Voraussetzung: Die Radio-Firmware sendet die Android-Bildschirmereignisse; keine garantierte Erkennung jeder OEM-Zündungsart.
- Zwei Testvergleiche lehnen NaN/Infinity jetzt ausdrücklich ab; Regressionen für langsame Abfragen, CAN-Motor-aus und Standby hinzugefügt.
- MAP-/Drehzahl-/Temperatur-Schätzung einschließlich ihrer bisherigen Grenzen unverändert. Durchschnitt ab insgesamt 1 km, Speicherung und Glättung bleiben erhalten.
- 21 Testgruppen und Syntaxaudit, kein erfolgreicher Android-Build oder Radiotest.

## Neu in v0.29.8

- Startgrenze der Durchschnittskachel von 5 auf 1 km insgesamt gesammelter OBD-Strecke reduziert. Diagnose und Grenztests angepasst. Keine neue Wartezeit nach Neustart, wenn die gespeicherte Grundlage bereits mindestens 1 km enthält.
- Anzeigeglättung, Verbrauchsspeicherung und vollständiges Mitzählen des Standverbrauchs unverändert. 1 km ist eine Anzeigeschwelle, keine Zusicherung für warmen Motor oder präzise Verbrauchsschätzung.
- 21 Java-Testgruppen/Quelltextaudit. Kein APK-Build oder Radiotest durchgeführt.

## Neu in v0.29.7

- Durchschnittskachel zeigt erst ab 5 km insgesamt gesammelter OBD-Strecke einen Wert. Die Grenze gilt über Neustarts hinweg, nicht pro Fahrt. Darunter --, auch bei altem gespeicherten Durchschnitt unter 5 km. Keine erfundenen Kilometer/Startliter.
- Reine Anzeigeglättung mit 15 Sekunden Zeitkonstante. Reale Standverbräuche zählen unverändert mit und können den Durchschnitt weiterhin erhöhen. Keine künstliche Obergrenze für den Anstieg. Nach Reset verschwindet der alte Wert.
- Diagnose ergänzt gesammelte Kilometer (3 Nachkommastellen), Kraftstoff (4 Nachkommastellen), Startphase und Summenquelle. Diagnose-Durchschnitt bleibt ungeglätteter Rohwert; Reichweitenbasis unverändert, Reichweitenanzeige weiterhin separat geglättet.
- Bestehende Speicherung der Liter/Kilometer gemeinsam in einem Preferences-Update beibehalten. Tests überprüfen Wiederherstellung; tatsächliche Radiowerte vor/nach Neustart sind noch zu vergleichen. Keine Änderung an Verbrauchsschätzung oder Momentananzeige.
- 21 Java-Testgruppen inklusive Quelltextaudit. Kein Android-APK-Build oder Radiotest durchgeführt.

## Neu in v0.29.6

- Nur AKTUELL/Momentanverbrauch: bei frischer OBD-Drehzahl größer 0 Verbrauch anzeigen, bei Drehzahl 0 oder unbekannter Drehzahl --. Gilt sowohl im Stand als auch bei rollendem Fahrzeug.
- Unter 3 km/h l/h, darüber l/100 km. Veraltete Proben nach 15 Sekunden ausgeblendet. Diagnose nutzt dieselbe Anzeigeprüfung.
- Durchschnitt, Tank, Restkilometer, Speicherung und Reichweitenglättung unverändert.
- 20 Java-Testgruppen inklusive Quelltextaudit; kein APK-Build oder Radiotest durchgeführt.

## Neu in v0.29.5

- Restkilometer-Anzeige startet mit gespeichertem Anzeigewert. Abweichungen über 3 km werden nach 5 Sekunden in gleicher Richtung mit höchstens 2 km/s übernommen. Normales Countdown-Verhalten bleibt direkt.
- Niedrige berechnete Reichweite bis 20 km sofort sichtbar. Kein Sprung nach langen Datenpausen: zeitlicher Glättungsschritt auf 2 Sekunden begrenzt. Fehlende Messwerte bestätigen keine Änderung.
- Rein visuelle Glättung nach der Berechnung im Hintergrunddienst. CAN-Tank, OBD-Durchschnitt, Verbrauchsspeicherung und Nachtank-Erkennung unverändert. Auch bestätigtes Nachtanken wird allmählich angezeigt.
- 20 Java-Tests inklusive Quelltextaudit; kein Android-APK-Build oder Radiotest durchgeführt.

## Neu in v0.29.4

- Momentanverbrauch unter 3 km/h bei laufendem Motor als l/h, ab 3 km/h als l/100 km. Anzeige und Diagnose verwenden dieselbe Formatierung.
- Frische OBD-Drehzahl, Geschwindigkeit und geschätzte Liter pro Stunde werden gemeinsam übernommen. Motor aus, fehlende Werte oder mehr als 15 Sekunden alte Proben: --. Keine gespeicherten Werte als Live-Standverbrauch.
- Durchschnitt, Speicherung und Reichweitenberechnung unverändert; Standverbrauch wird weiterhin im Durchschnitt berücksichtigt. Kachelgröße bleibt gleich, vorhandene automatische Textgröße bleibt aktiv.
- 19 Java-Tests/Quelltextaudit. Kein APK-Build oder Test am Radio durchgeführt.

## Neu in v0.29.3

- Bei aktiviertem DAB-Autostart wird immer auf INTERNET + VALIDATED am Android-Standardnetz gewartet. Eine bloße SIM-/WLAN-Verbindung genügt nicht.
- Prüfung alle 2 Sekunden ohne blockierendes Warten und ohne eigene Netzwerkanfragen. Ohne Internet kein DAB-Start, kein Timeout mit Offline-Start.
- Während eine andere App/Einstellungen vorne ist, pausiert das Warten; bei Rückkehr zum Launcher geht es weiter. Ausschalten des Autostarts beendet das Warten beim nächsten Check.
- Erst bestätigtes Internet verbraucht den einmaligen Startversuch pro Prozess. Danach wie bisher DABdream öffnen und nach 3 Sekunden zum Launcher zurückkehren.
- Android-Validierung garantiert nicht die Erreichbarkeit eines bestimmten Websenders. Meldet die Radio-Firmware trotz funktionierendem Internet nie VALIDATED, bleibt der Autostart wartend; dies muss auf dem Radio geprüft werden.
- Größeres Logo und alle Menü-/CAN-/OBD-Änderungen erhalten. 18 Java-Tests/Quelltextaudit; kein APK-Build oder Radiotest.

## Neu in v0.29.2

- DAB-Logo von 114 auf 128 dp Höhe vergrößert (rund 12 %), zusätzlich 6 dp Abstand oben.
- FIT_CENTER erhält das gesamte Bild ohne Zuschneiden. Platz bleibt auch bei fehlendem Logo reserviert.
- Bedientasten behalten ihre feste Höhe unten. Der verbleibende Metadatenbereich wird 20 dp kleiner; dynamische Schriftgrößen und feste Zeilenlimits bleiben aktiv, sehr lange Texte können weiterhin mit Auslassung enden.
- DAB-Autostart, Menü sowie CAN-/OBD-Berechnung und Speicherung unverändert.
- 18 Java-Tests/Quelltextaudit; kein APK-Build oder Radiotest.

## Neu in v0.29.1

- Einstellungen → Darstellung & Medien: „DABdream beim Launcher-Start öffnen und zurückkehren“, standardmäßig AUS, Auswahl gespeichert.
- Ein Startversuch pro Launcher-Prozess; nach 3 Sekunden einmalige Rückkehr zum bestehenden Launcher. Kein Start bei jeder Rückkehr von Apps/Einstellungen, keine Wiederholung bei Skin-Neuerstellung.
- Aktivieren und zum Launcher zurückkehren startet DABdream, sofern in diesem Prozess noch kein automatischer Versuch erfolgte. Neustart des Launcher-Prozesses erlaubt einen neuen Versuch. Standby ohne Prozessneustart löst keinen erneuten Start aus.
- Separaten Autostart in DABdream deaktivieren; automatische Wiedergabe wird von DABdream gesteuert. Die App wird nicht beendet. Manueller DAB-Dockbutton bleibt unverändert.
- Nicht installierte/nicht startbare App wird abgefangen; keine wiederholten Startversuche. Rückkehranforderung kann vom Radio/Android blockiert werden; kein Radiotest und kein APK-Build durchgeführt.
- 18 Java-Tests/Quelltextaudit einschließlich Startschleifenschutz; CAN-/OBD-Speicherung und Reichweitenberechnung unverändert.

## Neu in v0.29.0

- Hauptmenü auf fünf Bereiche reduziert: Karte & GPS, Tank & Verbrauch, OBD-Verbindung, Darstellung & Medien, System & Diagnose.
- Alle bisherigen Einstellungsseiten, Berechtigungen, Android-Einstellungen, Diagnose und Versionsinfo bleiben erreichbar. Keine neue App-Auswahl eingeführt.
- Zurück im Untermenü führt zur Übersicht; Zurück in der Übersicht zum Launcher. Untermenü bleibt nach Android-Neuerstellung oder Skin-Wechsel erhalten.
- Lange Menübeschreibungen dürfen umbrechen; Kategorien haben flexible Höhe und bleiben scrollbar.
- Reichweiten-Hilfetext und Quellenanzeige auf den gespeicherten OBD-Durchschnitt aus v0.28.3 angepasst, keine Berechnungs-/Speicheränderung.
- Java-Tests/Quelltextaudit; APK-Build und Radiotest stehen aus.

## Neu in v0.28.3

- Im Modus CAN + OBD2 wird der gespeicherte kumulative OBD-Durchschnitt auch für die Reichweite weiterverwendet, ohne auf CAN-Grundverbrauch umzuschalten.
- Tank und letzte Reichweite bleiben bei fehlenden CAN-Daten gespeichert sichtbar. Gespeicherte Werte werden nicht als frische Messungen ausgegeben; ohne neue Fahrdaten wird nichts weiter integriert.
- Ohne jemals brauchbaren OBD-Durchschnitt pausiert die kombinierte Berechnung. Eine vorhandene letzte Reichweitenanzeige bleibt erhalten, sonst erscheint --. Nur-CAN funktioniert unverändert.
- Der bisherige Durchschnitt und seine Liter-/Kilometer-Summen bleiben nach Neustart erhalten; Zurücksetzen löscht sie weiterhin absichtlich.
- Diagnose unterscheidet frischen und gespeicherten OBD-Durchschnitt als Reichweitenbasis.
- Update über dieselbe App installieren; App-Daten löschen oder Deinstallation entfernt gespeicherte Daten. Abrupter Stromverlust kann den letzten noch nicht gespeicherten Abschnitt verlieren.
- Java-Tests/Quelltextaudit; kein APK-/Gerätetest.

## Neu in v0.28.2

- Die Verbrauchskachel zeigt den gespeicherten OBD-Durchschnitt bereits ohne neue Adapterdaten an.
- Laufende Berechnung und gespeicherte Liter/Kilometer werden weiterverwendet, nicht beim Neustart zurückgesetzt.
- Anzeige-Fallback ist strikt getrennt von frischen VehicleRepository-Messwerten; Reichweite und OBD-Diagnose behandeln den gespeicherten Wert nicht als neue Messung.
- Manuelles Zurücksetzen entfernt auch den Anzeige-Fallback, statt einen alten RAM-Wert weiterzuzeigen.
- Alle UI-/Kartenänderungen aus v0.28.0 und v0.28.1 bleiben erhalten.
- Java-Tests/Quelltextaudit; kein APK-/Gerätetest.

## Neu in v0.28.1

- „KARTE“-Button oben rechts ausgeblendet; Offline-Kartenverwaltung bleibt im Einstellungsmenü.
- Quellenbalken wird bei jedem Kartenmoduswechsel fünf Sekunden angezeigt, danach ausgeblendet.
- Eigenes ⓘ unten links (6 dp Randabstand, 40-dp-Touchfläche) öffnet Quellen/Lizenzen mit anklickbaren Links; auch offline erreichbar.
- Doppelte MapLibre-Quellenbedienung deaktiviert. Alle Änderungen aus v0.28.0 bleiben erhalten.
- Java-Tests/Quelltextaudit geprüft; kein APK-/Gerätetest.

## Neu in v0.28.0

- Tank/Reichweite, Aktuell/Durchschnitt und Gurt/Scheibenwasser bleiben jeweils eine Kachel, mit zwei gleich breiten unsichtbaren Spalten und zentrierten Überschriften/Werten.
- Gemeinsame Schriftgrößen für Datenkacheln: Überschriften bis 10 sp, Werte bis 18 sp; lange Texte werden bei Platzbedarf kleiner.
- Verbrauchswerte zeigen nur „l“ ohne „/100km“; die Berechnung bleibt l/100 km, nicht l/h.
- DABdream+-Überschrift entfernt, Logo von 80 auf 114 dp vergrößert. Die entfernten 34 dp werden ausschließlich dem Logo zugewiesen, der Metadatenplatz bleibt erhalten.
- Titel (bis vier Zeilen) und Interpret (bis zwei Zeilen) bleiben dynamisch verkleinert in begrenzten Bereichen; die 48-dp-Bedientasten verschieben sich nicht. Ohne Bild bleibt die Logofläche reserviert. Extrem lange Texte können am kleinsten Schriftgrad weiterhin mit Auslassung enden.
- GPS, Karten, CAN/OBD, Reichweitenberechnung und Hintergrunddienst unverändert.

Prüfung: Java-Logiktests und Quelltext-/Layoutstruktur-Audit. Kein Android-Geräte- oder visueller Laufzeittest; ein vollständiger APK-Build benötigt die Android-/Gradle-Umgebung.

## Neu in v0.27.1

Das vorhandene Diagnose-/Debugmenü enthält jetzt einen eigenen OBD2-Adapterblock:
ausgewählte Adapteradresse, aktive Verbindung, Bluetooth-Berechtigung, Poll-Thread,
Status, Anzahl vollständiger Abfragerunden, letzte Antwort, letzte brauchbare Probe,
RPM/Speed/MAP/IAT/Kraftstoffrate, Momentan-/Durchschnittsverbrauch und aktuell
verwendete Reichweitenquelle. „Verbunden“ alleine bedeutet nicht „brauchbare Daten“;
NO DATA zeigt sofort fehlende nutzbare Verbrauchsdaten. Bei fehlenden Antworten
wird die letzte Probe spätestens nach 15 Sekunden als veraltet ausgewiesen.
Die bestehende Kopieren-/Teilen-Funktion nimmt diesen Block mit in den Debug-Log.

17 lokale Testsuiten bestanden, inklusive Diagnoseprüfungen am simulierten Adapter.
Kein vollständiger Android-Build/Gerätetest möglich (Gradle-Download nicht erreichbar).

## Neu in v0.27.0

Basis ist der bestehende Polo Launcher v0.26.1 (gleicher Paketname und vorhandene
Einstellungen), ergänzt um die Verbrauchs-/OBD-Funktionen aus PoloLauncher2 v0.9.0.
Die separate PoloLauncher2-App wird weder ersetzt noch automatisch umkonfiguriert.

- Links: Uhr/Datum, Tank + Reichweite, aktueller + durchschnittlicher OBD-Verbrauch,
  Bordspannung und Außentemperatur.
- Rechts: DAB-Kachel unverändert; darunter Gurt und Scheibenwasser gemeinsam in
  der kompakten Statuskachel. Beide Statuswerte haben dynamische Schriftgrößen.
- Einstellungen → Tank und Reichweite: Auswahl über den Schalter „CAN + OBD2“.
  Aus bedeutet nur CAN-Lernwert/Grundverbrauch; an bevorzugt einen plausiblen,
  frischen OBD-Durchschnitt und fällt bei fehlenden OBD-Daten auf CAN zurück.
  Standard ist CAN + OBD2, wie in der bereitgestellten v0.9.0.
- Einstellungen → Verbrauch / OBD2-Adapter: Bluetooth-Berechtigung, Auswahl eines
  bereits gekoppelten ELM327-SPP-Adapters, Status und bestätigtes Zurücksetzen des
  gespeicherten OBD-Durchschnitts. Bitte den Adapter in dieser App einmal auswählen;
  die private Adapterauswahl der separaten PoloLauncher2-App wird nicht übernommen.

Die OBD-Berechnung wurde mit 1,198 L Hubraum und 0,78 angenommenem volumetrischen
Wirkungsgrad aus der gelieferten Version übernommen (Polo 1.2 CGPB, Benziner).
Sie schätzt Verbrauch aus MAP, RPM, Ansauglufttemperatur und Geschwindigkeit.
Sie ist keine direkte Kraftstoffmessung und nicht für einen anderen Motor/TDI
kalibriert. Tank und Kilometerstand kommen weiterhin von CAN. Der OBD-Durchschnitt
wird aus aufsummierten Litern und Kilometern gebildet und über Neustarts gespeichert.
Im Stand bzw. ohne aktuelle Daten zeigt der Momentanverbrauch „--“; der zuletzt
angezeigte Durchschnitt bleibt während der laufenden Hauptseite erhalten.

CAN-Lernfunktion und OBD-Verbindung laufen über den vorhandenen Hintergrunddienst
auch bei anderen Apps im Vordergrund. CAN lernt unabhängig weiter, wenn OBD für
die Reichweite ausgewählt ist. „Nur CAN“ schaltet lediglich die Berechnungsquelle
um, nicht die OBD-Verbrauchskachel oder die Adapterverbindung ab.
Tankliter/Restkilometer bleiben ohne CAN und nach Neustarts wie in v0.26.1 sichtbar.
GPS, Karten, Zoom, DAB-Bedienung, Skins und horizontales Wischen bleiben erhalten.

Zusätzliche OBD-Absicherungen: ungültige Geschwindigkeit verwirft die ganze
Durchschnittsprobe, monotone Zeitmessung, kein Aufsummieren einer Verbindungspause,
synchronisierter Durchschnittsreset/-Speicherung, Schließen des fehlgeschlagenen
ersten Sockets vor Kanal-1-Fallback und Wechsel zum neu ausgewählten Adapter.

### Test auf dem Radio

1. Projekt wie gewohnt bauen und über die bestehende App installieren.
2. Unter Verbrauch / OBD2-Adapter Bluetooth erlauben und gekoppelten Adapter wählen.
3. Bei laufendem Motor/Fahrt Momentan- und Durchschnittskachel prüfen.
4. Unter Tank und Reichweite beide Modi testen; bei OBD-Verbindungsverlust den
   CAN-Rückfall prüfen. Ohne CAN müssen die letzten Tank-/Reichweitenanzeigen stehen bleiben.
5. Bei Problemen den Diagnose-Log senden (enthält jetzt OBD-Status und Reichweitenmodus).

Lokale Tests: siehe TESTBERICHT_v0.27.0.md. Kein vollständiger Android-Build oder
Gerätetest in dieser Umgebung; der Gradle-Download ist hier nicht erreichbar.

## Neu in v0.26.1

Die letzten gültigen Tankliter und Restkilometer werden separat als reine
Anzeigewerte gespeichert. Bei fehlendem CAN-Signal (beispielsweise Zündung aus)
bleiben beide Werte stehen. Auch beim nächsten Start werden sie direkt aus
dem Speicher angezeigt, ohne zusätzliches Symbol oder Statuskennzeichnung.
Neue gültige Daten aktualisieren die Anzeige wieder. Solange noch nie Werte
vorhanden waren, bleibt die Anzeige bei „--“.

Der Anzeigespeicher wird niemals als Messwert in die Verbrauchslernung eingespeist.
Ohne frische CAN-Daten pausiert die Lernfunktion weiterhin. Der Hintergrunddienst
und die GPS-Nachführung aus v0.26.0 bleiben erhalten. Ein besonderer Wiederanlauf
über OEM-Aufwachsignale ist in dieser Version nicht hinzugekommen.

Test auf dem Radio: gültige Werte anzeigen lassen, Zündung ausschalten und prüfen,
ob die Tankkachel stehen bleibt. Nach einem Neustart sollten die gespeicherten
Werte wieder erscheinen und sich mit neuen CAN-Daten aktualisieren.

## Neu in v0.26.0

Ein Vordergrunddienst übernimmt CAN-Erfassung, Reichweitenberechnung und
Verbrauchslernung, auch während Maps, CarPlay oder DABdream im Vordergrund ist.
Der Dienst wird automatisch beim Öffnen des Launchers gestartet; die Hauptseite
liest seine Ergebnisse nur noch. Es gibt genau einen Schreiber der Berechnungswerte.
Diagnose: „Verbrauchs-Hintergrunddienst: LÄUFT“.

Der Dienst hat eine leise Systembenachrichtigung und startet keine Oberfläche
von selbst. Ein normaler App-Wechsel beendet ihn nicht. Bei einem vom System
veranlassten Neustart werden die gespeicherten Werte geladen. Ein erzwungener
App-Stopp, das Abschalten des Radios oder aggressive OEM-Energiesparfunktionen
können auch diesen Dienst unterbrechen. Ohne frische CAN-Daten wird nicht gelernt;
die letzte Anzeige bleibt seit v0.26.1 erhalten (ohne weitere Lernproben).
Kein zusätzlicher GPS-Hintergrunddienst: der weiche GPS-Stand aus v0.25.1 bleibt.

Test auf dem Radio: Launcher öffnen, in der Diagnose Dienststatus prüfen,
anschließend eine andere App während einer Fahrt nutzen und zum Launcher
zurückkehren. Bei Problemen Diagnose-Log senden. Lokale Logik-/Quelltexttests
ersetzen keinen Android-Build oder Gerätetest.

## Neu in v0.25.1

GPS-Vorausberechnung entfernt und die weicheren Positionsübergänge aus v0.22.2
wiederhergestellt. Details und Testhinweise: GPS_FIX_0.25.1.md.
Die Verbrauchslernung und alle übrigen Funktionen aus v0.25.0 bleiben erhalten.

## Neu in v0.25.0

Die Restkilometer nutzen jetzt einen automatisch gelernten Durchschnittsverbrauch.
Automatik ist beim ersten Start dieser Funktion aktiviert. Ohne Lernwert gilt weiterhin
der einstellbare Grundverbrauch (standardmäßig 6,8 l/100 km).

Einstellungen → Tank und Reichweite:

- Automatische Verbrauchslernung ein-/ausschalten.
- Aktuellen wirksamen Verbrauch, Status und Anzahl bestätigter Lernabschnitte ansehen.
- Grundverbrauch für Start/manuellen Betrieb und Reserve einstellen.
- Lernwerte über einen bestätigten Reset zurücksetzen, ohne Reserve/Grundverbrauch zu löschen.

Der Lerner verwendet echte CAN-Tankliter und Kilometerstand, nicht die errechnete
Restmenge. Tankwerte werden 30 Sekunden stabilisiert. Ein unabhängiger Abschnitt braucht
mindestens 100 km Strecke und 5 L Tankabnahme. Aus Literverlust/Strecke ergibt sich der
Abschnittsverbrauch; er geht mit 25 % in den bisherigen Wert ein. Eine einzelne Aktualisierung
ändert ihn um maximal 0,5 l/100 km. Plausibler Bereich: 3–20 l/100 km.
Das ist ein vorsichtig gelernter Langzeitwert, keine Momentanverbrauchsanzeige.

Erkanntes Nachtanken (mindestens 3 L stabiler Anstieg gegenüber der letzten niedrigen
Bestätigung) beginnt einen neuen Abschnitt und behält den Lernwert. Kurze Schwankungen,
ungültige Kilometer, fehlende frische CAN-Daten oder unplausible Tankabfälle werden nicht
als Verbrauch übernommen. Ein konstanter Tankstand erzeugt keine wiederholten Lernproben.

Lernwert und laufender Abschnitt werden über Neustarts gespeichert. Beim Aus-/Einschalten
beginnt ein neuer Abschnitt; der letzte Lernwert bleibt erhalten. Ganze Liter, Sensorabweichungen
und insbesondere kleine oder während ausgeschaltetem Radio nicht erkannte Nachfüllmengen
begrenzen die Genauigkeit. Die Reichweite bleibt ausdrücklich eine Schätzung.

Alle bisherigen UI-/GPS-/CAN-/Kartenfunktionen und Fehlerbereinigungen bleiben enthalten.
14 lokale Testsuiten bestehen; vollständiger Android-Build hier weiterhin am nicht erreichbaren
Gradle-Download gescheitert. Details: `TESTBERICHT_v0.25.0.md`.

## Änderungen in v0.24.0

- Online-Ladezeitlimit wird während des Ladens nur einmal gesetzt, nicht bei jedem Dashboard-Tick verlängert.
- Nachtanken wird auch bei schrittweise steigenden Literwerten erkannt; 30-Sekunden-Bestätigung bleibt bestehen.
- Ein schräg begonnener Seiten-Swipe stellt den vorherigen GPS-Nachführungszustand beider Karten wieder her.
- Ungenaue/ungültige erste GPS-Messung wird nicht angezeigt; erst ein brauchbarer Fix initialisiert die Position.
- Senderlogos werden pro Bitmap-Quelle nur einmal verkleinert; fehlende Bilder entfernen das vorherige Logo.
- Serieller CAN-Rahmenparser arbeitet nach Länge und Prüfsumme. 0x2E innerhalb der Nutzdaten/Prüfsumme ist erlaubt.
- Bestätigte VW/HCT-Datenwege bleiben erhalten. Unbekannte Transporte bleiben Diagnose-only; unbestätigte Toyota-Zuordnungen entfernt.
- Broadcast-Werte und Zeitstempel werden gemeinsam unter der Repository-Sperre aktualisiert.
- CAN-Service-Bindings bleiben bei normalem Disconnect erhalten und werden bei Null-Binding/Binding-Died sauber freigegeben.
- Fehlgeschlagene Karten-Wiederherstellungen behalten die Sicherung auch beim nächsten Versuch.
- Straßenkorrektur-Schalter wird nach Rückkehr aus der Offline-Kartenverwaltung neu geprüft.
- Unbenutzte VW-Roh-CAN-Klasse und zwei unbenutzte Diagnosefelder entfernt.
- Zusätzlich: Seitenanimation wird durch Layout-Updates nicht mehr vorzeitig auf die Zielseite gesetzt; Windows-Buildskript korrigiert.

## Prüfung und Grenzen

`tests/run-tests.sh` führt 14 Testsuiten mit JDK 17+ aus. Unter Windows alternativ:
`powershell -ExecutionPolicy Bypass -File tests/run-tests.ps1`.
Die Tests verwenden die Produktionsklassen und bei Android-Abhängigkeiten ausdrücklich
vereinfachte Test-Doubles aus `tests/stubs`. Diese gehören nicht zur APK.
`TESTBERICHT_v0.24.0.md` dokumentiert die bisherigen Review-Punkte; der aktuelle Bericht
`TESTBERICHT_v0.25.0.md` ergänzt Lernfunktion und Integration.
Die Java-Quellen wurden außerdem vollständig auf Syntax und ausgewählte Verdrahtungen geprüft.
Das ist kein vollständiger Android-Build und kein Radio-/Fahrtest.
Ein Gradle-Build war hier wegen nicht erreichbarem `services.gradle.org` nicht möglich.

Optik, Skins, Fahrtrichtungsdrehung, GPS-Vorlauf, Datums-Autosizing, 3D, Online-/Offline-Karte
und die Reichweiten-Kachel bleiben enthalten. Bestehende Einstellungen bleiben erhalten.

## Funktionsstand aus v0.23.1

Neu: Eine begrenzte GPS-Vorausberechnung verringert das Hinterherlaufen der
Karte. Sie wird einmal im gemeinsamen GPS-Datenweg vor dem Straßenabgleich
angewandt, ohne den rohen GPS-Fix oder die Ortsanalyse zu verändern.
Nur mit zwei passenden GPS-Messungen, brauchbarer Geschwindigkeit/Fahrtrichtung,
Genauigkeit bis 25 m und ohne Empfangslücke. Bei stärkeren Richtungswechseln,
deutlichem Bremsen, alten Messungen oder verworfenen GPS-Sprüngen kein Vorlauf.
Vorhersage maximal 1,2 s und 25 m; Online-/Offline-Animationen verkürzt.
Die Reichweiten- und Datumsfunktionen aus den Vorversionen bleiben erhalten.

Tankliter und geschätzte Restkilometer stehen nebeneinander in einer gemeinsamen
Kachel. Der CAN-Kilometerstand zählt zwischen ganzen Literwerten herunter.
Einstellungen → Tank und Reichweite: Standardverbrauch 6,8 l/100 km,
Reserve 2,0 L; beide einstellbar. Seit v0.25.0 ist die automatische Verbrauchslernung enthalten.
Die Schätzung bleibt über Neustarts erhalten, wird aber ohne frische Tank-/
Kilometerdaten nicht angezeigt. Kleine Tankschwankungen setzen den Countdown
nicht zurück. Größere Tankanstiege werden nach 30 Sekunden als Nachtanken
gewertet; kleinere Nachfüllmengen sind nicht zuverlässig von Messrauschen
zu unterscheiden. Abgleich nach unten erfolgt gedämpft.

Neu in v0.22.2: Das Datum in der Uhrkachel bleibt mittig und einzeilig.
Die Schrift passt sich automatisch an die verfügbare Breite und Höhe an
(6–12 sp). Uhrzeit und Kachelgröße bleiben unverändert.

Korrektur in v0.22.1: Ein zeitversetzt eintreffender Straßenabgleich kann die
Zoomanimation nicht mehr abbrechen und die Karte auf Deutschlandansicht lassen.
Position, Fahrtrichtung und Fahrzoom werden gemeinsam an die Online-Kamera
übergeben; die Offline-Kamera besitzt zusätzlich eine feste Zoomsicherung.

## Neu in v0.22.0

- Unter **Karte → GPS-Position – Test** kann „Punkt auf Straße einrasten“ getrennt aktiviert werden. Der Test ist nach Installation standardmäßig ausgeschaltet.
- Die Korrektur durchsucht die ausgewählte Mapsforge-Offline-Region außerhalb des UI-Threads nach befahrbaren Straßen. Sie berücksichtigt Abstand, GPS-Genauigkeit, Fahrtrichtung, Straßenrichtung und unplausible Positionswechsel.
- Nur die angezeigte Kameraposition wird auf den plausiblen Straßenabschnitt projiziert. Rohe GPS-Daten, Ortsanalyse und Kursberechnung bleiben unverändert; ohne sicheren Treffer wird automatisch die normale geglättete GPS-Position verwendet.
- Online- und Offline-Karte erhalten denselben korrigierten Punkt. Ohne ausgewählte Offline-Region bleibt der Schalter deaktiviert.

## Neu in v0.21.0

- Im Kartenmenü lassen sich die Zielwerte für **Innerorts** und **Außerorts** getrennt einstellen. Die Voreinstellungen sind Zoom 15 innerorts und Zoom 13 außerorts.
- Die beiden Regler sind bei ausgeschaltetem ortsabhängigem Zoom sichtbar, aber deaktiviert. Ein gemeinsamer Reset stellt Grundzoom 15, innerorts 15 und außerorts 13 wieder her.
- Solange die Offline-Region noch kein Gebiet sicher erkannt hat, verwendet die GPS-Nachführung den Grundzoom. Die Sicherheitsgrenze von mindestens Zoom 12 bleibt dabei aktiv.
- Die Diagnose zeigt Grundzoom, Innerorts- und Außerorts-Wert separat an. Online- und Offline-Karte verwenden dieselben gespeicherten Einstellungen.

## Neu in v0.20.2

- Bei aktiver GPS-Nachführung unterschreiten Online- und Offline-Karte nie Zoom 12. Die Grenze greift bereits vor dem ersten GPS-Fix, damit beim Start keine Deutschlandansicht erscheint.
- Außerhalb einer Ortschaft bleibt das normale Ziel weiterhin Grundzoom minus 1 (bei Standard 15 also Zoom 14); die neue Grenze ist nur das Sicherheitsnetz für ungewöhnlich niedrige oder noch nicht sauber initialisierte Werte.
- Wer die Karte bewusst verschiebt und damit die GPS-Nachführung verlässt, kann weiterhin manuell bis Zoom 5 herauszoomen. Beim erneuten Zentrieren wird die Sicherheitsgrenze wieder angewendet.

## Korrektur in v0.20.1

- Zwei Aufrufe in der Offline-Karte übergeben `setVisibility()` jetzt korrekt eine Android-Sichtbarkeitskonstante statt eines Boolean-Werts. Damit ist der von Android Studio gemeldete Typfehler behoben und die Marker-Sichtbarkeit folgt weiterhin der Kartenansicht.
- Alle Karten-, DAB-, CAN- und Einstellungsfunktionen aus v0.20.0 bleiben unverändert erhalten.

## Neu in v0.20.0

- Die Hauptseite der Einstellungen ist in Status, Darstellung, Karte, System sowie Hilfe und Info gegliedert. Standard-Launcher, DAB-Medienzugriff und GPS werden oben als echte grüne bzw. orange Statuskarten angezeigt. Die frühere falsche Hervorhebung anhand des Wortes „Standard“ ist entfernt.
- Das Kartenmenü zeigt den aktiven Stil, Fahrtrichtung, Zoom und Offline-Region kompakt an. Neu sind ein Reset auf Zoom 15 und eine sichtbare Warnung, falls der automatische Zoom ohne ausgewählte Offline-Region aktiviert ist. Liberty, Bright und 3D bleiben erhalten.
- Die Offline-Kartenverwaltung zeigt belegten und freien Speicher. Regionen können ausgewählt, sicher neu geladen oder nach Bestätigung gelöscht werden; ein laufender Download kann abgebrochen werden. Beim erneuten Laden bleibt die vorhandene Karte bis zur vollständigen Prüfung der neuen Datei erhalten.
- Die Diagnose lässt sich direkt in die Zwischenablage kopieren oder über eine installierte App teilen.

## Neu in v0.19.1

- Die linke Fahrzeugkachel heißt jetzt **SCHEIBENWASSER** statt WISCHWASSER. Status und bestätigte CAN-Auswertung bleiben unverändert.
- Der Online-3D-Stil mit Liberty-Gebäuden und 45°-Neigung sowie sämtliche Funktionen aus v0.19.0 bleiben erhalten.

## Neu in v0.19.0

- GPS-Fixes mit mindestens 100 m gemeldeter Ungenauigkeit bewegen die Kamera nicht mehr. Die Kursberechnung funktioniert auf OEM-Radios auch dann, wenn Android nur normale GPS-Zeitstempel statt `elapsedRealtimeNanos` liefert.
- Ein horizontaler Ein-Finger-Wisch wechselt nun auf der gesamten Kartenkachel zur App-Seite; Tippen, vertikale Bewegung und Zwei-Finger-Zoom bleiben getrennt. Der Online-Karten-Timeout läuft nur im aktiven Vordergrund.
- Heruntergeladene Offline-Karten werden vor der Aktivierung mit Mapsforge geprüft und sicher ersetzt. Eine bereits funktionierende Karte bleibt bis zum erfolgreichen Wechsel erhalten; auch der erneute Download derselben Region wird erkannt.
- Generische CAN-Namen unterscheiden Tank-Prozent und Liter sowie Kühlmittel- und Außentemperatur korrekt und akzeptieren nur plausible Wertebereiche.
- DAB-Protokolle stammen nur noch von DABdream+. Eine MediaSession ohne Bild löscht kein bereits erkanntes Senderlogo. Der Medienzugriffs-Status aktualisiert sich nach der Rückkehr in die Einstellungen.
- Defekte App-Namen können die App-Seiten nicht mehr abbrechen. Der OEM-CAN-Poller verhindert durch eine Laufgeneration doppelte Schleifen nach schnellem Stoppen und Starten.
- Falls der Mapsforge-Grundrenderer nicht erstellt werden kann, versorgt eine unabhängige GPS-Quelle die Online-Karte. Nach wiederholten OpenFreeMap-Fehlern steigt der Neuversuchsabstand von 30 Sekunden schrittweise bis höchstens fünf Minuten.

Die folgenden Abschnitte dokumentieren die Entwicklung der vorherigen Versionen.

## Neu in v0.18.0

- Die Online-Karte versucht nach einem Ladefehler automatisch erneut zu starten. Bei einer wiederkehrenden SIM-/WLAN-Verbindung erfolgt der neue Versuch sofort, ansonsten nach einer kurzen Wartezeit.
- Die unsichtbare Offline-Karte wird nicht länger bei jedem GPS-Fix verschoben oder gedreht. Wiederholte Sichtbarkeitsmeldungen starten keine laufende Drehung mehr neu; beim Wechsel zurück auf offline springt sie auf die aktuelle geglättete Position.
- Eine beschädigte Offline-Kartendatei blockiert nicht mehr automatisch die Online-Karte. Der Launcher startet in diesem Fall einen leeren Grundrenderer für GPS und OpenFreeMap.
- Die CAN-Diagnoseseite baut ihre eigene Verbindung zum Fahrzeugdienst auf und aktualisiert die Anzeige nach dem Verbindungsaufbau erneut.
- Tank, Bordspannung, Außentemperatur und Türstatus besitzen eigene Zeitstempel. Bleibt das jeweilige CAN-Signal aus, werden keine alten Werte oder Türwarnungen unbegrenzt weiter angezeigt.

Die folgenden Abschnitte dokumentieren die Entwicklung der vorherigen Versionen.

## Neu in v0.17.0

- Nur noch zwei Kartenquellen: MapLibre/OpenFreeMap online (bei SIM/WLAN automatisch) und eine heruntergeladene Mapsforge-Karte offline. Die frühere Mapsforge-Online-Rasterkarte mit ihren Kachel-Downloads wurde entfernt. Ohne Internet und ohne ausgewählte Offline-Region ist keine Karte verfügbar; bei Ladefehlern der Onlinekarte übernimmt die Offline-Karte, soweit vorhanden.
- **Ruhige GPS-Nachführung** ist fest aktiviert und aus den Schaltern entfernt. Eine bestehende alte AUS-Einstellung wird ignoriert. Der echte GPS-Fix für den ortsabhängigen Zoom bleibt unverändert.
- **Fahrtrichtung oben** dreht nun auch die Mapsforge-Offline-Karte per nativer Kartenrotation; GPS-Button, rote Türwarnungen und Copyright-Leiste bleiben gerade stehen. Ein einzelner Schalter wirkt auf beide Karten. Ohne brauchbaren Kurs bleibt Norden oben, beim Anhalten bleibt die letzte Richtung erhalten. Die Drehung und Offline-Nachführung während der Fahrt bitte auf dem Radio testen; die Qualität hängt von dessen GPS ab.
- Unter Einstellungen und in der Diagnose sind die beiden Karten und ihre Caches korrekt beschrieben. Vorhandene heruntergeladene Offline-Regionen bleiben erhalten; die App-Installation nutzt denselben Paketnamen.

Die folgenden Abschnitte beschreiben ältere Versionen; die dort erwähnte dritte Kartenquelle und ihre Schalter sind seit v0.17.0 entfernt.


## Neu in v0.16.0

- **Einstellungen → Karte / Zoom:** „Ruhige GPS-Nachführung“ (standardmäßig an) dämpft einzelne Positionssprünge, hält die Kartenzentrierung bei GPS-Rauschen im Stand ruhig und berücksichtigt beim Fahren kurze GPS-Geschwindigkeits-/Kursprognosen. Wirkt auf Mapsforge und MapLibre; die unberührte GPS-Messung dient weiterhin dem ortsabhängigen Zoom. Die Karte kann die Qualität des Radiosensors nicht verbessern und zeigt keine garantierte straßengenaue Position.
- „Fahrtrichtung oben (Online-Vektorkarte)“ (standardmäßig an) dreht die MapLibre-Karte bei brauchbarem GPS-Kurs während der Fahrt; falls Geschwindigkeit/Kurs vom Radio fehlen, kann bei präzisen Positionsänderungen eine Richtung geschätzt werden. Im Stand bleibt die letzte Richtung erhalten; fehlt eine brauchbare Bewegungsrichtung, bleibt Norden oben. Die Mapsforge-Karte bleibt weiterhin Norden oben. Beide Optionen lassen sich unabhängig ausschalten.
- Bei GPS-Updates werden Position und Blickrichtung der Online-Karte gemeinsam über eine lineare Kamerafahrt aktualisiert; die Einstellung der Kartendrehung wird nach der Rückkehr aus dem Kartenmenü übernommen.


## Neu in v0.15.2

- Behebt den Android-Studio-Compilerfehler in `SettlementZoomAnalyzer.java`: Mapsforge erwartet `int`-Kachelkoordinaten; die bereits auf Zoomstufe 14 begrenzten `long`-Werte werden mit `Math.toIntExact` sicher konvertiert. Alle Funktionen aus v0.15.1 bleiben erhalten.

## Neu in v0.15.1

- Ein App-Katalog-Ereignis baut die Launcher-Hauptseite nicht mehr neu auf. Nur wenn sich die Liste installierter/startbarer Apps tatsächlich geändert hat, werden deren separaten Seiten im vorhandenen Launcher aktualisiert; Karte, DAB und CAN-Kacheln bleiben stehen.
- Unveränderte Werte und Texte werden beim regelmäßigen CAN-/DAB-Takt nicht erneut gesetzt. Kurzzeitige Statuslücken der SIM-/WLAN-Verbindung lösen nicht sofort einen Kartenwechsel aus.
- Den `uiMode`-Wechsel, etwa eine Tag-/Nachtmeldung des Radios, behandelt die Hauptseite ohne automatisches Neuanlegen der Activity. Bewusste Änderungen an Skin, Offline-Region und Online-Kartenstil dürfen weiterhin einmalig neu laden. Die Diagnose protokolliert diese Fälle, falls noch ein Aufblinken auftritt.
- Die in v0.15.0 eingeführte optionale MapLibre-Karte und alle bisherigen Funktionen bleiben erhalten. Ein APK-Build muss in Android Studio erfolgen; der erste Build braucht die MapLibre-Abhängigkeit aus Maven Central.

## Neu in v0.15.0

- Unter **Einstellungen → Karte / Zoom → Online-Karte** kann MapLibre mit OpenFreeMap aktiviert werden; standardmäßig bleibt die bisherige Karte ausgewählt. Liberty, Bright und 3D stehen als Online-Styles zur Wahl. Die Startseite wird nach einer Änderung neu geladen.
- Die Online-Vektorkarte folgt denselben GPS-Meldungen und übernimmt den gespeicherten Zoom. Der vorhandene ortsabhängige Zoom nutzt auch hier weiter die ausgewählte Mapsforge-Offline-Region; ohne Region bleibt der manuelle Zoom.
- Wenn die OpenFreeMap-Karte nicht geladen werden kann oder die Internetverbindung fehlt, übernimmt die bisherige Mapsforge-Karte automatisch. Alte Offline-Downloads und ihre Dateiformate bleiben unverändert. Bei Kartenproblemen zuerst im Kartenmenü MapLibre ausschalten, dann die Diagnose prüfen.
- MapLibre und Mapsforge sind getrennte Renderer mit getrennten Caches: Besuchte Ausschnitte sind **kein** verlässlicher vollständiger Offline-Download. 3D zeigt Karten-Gebäude und Perspektive, keine fotorealistischen Google-Maps-Bilder und keine eigene Routenführung.
- Neu ist die Gradle-Abhängigkeit `org.maplibre.gl:android-sdk:11.8.0` aus Maven Central; Android Studio muss sie beim ersten Build laden können. Erst mit der neuen Version auf deinem Android-13-Radio testen, bevor MapLibre dauerhaft aktiviert bleibt.

Testprojekt für Ronnys Android-13-Autoradio. Baut auf v0.13.1 auf; Paketname und Installation bleiben gleich.

## Neu in v0.14.0

- Unter **Einstellungen → Karte / Zoom** kann der automatische ortsabhängige Zoom ein- und ausgeschaltet werden. Auslieferungszustand **aus**. Der Slider legt weiterhin den manuellen Ausgangszoom fest.
- Für die Erkennung muss eine passende Mapsforge-Offline-Region heruntergeladen und ausgewählt sein; das gilt **auch bei aktiver Online-Karte**. Nur die Online-OSM-Kachelbilder und ihr Cache enthalten keine auswertbaren Straßen- und Ortsflächen-Daten. Es erfolgt weder eine Nominatim-Dauerabfrage noch eine Auswertung der gefahrenen Geschwindigkeit als alleinige Orts-Erkennung.
- Im Hintergrund wird höchstens alle acht Sekunden und nach mindestens 55 Metern eine nahe gelegene Kartenkachel aus der installierten Region gelesen; GPS-Fixes mit mehr als 40 m gemeldeter Ungenauigkeit werden nicht zur Orts-Erkennung verwendet. Nur bei einer nahen kartierten Straße und zwei übereinstimmenden Prüfungen schaltet die Ansicht innerhalb kartierter Wohn-/Geschäftsflächen auf Ausgangszoom + 2; auf eindeutigen Feld-/Waldflächen oder Autobahnen auf Ausgangszoom − 1. Unklare Positionen behalten den bisherigen Modus bzw. den manuellen Ausgangszoom. Die Anpassung erfolgt schrittweise. Manuelles Kartenverschieben oder -zoomen pausiert die GPS-Nachführung bis zum Tippen auf **GPS**.
- Wichtig: Kartenflächen entsprechen **nicht** den rechtlichen Grenzen einer geschlossenen Ortschaft. Fehlende oder ungenaue OSM-Daten sind möglich. Dies ist nur eine optionale Darstellungsfunktion, **keine Anzeige gültiger Geschwindigkeitsregeln**. Wenn sich auf dem Radio eine Region nicht auswerten lässt, bleibt der manuelle Zoom aktiv.

## Neu in v0.13.1

- Uhrzeit und Datum in der linken oberen Kachel horizontal mittig ausgerichtet. Die übrigen Kacheln bleiben unverändert.
- Die unter v0.13.0 beschriebene dauerhafte Kachelspeicherung betrifft genau die angezeigte **Online-OpenStreetMap-Karte**. Es werden nur besuchte Ausschnitte vorgehalten, keine vollständigen Regionen. Android kann den Cache entfernen; eine heruntergeladene Offline-Karte wird separat gespeichert.

## Neu in v0.13.0

- Die drei DAB-Steuerknöpfe haben etwas mehr Abstand (8 dp zwischen den Flächen); Logo, Titel und Interpret bleiben unverändert.
- **Einstellungen → Skins / Farben:** Kupfer (Standard bei frischer Installation), Nachtblau, Smaragd und Bordeaux. Die Auswahl bleibt nach einem Neustart erhalten. Der Kupfer-Skin entspricht weiterhin dem bisherigen Design.
- **Einstellungen → Karte / Zoom:** Zoomstufe 5–19 einstellbar. Der Wert wird direkt gespeichert und beim nächsten Öffnen auf Online- und Offline-Karten angewendet. Auch eine per Geste geänderte Zoomstufe wird beim Verlassen des Launchers gespeichert.
- Die bereits vorhandene Online-Kachelspeicherung nutzt nun einen persistenten Mapsforge-Cache. Ein wiederholt betrachteter Kartenausschnitt muss daher nach einem Launcher-Neustart nicht zwangsläufig vollständig erneut geladen werden. Der Cache ist begrenzt und kann von Android geleert werden; neue Gebiete brauchen Internet. Die herunterladbaren Mapsforge-Offline-Regionen bleiben davon unabhängig. Öffentliche OSM-Kacheln werden **nicht** als vollständige Regionen vorab heruntergeladen.

## Neu in v0.12.1

- Die sechs unteren Buttons sind von 76 auf 62 dp Höhe verkleinert, mit 32 statt 40 dp Icons und etwas mehr seitlichem Abstand. Die berührbaren Flächen bleiben größer als die Icons.
- Das YouTube-Symbol war zuvor als rotes Play-Symbol selbst gezeichnet (kein geladenes App-Icon). Es ist jetzt wie die anderen Symbole als helles Linien-Icon gezeichnet; die Aktion öffnet weiter YouTube.
- Links zeigt eine vierte Kachel **Scheibenwasser**: `NIEDRIG` beim vom Radio gelieferten Warnbit 0x40, `OK` ohne Warnung und `--` wenn 15 Sekunden lang kein entsprechendes Signal ankam. Das ist **kein gemessener Füllstand in Litern**.

## Neu in v0.12.0

- Bei Standortfreigabe fordert die Karte GPS-Fixes ungefähr jede Sekunde statt alle drei Sekunden bzw. fünf Meter an. Die tatsächliche Rate hängt vom GPS-Empfänger und Android ab.
- Zwischen aufeinanderfolgenden Positions-Fixes wird die Kartenmitte gleichmäßig animiert. Beim ersten Fix, bei großen Sprüngen oder nach längerer Empfangspause wird direkt zentriert; eine künstliche GPS-Vorhersage findet nicht statt.
- Alte Positionsmeldungen und grobe Netzwerk-Ortungen werden nicht über einen frischen GPS-Fix gestellt. Wird die Karte per Finger verschoben, pausiert die Nachführung wie bisher bis zum Tippen auf `GPS`. Beim Verlassen der Startseite wird die Animation gestoppt.

## Änderungen in v0.11.2

- Gurt-Kachel von 94 auf 76 dp verkleinert. Auch das DAB-Logo und die Abstände sind dezent kompakter, damit Titel **und** Interpret über den Steuerknöpfen Platz haben.
- DAB-Titel und Interpret nutzen Androids automatische Textgrößenanpassung in festen Teilbereichen. Titel 13–22 sp (bis vier Zeilen), Interpret 12–16 sp (bis zwei Zeilen). Extrem lange Radiotexte werden weiterhin am Ende gekürzt statt über die Tasten zu ragen.
- Die Karte startet eine Zoomstufe näher (15 statt 14); manuelles Zoomen per Finger bleibt möglich.

## Korrektur in v0.11.1

- Die Kartenansicht wird an allen vier Ecken auf die abgerundete Kupfer-Kachel begrenzt; Karte und ihre Bedienelemente ragen nicht mehr rechteckig über den Rand.
- Der Maps-Button im unteren Dock nutzt dieselbe neutrale Fläche wie die übrigen Buttons. Das Maps-Icon startet weiterhin die Google-Maps-App.

## Neu in v0.11.0

- Rechts ist die DAB-Kachel etwas kürzer; darunter steht eine separate Gurtstatus-Kachel. Sie nutzt das bereits bestätigte gemeinsame Warnbit 0x80 aus HCT 0x41/1 und gegebenenfalls ein Fahrzeug-Broadcastsignal. „⚠ Gurtwarnung“, „Keine Warnung“ oder bei fehlenden Daten nach 15 Sekunden „Kein Signal“. Das Signal sagt **nicht**, welcher Sitz betroffen ist; „Keine Warnung“ ist keine Bestätigung, dass alle Gurte eingerastet sind.
- Unten links auf der Karte ist `ONLINE/LOKAL · © OpenStreetMap-Mitwirkende` die erforderliche Kartenquellenangabe. Die zusätzliche Meter/Fuß-Skala zeichnet Mapsforge. Die Quellenangabe wurde nach oben versetzt, damit sich beide nicht mehr überlagern.

## Korrektur in v0.10.2

- Fehlenden Android-Import `android.view.View` im Seitenwischer ergänzt. Ohne ihn ließ sich v0.10.1 nicht kompilieren.

## Neu in v0.10.1

- Auf dem Cockpit lässt sich die App-Seite nun auch mit einer **waagerechten Wischgeste vom linken oder rechten Rand der Kartenkachel** erreichen (ca. 44 dp breite Randzone).
- Ein kurzes Tippen am Kartenrand bleibt ein Kartentipp. In der Mitte kann man die Karte weiterhin mit dem Finger verschieben und zoomen; ein horizontaler Ein-Finger-Wisch in der Mitte verschiebt bewusst die Karte statt die Launcher-Seite.
- Ein Tipp und ein Seitenwechsel schalten GPS-Folgen nicht mehr aus. Erst tatsächliches Verschieben oder eine Zoom-Geste pausiert das Folgen; `GPS` zentriert erneut.
- Zum Test: Auf der ersten Seite vom rechten Kartenrand zügig nach links wischen; zurück von der App-Seite nach rechts wischen. Zusätzlich normale Kartengesten in der Mitte und die Buttons am Kartenrand prüfen.

## Neu in v0.10.0

- Die mittlere Karte lädt interaktive OpenStreetMap-Kacheln über SIM oder WLAN. **Dafür muss keine Region heruntergeladen werden.** Der Kartendienst benötigt Internet, nicht den Google-Maps-API-Key.
- Ohne nutzbare Verbindung wird die zuvor heruntergeladene Mapsforge-Karte angezeigt. Ohne heruntergeladene Region gibt es einen deutlichen Hinweis statt einer weißen Fläche. Bei wiederhergestellter Verbindung wechselt die Karte zurück auf Online; GPS bleibt erhalten.
- Rechts oben führt `KARTE` zur Verwaltung der Offline-Regionen; unten steht `ONLINE` oder `LOKAL` mit sichtbarem OSM-Hinweis statt der irreführenden Beschriftung `OFFLINE`.
- Online-Kacheln werden ausschließlich für den jeweils sichtbaren Kartenausschnitt angefragt und separat zwischengespeichert. Die öffentliche [OSM-Kachelrichtlinie](https://operations.osmfoundation.org/policies/tiles/) untersagt Offline-Massendownloads; dafür dienen weiterhin die getrennten Mapsforge-Regionsdateien.
- Die Erkennung orientiert sich an Androids aktiver Internetverbindung. Manche Headunits melden eine funktionierende SIM nicht als `VALIDATED`; mobile Netze mit Internet-Capability gelten deshalb ebenfalls als online. Falls der OSM-Kachelserver trotz gemeldeter Verbindung nicht erreichbar ist, meldet der aktuelle Prototyp noch keinen einzelnen Kachelfehler zurück; dann in der Diagnose Netz/DNS prüfen. Live-Stau, Suchfunktion und Routenführung übernimmt weiterhin die Google-Maps-App über das Dock-Icon.

## Cockpit aus v0.9.0

- Die obere Kopfzeile mit „Polo Drive“ und Versionsangabe sowie die Seitenpunkte sind entfernt. **Wischen zwischen Cockpit und App-Seiten bleibt erhalten.**
- Songtitel und Interpret sind mittig unter dem DAB-Logo ausgerichtet. „Wiedergabe aktiv“ und „CAN LIVE“ werden nicht mehr im Cockpit angezeigt; DAB-Steuerung und CAN-Erfassung laufen unverändert weiter.
- Unten: sechs gut erkennbare, selbst gezeichnete Icons statt Text: Fahrzeug, DAB, Karte, CarPlay, YouTube und Einstellungen. Die Flächen haben weiterhin die gleichen Aktionen; YouTube setzt die installierte YouTube-App voraus.
- Die echte Versionsnummer wird nun unter **Einstellungen** angezeigt.

## Neu

- Die Mitte zeigt eine interaktive Online-Karte und ersatzweise eine **Mapsforge-Offline-Karte** statt des nicht ladenden Google-Verkehrs-Widgets. Eine heruntergeladene Region funktioniert ohne Internet; kein API-Key erforderlich.
- Unter **Einstellungen → Offline-Karten herunterladen / auswählen** ein deutsches Bundesland wählen, Größe bestätigen und Download-Fortschritt sehen. Erlaubt sind **WLAN und mobile Daten**; Roaming ist vorsichtshalber aus. Die Dateien stammen vom [Mapsforge-Kartenserver](https://download.mapsforge.org/maps/v5/europe/germany/), nicht vom öffentlichen OSM-Kachelserver.
- In der Kartenverwaltung den GPS-Zugriff für den Launcher freigeben. Die Karte zentriert sich auf den Standort, solange er innerhalb der gewählten Region liegt. Verschieben/Zoomen geht per Finger; die `GPS`-Taste zentriert wieder.
- Das Kartensymbol unten öffnet weiterhin Google Maps für Routen und Verkehr. Die Offline-Karte bietet für sich genommen *keine* Routenführung und keine Live-Staus.
- Nach Rückkehr von den Einstellungen wird eine frisch heruntergeladene oder neu ausgewählte Karte geladen.

## Weiterhin enthalten

- Kupfer-Cockpit, Uhr, CAN-Werte für Tank, Bordspannung und Außentemperatur.
- DABdream+-Titel und (falls die App sie liefert) Interpret, Bild und Steuerung über Android-Medienzugriff.
- Wischseiten mit allen installierten Apps sowie Fahrzeug-, DAB-, Maps-, CarPlay- und Einstellungs-Tasten.
- Optional als Android-HOME-Launcher wählbar. Launcher3 bis zum Funktionstest installiert lassen.

## Bauen und testen

1. Diesen Ordner in Android Studio öffnen und Gradle synchronisieren.
2. Für Gradle-Synchronisierung werden erstmals JitPack/Mapsforge-Bibliotheken geladen. Debug-APK über Build oder `BUILD_APK_WINDOWS.bat` bauen. APK aus demselben Android Studio / mit demselben Signaturschlüssel wie die vorige Version installieren. Debug (`.debug`) und Release sind verschiedene Paketkennungen.
3. Launcher öffnen und GPS-Zugriff unter Einstellungen → Offline-Karten erlauben: Mit SIM-Internet erscheint direkt die Online-Karte.
4. Für Fahrten ohne Netz: Unter Einstellungen → Offline-Karten → Bundesland (z. B. Sachsen, ca. 165 MB) herunterladen. Danach Flugmodus/Verbindung testweise ausschalten: Es erscheint `LOKAL` mit der gespeicherten Karte.
5. Launcher3 installiert lassen, bis der Test auf dem Radio erfolgreich ist.

Die Dateien liegen im app-spezifischen externen Speicher unter `Android/data/de.ronny.pololauncher[.debug]/files/offline_maps/`. Bei einer Deinstallation können sie mit entfernt werden. Eine fertige APK ist nicht Teil des ZIPs; ohne Radio/Android SDK ist kein realer Gerätetest möglich.
