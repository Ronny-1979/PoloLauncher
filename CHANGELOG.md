# PoloLauncher – Änderungsverlauf (neueste Version oben)


- Version: **0.31.147 / versionCode 228**. Basis v0.31.146.
- Fix: Nach Radio-aus während der Auswertung (Aufbau) wurden die fertig geladenen OSM-Rohdaten erneut heruntergeladen. Ursache: Wiederverwendung hing an einer erfolgreichen Server-Abfrage mit passendem Datum (beim Start oft noch kein Internet, Geofabrik aktualisiert täglich). Jetzt merkt sich eine Markierung (.ok) den vollständigen Download; beim Fortsetzen wird er ohne Server-Abfrage direkt weiter ausgewertet. Neu geladen wird nur noch bei defekten Daten oder einer ausdrücklich bestätigten Aktualisierung.
- Hinweis: Die Auswertung selbst beginnt nach einem Abbruch wieder von vorn (ohne neuen Download).

- Version: **0.31.146 / versionCode 227**. Basis v0.31.145.
- Menü-Umbau von v0.31.145 zurückgenommen: Das gewohnte Menü (v0.31.144) ist wieder da. Neu nur: Schieberegler sind schmaler (ca. 55 % der Breite), rechts bleibt freie Fläche zum Scrollen, damit man sie beim Wischen nicht versehentlich verstellt.

- Version: **0.31.145 / versionCode 226**. Basis v0.31.144.
- Menü komplett neu geordnet und ohne Slider: Alle Einstellungen werden nur noch per Tipp bedient. Beim Scrollen kann nichts mehr versehentlich verstellt werden.
- Schieberegler ersetzt durch Stufen-Tasten (− / +): Neigung, Gebäudehöhe, Zoom innerorts/außerorts, Reserve, Tankpreis-Intervall (5–30 Min.). Wischschalter ersetzt durch große Ein/Aus-Zeilen (ganze Zeile tippbar). Kartenmodus und Positionsmarke als Auswahlknöpfe.
- Einstellungen sind in Karten mit Überschrift gruppiert (Darstellung, 3D, Zoom, Warnungen, Offline-Karte; Fahrzeug, Tankstellen-Preise; Hintergrund, CarPlay, DAB; System). Größere Tippflächen, kürzere Hinweise, Tankerkönig-Schlüssel zeigt „gespeichert/fehlt“.

- Version: **0.31.144 / versionCode 225**. Basis v0.31.143.
- Der automatische Zoom an der Ortsgrenze läuft sanfter und langsamer: 0,5 statt 0,9 Zoomstufen pro Sekunde (zwei Stufen dauern etwa 4 s). Das Nachziehen im Stand dauert 1,8 s.

- Version: **0.31.143 / versionCode 224**. Basis v0.31.142.
- Automatisches Ein-/Auszoomen (Ortsgrenze) ruckelte, weil jede Zoomstufe als harter Sprung (moveCamera) alle 0,5 s kam und dabei die laufende Gleitbewegung der Karte abbrach (zwei Ruckler pro Wechsel). Jetzt gleitet der Zoom mit der normalen Kartenbewegung pro GPS-Fix mit höchstens 0,9 Stufen pro Sekunde zum Ziel; nur im Stand erledigt ein einzelnes weiches Easing den Rest.

- Version: **0.31.142 / versionCode 223**. Basis v0.31.141.
- Die Tankstellenkachel ist kleiner (weniger Innenabstand), und die DAB-Kachel hat wieder ihre volle Höhe, damit die Tasten unten nicht abgeschnitten werden. Höhenverhältnis rechts: Fahrzeug 0,32 / Tankstellen 0,08 / DAB 0,60 (DAB wie vor v0.31.141).

- Version: **0.31.141 / versionCode 222**. Basis v0.31.140.
- Die Tankstellenpreise (MK-T, HEM) haben eine eigene Glaskachel zwischen Fahrzeug- und Radiokachel; die Fahrzeugkachel endet unter „Aktuell“ und „Ø 100 KM“. Höhenverhältnis rechts: Fahrzeug 0,33 / Tankstellen 0,10 / DAB 0,57.

- Version: **0.31.140 / versionCode 221**. Basis v0.31.139.
- Tankstellenpreise haben dieselbe Schriftgröße (13 sp) wie der Name davor, vorher schrumpften sie automatisch. Kachelgrößen unverändert.

- Version: **0.31.139 / versionCode 220**. Basis v0.31.138.
- Tankstellenpreise ohne die letzte Stelle (9/10 Cent): 2,199 € wird als „2,19 €“ angezeigt (abgeschnitten, nicht gerundet).

- Version: **0.31.138 / versionCode 219**. Basis v0.31.137.
- Tankstellenfelder kürzer beschriftet: „MK-T“ (Marktkauf Oschatz) und „HEM“ (Dahlen).

- Version: **0.31.137 / versionCode 218**. Basis v0.31.136.
- Tankstellen-Preise größer dargestellt (Schrift 13/16 sp statt 9/10 sp); dafür sind die Kacheln Tempo und Drehzahl etwas niedriger.
- Neu in Einstellungen → Tank, Verbrauch & OBD: „Tankstellen: Aktualisierung alle … Minuten“ mit 5, 10, 15, 20, 25 oder 30 Minuten (Standard 10).

- Version: **0.31.136 / versionCode 217**. Basis v0.31.135.
- Fahrzeugkachel: Die Felder „Gurt“ und „Wasser“ sind entfallen. An ihrer Stelle stehen die E10-Preise der Marktkauf-Tankstelle in Oschatz und der HEM in Dahlen (günstigere grün, geschlossen/veraltet grau). Quelle: Tankerkönig (MTS-K, CC BY 4.0); Abruf alle 10 Minuten, letzte Preise bleiben offline gespeichert; Tippen auf die Felder öffnet die Einstellungen. Der kostenlose API-Schlüssel wird unter Einstellungen → Tank, Verbrauch & OBD → „Tankstellen-Preise“ eingetragen. Die Stations-IDs sucht die App selbst anhand von Name und Ort.
- Karte: Neue blaue Warnung „Waschwasser leer“ unter Tür- und Gurtwarnung, solange der Behälter als niedrig gemeldet wird (3 s entprellt). Die Gurtwarnung bleibt wie bisher.

- Version: **0.31.135 / versionCode 216**. Basis v0.31.134.
- Straßenauswertung: Brach der Parser mit „NegativeArraySizeException“ ab (defekte OSM-Rohdatei), blieb die kaputte Datei liegen und derselbe Fehler kam bei jedem Neustart. Jetzt wird die defekte Rohdatei gelöscht und automatisch neu geladen (bis zu 2 Mal je Lauf); erst danach erscheint ein Fehler.

- Version: **0.31.134 / versionCode 215**. Basis v0.31.133.
- Karten-Download robuster: Nach „MBTiles ungültig: Baden-Württemberg“ (Download komplett 534 MB, aber Datei defekt – vermutlich nach hartem Ausschalten mitten im Schreiben) wird die defekte Datei jetzt verworfen und automatisch neu geladen (bis zu 2 Mal je Lauf). Fortsetzen geschieht nur noch ab dem Punkt, der nachweislich auf den Speicher geschrieben wurde (fsync alle 8 MB plus Positionsdatei `.pos`); alles dahinter wird neu geholt. Alte Teil-Downloads ohne Positionsdatei beginnen einmalig neu.

- Version: **0.31.133 / versionCode 214**. Basis v0.31.132.
- Nacht-Automatik: Abends schaltet die Karte jetzt 30 Minuten vor Sonnenuntergang auf Nachtmodus. Morgens bleibt es beim Sonnenaufgang (Umschalten auf Tag).
- Version: **0.31.132 / versionCode 213**. Basis v0.31.131.
- Karten-Update läuft von selbst weiter: Bricht die Verbindung ab (z. B. Standby, Funkloch, „SocketException“), wird das nicht mehr als Fehler beendet. Der Dienst wartet (15 s, 30 s, 1, 2, 5, max. 10 min) und macht an derselben Stelle weiter. Zusätzlich weckt ein Alarm alle ~10 Minuten die App, falls Android den Dienst beendet hat; beim Einschalten des Radios und beim Start des Launchers wird ebenfalls fortgesetzt, bis alles fertig ist. Der Wunsch „Update läuft“ bleibt bis zum Ende gespeichert.
- Downloads setzen exakt am Abbruchpunkt fort (HTTP-Range mit If-Range): halb geladene Karten und OSM-Rohdaten werden nicht mehr von vorn geladen. Hat sich die Datei auf dem Server geändert, beginnt nur diese eine Datei neu.
- Beim Update-Lauf gilt „Server nicht erreichbar“ nicht mehr als „bereits aktuell“ (früher konnte eine Karte dadurch unbemerkt übersprungen werden); es wird stattdessen später erneut geprüft.
- „Direkt an Ortsgrenze umschalten“ ist dauerhaft aktiv: Schalter im Kartenmenü, Einstellung `direct_boundary_zoom` und alle Reste im Code entfernt (SettlementZoomGate verwendet immer die schnelle Ausfahrt: 60 m und 2 Bestätigungen, mindestens 6 s).
- Positionsmarken noch kleiner: Kürbis 26 × 24 dp, Schlitten 20 × 34 dp.

- Version: **0.31.131 / versionCode 212**. Basis v0.31.130.
- Positionsmarken verkleinert, weil sie auf dem Radio zu groß wirkten: Halloween-Kürbis jetzt 32 × 29 dp (vorher 48 × 44), Weihnachtsschlitten 24 × 40 dp (vorher 34 × 56). Der blaue Standard-Punkt bleibt bei 20 dp.

- Version: **0.31.130 / versionCode 211**. Basis v0.31.129. Keine Funktionsänderung der App.
- GitHub-Build: Die Debug-APK wurde mit einem eigenen, zufälligen Schlüssel signiert, obwohl das Secret DEBUG_KEYSTORE_BASE64 (Schlüssel D9:46:63:4A…) korrekt geladen war; deshalb ließ sie sich nicht über die bestehende App installieren. `app/build.gradle` trägt die Schlüsseldatei jetzt über `DEBUG_KEYSTORE_PATH` in `signingConfigs.debug` ein (nur wenn die Variable gesetzt ist, also nur im GitHub-Build). Der Workflow gibt Fingerabdrücke von Schlüssel und APK als Hinweis aus.

- Version: **0.31.129 / versionCode 210**. Basis v0.31.128.
- Diagnose: Im Abschnitt „OBD2-ADAPTER / ELM327“ steht jetzt ein „OBD2-Verlauf“ der letzten 60 wichtigen Ereignisse mit Uhrzeit (Verbindungsversuche mit Dauer und Fehler, Hänger nach 20 s, Verbindung verloren, Neuverbindung nach Standby, erste brauchbare Daten). Der gemeinsame Log vergisst diese Ereignisse nach etwa einer Minute, weil er von CAN-Meldungen überflutet wird. Keine Änderung am Verbindungsverhalten.

- Version: **0.31.128 / versionCode 209**. Basis v0.31.127.
- Die GPS-Taste behält ihren Text „◎  GPS“ und bekommt einen ovalen Rahmen im Stil des Info-Knopfs ⓘ (nur Linie mit dunklem Saum, keine Füllfläche). Der Ring mit Fadenkreuz aus v0.31.127 entfällt (GpsRingView gelöscht).

- Version: **0.31.127 / versionCode 208**. Basis v0.31.126.
- Die GPS-Taste (unten rechts auf der Karte) ist jetzt ein runder Ring mit Fadenkreuz in derselben Größe und Lage wie der Info-Knopf ⓘ links unten (ohne Hintergrundfläche, mit dunklem Saum für Lesbarkeit). Die Funktion (auf die GPS-Position zentrieren, Folgen einschalten) ist unverändert.

- Version: **0.31.126 / versionCode 207**. Basis v0.31.125.
- Weihnachtsschlitten: der Weihnachtsmann ist jetzt als Gestalt mit roter Zipfelmütze und weißem Bommel gezeichnet statt als Kreis-Ziel. Sonst unverändert.

- Version: **0.31.125 / versionCode 206**. Basis v0.31.124.
- Neu im Menü Karte: „Positionsmarke“ zum Umschalten zwischen dem blauen Standard-Punkt, einem Halloween-Kürbis und dem Weihnachtsschlitten (von oben gesehen, dreht sich mit dem GPS-Kurs, wenn Norden oben steht). Die Marken werden im Code gezeichnet (keine Bilddateien). Eine unbekannte oder fehlende Einstellung ergibt den Standard-Punkt. Die Wahl gilt, sobald man zur Hauptseite zurückkehrt.

- Version: **0.31.124 / versionCode 205**. Basis v0.31.123.
- Neu: Offline-Karten → „Von USB-Stick importieren“. Der Launcher sucht auf dem Stick den Ordner `PoloMaps` (Fallback: Ordnerauswahl), kopiert neuere Karten (`<land>-shortbread-1.0.mbtiles`) und Verkehrsdaten (`<land>-latest.osm.pbf`) und baut alles wie gewohnt auf. Alte Karten bleiben bis zum Ersatz aktiv.
- Neu: `tools/windows-downloader/` – Windows-Programm (PoloMapsDownloader.exe) lädt alle Dateien von Geofabrik mit einem Klick auf den Stick (Fortsetzen, Überspringen, Datum wie am Server).


- Version: **0.31.123 / versionCode 204**. Basis v0.31.122.
- Der Launcher prüft einmal pro Woche selbst, ob es neue Karten oder Verkehrsdaten gibt (nur Abfrage der Dateigrößen/Daten, kein Download). Ist die Woche abgelaufen, während das Radio aus war, geschieht das beim nächsten Start.
- Die Prüfung und das Popup erscheinen erst, wenn der Launcher komplett hochgefahren ist: seit 45 Sekunden sichtbar, Internet bestätigt, DAB-Autostart und Standby-Aufbau der Karte abgeschlossen, kein Kartenaufbau aktiv.
- Gibt es Neues, fragt ein Popup „Neue Karten verfügbar“: **Aktualisieren** startet den Download (auch über das Handynetz; die alte Karte bleibt bis zum Ende aktiv), **Später** fragt nach 24 Stunden erneut. Ohne Bestätigung wird nie geladen.
- Schlägt die Prüfung fehl (z. B. kein Netz), wird sie frühestens nach 30 Minuten wieder versucht. Die Prüfung per Knopf im Menü bleibt unverändert.


- Version: **0.31.122 / versionCode 203**. Basis v0.31.121.
- Bei „Aktuell“ und „Ø 100 KM“ ist die Einheit „l“ jetzt deutlich größer (75 % der Zahl statt 40 %).
- Rollt das Auto ohne Gas (l/100 km unter 0,05, z. B. Schubabschaltung), zeigt „Aktuell“ statt „0,0 l“ den Kraftstofffluss in l/h, wie schon im Stand. Bei Gas zeigt sie wieder l/100 km.


- Version: **0.31.121 / versionCode 202**. Basis v0.31.120.
- Solange nach dem Start noch keine Ortslage oder Geschwindigkeit erkannt ist, bleibt die Karte auf dem zuletzt genutzten Zoom. Eine noch unbekannte Ortslage überschreibt die gespeicherte nicht mehr (Fehler in v0.31.120: der erste Speichervorgang nach dem Start konnte sie löschen).


- Version: **0.31.120 / versionCode 201**. Basis v0.31.119.
- Der Kartenzustand (Position, Fahrtrichtung, Zoom und ob Ortslage oder außerhalb) wird alle 5 Sekunden sowie beim Ausschalten des Bildschirms und beim Pausieren gespeichert; dadurch gilt er auch, wenn das Radio nach einer Stunde ganz herunterfährt. Nach Standby oder Neustart startet die Karte sofort dort mit dem gleichen Zoom und der gleichen Ausrichtung, statt mit Standardzoom und Norden; die Zoomstufe der letzten Ortslage gilt, bis die erste Ortsanalyse da ist.


- Version: **0.31.119 / versionCode 200**. Basis v0.31.118.
- DAB-Autostart (nach Standby/Neustart): Der Launcher kehrt nicht mehr nach festen 3 Sekunden zurück, sondern erst wenn DABdream+ im Vordergrund ist und der Sender 2 Sekunden lang läuft. Verlässt man DABdream+ vorher selbst, wird man nicht zurückgeholt. Zeitlimit 45 s.
- CarPlay-Rückkehr (nur wenn die Automatik an ist): statt fester 5 Sekunden wartet sie, bis CarPlay als verbunden erkannt wird (Bluetooth-Audio/Freisprech verbunden oder aktive CarPlay-Mediensitzung, 3 s stabil). Zeitlimit 90 s. Ein Tipp auf CarPlay im Launcher bleibt wie bisher unangetastet.
- Das Diagnoseprotokoll zeigt die Signale („CarPlay-Automatik: Verbindungssignale …“, „DAB-Autostart: DABdream+ vorn=…, Sender läuft=…“).


- Version: **0.31.118 / versionCode 199**. Basis v0.31.117.
- In den vier Kacheln Tempo, Drehzahl, Aktuell und Ø 100 KM stehen Beschriftung und Wert jetzt mittig statt links.


- Version: **0.31.117 / versionCode 198**. Basis v0.31.116.
- Fährt das Auto (ab 5 km/h) und der Gurt ist offen, erscheint oben auf der Karte ein orange blinkender Balken „⚠ Gurt nicht angelegt!“ (unter der Türwarnung, falls auch diese aktiv ist). Er bleibt stehen, bis der Gurt angelegt ist; im Stand vor der Fahrt erscheint er nicht. Beim Motor-Aus verschwindet er. Die Türwarnung bleibt unverändert.


- Version: **0.31.116 / versionCode 197**. Basis v0.31.115.
- Das Senderlogo (Cover) der DAB-Kachel wird jetzt mit abgerundeten Ecken beschnitten, passend zur runden Rahmenkachel; die Ecken ragen nicht mehr heraus. Auch nicht quadratische Logos werden an ihrem tatsächlichen Rand gerundet.


- Version: **0.31.115 / versionCode 196**. Basis v0.31.114.
- Fahrzeugkachel (rechts oben) nach Konzept B: Tempo und Drehzahl als große Kacheln, darunter Aktuell und Ø 100 KM, Zahl groß und Einheit klein. Bordnetz und Außentemperatur stehen klein mit Symbol in der Kopfzeile neben Tank, Balken und Reichweite. Gurt und Wasser bleiben immer sichtbar.
- Das Polo-Bild in der Kachel entfällt (samt SquareFrameLayout und Bilddatei). Glas-Hintergrund und Farben unverändert.


- Version: **0.31.114 / versionCode 195**. Basis v0.31.113.
- Die „GPS“-Taste (unten rechts) und die „ⓘ“-Taste (unten links) auf der Karte haben keine Hintergrundfarbe mehr (transparent); ein weicher dunkler Textschatten hält sie auf heller Karte lesbar. Funktion unverändert.
- In der Statusanzeige steht statt „LTE“ jetzt „4G“.

- Version: **0.31.113 / versionCode 194**. Basis v0.31.112.
- Log vom 17.10. (v0.31.112) bestätigt die Selbstheilung: Zündung an, ELM antwortete minutenlang „SEARCHING… UNABLE TO CONNECT“; nach „keine brauchbaren Daten seit 58 s“ wurde neu initialisiert, danach kamen sofort Werte (3 s nach Motorstart). Die Wartezeit ist jetzt 30 s statt 45 s (Backoff bis 5 min bei geparktem Auto bleibt).

- Version: **0.31.112 / versionCode 193**. Basis v0.31.111.
- **Problem (selten):** Nach Radio-Standby und Motorstart fehlt der Live-Verbrauch; Standby-Zyklus (Zündung aus/an) behebt es. Der OBD-Thread war an drei Stellen festhängbar oder blind:
  1. `BluetoothSocket.connect()` hat kein Zeitlimit und kann auf Radio-Bluetooth-Stacks minutenlang hängen → jetzt Watchdog: nach 20 s wird der Socket geschlossen, der normale Wiederholversuch greift.
  2. `socket.close()` lief auf dem OBD-Thread und kann dort blockieren → jetzt in Hilfsthread, höchstens 1 s Wartezeit.
  3. Antwortete der Adapter dauerhaft ohne brauchbare Werte (z. B. „UNABLE TO CONNECT“, weil das Steuergerät noch schlief) und nicht mit Zeitüberschreitung, wurde er nie neu initialisiert → jetzt Neuaufbau nach 45 s ohne brauchbare Daten (bei geparktem Auto steigt die Wartezeit bis 5 min, bei echten Daten wieder 45 s).
- **Neu:** Beim Aufwachen aus dem Standby (SCREEN_ON bzw. Quick-Start-Erkennung) wird die alte Bluetooth-Verbindung verworfen und sofort neu aufgebaut (mindestens 20 s Abstand zwischen zwei Anforderungen).
- Im Log erkennbar an: „OBD2: Neuverbindung nach Standby angefordert“, „Verbindungsaufbau hängt seit 20 s“, „keine brauchbaren Daten seit … s, Adapter wird neu initialisiert“.

- Version: **0.31.111 / versionCode 192**. Basis v0.31.110.
- **Ursache (aus dem Log v0.31.110):** In Fahrt stand die Kamera bei 355 von 877 Fixes (40 %) schon still, wenn der nächste Fix eintraf (typisch ~880 ms bei ~1000 ms Fix-Abstand, bei jeder Geschwindigkeit); 877 Fahrt-Fixes standen nur 534 Kamerabewegungen gegenüber. Der „Zittern ignorieren“-Filter (Fix < 2 m von der Kamera) verglich den Fix mit dem Kamera-Ziel, das absichtlich ~1 s Fahrstrecke VORAUS liegt. Dadurch wurde fast jeder zweite Fix verschluckt: Karte steht ~1 s, springt dann 2 s weit.
- **Fix:** Der Filter gilt nur noch im Stand/Schleichfahrt (< 3 m/s, ohne Vorhalt). In Fahrt bewegt jeder Fix die Kamera (`CameraMotion.isJitter`, Tests ergänzt).
- Die Diagnosezeile „In Fahrt … Kamera stand beim Eintreffen schon still“ sollte jetzt gegen 0 gehen.

- Version: **0.31.110 / versionCode 191**. Basis v0.31.109. Fahrverhalten unverändert.
- Auswertung der Fahrt vom 14.10. (v0.31.109): GPS gleichmäßig (309 von 316 Abständen 0,7–1,3 s), Karte zeichnet Ø 58,9 Bilder/s (nur 5 langsame) – GPS und Zeichenleistung sind also nicht die Ursache des Stockens. Die 105 „Stillstände“ enthielten auch Standzeiten.
- Neu: Zeile „In Fahrt (>=11 km/h)“ zählt nur Fixes bei Fahrt, bei denen die Kamera beim Eintreffen schon stillstand, mit längster Dauer; zusätzlich Ereigniszeile „Kamera stand … ms still bei … km/h (Fix-Abstand … ms)“.

- Version: **0.31.109 / versionCode 190**. Basis v0.31.108. Fahrverhalten unverändert, nur zwei neue Diagnosezeilen unter „Kartennachführung“:
- **GPS-Abstand:** wie viele Fixes mit welchem Abstand (<0,7 s, 0,7–1,3 s, 1,3–2 s, >2 s) und der größte Abstand. Zeigt, ob das GPS ungleichmäßig liefert.
- **Karten-Bilder beim Gleiten:** Anzahl, Ø Bilder pro Sekunde, langsame Bilder (>120 ms) und „Stillstand zwischen Gleitbewegungen“ (Kamera stand >0,6 s still, bevor die nächste Bewegung kam) mit längster Dauer. Zeigt, ob das Radio zu langsam zeichnet oder die Kamera auf den nächsten Fix wartet.
- Auswertung: viele Fixes >1,3 s → GPS. Wenige Bilder/s oder viele langsame Bilder → Zeichenleistung (Neigung/3D-Gebäude/Schrift). Viele Stillstände trotz gleichmäßiger Fixes → Kamera-Gleitdauer.

- Version: **0.31.108 / versionCode 189**. Basis v0.31.107. Anzeige unverändert.
- Aus den Fahrt-Logs vom 14.10.: Beim Start hing die Oberfläche einmal 7,7 s, weil das Hintergrundbild für die Glas-Kacheln auf dem Hauptthread weichgezeichnet wurde. Das passiert jetzt schon beim App-Start in einem Hintergrund-Thread.
- Der UI-Wächter schrieb eine „Blockade“ von 108 s, obwohl die App nur im Standby eingefroren war (Hauptthread im Leerlauf). Leerlauf wird nicht mehr gemeldet; echte Blockaden weiter.
- Drehzahl nach Motor aus: unverändert (4-s-Regel); Test steht noch aus (10 s nach Motor aus auf die Anzeige schauen, dann Log).

- Version: **0.31.107 / versionCode 188**. Basis v0.31.106. Keine Funktionsänderung.
- Bei der Gesamtprüfung (Ressourcen, Werte, Assets, Abhängigkeiten) fand sich eine einzige Altlast: die Bibliothek `androidsvg` in `build.gradle`, die nirgends im Code verwendet wird. Entfernt (APK wird kleiner). Alle Ressourcen, Strings, Assets und die übrigen Abhängigkeiten sind in Benutzung.

- Version: **0.31.106 / versionCode 187**. Basis v0.31.105. **Keine Änderung an dem, was im Auto angezeigt wird** (Dashboard „Ø 100 KM“ und Reichweite nutzen weiter das gleitende 100-km-Profil).
- **Entfernt – „Ø seit Tanken“ komplett:** Klasse `Obd2RefuelAverager`, die Zeile „OBD Ø seit Tanken“ und die Tank-Werte in der Diagnose, der Knopf „Ø seit Tanken zurücksetzen“ in den OBD2-Einstellungen, die Speicherwerte `avg_since_refuel_*` (werden beim Start einmalig gelöscht), das Feld `VehicleState.avgConsumption`, die Übergabe „Nachtanken bestätigt“ an den OBD2-Client (`confirmRefuel`, `RangeEstimator.consumeRefillConfirmed`) sowie die zugehörigen Hinweistexte. Die Nachtank-Erkennung der Reichweite selbst bleibt.
- **Zusammengelegt:** (1) Rechteck-Suche der Verkehrsdatenbank (`TrafficDataStore`): die 6-fach kopierte Breiten-/Längen-Rechnung steckt jetzt in einer Hilfsklasse `Window`. (2) `HttpText.readLimited` ersetzt die zwei identischen Lese-Methoden von Wetter- und Ortsdienst. (3) `DabNotificationListener.accessGranted` ersetzt die doppelte Prüfung „Medienzugriff erlaubt“ in Einstellungen und Diagnose; `containsAny` wird nur noch in `AppLauncher` geführt.
- **Bewusst nicht zusammengelegt:** die Bildschirm-Aufbau-Hilfen der Einstellungsseiten (unterschiedliche Abstände, nur Optik, kein Nutzen) und die Weichzeichner-Schleifen (waagerecht/senkrecht, leistungsrelevant).
- Prüfung: alle Tests grün, alle 107 Klassen kompilieren gegen die echte Android-API, Querverweis-Analyse ohne neue Treffer.

- Version: **0.31.105 / versionCode 186**. Basis v0.31.104. **Keine Funktionsänderung.**
- **Exakte Analyse:** statt Namenssuche jetzt eine Auswertung über den Java-Compiler selbst (jeder Aufruf, jede Methodenreferenz und jedes Feld wird aufgelöst; Überschreibungen, Manifest-Klassen und Lebenszyklus-Methoden ausgenommen), wiederholt bis keine neuen Treffer mehr kamen. Zusätzlich PMD (ungenutzte Variablen/Parameter/Importe) und ein Abgleich der gespeicherten Einstellungs-Schlüssel.
- **Entfernt (Code):** `TrafficDataService` (wurde nirgends gestartet; die Verkehrsdaten baut `GermanyOfflineService`) samt Manifest-Eintrag; `SettlementLookahead`; alle Methoden, die nur noch Tests aufriefen (`mafGs`, `fuelPercent`, `MediaInfo.subtitle`, `displayAverage`, Zähler-Getter, `*.current()`, Komfort-Überladungen von `compose`/`build`/`scan`/`claim`/`finish`, `RangeStore`-Kurzformen u. a.); nie gelesene Felder (`Status`-Felder der Verkehrsdaten, `Candidate.roadBearing`, `Stats.neededNodes/speedWays`, `SpeedLimitDiagnostics.displayed`); die nie gesetzte Anzeige „Anzeige=--“ im Tempolimit-Diagnosetext; der nie gelesene Schlüssel `run_requested_at`; ungenutzte Importe.
- **Tests:** angepasst statt gelöscht – sie prüfen dasselbe jetzt über echte Ergebnisse (z. B. Zustand im `VehicleRepository`, Rückgabewerte, die gespeicherten Werte). `DiagLog.clear()` liegt jetzt als Hilfsklasse in `tests/` statt im Produktivcode.
- **Neu: `tests/compile-check.sh`** kompiliert alle Produktionsklassen (inkl. `MainActivity` und aller Activities) gegen eine echte `android.jar` (`ANDROID_JAR=… sh tests/compile-check.sh`). Die veralteten MapLibre-Platzhalter wurden dafür erneuert; ein nie genutzter Test-Stub (`DownloadManager`) entfiel.
- **Bewusst behalten:** die „Ø seit Tanken“-Berechnung (steht weiter in der Diagnose), `LocalTileServer` (Fallback-Stufe), Aufräumen alter Mapsforge-Karten, Manifest-Klassen (`PoloApplication`, `GermanyOfflineResumeReceiver`).


- Version: **0.31.104 / versionCode 185**. Basis v0.31.103. **Keine Funktionsänderung** – nur Entfernen von nachweislich ungenutztem Code.
- **Methode:** statische Analyse (PMD für ungenutzte private Teile, eigene Querverweis-Prüfung über alle 112 Dateien, Ressourcen-/Asset-/Manifest-Abgleich); jeder Treffer wurde einzeln geprüft (Methodenreferenzen wie `this::deliverArea` sind kein toter Code). Testsuite vor und nach dem Aufräumen grün.
- **Entfernt (Java):** `CompletionListeners`, `LoadTimeoutGate` (nur noch von eigenen Tests genutzt); nie aufgerufene Wetterkachel-Ausrichtung samt Hilfsfeldern in `MainActivity`; Reste der Online-Karte in `MapFollowDiagnostics`; ungenutzte Zähler/Getter (`LocalTileServer.served/missing`, `HctSyncDecoder.summary/lastDoorMask`, `VendorRuntime`/`SystemCanReceiver`-Durchreicher, `Obd2*.reliableDisplayAverage`, `TrafficDataStore.roadCount`, `SpeedLimitDiagnostics.poorGps/outsideMap/stale`, `GermanyOfflineStore.clearRunRequest`, `MapPreferences.offlineCache*`); tote Felder (`lastRaw`, `followingBeforeGesture`, Bereichs-Consumer im Panel); einmalige Zoom-Migration von v0.31.93; unbenutzte Parameter und Variablen.
- **Entfernt (Projektdateien):** alte `TESTBERICHT_*` (bis v0.31.95), `TESTLOG_*`, `BUILD_VALIDATION_*`, `BUILD_GRADLE_*`, `FULL_DEEP_DEBUG_*`, `GPS_FIX_*` sowie das veraltete `tests/run-tests.ps1` (war seit Monaten nicht mehr mit `run-tests.sh` synchron und hätte nicht mehr kompiliert).
- **Bewusst behalten:** Aufräumen alter Mapsforge-Karten (`removeLegacyMaps`, gibt Speicher frei), Abwärtskompatibilität der gespeicherten Verbrauchs-/Medien-Werte, `LocalTileServer` (Fallback-Stufe der Karten-Leiter), alle Ressourcen und Assets (alle in Verwendung).


- Version: **0.31.103 / versionCode 184**. Basis v0.31.102 (enthält dessen Fix „Drehzahl 0 bei verstummtem CAN“).
- Die 4-Sekunden-Regel hat bei Motor aus nicht gegriffen. Mögliche Ursache: zwei Quellen (CAN-Frame und Broadcast) liefern wechselnde Werte oder der Wert schwankt leicht. Damit das im Log sichtbar wird, schreibt die App im Stillstand jede Drehzahländerung (max. 1/s) mit Quelle: „RPM-Verlauf (Stillstand): CAN-Frame|Broadcast -> n U/min“. Verhalten sonst unverändert.


- Version: **0.31.102 / versionCode 183**. Basis v0.31.101.
- **Drehzahl bei Motor aus:** Wenn die Zündung aus ist, verstummt der CANBOX ganz; die Drehzahl fiel dann auf „-- U/min ALT“ bzw. blieb stehen. Jetzt gilt: Kommen keine Frames mehr und die letzte bekannte Geschwindigkeit war < 2 km/h, zeigt die Anzeige **0 U/min**. Brach der Empfang während der Fahrt ab (letzte Geschwindigkeit ≥ 2 km/h), bleibt es bei „--“. Zusätzlich greift weiterhin die Regel „4 s identischer Wert → 0“ bei Stillstand.
- Test: RpmFrozenTest um „CAN still bei Stillstand → 0“ und „Ausfall bei 80 km/h → unbekannt“ erweitert.


- Version: **0.31.101 / versionCode 182**. Basis v0.31.100.
- **Ursache „Verbrauch manchmal weg“:** Direkt nach dem Verbinden sucht der ELM327 (`ATSP0`) noch das Protokoll („SEARCHING…“). Das dauert mehrere Sekunden, die Befehle hatten aber nur 1,5 s Zeit. Ergebnis: Zeitüberschreitung → Verbindung verworfen → alles von vorn (ATZ…), im Log alle ~7 s sichtbar. Auch später riss ein einzelner verlorener Befehl die ganze Verbindung ab, und der Wert verfällt nach 15 s.
- **Fix:** (1) Support-Check und erste Abfragerunde nach dem Verbinden: 10 s Zeit; erscheint „SEARCHING“, wird die Wartezeit automatisch verlängert. (2) Ein einzelner Befehl ohne Antwort zählt nur noch als „keine Daten“ (Eingangspuffer wird neu synchronisiert); erst 8 Zeitüberschreitungen in Folge oder ein echter Verbindungsfehler lösen einen Neuaufbau aus. (3) Ein fehlgeschlagener Support-Check beendet die Verbindung nicht mehr. Im Log: „OBD2: 010C ohne Antwort (n/8), Verbindung bleibt bestehen“.
- **Drehzahl-Regel abgesichert:** „Eingefrorene Drehzahl → 0“ gilt nur bei Geschwindigkeit < 2 km/h oder unbekannt, nie während der Fahrt.


- Version: **0.31.100 / versionCode 181**. Basis v0.31.99.
- **Nacht-Theme:** Bei Sonnenuntergang (Kartenmodus „Automatisch“, oder fest „Nacht“) wird nicht mehr ein dunkler Schleier über die helle Karte gelegt, sondern der Stil umgeschaltet: VersaTiles `colorful-dark` (gleicher Ebenenaufbau wie das Tag-Theme `freedom`, 204 Ebenen je Paket, gleiche Symbole). `tools/offline-style/build.mjs` erzeugt dafür `layers_{full,full_3d,lite}_dark.jsonl` (Gebäude-Extrusion in dunklen Tönen, Hintergrund `#282725`); die Tagvorlagen sind unverändert (bytegleich geprüft). Beim Wechsel Tag/Nacht lädt die Karte den Stil einmal neu (kurzes Aufblitzen), auch wenn der Wechsel während eines Ladevorgangs kommt.
- Der Schleier (`nightShade`) ist abgeschaltet; der Kartenhintergrund hinter den Kacheln passt sich dem Thema an.
- Tests: `OfflineStyleTest` (Nacht-Vorlagen gleiche Ebenen, gültiges JSON, gleiche Symbole, Hintergrundfarbe), Quelltextprüfung. Kein Android-Build, nicht am Radio getestet.

## v0.31.99 – Ortszoom fest, Drehzahl, Ø 100 km

- Version: **0.31.99 / versionCode 180**. Basis v0.31.98.
- **Ortsabhängiger Zoom ist fest eingebaut:** Schalter und `MapPreferences.autoZoom` sind entfernt, Innerorts-/Außerorts-Zoom gilt immer (Standard 18/16; vor der Gebietserkennung 16). Im Menü bleibt nur der Hinweistext, Innerorts/Außerorts-Regler und „Direkt an Ortsgrenze umschalten“.
- **Drehzahl nach Motor aus:** Die CANBOX wiederholt nach dem Abstellen den letzten Wert (im Foto 856 U/min bei 12,7 V und 0 km/h), ohne dass die Öl-/Ladewarnung immer gesetzt ist. `VehicleState.noteRpm` merkt, wann sich der Wert zuletzt geändert hat; ein Wert, der bei weiter eintreffenden Frames länger als 4 s exakt gleich bleibt, zeigt `VehicleRepository.snapshot()` als 0 (ein laufender Motor schwankt von Frame zu Frame um einige U/min). Test: `RpmFrozenTest`.
- **Ø 100 KM statt Ø seit Tanken:** Die Kachel zeigt jetzt den gleitenden, streckengewichteten Mittelwert der letzten 100 km (`Obd2Runtime.rangeAverage`, dasselbe Fenster wie für die Restreichweite). Der Wert seit dem Nachtanken bleibt nur in der Diagnose; der Zurücksetzen-Knopf im OBD-Menü ist entsprechend beschriftet.
- Kein Android-Build, nicht am Radio getestet.

## v0.31.98 – nur Offline-Karte, 3D-Menü, weichere Kamera

- Version: **0.31.98 / versionCode 179**. Basis v0.31.97 (Offline-Karte läuft auf dem Radio).
- **Online-Karte entfernt:** `OnlineVectorMapPanel`, der Schalter „Nur Offline-Karte (Test)“ und die Kartenstil-Auswahl (Liberty/Bright/3D) sind weg. Ohne installierte Offline-Karte erscheint der Hinweis „Keine Offline-Karte“ (Menü → Karte → Offline-Karte verwalten).
- **Neues Menü „3D-ANSICHT“:** Neigung 0–60° in 5°-Schritten (Standard 45°) und Gebäudehöhe 0–30 m (Standard 9 m, 0 = flach). Die Gebäudehöhe wird über `OfflineStyleComposer.withBuildingHeight` in die 3D-Vorlage eingesetzt; bei Änderung lädt `applyViewSettings()` beim Zurückkehren zur Hauptseite den Stil neu, die Neigung wirkt sofort. Zurücksetzen-Knopf.
- **Zoom:** Der Grundzoom-Regler ist aus dem Menü entfernt. Es bleiben Innerorts (Standard 18) und Außerorts (Standard 16); der intern genutzte Grundwert ist 16 bzw. die zuletzt per Pinch gewählte Stufe, solange „Ortsabhängigen Zoom verwenden“ aus ist.
- **Sanftere Kamera:** `BearingSmoother` filtert den Kurs (Zeitkonstante 0,9 s, höchstens 120°/s, kürzester Weg), und die Gleitfahrt dauert jetzt 1,1 × das geglättete Fix-Intervall statt 0,85 ×, sodass sich die Schritte überlappen und die Karte zwischen zwei Fixes nicht stehen bleibt.
- Tests: `BearingSmootherTest`, Ergänzungen in `OfflineStyleTest` (Gebäudehöhe) und `SourceAuditTest`. Kein Android-Build, nicht am Radio getestet.

# PoloLauncher v0.31.97 – Offline-Karte: Stil-JSON wurde als Internetadresse gelesen

- Version: **0.31.97 / versionCode 178**. Basis v0.31.96.
- **Befund aus dem Log v0.31.96 (13.10. 07:51):** Alle fünf Versuche scheitern, auch die reine Hintergrund-Probe ohne jede Kartendatei. Damit ist klar: weder `mbtiles://`, Sprite, Schrift noch Kachelserver sind schuld. Die App übergab den fertigen Stil-JSON an `map.setStyle(String, …)`; diese Methode erwartet eine **URL** (so lädt die Online-Karte ihren Stil), den JSON-Text kann sie nicht laden und meldet nie „fertig“.
- **Korrektur:** `map.setStyle(new Style.Builder().fromJson(json), …)`. Quelltext-Prüfung verbietet `setStyle(json,` künftig.
- Der Rest (Kachelserver, Rückfallstufen, Selbsttest) bleibt als Absicherung. Kein Android-Build, nicht am Radio getestet.

## v0.31.96 – Kachelserver als zweiter Weg

- Version: **0.31.96 / versionCode 177**. Basis v0.31.95.
- **Befund aus den Logs v0.31.94 (16:39–16:41):** Auch mit der Adresse `mbtiles:///…` wird der Stil nie angewendet. Die Kartendateien selbst sind lesbar (Einrasten: 77 von 85 Fixen aus den Kartenkacheln), das Problem liegt also bei MapLibres eigener `mbtiles://`-Quelle, beim Sprite oder bei den Schriften. Welche genau, ist offen; die Quellen im Netz widersprechen sich bei der Adressform.
- **Neu: `LocalTileServer`** (nur 127.0.0.1, zufälliger Port). Er liefert MapLibre die Kacheln aus den MBTiles-Dateien (`/t/<Paket>/<z>/<x>/<y>.pbf`) sowie Schrift und Sprite (`/f/…`) per HTTP; MapLibre braucht dann weder `mbtiles://` noch `file://`. `network_security_config.xml` erlaubt Klartext-HTTP ausschließlich für 127.0.0.1/localhost.
- **Versuchsleiter in `GermanyOfflinePanel`** (je 12 s aktive Zeit, jeder Schritt im Log): 0 direkt `mbtiles://` voll → 1 Kachelserver voll → 2 Kachelserver ohne Icons → 3 Kachelserver nur Flächen und Linien → 4 Hintergrund-Probe. Der zuletzt erfolgreiche Weg (direkt/Kachelserver) wird gemerkt und beim nächsten Start zuerst genommen.
- **Log-Zeilen:** „Kachelserver gestartet: http://127.0.0.1:PORT“, „Kachelserver Selbstabruf: … → HTTP 200, N B“ (prüft Server, Klartext-Erlaubnis, Sprite und die Kachel unter der Position), „Offline-Stil Versuch N (…)“, plus der Selbsttest aus v0.31.95.
- Tests: `LocalTileServerTest` (Kachel unverändert inkl. gzip-Kennung, 404, ungültige Namen, Pfadflucht, Schrift mit Leerzeichen/Komma, 200 parallele Abrufe), `OfflineStyleTest` (Quelle mit `tiles`-Vorlage), Quelltext-Prüfungen. Kein Android-Build, nicht am Radio getestet.

## v0.31.95 – Offline-Karte: Selbsttest und Rückfallstufen

- Version: **0.31.95 / versionCode 176**. Basis v0.31.94 (korrigierte `mbtiles:///`-Adresse bleibt).
- **Selbsttest im Diagnose-Log** (`OfflineMapSelfTest`, einmal je Start im Hintergrund): Zeilen „Offline-Selbsttest <Land>: …“ mit Dateigröße, genauer MapLibre-Adresse, allen Kopfdaten (`metadata`), Zoomstufen, der Kachel 14 unter der Position (Größe, gzip, Zahl der Straßensegmente) sowie Sprite- und Schriftdateien.
- **Rückfallstufen statt Hängen:** Stufe 0 voll → 1 ohne Sprite (Icons) → 2 nur Flächen und Linien, ohne Schrift/Icons, nur das nächste Paket → 3 Probe mit reinem Hintergrund (nur Diagnose: kann MapLibre überhaupt Stile laden?). Jede Stufe hat 20 s **aktive** Zeit (Karte sichtbar und gestartet; die Zeit bei gestoppter Activity zählt nicht – im Log v0.31.93 lief die alte 45-s-Frist teilweise ab, während die App im Hintergrund war). Jeder Schritt steht im Log („Offline-Stil Stufe N … fehlgeschlagen: …“). Gelingt eine Stufe > 0, erscheint kurz „Offline-Karte eingeschränkt“.
- Ladefehler von MapLibre vor dem Stil führen sofort zur nächsten Stufe, danach werden sie nur noch protokolliert (max. 5) und beenden die Karte nicht mehr.
- Tests: Rückfallstufen im `OfflineStyleTest`, Quelltext-Prüfungen im `SourceAuditTest`. Kein Android-Build, nicht am Radio getestet.

## v0.31.94 – Offline-Karte: falsche Kartenadresse behoben

- Version: **0.31.94 / versionCode 175**. Basis v0.31.93.
- **Ursache für das schwarze Bild (aus dem Log v0.31.93, „Stil noch nicht angewendet seit 184 s“, Zeitüberschreitung nach 45 s; dieselbe Zeitüberschreitung steht schon im Log v0.31.75):** Die Quellen des Offline-Stils hießen `mbtiles://file:///data/...` (Schema plus `file://`-Adresse). MapLibre erwartet `mbtiles://` plus absoluten Pfad, also `mbtiles:///data/...`. Die Datei ließ sich dadurch nie öffnen, der Stil wurde nie fertig, die Karte blieb leer. Jetzt: `"mbtiles://" + file.getAbsolutePath()`. Belegt durch Berichte, in denen `file://` durch `mbtiles://` ersetzt wird (maplibre-react-native Discussion 591); **am Radio nicht bestätigt**.
- `GermanyOfflineAnalyzer.close()` schließt die Verkehrsdatenbank nicht mehr auf dem UI-Thread (Log: UI 5 s blockiert in `onDestroy`).
- Neue Quelltext-Prüfungen gegen die alte Adressform.

## v0.31.93 – Standardzoom innerorts 18, außerorts 16

- Version: **0.31.93 / versionCode 174**. Basis v0.31.92.
- Standard: Ortszoom (innerorts) **18**, außerorts **16**, Grundzoom **16**. „Zoomwerte auf Standard zurücksetzen“ setzt darauf. Gespeicherte Werte werden beim ersten Start einmalig darauf zurückgesetzt.
- Gebäude sind ab Zoom 15 (3D ab 15,5) voll sichtbar, also bei beiden Standardwerten.

## v0.31.92 – Standardzoom 14

- Version: **0.31.92 / versionCode 173**. Basis v0.31.91.
- Standard-Grundzoom und Ortszoom (innerorts) jetzt **14** (vorher 15), außerorts bleibt **13** (weiter herausgezoomt; Untergrenze 12). Die Regler erlauben weiterhin bis Zoom 19, „Zoomwerte auf Standard zurücksetzen“ setzt auf 14/14/13.
- Einmalige Zurücksetzung der gespeicherten Zoomwerte auf die neuen Standards beim ersten Start, danach gelten wieder die eigenen Einstellungen.

## v0.31.91 – Offline-Karte im Liberty-Stil, 3D-Gebäude, Umschalter „Nur Offline (Test)“

- Version: **0.31.91 / versionCode 172**. Basis v0.31.90.
- **Neuer Offline-Stil.** Die Ebenen stammen aus dem VersaTiles-Stil „freedom“ (MIT, Optik wie OSM Liberty) für das Shortbread-Schema der Geofabrik-Pakete: Landnutzung, Wald, Parks, Gewässer, Straßen mit Rand, Tunnel, Brücken, Einbahnpfeile, Bahnlinien, Gebäude, Straßen-/Autobahn-/Orts-/Gewässernamen und Symbole (Tankstelle, Gaststätte, Apotheke, Bahnhof …). Erzeugt von `tools/offline-style/build.mjs` nach `app/src/main/assets/offline_style/` (204 Ebenen je Paket, Sprite mit 126 Symbolen, CC0).
- **Nur die Pakete um die Position.** Statt 14 Quellen mit rund 180 Ebenen gleichzeitig bindet der Stil höchstens 3 Bundesländer ein (`GermanyRegionIndex`, Puffer 0,12°). Nähert sich das Auto einem weiteren Bundesland (0,04°), wird der Stil einmal neu geladen (frühestens alle 15 s). Beim Start dient die zuletzt bekannte Position; ohne sie und im Ausland erscheint eine grobe Übersicht (15 Ebenen, alle Pakete).
- **3D (Kartenstil „3D“ gilt jetzt auch offline):** Karte 45° geneigt, Gebäude als Extrusion. **Grenze:** Die Geofabrik-Gebäude haben keine Höhenangaben (nur das Feld `dummy`); alle Gebäude bekommen daher dieselbe Höhe (9 m, wächst zwischen Zoom 14 und 15,5 ein). Sobald eine Kachel `height` enthält, wird es verwendet. Die Online-Karte (OpenFreeMap) hat kartierte Höhen, die Offline-Karte nicht.
- **Kein schwarzes Bild mehr:** MapView-Hintergrund in Papierfarbe, Hinweis „Offline-Karte wird geladen …“, nach 20 s „lädt noch …“, bei Fehlern der Fehlertext in der Karte.
- **Neuer Schalter** unter KARTE → KARTENQUELLE: „Nur Offline-Karte (Test)“. Die Online-Karte wird dann nicht erzeugt (auch nicht mit Internet). Schlägt die Offline-Karte fehl, bleibt sie mit Fehlermeldung stehen, statt hinter der Online-Karte zu verschwinden. Der Abschnitt ONLINE-KARTENSTIL heißt jetzt KARTENSTIL.
- Diagnose-Log: „Offline-Stil: N Ebenen, Pakete […]“, „Stil angewendet nach … ms“, „neues Bundesland in Sicht“.
- Tests: `OfflineStyleTest` (Region-Auswahl inkl. Schleifenfreiheit, gültiges JSON, jede Quelle deklariert, Schriftname passt zu den heruntergeladenen Glyphen, jedes Symbol im Sprite), Stil zusätzlich mit der offiziellen MapLibre-Stilprüfung validiert (0 Fehler) und mit synthetischen Kacheln in Chromium gerendert (`tools/offline-style/preview`).
- **Nicht geprüft:** Kein Android-Build, kein Test mit echten Geofabrik-Kacheln und auf dem Radio (Geofabrik ist von hier nicht erreichbar). Annahmen: `sprite` und `glyphs` per `file://` (Glyphen funktionieren bereits so), Ebenen- und Eigenschaftsnamen nach Shortbread 1.0. Wenn Daten fehlen sollten (z. B. leere `streets`/`buildings`), lädt „Offline-Karte neu laden“ das Paket erneut.
- Lizenz/Quellen: Stil © VersaTiles (MIT, https://github.com/versatiles-org/versatiles-style), Symbole Maki/Temaki (CC0), Daten © OpenStreetMap-Mitwirkende (ODbL), Kacheln Geofabrik Shortbread.

## v0.31.90 – Offline-Karte zuerst, Online-Karte erst danach

- Version: **0.31.90 / versionCode 171**. Basis v0.31.89.
- Beobachtung (Log v0.31.88): Beim Start meldete die Deutschland-Karte für die ersten 3 Fixe „nicht bereit“ (Stil noch nicht angewendet), während gleichzeitig die Online-3D-Karte erstellt und geladen wurde. Zwei MapLibre-Renderer konkurrieren dann um GPU, Speicher und Datenträger; auf dem Radio blieb die Kachel schwarz, bis die Online-Karte fertig war.
- **Offline zuerst:** `MainActivity` erstellt die Online-Karte erst, wenn die Offline-Karte `ready()` meldet (Stil angewendet), spätestens nach 15 s. Das Anwenden des Stils löst jetzt sofort `updateMapConnection()` aus, die Online-Karte startet also unmittelbar danach; mit Internet erscheint sie weiterhin, sobald sie fertig geladen ist. Ohne Internet ändert sich nichts.
- **Diagnose:** neue Zeile „Offline-Karte: Stil nach … ms · erster vollständiger Frame nach … ms“ (oder „noch nicht“ mit Sekundenzähler) sowie Log-Zeile „Offline Deutschland MapLibre: Stil angewendet (sichtbar=…, gestartet=…, fortgesetzt=…)“. Damit sieht man beim nächsten Start, ob die Offline-Karte langsam lädt oder gar nicht rendert.
- Nicht belegt: warum das Laden der Offline-Karte so lange dauert (Verdacht: der Stil öffnet 16 Regionsdateien und hat ca. 200 Ebenen). Das zeigt erst die neue Diagnosezeile.
- Kein Android-Build hier; Radiotest nötig (Kaltstart mit und ohne Internet).

## v0.31.89 – Meldung „Polo Launcher reagiert nicht“: Absicherung und Ursachensuche

- Version: **0.31.89 / versionCode 170**. Basis v0.31.88.
- **Wichtig:** Aus dem Log ist die Ursache der Meldung nicht direkt ablesbar (kein Java-Absturz gespeichert, das Log zeigt nur den Neustart um 14:47:58). Deshalb zwei Maßnahmen: eine Absicherung der bekannten Risiken und eine Aufzeichnung für den Wiederholungsfall.
- **Absicherung (neu in v0.31.87 hinzugekommene Blockier-Risiken):** `GermanyRoadPositionMatcher.close()` schloss die Kachel- und Verkehrsdatenbanken auf dem UI-Thread und wartete dabei auf einen laufenden Lesezugriff (kalter Speicher nach Standby = Sekunden). Jetzt schließt der Worker die Datenbanken nach seinem aktuellen Auftrag (`shutdown` statt `shutdownNow`); `GermanyRoadTiles.activeRegion()` ist nicht mehr synchronisiert; das Vorwärmen der Kachelköpfe startet erst 20 s nach dem Start, damit es nicht mit dem ersten Laden der Offline-Karte um den Speicher konkurriert.
- **`UiStallWatchdog`:** Ein Hintergrundthread prüft zweimal pro Sekunde, ob der UI-Thread antwortet. Blockiert er länger als 2,5 s, steht im Diagnose-Log „UI-Thread blockiert seit X s: …“ mit den obersten 16 Aufrufen des UI-Threads; die letzte Meldung wird gespeichert und in der Diagnose als „Letzte UI-Blockade“ angezeigt (überlebt „App schließen“). Wiederholt sich die Meldung, zeigt diese Zeile die genaue Stelle.
- Neue Quelltext-Prüfungen in `SourceAuditTest`. Kein Android-Build hier; Radiotest nötig.

## v0.31.88 – Kein Zurückspringen des Punktes

- Version: **0.31.88 / versionCode 169**. Basis v0.31.87.
- Ursache laut Log/Code: Die Kamera sitzt in der Kartenmitte (dort steht der Punkt). `OnlineVectorMapPanel.setVisible(true)` zentrierte bei **jedem** Aufruf neu auf den rohen letzten Fix, ohne Vorlauf. `updateMapConnection()` ruft das bei jedem Netz-/Statuswechsel auf. Der Punkt sprang dadurch um die Länge des Vorlaufs plus Fixalter zurück und wurde vom nächsten Fix wieder vorgezogen (Log v0.31.87: 772 Kamerabewegungen bei 580 Fixen). Zusätzlich konnte der Vorlauf beim Bremsen überschießen.
- **`CameraAim`:** (1) Zentriert wird nur noch, wenn die Karte wirklich sichtbar wird, und dann auf das letzte Ziel mit Vorlauf (`hold`), nicht auf den rohen Fix. (2) Der Vorlauf nutzt die kleinere der letzten beiden Geschwindigkeiten, überschießt also beim Bremsen nicht. (3) Das Kameraziel geht bei Geradeausfahrt nie rückwärts (nur Rückwärtsanteil wird verworfen, Querversatz bleibt; Ausnahmen: Wendung > 60°, Stillstand, Ziel älter als 5 s, Rücksprung > 40 m gilt als echt).
- Gilt für Online- und Offline-Karte. Diagnose: „Kamera: Rückwärtssprünge verhindert N".
- Neuer Test: `CameraAimTest`. Kein Android-Build hier; Radiotest nötig.

## v0.31.87 – Einrasten mit allen Straßen aus den Offline-Kacheln

- Version: **0.31.87 / versionCode 168**. Basis v0.31.86 (Straßen-Kontinuität bleibt).
- **Neu: `GermanyRoadTiles` + `MvtRoadDecoder`.** Der Matcher liest die Straßenlinien direkt aus den Shortbread-Kacheln (`*.mbtiles`) des Deutschland-Pakets (Zoom 14, Ebene `streets`, nur befahrbare Klassen). Dort sind **alle** Straßen enthalten, nicht nur die mit Tempolimit-Tag. Es braucht keine neuen Daten und keinen neuen Download.
- Unabhängig vom Bildschirm: Die Kachel unter dem Auto wird direkt gelesen (kein `queryRenderedFeatures` mehr). Es funktioniert auch bei verdeckter oder noch nicht gezeichneter Karte, bei jedem Zoom und bei Neigung.
- Dekodierte Kacheln werden zwischengespeichert (8 Stück, ca. eine Kachel je 1,5 km Fahrt). Pro Region bleiben höchstens drei Datenbanken geöffnet; die Region wird über die `bounds`-Metadaten gewählt. Die Kopfdaten werden einmalig im Hintergrund eingelesen.
- Reihenfolge der Quellen: Kacheln → Verkehrsindex (nur Straßen mit Tempolimit-Tag) → Roh-GPS. Tempolimit, Blitzer und Ortserkennung bleiben unverändert auf Roh-GPS und Verkehrsindex.
- **Aufgeräumt:** `RenderedRoadReader`, die Panel-Methoden `roadSegments`, die `RenderedRoads`-Verdrahtung in `MainActivity`/Matcher (v0.31.86), `MapPreferences.roadSnap()` samt Prüfungen, `TrafficDataStore.findNearestRoad` + `RoadMatch` und `GermanyTrafficReader.findNearestRoad` (alles ungenutzt) sind entfernt. Die Karte-Seite im Menü nennt jetzt, dass die Offline-Karte die Straßen zum Einrasten enthält. Einen Schalter gibt es weiter nicht (Einrasten ist fest verbaut).
- Diagnose: „Straßenkorrektur: eingerastet X · Roh-GPS Y · davon aus Kartenkacheln N"; das Log nennt die aktive Kachelregion.
- Neue Tests: `MvtRoadDecoderTest` (Kacheln eines unabhängigen Encoders, gzip und roh, kaputte und abgeschnittene Kacheln, Kachelindex, Bounds) und Quelltext-Prüfungen gegen `queryRenderedFeatures`.
- Grenzen: Kein `assembleDebug` und kein Test mit echten Kacheln hier. Annahmen: Tabelle `tiles` im TMS-Schema, Ebene `streets` mit Eigenschaft `kind`, Zoom 14 vorhanden. Im Log zeigt `Straßenkacheln: …` Fehler beim Öffnen/Lesen.

## v0.31.86 – Straßen-Kontinuität und Kartenstraßen (Kartenabfrage seit v0.31.87 ersetzt)

- Version: **0.31.86 / versionCode 167**. Basis v0.31.85, Fangwerte unverändert.
- **Straßen-Kontinuität (`RoadSnapSelector`):** Der Matcher bewertet pro Fix mehrere Straßenkandidaten (bis 8) statt nur die nächste. Setzt ein Kandidat eine der letzten Straßen fort (gemeinsamer Endpunkt, 3 m), gilt er als verbunden. Ein nicht verbundener Kandidat braucht 10 m Vorsprung **und** zwei aufeinanderfolgende übereinstimmende Fixe, bevor gewechselt wird. Das verhindert das Springen zwischen Parallelstraßen und an Kreuzungen. Ist nichts Verbundenes mehr in Reichweite, wird sofort gewechselt. Nach 6 s ohne Treffer wird der Verlauf verworfen.
- **Kartenstraßen als Fallback (`RenderedRoadReader`):** Liefert die Verkehrsdatenbank (nur Straßen mit Tempolimit-Tag) keinen Kandidaten, liest der Matcher die von der sichtbaren Karte gezeichneten Straßen (`queryRenderedFeatures`, Offline: Shortbread `kind`, Online: `class`; nur befahrbare Klassen, keine Wege/Bahn/Zufahrten). Abfrage max. 150 ms, sonst Roh-GPS. Tempolimit, Blitzer und Ortserkennung bleiben unverändert auf Roh-GPS/Datenbank.
- Diagnose: „Straßenkorrektur: eingerastet X · Roh-GPS Y · davon über Kartenstraßen N".
- Nicht enthalten: Fusion mit der CAN-Geschwindigkeit (noch nicht geprüft).
- Neuer Test: `RoadSnapSelectorTest`. Kein `assembleDebug` hier; die MapLibre-Abfrage in `RenderedRoadReader` ist nur im Auto prüfbar.

## v0.31.85 – Flüssige Kamera auf v0.31.84

- Version: **0.31.85 / versionCode 166**. Basis ist dein v0.31.84 (Fangwerte 18–35 m / 55° unverändert).
- **Anpassbare Gleitdauer:** Die Kamera gleitet jetzt so lange, wie die GPS-Fixe wirklich auseinanderliegen (Online 0,9 × Abstand, 450–1500 ms; Offline 0,85 ×, 450–2200 ms). Ein unregelmäßig eintreffendes Einrast-Ergebnis startet kein halbfertiges Gleiten mehr neu (weniger Ruckeln).
- **Vorhalt (`CameraMotion`):** Die Kamera zielt auf die Position, an der das Auto am Ende des Gleitens ist (ab 3 m/s, höchstens 28 m, nur bei verlässlichem Kurs). Die Karte hängt dadurch nicht mehr hinterher. Der Punkt selbst bleibt an der GPS-/Straßenposition.
- **Versatz-Übernahme im Matcher:** Konnte ein Fix nicht eingerastet werden (Straße ohne Tempolimit-Tag, Kreuzung, schlechte Genauigkeit), wird der zuletzt gemessene Versatz zur Straße bis zu 8 s lang mit abnehmender Stärke weiterverwendet. Der Punkt springt nicht mehr zwischen Straße und Roh-GPS hin und her.
- Startup-Fix (Offline-Karte zuerst sichtbar, Neuzeichnen) aus v0.31.84 unverändert.
- Neuer Test: `CameraMotionTest`. Radiotest ist weiter nötig; ein `assembleDebug` ist hier nicht gelaufen.

## v0.31.84 – Offline-First Startup Fix

- Version: **0.31.84 / versionCode 165**.
- Die fertige Deutschland-Offlinekarte wird beim Launcher-Start jetzt **immer zuerst sichtbar aktiviert**, noch bevor die Online-3D-Karte bereit ist.
- `GermanyOfflinePanel` startet intern nicht mehr fälschlich mit `visible=true`; dadurch kann der erste `setVisible(true)` nicht mehr als wirkungsloser No-op enden.
- Beim Sichtbarwerden und nach `onResume()` fordert die Offline-MapLibre-Karte mit `triggerRepaint()` + `postInvalidate()` einen frischen Frame an. Das hilft auch nach ACC/Standby, wenn die TextureView-Oberfläche verworfen wurde.
- Die Onlinekarte lädt weiterhin parallel und wird erst eingeblendet, wenn `ready()` erfüllt ist. Ohne Internet bleibt die Deutschlandkarte sichtbar.
- Live-GPS, Straßeneinrasten aus v0.31.83, Auto-Zoom, Tempolimits und Blitzer sind funktional unverändert.

## v0.31.83 – Straßeneinrasten wie im bewährten Mapsforge-Pfad

- Basis ist v0.31.82; die funktionierende Live-Nachführung bleibt unverändert.
- Video-/Codevergleich mit v0.31.49: Die neue Deutschland-Straßenkorrektur war deutlich zu streng (12–26 m, ohne sicheren Kurs maximal 14 m). Der alte Mapsforge-Pfad arbeitete zuverlässig mit 18–35 m abhängig von der GPS-Genauigkeit. Diese bewährte Distanzlogik ist wieder hergestellt.
- Fahrtrichtungs-Toleranz wieder 55° wie im alten Matcher.
- Ein vorhandenes Straßensegment wird nicht mehr nur deshalb verworfen, weil ein richtungsabhängiges Tempolimit für genau diesen Fix nicht auflösbar ist. Die Geometrie bleibt für das Einrasten gültig.
- Diagnose zeigt zusätzlich: `Straßenkorrektur: eingerastet X · Roh-GPS Y`. Damit ist beim Radiotest sofort sichtbar, wie oft wirklich auf die Straße korrigiert wurde.
- Grenze bleibt: Das vorhandene kompakte Deutschland-Verkehrspaket enthält nicht jede Straße Deutschlands. Wo keine passende Straßenachse im Index vorhanden ist, bleibt bewusst Roh-GPS.
- Version: **0.31.83 / versionCode 164**.

## v0.31.82 – Flüssiger Start

- `MainActivity.onCreate` und der Karten-Konstruktor lesen nur noch das billige READY-Flag. Die vollständige Prüfung (14 Karten, Schriften, alle Verkehrs-SQLite-DBs, Ordnergröße) läuft in einem Hintergrund-Thread. Ist das Paket unvollständig, wird READY zurückgesetzt und die Hauptseite einmal neu aufgebaut (dann ohne Deutschland-Karte, mit Online-Karte falls Internet).
- Menüs „Karte"/„Diagnose" profitieren vom Cache der Prüfung (siehe v0.31.81).
- Version: **0.31.82 / versionCode 163**.

## v0.31.81 – Offline-Karte sofort, Online bei Internet; Menüs öffnen schnell

- Kartenverhalten wie in der alten Version: Die Offline-Karte Deutschland wird von Anfang an angezeigt. Sobald Internet da ist und die Online-Karte fertig geladen hat, wird auf die Online-Karte umgeschaltet. Dort laufen Position, Zoom (Innerorts/Außerorts), Tempolimit und Blitzer weiter über das Deutschland-Paket.
- Menü „Karte" und „Diagnose" öffneten verzögert, weil auf dem UI-Thread mehrfach das ganze Deutschland-Paket geprüft wurde (14 Karten, Glyphen, alle Verkehrs-SQLite-DBs) und der 8-GB-Ordner gezählt wurde. Jetzt: positives Prüfergebnis wird zwischengespeichert (5 min, pro Paketstand), Größenberechnung 30 s, beides wird beim App-Start im Hintergrund vorgewärmt; die Diagnose baut ihren Text im Hintergrund auf und öffnet sofort.
- Mapsforge: JitPack-Repository aus `settings.gradle` entfernt (keine Abhängigkeit mehr).
- Version: **0.31.81 / versionCode 162**.

## v0.31.80 – Offline Deutschland sofort als Hauptkarte

- Ist das Deutschland-Paket installiert, wird die Offline-Karte von Anfang an angezeigt und gestartet, auch wenn Internet vorhanden ist. Die Online-Karte (OpenFreeMap) wird nur noch als Rückfall benutzt, wenn die Deutschland-Karte fehlschlägt oder nicht installiert ist.
- Wetter und Ortsname nutzen weiter den echten Netzstatus.
- Version: **0.31.80 / versionCode 161**.

## v0.31.79 – Komplett auf die Offline-Karte Deutschland umgebaut

- **Mapsforge ist komplett entfernt**: Gradle-Abhängigkeit, alter Kartenspeicher (`OfflineMapStore`, `MapFileActivation`, `OfflineMapMetadata`), alter Ortszoom-Analysator (`SettlementZoomAnalyzer`, `TileNeighborhood`), alte Karte (`OfflineMapPanel`) und alle zugehörigen Tests/Stubs.
- Karte: nur noch **Offline Deutschland** (MapLibre) oder **Online-Karte** (OpenFreeMap), GPS direkt an die sichtbare Karte.
- Ortsabhängiger Zoom, Tempolimit und Blitzer kommen ausschließlich aus dem Deutschland-Paket (`GermanyOfflineAnalyzer`). Neue kleine Klassen: `MapArea` (Innerorts/Außerorts) und `GermanyRegions` (Bundesländer-Liste).
- Entfernt aus der Hauptseite: Erkennung alter Kartendownloads/Regionswechsel, Neuaufbau wegen „alter Verkehrsdaten". Entfernt aus Einstellungen/Diagnose: Übergangs- und Fallback-Hinweise.
- Ein Android-Build lief hier nicht; die Test-Suite ist grün.
- Version: **0.31.79 / versionCode 160**.

## v0.31.78 – Alte Kartenlogik aufgeräumt

- Gelöscht (nicht mehr benutzt, seit die Karte nur noch über Offline Deutschland bzw. Online läuft): `SpeedLimitAnalyzer`, `SpeedCameraAnalyzer`, `RoadPositionMatcher`, `OfflineMapCapabilities` samt zugehöriger Tests und Prüfregeln. Tempolimit, Blitzer und Straßenabgleich laufen weiter über `GermanyOfflineAnalyzer` und `GermanyRoadPositionMatcher`.
- Noch vorhanden, weil andere Teile daran hängen: `OfflineMapStore`/`MapFileActivation`/`OfflineMapMetadata` (alter Karten-Download und Signaturen), `SettlementZoomAnalyzer` (Ortszoom, nutzt noch Mapsforge-Leser), `TileNeighborhood` und die Mapsforge-Gradle-Abhängigkeit.
- Version: **0.31.78 / versionCode 159**.

## v0.31.77 – Alte Mapsforge-Karte entfernt

- Die alte Offline-Karte (`OfflineMapPanel`, Mapsforge) wird nicht mehr erzeugt und ihre Klasse ist gelöscht. Karte läuft nur noch über **Offline Deutschland** (MapLibre) bzw. die **Online-Karte**, wenn Internet da ist.
- Schlägt die Deutschland-Karte fehl, gibt es keinen stillen Wechsel auf die alte Karte mehr; die Online-Karte bekommt dann das GPS direkt. Das war genau der Weg, der in v0.31.75 die Karte einfrieren ließ.
- Ist das Deutschland-Paket noch nicht fertig, gibt es nur die Online-Karte (bei Internet) bzw. den Hinweis „Karte konnte nicht geöffnet werden".
- Unverändert (andere Klassen hängen daran): Download-/Verwaltungsseite, alte Kartenspeicher-Klassen und die Mapsforge-Gradle-Abhängigkeit. Ein Android-Build lief hier nicht.
- Version: **0.31.77 / versionCode 158**.

## v0.31.76 – Karte bleibt stehen: Ursache gefunden und behoben

- Ursache laut Kartenereignis-Protokoll (v0.31.75): Die unsichtbare Deutschland-Offline-Karte meldete 45 s nach dem Start „Zeitüberschreitung beim Laden" (ein nicht angezeigtes MapLibre lädt nie). Daraufhin wurde sie verworfen und die alte Mapsforge-Karte als Fallback aktiviert, die den GPS-Fix für sich übernahm. Die sichtbare Online-Karte bekam danach keine Positionen mehr und blieb stehen.
- Fix 1: Der Mapsforge-Fallback reicht Position und Ortsbereich immer an die sichtbare Online-Karte weiter.
- Fix 2: Ein Timeout der Deutschland-Karte zählt nicht als Fehler, solange sie unsichtbar ist; er wird neu gesetzt und greift erst, wenn sie angezeigt wird.
- Version: **0.31.76 / versionCode 157**.

## v0.31.75 – Kartenereignis-Protokoll in der Diagnose

- Befund aus Test mit v0.31.73: Karte folgt ca. 10 s, dann steht sie. Der Zähler zeigte 206 GPS-Fixes, aber nur ca. 15 an der Kartenlogik. Der Hauptlog ist durch CAN-Zeilen überflutet und reicht nur ~40 s zurück.
- Die Diagnose enthält jetzt „Kartenereignisse": Lebenszyklus der Hauptseite, GPS Start/Stopp, Empfänger gesetzt/entfernt, Umschaltung Online/Offline, Kartenfehler, jeweils mit Uhrzeit.
- Funktional unverändert gegenüber v0.31.74. Version: **0.31.75 / versionCode 156**.

## v0.31.74 – Kartennachführung: GPS-Empfänger robuster, genauere Diagnose

- Log von v0.31.73: 206 GPS-Fixes kamen an, aber nur ca. 15 erreichten den Straßenabgleich/die Karte. Ursache nicht sicher belegt; auffällig ist ein fehlender oder verlorener Empfänger der Kamera-Fixes.
- `onStart()` hängt den Kamera-Empfänger jetzt wieder an und startet das GPS sofort neu (bisher erst beim nächsten periodischen Karten-Durchlauf).
- Neue Diagnose-Zeile: „Ohne Empfänger verworfen" und „GPS-Neustarts".
- Version: **0.31.74 / versionCode 155**.

## v0.31.73 – Kompilierfehler „Unzulässige Vorwärtsreferenz" behoben

- v0.31.72 ließ sich nicht bauen: Der Lambda-Initialisierer `rebuildMapAfterWake` in `MainActivity` las das weiter unten deklarierte Feld `activityVisible` über den einfachen Namen (Java: illegal forward reference). Fix: Zugriff über `this.activityVisible` / `this.wakeMapRebuildPending`. Verhalten unverändert.
- Die JVM-Tests kompilieren `MainActivity` nicht; ein Quelltext-Guard prüft jetzt die `this.`-Schreibweise. Ein echter Android-/Gradle-Build lief hier **nicht**.
- Enthält alle Änderungen aus v0.31.69–72. Version: **0.31.73 / versionCode 154**.

## v0.31.72 – Karte nach Standby neu aufbauen

- Fehlerbild: Nach dem Aufwachen des Radios aus dem Standby blieb die Kartenfläche blau, erst ein kompletter Neustart brachte die Karte zurück. Die App wertete die Aufwach-Signale (Bildschirm an, Quick-Start-Broadcast des Dienstes) bisher nur für DAB aus.
- Jetzt baut `MainActivity` bei einem Aufwachen die Hauptseite (inkl. aller Kartenrenderer) einmal neu auf: 2 s nach dem Signal, nur wenn sichtbar (sonst beim nächsten Resume), mit statischem 15-s-Cooldown gegen Doppelauslösung.
- Im DiagLog stehen „Standby-Aufwachen erkannt (…)" und „Karte nach Standby neu aufgebaut".
- Enthält alle Änderungen aus v0.31.69–71. Version: **0.31.72 / versionCode 153**.

## v0.31.71 – Straßeneinrasten fest eingebaut

- „Punkt auf Straße einrasten" ist kein Menüpunkt mehr, sondern **immer aktiv** (Online-, Deutschland- und alte Mapsforge-Karte). Der alte Schalter und sein gespeicherter Wert werden ignoriert.
- Das Einrasten bleibt konservativ: nur bei eindeutig passendem, lokal aufgelöstem Straßenabschnitt; sonst Roh-GPS. Tempolimit, Blitzer und Ortszoom nutzen weiter das Roh-GPS.
- Da jetzt jede Fahrt über den Straßenabgleich läuft, ist der Listener-Fix aus v0.31.69 (Karte blieb stehen) besonders wichtig und bleibt enthalten, ebenso die Live-Diagnose aus v0.31.70.
- Version: **0.31.71 / versionCode 152**.

## v0.31.70 – Live-Diagnose „Kartennachführung"

- Neue Zeilen auf der Diagnose-Seite zeigen live, an welcher Stufe die Kartennachführung aussteigt: GPS roh → Filter → Weiterleitung (Online / Deutschland / Straßenabgleich) → tatsächliche Kamerabewegung inkl. Verwerfungsgründen (versteckt, folgt nicht, nicht bereit) und Alter der letzten Bewegung.
- Keine Änderung am Fahrverhalten. Enthält den Listener-Fix aus v0.31.69.
- Version: **0.31.70 / versionCode 151**.

## v0.31.69 – Karte bleibt nicht mehr stehen (Straßenkorrektur-Listener)

- **Ursache gefunden:** `GermanyRoadPositionMatcher` hielt seinen Positions-Listener nur über eine `WeakReference`. `MainActivity` übergibt dort einen inline-Lambda, den sonst nichts referenziert. Nach dem ersten Garbage-Collection-Lauf war der Listener weg, der Matcher lieferte keine Kamera-Position mehr (auch nicht über den 350-ms-Fallback), die Karte blieb stehen. Roh-GPS für Tempolimit/Blitzer läuft über einen anderen Pfad und war nicht betroffen.
- Betrifft den Pfad mit aktivierter Option **„Punkt auf Straße einrasten"** (Karteneinstellungen) und vorhandenen Verkehrsdaten.
- Fix: Der Matcher hält den Listener stark (er gehört der Activity und wird in `onDestroy` geschlossen).
- Neuer Regressionstest `GermanyRoadMatcherListenerTest` (schlug vor dem Fix fehl, danach grün).
- Version: **0.31.69 / versionCode 150**.

## v0.31.68 – Fahrzeit-UI-Blockade der Deutschlandkarte entfernt

- Der auf dem Radio reproduzierte Fehler **„Karte läuft kurz und bleibt dann stehen, Tempolimit läuft weiter“** wurde gegen v0.31.49 bis in den 3-Sekunden-Laufzeitpfad verglichen.
- Seit der Deutschland-Umstellung rief `refreshNetworkState` alle **3 Sekunden auf dem UI-Thread** `GermanyOfflineStore.ready()` auf. Diese Methode validiert nicht nur ein Flag, sondern prüft alle Deutschland-Artefakte und öffnet in `trafficReady()` nacheinander **alle 14 regionalen Traffic-SQLite-Datenbanken**. v0.31.49 hatte diesen schweren Fahrzeit-Check nicht.
- Neuer `markedReady()`-Pfad: Im laufenden Fahrbetrieb wird nur noch das persistente READY-Flag gelesen. Die vollständige Paketvalidierung bleibt auf Start/Setup/Diagnose beschränkt.
- Auch `onResume()` führt die große Deutschland-Validierung nicht mehr mehrfach synchron aus.
- Ein neuer Regressionstest verbietet `GermanyOfflineStore.ready()` ausdrücklich im 3-Sekunden-Netzwerk-/Kartenloop.
- Alle Änderungen aus v0.31.67 bleiben enthalten: dauerhaftes Follow, getrenntes Roh-/Kamera-GPS, konservatives Straßeneinrasten mit 350-ms-Fallback und automatischer Innerorts-/Außerorts-Zoom.
- Version: **0.31.68 / versionCode 149**.

## v0.31.66 – Dauerhafte Nachführung + direkter GPS-Pfad

- Reproduktionshinweis vom Radio berücksichtigt: Tempolimits aktualisieren weiter, während die Karte nach wenigen Sekunden stehenbleibt. Damit ist der rohe GPS-Pfad aktiv; nur der Kamerapfad kann aussteigen.
- Die Fahrzeugkarte arbeitet jetzt ausdrücklich im Modus **Nachführung immer aktiv**. Weder Deutschland-MapLibre noch Online-MapLibre noch der alte Mapsforge-Fallback setzen `following=false`. Ein versehentlicher/weitergereichter Karten-Gestenstatus kann die Navigation daher nicht mehr dauerhaft anhalten.
- Der bereits geglättete GPS-Fix aus `FallbackLocationFeed` wird direkt an die **gerade sichtbare Karte** geleitet: online direkt an `OnlineVectorMapPanel`, offline direkt an `GermanyOfflinePanel`. Die unsichtbare Deutschlandkarte ist nicht mehr Zwischenstation für Kamera-GPS.
- Roh-GPS bleibt davon getrennt und geht weiterhin direkt an die Deutschland-Analyse für Tempolimit, feste Blitzer und Ortsklassifikation/Autozoom.
- Beim Sichtbarwerden der Onlinekarte wird sofort auf den zuletzt bekannten GPS-Fix zentriert; danach übernimmt der direkte Live-Feed.
- Alle bisherigen Deutschlandkarten-, Update-, Auto-Resume-, Traffic- und Style-ready-Fixes bleiben erhalten.
- Version: **0.31.66 / versionCode 147**.

## v0.31.65 – Sachsen-Follow-Parität für Deutschland

- Der bewährte Karten-/GPS-Pfad aus v0.31.49 wurde gezielt mit der neuen Deutschlandkarte verglichen. `GpsFollowFilter`, `TravelBearingTracker`, `MapPreferences`, `FallbackLocationFeed`-Filterregeln und die Follow-Schwellen bleiben unverändert.
- Rohes GPS und Kameraposition sind wieder sauber getrennt: Der ungefilterte Fix geht an Tempolimit-/Blitzer-/Ortsanalyse; nur der geglättete Fix bewegt die Karte. Das entspricht der ursprünglichen Absicht von `GpsFollowFilter` und dem alten Sachsen-Pfad.
- Eine versteckte Deutschlandkarte bewegt ihre Kamera nicht mehr im Hintergrund, wenn die Onlinekarte sichtbar ist. Sie analysiert und leitet GPS weiterhin weiter und springt beim erneuten Anzeigen sofort auf die aktuelle Position.
- Die Deutschlandkamera übernimmt die bewährten Follow-Sicherungen aus v0.31.49: unter 2 m kein Zittern, bei normaler GPS-Folge bis 250 m sanftes Gleiten, nach langen Lücken oder großen Positionssprüngen sofortiges Einrasten statt einer langen Animation.
- Der automatische Zoom läuft wieder getrennt von der Positionsbewegung. Neue GPS-Fixes können damit keine laufende Zoomänderung ständig überschreiben.
- Style-ready/Render-Fixes aus v0.31.64, Deutschland-Updateprüfung, Hintergrund-/ACC-Auto-Resume sowie Tempolimit-/Blitzerdaten bleiben erhalten.
- Version: **0.31.65 / versionCode 146**.

## v0.31.64 – Live-Karte ab Style-ready, Renderbestätigung getrennt

- Deutschland-Live-Follow und Ortszoom bleiben wie in v0.31.63 von der Traffic-Analyse und `fully rendered` entkoppelt.
- Zusätzlich beendet jetzt bereits ein erfolgreich angewendeter lokaler MapLibre-Style den 45-Sekunden-Lade-Timeout. Eine sichtbare, benutzbare Deutschlandkarte kann dadurch nicht mehr nur deshalb als fehlgeschlagen markiert werden, weil MapLibre `fully rendered=true` verspätet oder gar nicht meldet.
- Auch die Online-MapLibre-Karte besitzt nun einen eigenen `cameraReady()`-Pfad. GPS-Follow und adaptiver Ortszoom reagieren ab dem Style-Callback und warten nicht mehr auf den strengeren `loaded/ready`-Status.
- Der strengere Lade-/Renderstatus bleibt weiterhin für die sichtbare Umschaltung auf Online sowie für die einmalige Deutschland-Renderbestätigung/Migration erhalten.
- GPS-Quelle, Jitterfilter, Fahrtrichtung, Zoomwerte und Traffic-/Blitzerlogik wurden gegenüber v0.31.63 nicht verändert.

## v0.31.63 – Live-Follow darf nicht auf „fully rendered“ warten

- Behebt den zweiten, auf dem Radio reproduzierten Grund dafür, dass die Deutschlandkarte am Startpunkt stehen bleiben konnte, obwohl GPS-basierte Tempolimits weiter funktionierten.
- `GermanyOfflinePanel` hat die Kamera-Nachführung und den automatischen Ortszoom bisher zusätzlich an `ready()` gekoppelt. Dieses `ready()` wird erst nach MapLibres Callback **vollständig gerendert** gesetzt. Bei lokalen MBTiles kann die Karte bereits sichtbar und benutzbar sein, während dieser Bestätigungsstatus noch aussteht.
- Live-GPS und Ortszoom verwenden jetzt einen eigenen `cameraReady()`-Status: MapLibre-Instanz vorhanden + lokaler Style angewendet + kein Rendererfehler. Dadurch bewegt sich die Karte ab dem ersten benutzbaren Frame weiter und der adaptive Ortszoom kann sofort reagieren.
- Der strenge `fully rendered`-Callback bleibt unverändert für die einmalige Renderbestätigung und die sichere Legacy-/Sachsen-Migration erhalten. Er blockiert aber keine Fahrfunktion mehr.
- Der v0.31.62-Fix (GPS vor Traffic-Analyse) sowie Updateprüfung und Hintergrund-/ACC-Auto-Resume aus v0.31.60/61 bleiben enthalten.
- Version: **0.31.63 / versionCode 144**.

## v0.31.62 – Live-Navigation unabhängig von Verkehrsanalyse

- Behebt den auf dem Radio bestätigten Fehler, dass die Deutschland-/Online-Karte beim Losfahren am Startpunkt stehenbleiben konnte, obwohl Tempolimits weiter erkannt wurden.
- Ursache: Die sichtbare Kartenposition wurde erst nach der asynchronen Abfrage der regionalen Tempolimit-/Blitzer-SQLites veröffentlicht. Diese Abfrage kann auf der Headunit länger als der GPS-Takt dauern.
- GPS folgt jetzt wieder dem bewährten Prinzip der älteren Kartenlogik: Jeder bereits gefilterte Live-Fix wird sofort an Kamera, Fahrtrichtung und gegebenenfalls die Online-MapLibre-Karte weitergegeben.
- Tempolimits, feste Blitzer und Orts-/Zoomklassifikation laufen danach unabhängig im Hintergrund und können die Kartenbewegung nicht mehr blockieren.
- `currentLocation()` bevorzugt den neuesten Live-GPS-Fix; ein verzögerter Traffic-Lookup kann dadurch auch Wetter/Nachtmodus nicht auf einer alten Position festhalten.
- Alle Funktionen aus v0.31.61 (Deutschland-Updateprüfung, Hintergrundaufbau, ACC-/Boot-Auto-Resume und Heartbeat) bleiben erhalten.
- Version: **0.31.62 / versionCode 143**.

## v0.31.61 – Hintergrundaufbau + automatische Wiederaufnahme

- Der Deutschland-Aufbau bleibt ein eigener Foreground-Service und ist weiterhin vollständig vom Kartenmenü bzw. Launcher-Task entkoppelt.
- Der laufende Installations-/Updateauftrag wird vor dem Start persistent gespeichert. Wird der App-Prozess durch ACC/Zündung oder Android beendet, bleibt dieser Auftrag erhalten.
- `GermanyOfflineService` verwendet jetzt `START_REDELIVER_INTENT`: Android soll den letzten Installationsauftrag nach einem Service-/Prozessverlust selbst erneut zustellen.
- Zusätzlich startet `PoloApplication` einen noch offenen Auftrag beim nächsten Prozessstart erneut. `MainActivity` und die Offline-Kartenverwaltung dienen als weitere Fallbacks für restriktive Radio-Firmware.
- Nach einem echten Android-Neustart stellt `GermanyOfflineResumeReceiver` einen offenen Deutschland-Aufbau über `BOOT_COMPLETED` automatisch wieder her.
- Ein 20-Sekunden-Heartbeat hält den Fortschrittsstatus während langer OSM/PBF-Durchläufe frisch. Dadurch erscheint nicht mehr fälschlich „unterbrochen“, nur weil der Parser länger als zwei Minuten keine sichtbare Zahl aktualisiert.
- Ein System-/ACC-bedingtes `onDestroy()` wird nicht mehr als Benutzer-Abbruch behandelt. Nur der ausdrückliche Button **„Aufbau abbrechen“** löscht den Wiederaufnahmeauftrag.
- Bereits fertige Karten, Glyphs und Verkehrsdaten werden nach einer Wiederaufnahme weiterhin lokal übersprungen; vollständige Traffic-PBFs bleiben nach unterbrochener Analyse wiederverwendbar.
- Die manuelle Deutschland-Updateprüfung aus v0.31.60 bleibt unverändert erhalten.
- Version: **0.31.61 / versionCode 142**.

## v0.31.60 – Deutschland-Updates nur noch auf Knopfdruck

- Die alte Mapsforge-/Sachsen-Updateprüfung wurde vollständig aus `OfflineMapStore` entfernt. Die frühere Sachsenkarte bleibt nur noch als Übergangs-Fallback bis die neue Deutschlandkarte einmal erfolgreich gerendert wurde.
- Bei einem fertigen Deutschland-Paket heißt der Hauptbutton jetzt **„Auf Updates prüfen“**. Es gibt keine automatische Kartenprüfung im Hintergrund.
- Die Prüfung verwendet ausschließlich leichte HTTP-`HEAD`-Abfragen für alle 14 Deutschland-Regionen – getrennt für Karten-MBTiles und OSM-Verkehrsquellen.
- Das Ergebnis wird getrennt angezeigt, z. B. **„Karten: 2 Regionen neuer · Verkehr: 1 Region neuer“**.
- Erst nach einer ausdrücklichen Bestätigung startet die Aktualisierung. Der Builder lädt und verarbeitet weiterhin nur Regionen, deren Remote-Datei wirklich neuer/geändert ist.
- Erstinstallation und **„Aufbau fortsetzen“** bleiben strikt lokal-first: gültige vorhandene Teildaten werden nicht wegen einer zwischenzeitlichen Serveränderung neu geladen.
- Version: **0.31.60 / versionCode 141**.

## v0.31.59 – echtes Fortsetzen ohne erneuten Karten-Download

- **„Aufbau fortsetzen“ lädt bereits gültige Bundesland-MBTiles nicht mehr erneut herunter.** Lokale technische Gültigkeit ist im Resume-Modus maßgeblich; geänderte Servergröße/`Last-Modified` lösen dort keinen Neu-Download mehr aus.
- Die Online-Aktualitätsprüfung bleibt erhalten, wird aber nur noch beim ausdrücklich angebotenen **„Deutschland-Karte aktualisieren“** verwendet, wenn das komplette Deutschland-Paket bereits `READY` ist.
- Ein bereits vollständig geladener und gültiger `*.mbtiles.part` wird nach einer Unterbrechung direkt aktiviert, statt vor dem nächsten Versuch gelöscht zu werden.
- Dasselbe Resume-Prinzip gilt für bereits fertig aufgebaute Tempolimit-/Blitzer-Datenbanken: beim Fortsetzen werden sie übernommen; eine Server-Aktualisierung erfolgt erst im echten Update-Modus.
- Enthält weiterhin die v0.31.58-Fixes für Android-SQLite-PRAGMAs und das Behalten vollständig geladener Traffic-PBFs nach Analysefehlern.
- Version: **0.31.59 / versionCode 140**.

## v0.31.58 – SQLite/Verkehrsdaten-Fortsetzen

- Android-kompatible PRAGMA-Ausführung über `rawQuery()` statt `execSQL()` für `journal_mode`, `synchronous` und `temp_store`.
- Behebt den Absturz bei Deutschland-Aufbau Schritt 23/37 (`SQLiteException: Queries can be performed using query or rawQuery methods only`).
- Vollständig geladene OSM-PBFs werden nach Analyse-/SQLite-Fehlern behalten und beim nächsten „Aufbau fortsetzen“ wiederverwendet.
- Nur erfolgreiche Verkehrsdaten-Aktivierung löscht die große PBF-Quelldatei; Teil-Downloads werden weiterhin verworfen.

# Polo Launcher v0.31.57

Ein speziell für ein Android-13-Autoradio und den VW Polo 6R entwickelter Querformat-Launcher. Er verbindet Navigation, Fahrzeugdaten, OBD2-Verbrauch, Restreichweite, DABdream+, Wetter und einen App-Launcher in einer gemeinsamen Cockpit-Oberfläche.




## Neu in v0.31.57 – finaler Glyph-Fix aus v0.31.56

- Finalisiert den Fix für den realen Abbruch bei **Schritt 21/37** (`Glyph-Datei leer`).
- Die frühere feste 100-Byte-Grenze ist vollständig entfernt. Gültige leere MapLibre-Glyph-PBFs (ca. 32 Byte) werden anhand ihrer Protobuf-Struktur und des erwarteten Unicode-Ranges akzeptiert.
- Bereits vorhandene gültige `<range>.pbf.part`-Dateien werden beim **Aufbau fortsetzen** direkt übernommen; die bereits geladenen Deutschland-MBTiles und Traffic-Daten bleiben erhalten.
- Ungültige HTML-/JSON-/Proxy-Antworten werden weiterhin verworfen; MapLibre plus GitHub-Fallback bleiben aktiv.
- Android-Packaging-Fix für die doppelte `osmosis-plugins.conf` bleibt enthalten.
- Versionsnummer auf **0.31.57 / versionCode 138** erhöht, damit dieser korrigierte Stand eindeutig von den verschiedenen 0.31.56-Paketen zu unterscheiden ist.

## Neu in v0.31.56 – Glyph-Reparatur + Android-Packaging-Fix

- Behebt den realen Radiofehler **„Glyph-Datei leer“** bei Schritt 21/37. Vorhandene gültige Deutschland-MBTiles und Traffic-Daten werden weiterverwendet; nur fehlende/ungültige Glyph-Ranges werden repariert.
- **Root-Cause-Fix:** Eine gültige leere MapLibre-Glyph-Range kann nur ~32 Byte groß sein. v0.31.55 hat solche korrekten PBFs fälschlich als „leer“ verworfen. v0.31.56 prüft deshalb die Protobuf-Struktur und den deklarierten Range-Namen statt einer 100-Byte-Grenze.
- HTML/JSON/kaputte Antworten werden durch die strukturelle PBF-Prüfung verworfen; bei echtem Downloadfehler gibt es zusätzlich den GitHub-Fallback.
- Pro Glyph-Range gibt es Wiederholungsversuche über `demotiles.maplibre.org` und anschließend einen unabhängigen Raw-GitHub-Fallback aus dem offiziellen `maplibre/demotiles`-Repository.
- Behebt den Android-Gradle-Fehler `2 files found with path 'osmosis-plugins.conf'` über AGP `packaging.resources.pickFirsts`.
- **Keine Datenmigration:** Speicherpfade/Preferences/Dateinamen bleiben unverändert, damit bereits geladene ~5,5 GB beim App-Update erhalten bleiben.

## Neu in v0.31.55 – Regionale Traffic-Aktivierung für Deutschland

- Im Launcher gibt es weiterhin nur **Online** und **Offline Deutschland**. Keine Bundeslandauswahl und keine separate Verkehrskarte.
- Die Offline-Darstellung nutzt lokale Geofabrik-Shortbread-MBTiles als Basiskarte. **POI- und ÖPNV-Layer werden bewusst nicht dargestellt oder ausgewertet.** Orts- und Straßennamen bleiben erhalten.
- Die zusätzlichen Fahrdaten enthalten ausschließlich **Straßen mit verwertbaren Tempolimit-Tags** und **feste Blitzer/Enforcement-Geräte**. Normale Straßen ohne Tempodaten werden nicht mehr in den Verkehrsindex übernommen.
- **Neu in v0.31.55:** Zur Laufzeit werden nicht mehr alle 14 Verkehrs-SQLites offen gehalten. Anhand der GPS-Position bleibt nur die aktuelle Traffic-Region aktiv; bis 20 km vor einer Grenze wird eine passende Nachbarregion vorgeladen.
- Die bisherige Region bleibt mit 25-km-Hysterese kurz geöffnet, damit es beim Grenzübertritt keine Lücke gibt. Nach jeweils 5 km Fahrt wird die Regionsauswahl neu geprüft.
- Geschlossene Regionen bleiben ausschließlich auf dem Flash-Speicher. Nur die aktuell benötigten SQLite-Seiten werden von Android/SQLite in den RAM-Cache geladen; die Deutschlandkarte selbst bleibt vollständig verfügbar.
- Im Diagnose-Log erscheint bei einem Wechsel z. B. `Deutschland Verkehr aktiv: Sachsen + Thüringen · 2/14 Indizes geöffnet`.
- Damit entfällt der speicherkritische Vollstraßen-Import aus v0.31.53. Der PBF-Scanner hält beim Zwei-Pass-Aufbau nur die benötigten Nodes der Tempolimit-Straßen plus Enforcement-Geräte im Speicher.
- Brandenburg deckt Berlin ab und Niedersachsen deckt Bremen ab; die doppelten Berlin-/Bremen-Downloads wurden entfernt. Intern werden dadurch 14 Geofabrik-Extrakte verwendet.
- Tempolimits werden weiterhin auch im Stand erkannt. Richtungsabhängige Werte werden ohne brauchbare Fahrtrichtung nicht geraten.
- Blitzer bleiben bis 700 m voraus verfügbar. Fehlt beim kompakten Tempolimit-Index eine Straßenachse am Blitzer, wird nur die sichere Vorwärts-/Entfernungsprüfung verwendet statt den Blitzer komplett zu verwerfen.
- Deutschland verwendet im kompakten Modus **kein Straßen-Snapping** auf den Tempolimit-Segmenten; sonst könnte ein Fahrzeug auf einer Straße ohne explizites `maxspeed` fälschlich auf eine benachbarte Tempolimit-Straße gezogen werden.
- Die Deutschland-Signatur enthält keine wachsenden Downloadgrößen mehr. Dadurch kann ein mehrstündiger Download die Hauptseite nicht mehr alle paar Sekunden neu erstellen.
- Die alte Sachsen-/Mapsforge-Karte wird **nicht beim Downloadabschluss gelöscht**. Sie bleibt als Fallback erhalten, bis MapLibre mit der neuen Deutschlandkarte tatsächlich einen vollständig gerenderten Deutschland-Frame gemeldet hat.
- Schlägt die neue Offline-MapLibre-Karte vorher asynchron fehl, versucht der Launcher automatisch die alte Mapsforge-Karte als Fallback.

**Wichtig:** Die von Geofabrik gelieferten Shortbread-MBTiles können intern weiterhin POI-Features enthalten. v0.31.55 lädt jedoch keinen eigenen POI-Index, rendert keine POI-/ÖPNV-Layer und nutzt diese Daten nicht. Ein physisches Entfernen aus jedem Vector-Tile würde eine komplette MVT-Neukodierung auf dem Autoradio erfordern und ist absichtlich nicht Teil des Installationswegs.

## Neu in v0.31.52 – Blitzer-Diagnose + Tempolimit im Stand

- Ein tatsächlich eingeblendeter fester Blitzer wird jetzt erst **nach dem Gate** protokolliert: Quelle, OSM-ID (bei Verkehrsdatenbank), Entfernung, Position und `Anzeige=JA`. Beim Verschwinden der Warnung wird auch das Ausblenden geloggt.
- Tempolimits werden ausdrücklich ohne Mindestgeschwindigkeit ausgewertet. Solange noch kein Limit gefunden wurde, verhindert fehlende Bewegung keine erneute Suche.
- Beim Wiederöffnen/Fortsetzen der Karte wird eine frische letzte Position sofort erneut an die Tempolimit-Erkennung gegeben, auch wenn das Fahrzeug steht.
- Richtungsabhängige OSM-Limits werden im Stand weiterhin **nicht geraten**: ohne verlässliche Fahrtrichtung wird nur ein eindeutiges allgemeines Limit verwendet.

## Neu in v0.31.51 – Verkehrsdaten-Import gehärtet

- Der PBF-Scanner übernimmt nur noch Straßen, deren Tempoinformation wirklich auf einen verwertbaren Wert aufgelöst werden kann. Reine Metadaten wie `source:maxspeed=sign` oder unklare Angaben wie `maxspeed=signals` blähen die lokale Datenbank nicht mehr unnötig auf.
- Bei der Laufzeitsuche kann eine geometrisch nähere Straße mit nicht auflösbarer Tempoinformation keine zweite, ebenfalls passende Straße mit einem gültigen Tempolimit mehr verdecken. Unbekannte Kandidaten werden übersprungen.
- Der WakeLock für Download + zwei vollständige PBF-Pässe + SQLite-Aufbau wurde von 30 Minuten auf maximal 2 Stunden erweitert. Damit bricht ein langsamer Import auf dem Autoradio nicht nur wegen der längeren Verarbeitungszeit aus dem CPU-Wachzustand.
- Zusätzliche Regressionstests prüfen unbrauchbare `source:maxspeed`-Metadaten, `maxspeed:type=DE:urban`, richtungsabhängige Limits und die neuen Schutzregeln.
- Alle bestehenden JVM-/Quelltests laufen weiterhin durch; ein vollständiger Android-Gradle/APK-Build ist in dieser Umgebung weiterhin nicht verfügbar.


## Neu in v0.31.50 – eigene Offline-Verkehrsdatenbank

- Die normale Mapsforge-Karte bleibt unverändert für Darstellung, Ortszoom und Straßenkorrektur.
- Zusätzlich kann für die **ausgewählte Offline-Region** eine kleine Verkehrsdatenbank aufgebaut werden.
- Quelle ist direkt der aktuelle Geofabrik-OSM-PBF-Extrakt der Region, z. B.
  `sachsen-latest.osm.pbf`. Für Sachsen liegt der Rohdownload derzeit bei ungefähr 256 MB.
- Der PBF wird auf dem Radio **zweimal gestreamt**: Pass 1 merkt nur Knoten von befahrbaren Straßen
  mit `maxspeed`/`zone:maxspeed`/Richtungstags; Pass 2 schreibt nur diese Straßensegmente und
  feste `highway=speed_camera`-Punkte in SQLite.
- Nach erfolgreichem Aufbau wird die große `.osm.pbf`-Rohdatei automatisch gelöscht. Im Alltag
  bleibt nur die kompakte, räumlich indizierte SQLite-Datenbank.
- Download und Konvertierung laufen als Foreground-Service mit Fortschritt und Abbrechen-Funktion.
  Die bisherige Verkehrsdatenbank bleibt bis zu einem vollständig erfolgreichen Austausch aktiv.
- Tempolimit- und Blitzer-Erkennung bevorzugen die neue Verkehrsdatenbank. Falls sie nicht installiert
  ist, bleibt die bisherige Mapsforge-Erkennung als Fallback erhalten.
- Die Offline-Kartenverwaltung enthält dafür eine neue Karte **„VERKEHRSDATEN“** mit
  Herunterladen, Prüfen/Aktualisieren, Abbrechen und Löschen.
- Diagnose zeigt Region, OSM-Stand, Segment-/Blitzerzahl, DB-Größe und die tatsächlich verwendete
  Tempolimitquelle.
- Datenquelle: Geofabrik / OpenStreetMap-Mitwirkende, ODbL.


## Neu in v0.31.49 – Tempolimit-Diagnose + Sniffer entfernt

- Die Diagnose zeigt live, ob die Offline-Karte Tempolimitdaten unterstützt, welche Straße gefunden wurde, Abstand/Suchradius, das verwendete OSM-`maxspeed` und den tatsächlich angezeigten Wert.
- Fehlt das Schild, steht der konkrete Grund dabei: GPS zu ungenau, außerhalb der Offline-Karte, keine passende Straße, kein nutzbares `maxspeed` oder fehlende `maxspeed`-Tags.
- Die alte Sniffer-Heuristik für unbekannte Broadcasts wurde vollständig entfernt.
- Unbekannte Broadcast-/Vendor-Daten bleiben rein diagnostisch und können keine Fahrzeugwerte mehr anhand geratenen Schlüsselnamens überschreiben.


## Neu in v0.31.48 – Android-Java-Compile-Fix

- Doppelte `onBackPressed()`-Methode in `SettingsActivity` zusammengeführt. Die vorhandene Seitennavigation bleibt erhalten; während eines laufenden Hintergrundbild-Speichervorgangs wird Zurück weiterhin kurz blockiert.
- `SettlementZoomAnalyzer` behält jetzt die tatsächlich geprüfte `Location source`, damit der Schutz gegen veraltete Mapsforge-Ergebnisse kompilierbar und korrekt ist.
- Die JVM-Testkompilierung kompiliert ab jetzt zusätzlich `SettlementZoomAnalyzer` und `SpeedLimitAnalyzer` wirklich mit. Genau der fehlende Compile-Schritt hatte den `source`-Fehler zuvor nicht erkannt.
- SourceAudit prüft zusätzlich, dass `SettingsActivity` `onBackPressed()` nur einmal definiert.


## Neu in v0.31.47 – Mapsforge Build-Fix

- JitPack wieder in `dependencyResolutionManagement.repositories` eingetragen.
- Die Mapsforge-0.30.0-Abhängigkeiten bleiben bei den JitPack-Submodul-Koordinaten `com.github.mapsforge.mapsforge:*`.
- Ursache des Fehlers in v0.31.46: Beim Cleanup wurde JitPack entfernt, obwohl Mapsforge weiterhin darüber bezogen wird. Google Maven und Maven Central können diese Koordinaten nicht auflösen.
- Neuer Regressionstest: Sobald `app/build.gradle` Mapsforge über `com.github.mapsforge.mapsforge:*` verwendet, muss `settings.gradle` zwingend `https://jitpack.io` enthalten.


## Neu in v0.31.46 – vollständiger Deep-Debug des gesamten Projekts

Diesmal wurde nicht nur die Kartenfunktion geprüft, sondern der komplette Produktionsbaum:
Android-Lifecycle, UI, Hintergrundthreads, Datei-/Netzwerk-I/O, CAN/HCT, OBD2, DAB,
CarPlay, Reichweite/Verbrauch, Wetter/Ort, Online-/Offline-Karte, Map-Worker,
SharedPreferences/Migrationen, Bitmaps/Blur, Broadcast-/Binder-Eingaben, Manifest,
Gradle-Konfiguration und Ressourcen.

Konkrete Korrekturen:
- Hintergrundbild-Import und Zurücksetzen laufen nicht mehr mit Kopieren/fsync/Dateioperationen auf dem UI-Thread.
- Diagnose-Dateiexport läuft nicht mehr über einen potenziell langsamen DocumentProvider auf dem UI-Thread.
- Der exportierte CAN-Broadcast-Empfänger begrenzt jetzt Top-Level-Extras und die Länge des generischen Diagnoseaufbaus; verschachtelte Daten waren bereits begrenzt.
- Übergroße HCT-Binder-Callback-Frames werden früh verworfen.
- Nach einem erfolgreichen Offline-Karten-Download wird die exakt passende Server-Metadatenbasis (`ETag`/`Last-Modified`/Größe) gespeichert. Dadurch kann eine frisch geladene Karte nicht direkt wieder fälschlich als Update erscheinen.
- Ortszoom nutzt für Geschwindigkeits-Tags jetzt denselben einheitenbewussten Parser wie das Tempolimit. `50 mph` wird deshalb nicht mehr als innerörtliche 50 km/h missverstanden.
- Feste Blitzer an Kreuzungen behalten mehrere gleich nahe Straßenachsen. Ein zufällig zuerst gelesener Querweg kann einen korrekten Treffer nicht mehr allein verwerfen.
- Ergebnisse langsamer Mapsforge-Lesevorgänge werden verworfen, wenn das Fahrzeug inzwischen deutlich weitergefahren ist; dadurch können veraltete Orts-/Limit-/Blitzerwerte nicht kurz vor dem neuen Ergebnis aufblitzen.
- UsageStats- und CarPlay-Paketabfragen wurden aus dem 500-ms-Service-Mainloop auf einen begrenzten Hintergrundworker verlegt.
- Serielle CAN-Resynchronisierung ist jetzt iterativ und bricht Marker-Stürme begrenzt ab; dadurch kann beschädigter Eingang keine Rekursionstiefe oder quadratische Wiederholung aufbauen.
- HCT-Binder-Dekodierung läuft nicht mehr unter dem globalen Binder-Monitor; der OEM-Poller synchronisiert seine Zeitplanfelder konsistent.
- DAB-MediaSession-Rennen beim Verschwinden einer Session werden abgefangen. Titel/Interpret/Stationsmetadaten sind auf 512 Zeichen begrenzt, bevor sie geloggt, gespeichert oder angezeigt werden.
- Wallpaper-Blur-Invalidierung recycelt kein Bitmap mehr, das noch von einem Drawable-Shader benutzt werden könnte.
- Die Update-Metadaten einer frisch heruntergeladenen Karte werden nach der sicheren Aktivierung asynchron ermittelt; eine langsame HEAD-Anfrage verzögert die Kartenaktivierung nicht.
- Weitere sicher bestätigte unbenutzte Getter/Helfer entfernt.
- Ungenutztes JitPack-Repository entfernt; Abhängigkeiten kommen nur noch aus den tatsächlich benötigten Repositories.


## Neu in v0.31.45 – Deep Debugging

- **Blitzer-Richtungsfehler behoben:** Die Orientierung einer OSM-Straßenlinie ist keine Fahrtrichtung. Eine Straße, deren Knoten in Gegenrichtung gespeichert waren, konnte einen korrekten Blitzer bisher verwerfen. Straßenachsen behandeln jetzt 0° und 180° korrekt als dieselbe Straße.
- **Karten-Update-Erkennung gehärtet:** Eine geänderte Dateigröße wird nicht mehr von einem unveränderten `Last-Modified` verdeckt. Bei alten Installationen wird außerdem eine klar neuere Serverkarte nicht mehr nur deshalb als „aktuell“ übernommen, weil sie zufällig exakt dieselbe Byte-Größe hat.
- **Offline-Datenfähigkeit wird jetzt aus dem Mapsforge-Header geprüft.** Tempolimit- und Blitzer-Scanner laufen nicht mehr sinnlos weiter, wenn die gewählte `.map` die benötigten `maxspeed`-/`speed_camera`-Tags überhaupt nicht enthält. Der Grund wird im Diagnose-Log sichtbar.
- Tempolimitparser um reale deutsche OSM-Schreibweisen wie `zone:maxspeed=DE:30`, `DE:zone30`, `DE:zone:30` und `maxspeed:type` erweitert.
- Straßenverbinder (`primary_link`, `secondary_link`, `tertiary_link`) werden in der Orts-/Straßenlogik konsistenter berücksichtigt.
- Kamera-Overlay erzeugt beim Zeichnen kein neues `Paint`-Objekt mehr; reduziert unnötigen GC-Druck.
- Offline-Kartenverwaltung fragt im Leerlauf nur noch alle **5 s** statt jede Sekunde Dateisystem/DownloadManager ab; während eines Downloads bleibt die 1-s-Aktualisierung erhalten.
- Nicht verwendete Update-Metadaten-Felder (`checkedAt`-UI-Kopie und nie gelesene Remote-Mirror-Werte) entfernt.
- Deep-Lint auf dem JVM-kompilierbaren Teil ausgeführt; keine Produktionswarnungen, nur ein irrelevanter `serialVersionUID`-Hinweis in einem Android-Teststub.

## Neu in v0.31.44 – Code-Cleanup

- Unbenutzte öffentliche/helper APIs entfernt, die im gesamten Projekt keinen Aufrufer mehr hatten.
- Alte, nicht verwendete `PageListener`-Schiene aus `PagedLauncherLayout` entfernt; die aktive `PageGestureListener`-Logik bleibt unverändert.
- Nicht genutzte HCT/CAN-Diagnosegetter und der unbenutzte generische Read-Query-Builder entfernt. Aktive Diagnose-Summaries, CAN-Decoding und Binder-Kommunikation bleiben erhalten.
- Unbenutzten `totalPackets`-Zähler aus dem HCT-Decoder entfernt; der tatsächlich verwendete `validPackets`-Zähler bleibt erhalten.
- Unbenutzte Map-Helfer (`currentSpeedLimit`, `anyUpdateAvailable`) entfernt.
- Unbenutzte `SystemCanReceiver.actionSnapshot()`, `VehicleRepository.isRecent()`, `GlassBackgroundView.refreshBackground()` und die unbenutzte Farbe `ACCENT_STRONG` entfernt.
- Imports nach dem Cleanup erneut geprüft und verwaiste Imports entfernt.
- Migrationscode für alte Nutzer-/Verbrauchs-/Hintergrunddaten wurde bewusst **nicht** entfernt, da er bei Updates noch echte Daten übernehmen kann.
- Manifest-Komponenten wie `PoloApplication` wurden ebenfalls geprüft und bleiben erhalten, auch wenn sie nicht aus Java referenziert werden, weil Android sie über das Manifest startet.


## Neu in v0.31.43 – Kartenstand und Update-Erkennung

- Die Offline-Kartenverwaltung zeigt bei jeder installierten Region jetzt **`Kartenstand: dd.MM.yyyy`**. Das Datum wird direkt aus dem Erstellungsdatum im Mapsforge-Dateikopf gelesen und ist daher nicht bloß das lokale Download-Datum.
- Beim Öffnen der Offline-Kartenverwaltung werden installierte Karten automatisch auf eine neuere Serverversion geprüft – pro Region höchstens **einmal in 24 Stunden**.
- Zusätzlich gibt es den Button **„Auf Karten-Updates prüfen“** für eine sofortige manuelle Prüfung.
- Die Serverprüfung verwendet eine leichte HTTP-`HEAD`-Abfrage und merkt sich **ETag**, `Last-Modified` und Dateigröße.
- Bei älteren bereits installierten Karten ohne gespeicherte Server-Metadaten wird die vorhandene Datei beim ersten Check sicher migriert: gleiche Dateigröße gilt als aktuelle Ausgangsversion; bei fehlender Größenangabe dient das eingebettete Mapsforge-Datum mit Toleranz als Fallback.
- Gibt es eine neuere Datei, steht direkt an der Region **`UPDATE VERFÜGBAR`** und der mittlere Button heißt **„Aktualisieren“** statt „Neu laden“.
- Nach einem erfolgreichen Karten-Update wird die Versionsbasis bewusst zurückgesetzt und beim nächsten HEAD-Check frisch an die gerade installierte Serverdatei gebunden.
- Die bestehende sichere Austauschlogik bleibt unverändert: Die alte Karte bleibt erhalten, bis der neue Download vollständig geladen, als Mapsforge-Datei validiert und sicher aktiviert wurde.


## Neu in v0.31.42 – optionale feste Blitzer aus der Offline-Karte

- Neuer Schalter unter **Karte & GPS → Verkehrswarnungen**: `Feste Blitzer aus Offline-Karte anzeigen`.
- Die Funktion ist **standardmäßig AUS** und wird nur nach ausdrücklicher Aktivierung ausgewertet.
- Quelle ist ausschließlich die ausgewählte Mapsforge-/OSM-Offlinedatei. Erkannt werden explizite OSM-Kameraeinträge (`highway=speed_camera` bzw. `enforcement=maxspeed`); fehlende Daten werden nicht ergänzt oder geraten.
- Bei aktivierter Funktion erscheint oben links in der Kartenkachel ein selbst gezeichnetes Kamera-Symbol mit der Entfernung zum nächsten passenden festen Blitzer.
- Warnbereich maximal **700 m**. Kandidaten müssen vor dem Fahrzeug liegen und – sofern die Straßengeometrie ermittelbar ist – zur aktuellen Fahrtrichtung passen. Kameras auf deutlich quer verlaufenden Straßen werden dadurch verworfen.
- Die Suche nutzt einen gecachten **3×3-Tile-Bereich** der Offline-Karte und läuft auf einem eigenen Worker; dadurch gibt es nicht bei jedem GPS-Tick neun neue Datei-Lesevorgänge.
- Kurze GPS-/Kartenaussetzer halten eine bestehende Anzeige höchstens 3 Sekunden; ohne frische Daten wird die Warnung spätestens nach 5 Sekunden ausgeblendet.
- Die Erkennung läuft auch weiter, wenn MapLibre als Online-Karte sichtbar ist, solange eine Offline-Region ausgewählt ist.


## Neu in v0.31.41 – Tempolimit aus der Offline-Karte

- Oben rechts in der Kartenkachel erscheint jetzt ein **echtes gezeichnetes Tempolimit-Verkehrsschild**: weißer Kreis, roter Rand, schwarze Zahl. Keine Emoji-/Font-Grafik.
- Die Daten kommen aus der installierten **Mapsforge-/OpenStreetMap-Offlinedatei**. Dadurch funktioniert die Erkennung auch dann, wenn auf dem Bildschirm gerade die Online-MapLibre-Karte angezeigt wird.
- Die aktuelle Straße wird anhand von Position, GPS-Genauigkeit, Fahrtrichtung und Straßengeometrie bestimmt; Tile-Nachbarn an Kartengrenzen werden ebenfalls geprüft.
- Unterstützt werden `maxspeed`, `maxspeed:forward`, `maxspeed:backward`, `zone:maxspeed` sowie sichere deutsche Standardwerte wie `DE:urban` (50) und `DE:rural` (100).
- `none`/`DE:motorway` wird als **unbeschränkt** erkannt; weil dafür kein numerisches Tempolimit-Schild sinnvoll ist, wird das Schild in diesem Zustand ausgeblendet.
- Unklare Angaben wie `signals`, zusammengesetzte Werte oder fehlende Daten werden **nicht geraten**.
- Gegen Flackern an Kreuzungen braucht ein geändertes Limit zwei übereinstimmende Treffer. Kurze OSM-Lücken halten das letzte sichere Schild bis zu 6 Sekunden; ohne frische GPS-Daten verschwindet es spätestens nach 8 Sekunden.


## Neu in v0.31.40 – adaptiver, stabiler Ortszoom

- Ortszoom reagiert jetzt deutlich schneller: im stabilen Modus etwa alle **2 s / 15 m** statt 8 s / 55 m; der direkte Modus bleibt noch schneller.
- Neue **Vorausschau in Fahrtrichtung**: bei guter GPS-/Kursqualität wird zusätzlich ein Punkt ungefähr 4 Sekunden voraus geprüft, begrenzt auf 25–100 m. Dadurch kann der Innerorts-Zoom schon kurz vor dem erkannten Ortseingang aktiv werden.
- **Asymmetrische Hysterese**: hineinzoomen ist schnell, herauszoomen absichtlich streng. Im stabilen Modus bleiben mindestens 10 s Innerorts erhalten; danach braucht es mindestens 3 klare, aufeinanderfolgende Außerorts-Erkennungen und ungefähr 120 m Strecke. Im direkten Modus gelten kürzere, aber weiterhin geglättete Grenzen.
- `UNKNOWN`/OSM-Lücken innerhalb eines Ortes halten den bisherigen Innerorts-Zustand und unterbrechen eine begonnene Außerorts-Serie. Dadurch soll das bisher beobachtete **15 → 13 → 15** mitten in Ortschaften verschwinden.
- OSM-Erkennung erweitert: neben Wohn-/Gewerbe-/Retail-Flächen zählen jetzt auch **Industrieflächen**, `highway=residential`, `living_street`, urbane `maxspeed`-/`zone:maxspeed`-Hinweise, Straßenbeleuchtung und nahe Gebäude als zusätzliche Indizien.
- Straßen an Polygonrändern erhalten einen kleinen Puffer, damit eine Hauptstraße mitten im Ort nicht nur deshalb als außerorts gilt, weil die Wohnfläche erst an der Grundstücksgrenze beginnt.
- Der Mapsforge-Worker verwirft während einer laufenden Prüfung nicht mehr einfach die neueste GPS-Position, sondern merkt sie vor und verarbeitet anschließend den aktuellsten Stand.
- Zoomanimation beschleunigt: Offline-Zoomschritte **500 ms** statt 950 ms, Online-Zoom **600 ms** statt 800 ms.


## Neu in v0.31.39 – Schnellstart/Standby für DABdream+ abgesichert

- Der normale Weg über Android `SCREEN_OFF` / `SCREEN_ON` bleibt unverändert.
- Zusätzlich überwacht der bereits laufende Reichweiten-Hintergrunddienst seinen 500-ms-Heartbeat. Bleibt dieser **mindestens 15 Sekunden** aus, obwohl der Prozess danach weiterläuft, wird das als proprietärer Headunit-Schnellstart/Standby erkannt.
- Dieser Fallback greift nur, wenn nicht gerade ein reguläres `SCREEN_ON` angekommen ist; dadurch wird derselbe Wake-Vorgang nicht doppelt verarbeitet.
- Beim erkannten Schnellstart wird der DAB-Autostart wieder scharfgeschaltet. Ist die Hauptseite sichtbar, wartet sie wie bisher auf bestätigtes Internet, startet DABdream+ und kehrt nach 3 Sekunden zum Polo Launcher zurück.
- Wenn der Launcher beim Aufwachen noch nicht sichtbar ist (z. B. weil CarPlay zuerst vorne ist), bleibt DAB nur scharfgeschaltet. Sobald der Launcher durch CarPlay-Auto-Return oder normal wieder sichtbar wird, startet die bestehende DAB-Logik.
- Ein Prozess-Neustart wird nicht fälschlich als Standby erkannt.


## Neu in v0.31.38 – CarPlay automatisch zurück zum Launcher

- Neuer Schalter unter **Darstellung & Medien → CarPlay**: `Nach automatischer CarPlay-Verbindung zum Launcher zurück`.
- Ist der Schalter aus, ändert sich am bisherigen Verhalten nichts.
- Ist er an und Androids **Nutzungszugriff** wurde einmal erlaubt, erkennt der Hintergrunddienst, wenn die installierte CarPlay-App automatisch in den Vordergrund kommt.
- Der Launcher wartet **5 Sekunden**, damit CarPlay die Verbindung zum iPhone sauber aufbauen kann, und holt danach einmal den Polo Launcher nach vorn.
- Kein Hin-und-her: derselbe CarPlay-Vordergrundvorgang wird nur einmal behandelt; erst nachdem CarPlay mindestens eine Minute nicht im Vordergrund war, wird für eine neue Verbindung neu scharfgeschaltet.
- Öffnest du CarPlay absichtlich über die linke Leiste oder **Alle Apps**, wird die Automatik für diesen Vorgang unterdrückt und CarPlay bleibt im Vordergrund.
- Usage Access wird ausschließlich benutzt, um die aktuelle Vordergrund-App zu erkennen; die Funktion ist vollständig abschaltbar.


## Neu in v0.31.37 – Fehler- und Performance-Paket

- **Mobilfunk-Netztyp:** Android 13 erhält jetzt `READ_BASIC_PHONE_STATE`, damit LTE/5G im Sidebar-Status ausgelesen werden kann.
- **RPM/Motorstatus:** der Mode-3-Motorstatus gilt nur noch 5 Sekunden. Ein alter „Motor AUS“-Frame kann die Drehzahl nicht mehr dauerhaft auf 0 festhalten.
- **Nur ungefähre Standortfreigabe:** GPS- und Netzwerk-Provider werden getrennt behandelt; eine fehlende FINE-Berechtigung blockiert den Network-Provider nicht mehr.
- **Ortsname/Nominatim:** ein abweichender Einzel-Treffer wird nicht mehr sofort dauerhaft gespeichert; gespeichert werden nur angezeigte bzw. bestätigte Orte.
- **Glass-Blur:** im normalen Zeichenpfad werden keine SharedPreferences-/Datei-Metadaten mehr pro Kachel abgefragt. Der Cache wird nur bei Hintergrundwechsel bzw. Displaygrößenänderung neu aufgebaut.
- **Straßen-Snapping/Ortszoom:** nahe Mapsforge-Tile-Grenzen werden die tatsächlich angrenzenden Tiles zusätzlich geprüft (maximal vier statt pauschal 3×3).
- **Online-Nachtmodus:** ein MapLibre-Stil wird nur neu geladen, wenn sich die tatsächlich verwendete OpenFreeMap-Style-URL ändert.
- **OBD:** laufender 100-km-Verbrauch wird direkt aus dem Averager gelesen statt alle 500 ms zu serialisieren/parsen; gespeicherter Wert wird gecacht. `BluetoothSocket.close()` läuft beim Stoppen außerhalb des UI-Threads.
- **Offline-Karten:** Aktionsmeldung verschwindet nach 5 Sekunden wieder zugunsten „Aktive Karte …“; Kartenzeilen haben 96 dp statt 94 dp.


## Neu in v0.31.36 – kompletter Tiefencheck

- Alle Produktionsquellen und Ressourcen erneut statisch geprüft.
- Hintergrundbilder werden speicherschonender dekodiert: 4K/8K-Fotos und extreme Panoramen erzeugen nicht mehr unnötig riesige ARGB-Bitmaps im RAM.
- Hintergrunddateien sind auf 64 MB begrenzt; normale JPG/PNG/WEBP-Wallpaper liegen deutlich darunter.
- Einen unerreichbaren gemischt geschriebenen Schlüssel in der Diagnose-Heuristik für die Tür hinten rechts korrigiert (`doorrR` → `doorrr`).
- Wetter-/Orts-Netzwerkcallbacks greifen nach dem Zerstören der Activity nicht mehr auf alte Views zu.
- Nicht mehr benötigte Persistable-URI-Anforderung, drei unbenutzte Imports, eine unbenutzte lokale Variable und die wirkungslosen Skip-Glyph-Argumente entfernt.
- Neuer Regressionstest für die Wallpaper-Speicherbegrenzung sowie zusätzliche SourceAudit-Schutzprüfungen.


## Neu in v0.31.35 – Drehzahl bei Motor AUS

- Fehler behoben: Der CAN-Bus konnte nach dem Abstellen des Motors den letzten positiven Drehzahlwert (z. B. `1095 U/min`) stehen lassen.
- Der bereits bestätigte HCT-Status `0x41 / Mode 3` wird jetzt als Motorstatus genutzt: **Ölwarnung + Ladewarnung gemeinsam = Motor AUS**.
- In diesem Zustand zeigt der Launcher sofort **`0 U/min`**, selbst wenn anschließend noch ein eingefrorener Mode-2-Drehzahlwert wiederholt wird.
- Sobald beide Warnungen wieder aus sind, werden frische Drehzahlen normal angezeigt.
- Einzelne Öl- oder Ladewarnungen werden nicht als Motor-Aus interpretiert, damit echte Warn-/Fehlerzustände nicht falsch ausgewertet werden.


## Neu in v0.31.34 – BuildConfig-Kompilierfehler behoben

- `BuildConfig.VERSION_NAME` wird in `WeatherClient` und `LocalityClient` für den dynamischen User-Agent verwendet.
- In AGP 8.x muss die BuildConfig-Generierung dafür explizit aktiviert sein; `app/build.gradle` enthält jetzt `buildFeatures { buildConfig true }`.
- Der SourceAudit-Test prüft das jetzt ebenfalls, damit dieser Fehler künftig nicht mehr unbemerkt durch die Quelltests kommt.


## Neu in v0.31.33 – vollständiger Cleanup-Review

- Nicht mehr erreichbare Altteile entfernt: **alter Skin-/Farben-Chooser**, **CopperSkin**, **altes selbst gezeichnetes CarPlay-Icon** und mehrere unbenutzte Dashboard-Hilfsmethoden.
- Es gibt keinen versteckten alten Skin-Zustand mehr; die Oberfläche verwendet nur noch den aktuellen Glass-Look.
- Dialoge laufen zentral über `GlassSkin.dialog()`.
- Hintergrundwechsel ist robuster: das vorherige Bild bleibt als Backup erhalten; ein unterbrochener Wechsel wird beim nächsten Start automatisch wiederhergestellt.
- Frosted-Glass-Flächen liefern jetzt auch eine echte **runde Outline** für Clipping und Elevation/Schatten.
- Wetter- und Ortsdienst verwenden automatisch die aktuelle `BuildConfig.VERSION_NAME` statt der alten festen Kennung `0.31.4`.
- Der alte Kupfer-Systemakzent wurde auf den aktuellen Glass-Akzent umgestellt.
- Die statischen Tests wurden um Guards gegen die entfernten Altpfade erweitert.


## Neu in v0.31.32

- DAB-Skip-Symbole sehen weiterhin wie die bisherigen **zwei weißen Pfeile** aus; ergänzt wurde nur der senkrechte Skip-Strich. Sie werden als eigene Geometrie gezeichnet, nicht als Emoji oder systemabhängiges Symbol.
- Unter dem Datum steht jetzt dynamisch der **Wochentag** (`Montag`, `Dienstag`, …) im gleichen dezenten, fetten Stil wie das Datum.
- Dynamische Texte nochmals geprüft. Sendername, Titel, Interpret, Netzbetreiber, Empfang, Wetter-Ort, Temperaturen und Fahrzeugwerte hatten bereits Auto-Size. Zusätzlich abgesichert wurden **GPS-Status**, **Türwarnung** und der wechselnde **Kartenstatus**.


## Neu in v0.31.31

- Die beiden DAB-Skip-Symbole werden jetzt **nicht mehr als Schriftzeichen** gerendert, sondern geometrisch gezeichnet.
- Dadurch sitzen Strich und Doppelpfeile **optisch exakt mittig** in den runden Glass-Buttons.
- Form, Größe, Abstand, Glass-Hintergrund und Bedienfunktion der Buttons bleiben unverändert.
- Auch beim Play/Pause-Text wurde das zusätzliche Font-Padding deaktiviert, damit die Mitte sauberer sitzt.


## Neu in v0.31.30

- Die DAB-Bedientasten verwenden jetzt die passenden **Skip-Symbole**:
  - `⏮` = vorheriger Sender
  - `⏭` = nächster Sender
- Größe, Buttonform, Glass-Look und Abstände bleiben unverändert; geändert wurden nur die Symbole.
- Die Funktion bleibt weiterhin echter Senderwechsel (`PREVIOUS` / `NEXT`) und kein Spulen.


## Neu in v0.31.29 – Blur-Code bereinigt + Wetterzeile ausgerichtet

- Alte Blur-Testlogik vollständig aus dem Laufzeitcode entfernt: kein Schalterzustand, keine Blur-an/aus-Zweige und keine `GlassEffects`-Hilfsklasse mehr.
- Frosted Glass ist jetzt direkt der einzige Renderpfad.
- Hintergrundwechsel bleibt weiterhin dynamisch und invalidiert den Blur-Cache.
- Ortsname im Wetterbereich nutzt jetzt **dieselbe Schriftgröße, Bold-Stärke, Auto-Size und Zeilenhöhe wie „Sonnenaufgang“**.
- Dadurch liegen Ortsname und „Sonnenaufgang“ optisch auf derselben Höhe.


## Neu in v0.31.28 – Blur fest eingebaut + Navi-Rundung korrigiert

- **Glass-Blur ist jetzt Standard** und dauerhaft aktiv. Der frühere Test-Schalter wurde aus dem Menü entfernt.
- Die **Navigations-Kachel** clippt ihre Inhalte jetzt wieder sauber auf die runden Ecken. Dadurch ragen Karteninhalte nicht mehr in den Ecken aus dem Panel heraus.
- Die bisherigen True-Glass/Blur-Änderungen bleiben erhalten, laufen jetzt aber ohne zusätzlichen Benutzerschalter.


## Neu in v0.31.27 – Review-Fixes

- Hintergrundwechsel jetzt mit eigener **Revisionsnummer**: auch zwei Bilder mit gleicher Dateigröße/gleichem Dateizeitstempel werden sicher als Änderung erkannt.
- Ausgewählte Hintergrunddateien werden vor Aktivierung als echtes, von Android dekodierbares Bild validiert.
- Blur-Quelle verwendet jetzt exakt dieselbe dunkle Bildtönung wie der sichtbare Hintergrund; dadurch passen Hintergrund und Frosted-Glass-Ausschnitt farblich besser zusammen.
- Linien des eingebauten Standard-Hintergrunds werden im Blur-Cache maßstabsgerecht gerendert.
- Blur-Drawables cachen ihren `BitmapShader`, statt ihn bei jedem Neuzeichnen neu zu erzeugen.
- CarPlay: Wenn ein Paket mehrere Launcher-Aktivitäten besitzt, wird jetzt **das Icon genau der erkannten CarPlay-Launcher-Aktivität** geladen – nicht einfach das erste Icon des Pakets.


## Neu in v0.31.26

- Fehler behoben: Ein in den Einstellungen ausgewähltes Hintergrundbild wird jetzt beim Zurückkehren zum Launcher **sofort übernommen**.
- Ursache war, dass die bereits laufende Hauptseite noch ihre alte Hintergrund-View im Speicher hatte, während die Einstellungsseite schon die neue Datei geladen hatte.
- Der Launcher merkt sich jetzt eine Hintergrund-Signatur und baut die Hauptseite automatisch neu auf, sobald das Bild geändert wurde.
- Dasselbe gilt für den **Glass-Blur-Schalter**: Änderungen werden beim Zurückkehren zuverlässig auf alle Kacheln angewendet.
- Das gespeicherte Bild bleibt weiterhin dauerhaft im privaten App-Speicher erhalten.


## Neu in v0.31.25

- **Glass-Blur nochmals näher an CarPlay angepasst:** deutlich klareres Glas, kaum noch weißer Schleier, dafür stärkerer echter Hintergrund-Blur.
- Die Kartenfarbe kommt jetzt fast vollständig aus dem Hintergrund selbst; nur eine sehr leichte dunkle Glas-Tönung und feine helle Kante bleiben darüber.
- Auch Chips und Touch-Flächen verwenden im Blur-Modus den klareren Stil.
- **CarPlay-Icon korrigiert:** die Sidebar lädt jetzt wie die Seite „Alle Apps“ das Icon der tatsächlichen `LAUNCHER`-Activity (`ResolveInfo.loadIcon`) statt nur das Application-Icon bzw. unser Fallback.
- Die CarPlay-App-Erkennung durchsucht jetzt direkt alle Launcher-Aktivitäten und berücksichtigt deren sichtbaren App-Namen, Paket und Activity-Namen.
- Der CarPlay-Start verwendet dieselbe erkannte Launcher-Aktivität, deren originales Icon angezeigt wird.


## Neu in v0.31.24

- **Glass-Blur überarbeitet:** bei aktiviertem Blur sind die Kacheln deutlich klarer und nicht mehr stark milchig.
- Der eigentliche Hintergrund-Blur wurde verstärkt (mehrere Blur-Pässe), während der weiße Glas-Schleier stark reduziert wurde.
- Dadurch bleiben Hintergrundfarben sichtbar und erscheinen hinter den Kacheln wirklich weichgezeichnet statt weiß überlagert.
- **DAB-Kachel korrigiert:** Logo/Metadaten und Bedientasten sind kompakter verteilt; die drei Tasten bleiben vollständig innerhalb der Kachel.
- Senderlogo bleibt weiterhin vollständig sichtbar (`FIT_CENTER`).


## Neu in v0.31.23

- Unter **Einstellungen → Darstellung & Medien** gibt es jetzt den Schalter **„Glass-Blur testen“**.
- Standardmäßig bleibt Blur **aus**, damit das bisherige Verhalten unverändert ist.
- Aktiviert: Hinter den Glass-Kacheln wird eine weichgezeichnete, positionsgenaue Kopie des aktuellen Hintergrundbilds angezeigt.
- Text, Symbole und Karteninhalte werden nicht weichgezeichnet.
- Für die Headunit wird kein permanenter Fullscreen-Live-Blur gerechnet: Der Launcher erzeugt einmalig eine kleine Blur-Kopie des statischen Wallpapers und verwendet davon nur die passenden Ausschnitte unter den Kacheln.
- Beim Wechsel des Hintergrundbilds wird der Blur-Cache automatisch neu erstellt.


## Neu in v0.31.22

- Die **Glass-Kacheln sind jetzt neutraler** aufgebaut und färben den Hintergrund nicht mehr stark blau ein.
- Dadurch schimmert ein frei gewähltes Hintergrundbild jetzt **deutlich realistischer durch die Kacheln** durch.
- Beispiel: grauer Hintergrund → graue/silbrige Kacheln, blauer Hintergrund → bläuliche Kacheln.
- Die Flächen bleiben dabei leicht **milchig/frosted**, damit der Look nach Glas aussieht und die Schrift lesbar bleibt.
- Technisch weiterhin ohne Live-Blur, damit die Performance auf dem Radio stabil bleibt.


## Neu in v0.31.21

- Eigene Hintergrundbilder werden jetzt **in den privaten Speicher des Launchers kopiert**.
- Dadurch bleibt das gewählte Bild **zuverlässig nach Radio-Neustarts, Kaltstarts und App-Neustarts** erhalten.
- Der Launcher ist nicht mehr von einer später eventuell verlorenen Dateimanager-/DocumentProvider-Berechtigung abhängig.
- Das Bild bleibt so lange aktiv, bis **„Standard-Hintergrund verwenden“** gewählt wird.
- Nur das Löschen der App-Daten oder eine Deinstallation entfernt das intern gespeicherte Hintergrundbild ebenfalls.


## Neu in v0.31.20

- Linke Leiste fein ausgerichtet: Uhrzeit, Datum, Anbieter, Empfang und GPS haben gleichmäßigere Abstände.
- Unter **Einstellungen → Darstellung & Medien** kann jetzt ein eigenes Hintergrundbild vom Radio ausgewählt werden.
- Die Auswahl erfolgt über den Android-Dateidialog; der Zugriff wird nach Möglichkeit dauerhaft gespeichert.
- Der originale blaue Glass-Hintergrund bleibt immer verfügbar und kann mit **Standard-Hintergrund verwenden** jederzeit wieder aktiviert werden.
- Eigene Bilder werden bildschirmfüllend als `CENTER_CROP` dargestellt; eine leichte Navy-Tönung hält Text und Glass-Karten lesbar.
- Falls ein ausgewähltes Bild nicht mehr verfügbar ist, fällt der Launcher automatisch auf den Standard-Hintergrund zurück.


## Neu in v0.31.19

- Linke Leiste: unter der Uhrzeit steht jetzt das aktuelle Datum im Format `d.M.yy` (zum Beispiel `3.10.26`).
- Die Datumszeile ist dezent in der **halbtransparenten/muted** Optik gehalten – passend zur Wetterbeschreibung.


## Neu in v0.31.18

- Ortsname im linken Bereich der Wetter-Kachel jetzt **zentriert** über dem aktuellen Wetter.
- Aktuelle Wettergruppe (Symbol + Temperatur + Hoch/Tief) jetzt ebenfalls **zentrierter aufgebaut**.
- Beschreibung unten (z. B. "Bewölkt · ☂ 3 %") jetzt **mittig** ausgerichtet.


## Neu in v0.31.17

- CarPlay in der linken Leiste verwendet wieder das **originale Icon der installierten CarPlay-App**; nur wenn das Paket kein Icon liefert, greift der Launcher auf ein Fallback zurück.
- Media-Kachel neu aufgebaut: **Sendername oben**, darunter ein **größeres Senderlogo**, darunter **Titel** und **Interpret**.
- Senderlogos werden mit `FIT_CENTER` dargestellt und nicht zugeschnitten; unterschiedliche Logo-Seitenverhältnisse bleiben vollständig sichtbar.
- Titel und Interpret dürfen weiterhin bis zu zwei Zeilen nutzen und skalieren dynamisch, ohne die Kachel unnötig zu quetschen.


## Neu in v0.31.16

- Karten-/Lizenzfenster an den Glass-Look des Launchers angepasst.
- Fahrzeug-Vorschaubild rechts oben jetzt in einer quadratischen Kachel.
- Blau des kleinen Polo-Bildes an die gewünschte kräftigere Fahrzeugfarbe angenähert.

## Neu in v0.31.15

- Fahrzeugbild weiter bereinigt: Der dünne Streifen oberhalb der Antenne wurde entfernt.
- Der störende Schattenrest am vorderen Rad bzw. unter dem Fahrzeugbild wurde ebenfalls entfernt.

## Neu in v0.31.14

- Launcher-Einstellungen wieder dauerhaft in der linken Seitenleiste ergänzt.
- Der Button sitzt unten direkt über „Alle Apps“ und verwendet immer das eigene Zahnrad-Symbol des Launchers.

## Neu in v0.31.12

- CarPlay-Icon im Launcher überarbeitet und an den gewünschten Stil angepasst.
- Das neue CarPlay-Icon wird jetzt konsistent in Sidebar und App-Kacheln verwendet.

## Neu in v0.31.11

- Wetterkachel verfeinert: „Sonnenaufgang“ und „Sonnenuntergang“ haben jetzt dieselbe Schriftgröße wie der Ortsname.
- Die Uhrzeiten für Sonnenaufgang und Sonnenuntergang sind größer dargestellt und damit besser lesbar.
- Die Wochentage der Folgetage (SA, SO, MO ...) sind ebenfalls größer.

## Neu in v0.31.10

- Fahrzeugbild freigestellt: In der Fahrzeugkachel ist jetzt nur noch der blaue Polo ohne blauen Hintergrund sichtbar.
- DAB-Kachel nutzt den gewonnenen Platz: Titel und Interpret werden größer dargestellt.
- GPS-Zentrierbutton in Online- und Offline-Karte sitzt jetzt auf derselben Höhe und mit demselben Randabstand wie der Info-Button links.
- Fahrzeugkachel: Bordnetz zeigt jetzt ein Volt-/Energie-Symbol, Außentemperatur ein Temperatur-Symbol.

## Neu in v0.31.9

- DAB-Steuertasten sitzen jetzt unten in der Media-Kachel mit gleichmäßigem Rand und sind kompakt quadratisch.
- Sonnenaufgang beginnt oben auf Höhe des Ortsnamens, Sonnenuntergang endet unten auf Höhe der aktuellen Wetterzeile.
- Höchst-/Tiefsttemperaturen der drei Folgetage werden größer dargestellt.
- Der Menüpunkt „Skins und Farben“ wurde entfernt; die Glass-Oberfläche ist jetzt der feste Launcher-Look.

## Neu in v0.31.8

- Fahrzeugbild in der rechten Fahrzeugkachel durch das vom Nutzer bereitgestellte blaue Polo-Bild ersetzt
- Wetterpiktogramme behalten ihre originalen Mehrfarben (z. B. gelbe Sonne, blaue Regentropfen); ein eventuell vom Radio gesetzter ColorFilter wird zentral entfernt

## Neu in v0.31.7

- DAB-Steuerung: Zurück, Pause/Play und Vor als kleinere quadratische Glastasten
- DAB-Titel und Interpret mittig ausgerichtet; beide behalten ihre dynamische Zweizeilen-Skalierung
- Wetter: Sonnenaufgang, Uhrzeit, Sonnenuntergang und Uhrzeit jetzt sauber untereinander direkt neben dem aktuellen Wetter
- keine Trennlinie mehr zwischen aktuellem Wetter und Sonnenzeiten, weil beides zum selben Tag gehört
- Mobilfunk-Empfangsbalken mit sichtbaren Abständen zwischen den einzelnen Balken

## Neu in v0.31.5

- DAB-Kachel neu sortiert: Cover klein oben links, Sender rechts daneben, darunter Titel und Interpret über die ganze Breite
- DAB-Cover wird jetzt vollständig im Originalformat angezeigt (FIT_CENTER statt Beschnitt)
- Wetterkachel kompakter: aktuelles Wetter links, Sonnenaufgang und Sonnenuntergang direkt daneben, rechts drei Folgetage
- Sonnenaufgang und Sonnenuntergang zeigen die Uhrzeit jetzt jeweils in einer eigenen Zeile unter der Bezeichnung
- Fahrzeugkachel: Auto kleiner, damit Balken und Restreichweite vollständig in eine gemeinsame Zeile passen
- Mobilfunksignal in der linken Leiste nutzt ansteigende Empfangsbalken im CarPlay-Stil

## Neu in v0.31.4

- Wetterkachel neu aufgeteilt: aktuelles Wetter, Sonne sowie die zwei nächsten Tage sind gleichzeitig sichtbar
- heutige Höchst-/Tiefsttemperatur sitzt kompakt direkt unter der aktuellen Temperatur
- heutige Niederschlagswahrscheinlichkeit steht in derselben Zeile wie die Wetterbeschreibung (z. B. „Bewölkt · ☂ 35 %“)
- die Aktualisierungs-Uhrzeit wurde aus der Wetteranzeige entfernt; die Uhrzeit bleibt ausschließlich in der linken Seitenleiste
- morgen und übermorgen zeigen jeweils Wochentag, Wettersymbol, Beschreibung sowie Höchst-/Tiefsttemperatur

## Neu in v0.31.3

- rechte Shortcut-Kachel entfernt; Maps, DABdream+, CarPlay, YouTube, Einstellungen und Apps sitzen jetzt als echte App-Symbole in der schmaleren linken Glasleiste
- linke Leiste schmaler; Uhr ohne orange Fläche, Mobilfunkanbieter größer und Empfang als Balken mit Netztyp
- DAB-Sendererkennung verbessert: DLS/„Interpret - Titel“-Zeilen werden nicht mehr als Sender gespeichert; Senderquellen wie Subtext/Album/Description werden bevorzugt
- Fahrzeugkachel: Tank, Reichweitenbalken und Restkilometer stehen jetzt in einer gemeinsamen Zeile; Bordnetz/Außen umbrechen nicht mehr
- Wetterkachel etwas flacher und um die Vorhersage für morgen mit Wettersymbol sowie Max/Min erweitert
- GPS-Zentrierbutton in Online- und Offlinekarte verwendet jetzt denselben Glass-Look wie die neue Oberfläche
- rechte Fläche besteht nur noch aus Fahrzeug- und DAB-Kachel und nutzt den frei gewordenen Platz vollständig

## Neu in v0.31.2

- überarbeiteter CarPlay-ähnlicher Glass-Look mit transparenteren, feineren Glasflächen und dezenteren Lichtlinien
- linke Leiste ohne doppelte Bordspannung; Mobilfunk zeigt nach Möglichkeit Anbieter, Netztyp und Signalstärke
- echte App-Symbole für Maps, DABdream+, CarPlay, Android-Einstellungen und YouTube in Glaskacheln
- DAB-Kachel mit quadratischem Cover sowie getrennten, dynamisch skalierten Zeilen für Sender, Titel und Interpret
- Fahrzeugkachel mit sichtbarem Restreichweiten-/Tankbalken und dezenteren Gurt-/Scheibenwasser-Statusfeldern
- Wetterkachel ohne doppelte Uhrzeit, dafür Tages-Max/Min, Niederschlagswahrscheinlichkeit, Sonnenaufgang und Sonnenuntergang
- Einstellungs-, Karten-, OBD-, Reichweiten-, Offlinekarten-, Skin- und Diagnoseansichten optisch auf den neuen Glass-Look abgestimmt

> **Projektstatus:** funktionsfähiger Quelltextstand für das Zielradio. Die Java-Logik und der Quelltext wurden automatisiert geprüft. Ein vollständiger APK-Build und ein abschließender Test direkt am Radio müssen in Android Studio beziehungsweise auf dem Fahrzeuggerät erfolgen.

## Inhaltsverzeichnis

- [Überblick](#überblick)
- [Cockpit-Oberfläche](#cockpit-oberfläche)
- [Karten und Navigation](#karten-und-navigation)
- [Automatischer Karten-Nachtmodus](#automatischer-karten-nachtmodus)
- [Wetter und Ortsanzeige](#wetter-und-ortsanzeige)
- [CANBUS-Fahrzeugdaten](#canbus-fahrzeugdaten)
- [OBD2 und Momentanverbrauch](#obd2-und-momentanverbrauch)
- [Durchschnittsverbrauch](#durchschnittsverbrauch)
- [Tank und Restreichweite](#tank-und-restreichweite)
- [DABdream und Mediensteuerung](#dabdream-und-mediensteuerung)
- [Apps und Schnellstartleiste](#apps-und-schnellstartleiste)
- [Einstellungen und Diagnose](#einstellungen-und-diagnose)
- [Berechtigungen und Datenschutz](#berechtigungen-und-datenschutz)
- [Stabilität und Schutzmaßnahmen](#stabilität-und-schutzmaßnahmen)
- [Installation und Einrichtung](#installation-und-einrichtung)
- [Projekt bauen](#projekt-bauen)
- [Tests](#tests)
- [Bekannte Grenzen](#bekannte-grenzen)
- [Technische Daten](#technische-daten)
- [Neu in v0.31.1](#neu-in-v0311)
- [Neu in v0.31.0](#neu-in-v0310)
- [Neu in v0.30.23](#neu-in-v03023)
- [Neu in v0.30.22](#neu-in-v03022)
- [Neu in v0.30.21](#neu-in-v03021)
- [Neu in v0.30.20](#neu-in-v03020)
- [Neu in v0.30.19](#neu-in-v03019)
- [Neu in v0.30.18](#neu-in-v03018)
- [Neu in v0.30.17](#neu-in-v03017)

## Überblick

Der Polo Launcher kann als Android-Startbildschirm (`HOME`) verwendet werden. Die Hauptseite bleibt im Querformat und ist für die breite Anzeige eines Autoradios aufgebaut.

Die wichtigsten Funktionen sind:

- Online-Navigation mit MapLibre und OpenFreeMap
- herunterladbare Offline-Karten mit Mapsforge
- automatischer Tag-/Nachtmodus der Karte
- GPS-Nachführung, Fahrtrichtung oben und ortsabhängiger Zoom
- optionale Straßenkorrektur anhand der Offline-Karte
- aktuelles Wetter und Drei-Tage-Vorschau für die GPS-Position
- Anzeige der aktuellen Ortschaft
- VW-Polo-Fahrzeugdaten über den HCT/Microntek-CANBUS-Dienst
- ELM327/OBD2-Verbindung über Bluetooth
- berechneter Momentan- und Durchschnittsverbrauch
- geglättete Restreichweite mit Tankreserve
- DABdream-Medienanzeige und Mediensteuerung
- Schnellstart für Fahrzeug-, Radio-, Karten-, CarPlay- und Video-Apps
- frei wählbares, dauerhaft gespeichertes Hintergrundbild
- festes CarPlay-artiges Frosted-Glass-Design mit gecachtem Blur
- vollständige interne Diagnose zum Kopieren, Teilen und Speichern als Datei

## Cockpit-Oberfläche

### CarPlay-Glass-Fahrzeugkachel v0.31.1

Die obere rechte Fahrzeugkachel zeigt jetzt einen blauen Polo links und rechts die drei primären Werte Tank/Restreichweite, Bordspannung und Außentemperatur. Geschwindigkeit, Drehzahl, Momentanverbrauch und Durchschnittsverbrauch bleiben in einer kompakten Zusatzzeile sichtbar; Gurt und Scheibenwasser bleiben als Statusfelder darunter erhalten.

### Linke Leiste

Die feste linke Leiste zeigt Uhrzeit, Datum, Wochentag, Mobilfunkanbieter/Empfang und GPS-Status. Darunter liegen die Schnellstarts für Google Maps, DABdream+, CarPlay und YouTube; ganz unten bleiben Launcher-Einstellungen und „Alle Apps“ erreichbar.

### Mitte

In der Mitte befindet sich die Navigationskarte. Direkt darunter zeigt eine kompakte Wetterkachel:

- aktuellen Ortsnamen, Wettersymbol und Temperatur
- Höchst-/Tiefstwert und Wetterbeschreibung
- Sonnenaufgang und Sonnenuntergang
- die nächsten drei Kalendertage mit Symbol, Beschreibung, Höchst- und Tiefsttemperatur

### Rechte Spalte

Oben steht die Fahrzeugkachel mit Polo-Bild, Tank/Restreichweite, Bordspannung, Außentemperatur, Geschwindigkeit, Drehzahl, Momentan-/Durchschnittsverbrauch sowie Gurt- und Scheibenwasserstatus.

Darunter zeigt die DAB-Kachel Sendername, vollständig eingepasstes Senderlogo, Titel, Interpret und die drei Medien-/Senderwechsel-Tasten.

### App-Seiten

- Installierte und startbare Apps werden automatisch ermittelt.
- Pro Seite werden bis zu 18 Apps angezeigt.
- Zwischen den Seiten kann horizontal gewischt werden.
- Das Wischen funktioniert auch über dem Kartenbereich.
- Home/Zurück führt wieder zur Cockpit-Hauptseite.
- Vor dem Öffnen einer App springt der interne Launcher auf die Hauptseite zurück.
- App-Symbole werden im Hintergrund geladen, damit der Startbildschirm nicht blockiert.

### Erscheinungsbild

Der aktuelle Glass-Look ist fest. Unter **Darstellung & Medien** kann ein eigenes Bild vom Radio gewählt oder jederzeit der eingebaute Standardhintergrund wiederhergestellt werden. Das Bild wird in den privaten App-Speicher kopiert und bleibt nach Neustarts erhalten. Die Kacheln verwenden dauerhaft den gecachten Frosted-Glass-Blur und übernehmen dadurch die Farben des gewählten Hintergrunds.

## Karten und Navigation

### Online-Karte

Bei einer verfügbaren Internetverbindung verwendet der Launcher MapLibre mit OpenFreeMap. Als Tagesstile sind **Liberty**, **Bright** und **3D** wählbar. Der 3D-Stil verwendet eine geneigte Kartenansicht, die auch beim Wechsel in den Nachtmodus erhalten bleibt.

### Offline-Karte

Ohne Internet oder nach einem Fehler der Online-Karte wechselt der Launcher auf eine lokal gespeicherte Mapsforge-Karte. Die Offline-Karte wird nicht aus einzelnen Online-Kacheln zusammengesetzt, sondern als vollständige `.map`-Datei heruntergeladen.

Unterstützte Regionen:

- Baden-Württemberg, Bayern, Berlin, Brandenburg, Bremen und Hamburg
- Hessen, Mecklenburg-Vorpommern, Niedersachsen und Nordrhein-Westfalen
- Rheinland-Pfalz, Saarland, Sachsen und Sachsen-Anhalt
- Schleswig-Holstein und Thüringen

### Offline-Kartenverwaltung

Die Einstellungsseite kann eine Region auswählen, Karten herunterladen, den Fortschritt anzeigen, Downloads abbrechen, vorhandene Karten ersetzen, prüfen, aktivieren oder löschen.

Der Download läuft über Androids `DownloadManager`. WLAN und Mobilfunk sind erlaubt, Roaming ist gesperrt. Vorher wird der freie Speicher gegen die vom Server gemeldete Dateigröße geprüft. Eine bestehende Karte bleibt erhalten, bis die neue Datei vollständig geladen und als plausibel geprüft wurde.

### GPS-Funktionen

- automatische Nachführung und geglättete Positionsbewegung
- animierte Kartendrehung und optional Fahrtrichtung oben
- Nordausrichtung ohne brauchbaren GPS-Kurs
- Zoomstufen 5 bis 19
- Grundzoom sowie eigene Werte für innerorts und außerorts
- GPS-Schaltfläche zum erneuten Zentrieren

Mit genauer Standortfreigabe steht GPS zur Verfügung. Mit nur ungefährer Freigabe kann Android lediglich den Netzwerkanbieter liefern.

### Ortsabhängiger Zoom

Anhand der ausgewählten Offline-Karte erkennt der Launcher, ob sich das Fahrzeug innerhalb oder außerhalb einer Ortschaft befindet, und verwendet den jeweiligen Zoomwert. Diese Auswertung funktioniert im Hintergrund sowohl bei der Online- als auch bei der Offline-Darstellung.

Für die Umschaltung gibt es zwei gespeicherte Betriebsarten:

- **Stabil:** wartet mindestens 8 Sekunden, etwa 55 Meter Bewegung und zwei gleiche Erkennungen. An einer erkannten Ortskante hält ein 140-Meter-Puffer den bisherigen Zoom. Das verhindert nervöses Umschalten bei GPS-Sprüngen und unvollständigen Kartenflächen.
- **Direkt an Ortsgrenze:** prüft ungefähr jede Sekunde, reagiert ab etwa 5 Metern Bewegung, benötigt nur eine Erkennung und verwendet keinen 140-Meter-Randpuffer. Dadurch wird beim Einfahren unmittelbar auf den Innerorts-Zoom und beim Verlassen auf den Außerorts-Zoom gewechselt.

Der Direktmodus folgt den in der installierten Offline-Karte erfassten Wohn-, Gewerbe- und Einzelhandelsflächen. Weil diese Flächen nicht immer exakt dem amtlichen Ortsschild entsprechen, bleibt der stabile Modus die robustere Alternative.

### Straßenkorrektur

Die optionale Testfunktion vergleicht die GPS-Position mit Straßen aus der Offline-Karte. Entfernung und Fahrtrichtung werden berücksichtigt. Die Arbeit läuft außerhalb des UI-Threads und besitzt feste Warte- und Gültigkeitsgrenzen, damit eine langsame Kartenabfrage keine neuere GPS-Position überschreibt.

### Automatischer Rendererwechsel

- Bei nutzbarem Internet wird die Online-Vektorkarte bevorzugt.
- Ohne Internet wird die heruntergeladene Offline-Karte verwendet.
- Nach einem Ladefehler fällt die Karte auf Offline zurück.
- Spätere Online-Versuche werden mit Abstand wiederholt.
- Position, Zoom, Drehung und Nachführstatus bleiben möglichst erhalten.

## Automatischer Karten-Nachtmodus

In den Karteneinstellungen stehen **Automatisch**, **Tag** und **Nacht** bereit.

Im automatischen Modus berechnet der Launcher Sonnenaufgang und Sonnenuntergang lokal aus Datum, Uhrzeit und GPS-Position. Dafür ist kein Internet erforderlich. Bis zum ersten GPS-Fix wird eine feste Position in Sachsen verwendet. Der Zustand wird einmal pro Minute geprüft.

- Online bleibt der farbige OpenFreeMap-Liberty-Stil erhalten und wird mit einer leichten blaugrünen Nachtabdunklung versehen.
- Offline bleibt das gut lesbare Mapsforge-MOTORIDER-Render-Thema erhalten und erhält dieselbe Nachtabdunklung.
- Der frühere fast schwarze Online-Stil und das sehr dunkle Offline-Thema werden nicht mehr verwendet; Straßen, Gebäude und Beschriftungen bleiben dadurch erkennbar.
- Noch vorhandene dunkle Offline-Kacheln aus v0.30.15/16 werden beim ersten Start einmalig aus dem Cache entfernt.
- GPS-Position, Zoom, Drehung und Nachführmodus bleiben erhalten.
- Beim Tagesstil 3D bleibt die geneigte Ansicht auch nachts aktiv.

## Wetter und Ortsanzeige

### Wetterdaten

Open-Meteo liefert das aktuelle Wetter und die folgenden drei Kalendertage mit Temperatur, Beschreibung, Tageshöchst- und Tiefstwerten.

Eigene skalierbare Vektorsymbole decken Sonne, Mond, teilweise Bewölkung, Wolken, Nebel, Regen, Schnee, Gewitter und unbekannte Zustände ab. Sie besitzen feste natürliche Farben – etwa gelbe Sonne, helle Wolken, blauen Regen und gelben Blitz – und werden vom festen Glass-Look nicht umgefärbt.

### Ortsname

Der Ortsname wird über OpenStreetMap/Nominatim bestimmt. Je nach Antwort werden Stadt, Gemeinde, Dorf, Ortsteil oder Landkreis verwendet.

- Nach ungefähr 250 Metern Bewegung darf erneut geprüft werden.
- Ortsabfragen erfolgen höchstens einmal in 30 Sekunden.
- Ein abweichender Ortsname muss zweimal nacheinander bestätigt werden.
- Dadurch wechselt die Anzeige an Gemeindegrenzen nicht wegen eines einzelnen Ausreißers.
- Nach einem bestätigten Ortswechsel wird das Wetter sofort neu geladen.

### Aktualisierung und Cache

- In derselben Ortschaft wird das Wetter alle drei Minuten aktualisiert.
- Fehler führen nicht zu hektischen Dauerabfragen.
- Die letzte gültige Vorhersage bleibt bis zu 12 Stunden gespeichert.
- Wetter- und Ortsabfragen laufen vollständig im Hintergrund.
- Netzwerkantworten besitzen Zeit- und Größenlimits.

Gerundete GPS-Koordinaten werden per HTTPS an Open-Meteo und Nominatim übertragen. Es gibt kein Benutzerkonto und keinen API-Schlüssel.

## CANBUS-Fahrzeugdaten

Der Launcher bindet sich an den HCT/Microntek-CANBUS-Dienst des Radios. Er registriert einen passiven Callback und führt zusätzlich ausschließlich lesende OEM-Abfragen aus. Ausgewertet werden insbesondere HCT-Frames der Funktion `0x41`.

Angezeigt oder verarbeitet werden:

- Motordrehzahl und Fahrzeuggeschwindigkeit
- Bordnetzspannung und Außentemperatur
- Kilometerstand als Berechnungsgrundlage
- Tankinhalt in ganzen Litern
- Türzustände, Gurt- und Scheibenwasserwarnung
- Öl-, Lade- und weitere OEM-Zustände für die Diagnose

Der Polo-6R-Tank wird intern mit 45 Litern Nennvolumen behandelt. Ein Tank-Rohwert 0 wird als ungültig verworfen, damit ein fehlender Wert nicht wie ein leerer Tank wirkt.

Gurt und Scheibenwasser werden nach 15 Sekunden ohne frische Daten nicht als aktueller Zustand weitergeführt. Die Drehzahl besitzt eine zusätzliche kurze Frischegrenze, damit nach dem Abstellen des Motors nicht lange der letzte Wert stehen bleibt.

Der bekannte defekte Kontakt der Tür vorn links kann ausgeblendet werden und ist standardmäßig als defekt markiert.

Eine heuristische Erkennung unbekannter Broadcast-Felder ist standardmäßig ausgeschaltet. Bestätigte HCT-Daten und bekannte Broadcasts werden immer ausgewertet. So kann ein fremdes Feld namens `speed` keine bestätigte Fahrzeuggeschwindigkeit überschreiben.

## OBD2 und Momentanverbrauch

Der ELM327 wird zuerst in Android gekoppelt und anschließend im Launcher ausgewählt. Der aktuelle Verbrauchsweg ist auf den VW Polo V/6R mit 1,2-Liter-CGPB-Benzinmotor abgestimmt und nicht für Dieselmotoren geeignet.

### Verwendete PIDs

Da keine direkt nutzbare MAF- und Tankstand-PID vorhanden ist, liest der Launcher:

- `010C` – Motordrehzahl
- `010D` – Geschwindigkeit
- `010B` – Saugrohrdruck/MAP
- `010F` – Ansauglufttemperatur/IAT

Aus Drehzahl, Druck, Temperatur, Hubraum, Luftdichte, Benzin-Luft-Verhältnis und einem Wirkungsgradmodell wird der Kraftstoffdurchsatz geschätzt.

### Momentananzeige

| Zustand | Anzeige |
|---|---|
| Motor aus oder Drehzahl unbekannt | `--` |
| OBD-Daten älter als 15 Sekunden | `--` |
| Motor läuft, unter 3 km/h | zum Beispiel `0,8 l/h` |
| Fahrzeug fährt, ab 3 km/h | kompakt zum Beispiel `6,1 l` |

Im Stand ist `l/100 km` nicht sinnvoll, weil keine Strecke zurückgelegt wird. Deshalb wird dort der zeitbezogene Durchsatz `l/h` angezeigt.

### Optionale Schubabschaltungsschätzung

Beim Ausrollen kann der Durchsatz auf 0 gesetzt werden, wenn mindestens 1300 U/min, höchstens 30 kPa MAP und mindestens 10 km/h anliegen. Diese Grenzwerte sind nicht am Fahrzeug messtechnisch bestätigt. Die Funktion ist abschaltbar und wird in der Diagnose als Schätzung gekennzeichnet.

### Verbindungsschutz

- ELM327-Initialisierung und PID-Unterstützungsprüfung
- Eingangspuffer vor jedem Befehl leeren
- verspätete oder verschobene Antworten erkennen und verwerfen
- Wiederverbindung nach 2, 4, 8, 16 und maximal 30 Sekunden
- neu gewählten Adapter sofort verbinden
- keine enge Fehler-Endlosschleife
- begrenzte Antwortgrößen und Zeitüberschreitungen

## Durchschnittsverbrauch

Die sichtbare Durchschnittskachel arbeitet als Speicher **seit dem letzten bestätigten Nachtanken**. Das entspricht der bei VW beschriebenen Fahrdaten-Speicherlogik „Seit Tanken“. Der Durchschnitt ist kein Mittelwert angezeigter Einzelwerte, sondern wird aus den tatsächlichen OBD-Zeit- und Streckensummen berechnet:

1. Durchfluss in `l/h` × Zeit ergibt verbrauchte Liter.
2. Geschwindigkeit × Zeit ergibt Kilometer.
3. `Liter / Kilometer × 100` ergibt den Durchschnitt.

Damit wiegen lange Fahrabschnitte stärker als kurze Spitzen. Im Stand kommen 0 Kilometer hinzu, der geschätzte Standverbrauch wird aber weiter zu den Litern addiert. Ungültige Zeitabstände und lange Standby-Lücken werden nicht rückwirkend hochgerechnet.

Liter und Kilometer seit dem Nachtanken bleiben über Neustarts und Zündungszyklen erhalten. Ein stabiler erkannter Tankanstieg setzt diesen sichtbaren Speicher automatisch zurück; die Taste in den OBD2-Einstellungen kann ihn zusätzlich manuell zurücksetzen.

Für die Restreichweite existiert getrennt davon ein gespeichertes, gleitendes Fahrprofil der letzten 100 km. Neue Strecke verdrängt darin sofort die gleich große älteste Strecke. Dadurch reagiert die Reichweite auf die jüngere Fahrweise, ohne dass ein einzelner Motorstart oder wenige Minuten Standgas den gesamten „Seit Tanken“-Durchschnitt ersetzen. Die exakte interne VW-Reichweitenformel ist nicht öffentlich; diese Trennung bildet das dokumentierte Verhalten nach, ohne eine proprietäre Formel vorzutäuschen.

### 1-km-Anzeigeschwelle

Die Durchschnittskachel zeigt nach einem Nachtanken oder manuellen Zurücksetzen erst ab 1 km neuer ausgewerteter OBD-Strecke einen Wert. Das ist nur eine Reifegrenze der sichtbaren Anzeige:

- keine künstlichen Kilometer
- kein verworfener Standverbrauch
- kein neuer 1-km-Start nach einem Neustart mit gespeichertem Seit-Tanken-Speicher
- keine Veränderung der Rohdaten für die Reichweite

### Anzeigeglättung

Der sichtbare Durchschnitt folgt dem Rohwert mit ungefähr 15 Sekunden Zeitkonstante. Diese Glättung betrifft nur die Kachel und fließt niemals zurück in Verbrauchssummen oder Reichweitenberechnung.

## Tank und Restreichweite

Vereinfacht gilt:

```text
Restreichweite = (geschätzte Tankliter - Reserve) / Verbrauch × 100
```

Standard sind 2,0 Liter Reserve. Die Reserve ist einstellbar und wird vor der Berechnung vom verfügbaren Tankinhalt abgezogen.

### Feste Reichweitenberechnung CAN + OBD2

Die Restreichweite verwendet immer den Tankstand und Kilometerstand aus dem CANBUS zusammen mit dem aktuellen beziehungsweise gespeicherten adaptiven OBD2-Fahrprofil der letzten 100 km. Der sichtbare Durchschnitt „Seit Tanken“ ist davon unabhängig. Der frühere auswählbare Modus **Nur CAN** wurde entfernt.

Ohne brauchbares OBD2-Fahrprofil wartet die Berechnung auf gültige Daten und behält die letzte Anzeige. Sie wechselt nicht unbemerkt auf einen manuellen oder CAN-gelernten Ersatzverbrauch.

### Tankmodell

Weil der CANBUS nur ganze Liter liefert, schätzt der Launcher Zwischenwerte aus letztem bestätigten Tankstand, Kilometerstand und Verbrauch. Neue CAN-Werte müssen 30 Sekunden stabil sein. Unplausible Kilometer- oder Tanksprünge werden verworfen oder führen zu einem kontrollierten Neuansatz.

Ist nur der letzte bekannte Tankwert verfügbar, bleibt die Literzahl sichtbar und wird orange dargestellt. Ein zusätzlicher Text `ALT` wird beim Tankwert nicht angehängt.

Ein Tankanstieg von mindestens 3 Litern und mindestens 2 Litern über dem bisherigen Schätzwert gilt nach 30 Sekunden stabiler Bestätigung als Nachtanken. Dann wird auch der sichtbare Durchschnitt „Seit Tanken“ automatisch neu begonnen. Das geglättete Reichweitenprofil bleibt erhalten, damit die Anzeige direkt nach dem Start beziehungsweise Tanken nicht unplausibel springt.

### Glättung der Restkilometer

- Unterschiede bis 3 km werden direkt übernommen.
- Größere Änderungen müssen 5 Sekunden dieselbe Richtung behalten.
- Danach folgt die Anzeige mit höchstens etwa 2 km pro Sekunde.
- Werte bis 20 km werden sofort angezeigt.
- Nach langen Pausen wird kein riesiger Zeitschritt nachgeholt.
- Ein gespeicherter Anzeigewert dient als ruhiger Startwert.

Diese Glättung verändert ausschließlich die sichtbare Zahl. Tankmodell, Seit-Tanken-Durchschnitt und Reichweitenprofil bleiben unangetastet.

### Hintergrunddienst

Ein Foreground-Service sammelt CAN- und OBD-Daten weiter, wenn DABdream, CarPlay oder eine andere App im Vordergrund liegt. Er verwendet eine Statusbenachrichtigung und ist gegen unzulässige Hintergrundstarts neuer Android-Versionen abgesichert.

## DABdream und Mediensteuerung

DABdream+ wird über `com.thf.dabplayer` und zusätzlich über den App-Namen erkannt.

Über Notification-Listener und MediaSession erhält der Launcher Titel, Interpret, Sender-/Albumhinweis, Wiedergabestatus, Bild und unterstützte Aktionen. Die Sitzung wird ereignisgesteuert beobachtet; große Grafiken werden für die Anzeige verkleinert.

Je nach MediaSession stehen vorheriger Sender/Titel, Play/Pause und nächster Sender/Titel zur Verfügung. Ein Tipp auf die Medienkachel öffnet DABdream.

### Autostart mit Internetprüfung

1. Auf ein von Android als nutzbar validiertes Standardnetz warten.
2. Währenddessen den Launcher bedienbar lassen.
3. Alle zwei Sekunden prüfen, ohne selbst eine Testverbindung zu öffnen.
4. Erst danach DABdream starten.
5. Nach drei Sekunden zum Launcher zurückkehren.
6. Nur einmal pro Bildschirm-/Standby-Zyklus starten.
7. Durch `SCREEN_OFF` für den nächsten Zyklus freigeben.

Der Launcher wählt keinen Sender aus. DABdream verwendet seine letzte oder dort konfigurierte Auswahl. Empfehlenswert ist, den eigenen DABdream-Autostart auszuschalten, damit nicht zwei Mechanismen gegeneinander arbeiten.

## Apps und Schnellstartleiste

Bekannte Apps sind unter anderem Google Maps, YouTube, Polo CAN, Polo CAN Debug und DABdream+. Für CarPlay werden ZLink/ZLink5, SpeedPlay, AutoKit, Carlinkit und weitere Varianten gesucht; zusätzlich ist eine Suche über App- und Paketnamen vorhanden.

Systemeinstellungen und App-Seiten werden mit Rückfallwegen geöffnet, weil Radio-Firmwares einzelne Android-Standardaktivitäten entfernen können.

## Einstellungen und Diagnose

Das kompakte Hauptmenü besteht aus vier Bereichen. Darin stehen Seiten bereit für:

- Erscheinungsbild
- Kartenstil, Zoom, Fahrtrichtung und Nachtmodus
- Offline-Karten innerhalb der Karteneinstellungen
- feste CAN+OBD2-Reichweite und Reserve
- OBD2-Adapter, Durchschnitt seit Tanken, Reichweitenprofil und Schubabschaltung
- DABdream-Autostart
- Fahrzeugoptionen
- Diagnose und Sniffer-Heuristik

Der Diagnosebericht kann aktualisiert, kopiert, geteilt und über den Android-Dateidialog als UTF-8-Textdatei gespeichert werden. Er enthält App-/Launcherstatus, Karte, GPS, Nachtmodus, Download, Reichweite, OBD2, letzte PIDs, Seit-Tanken-Summen, adaptives 100-km-Reichweitenfenster, DABdream, aktive MediaSessions, HCT-Binder, OEM-Poll, Fahrzeugwerte samt Datenalter, den letzten Java-Absturz, wichtige Apps und interne Meldungen.

Der Diagnosepuffer ist auf 1200 Zeilen begrenzt; die Oberfläche verwendet höchstens die letzten 600 Einträge. Einzelne Zeilen werden gekürzt.

## Berechtigungen und Datenschutz

| Berechtigung | Zweck |
|---|---|
| Internet | Online-Karte, Wetter, Ortsname, Karten-Download |
| Netzwerkstatus | Kartenumschaltung und DAB-Autostart |
| Standort | Karte, Wetter, Ortsname, Zoom, Straßenkorrektur |
| Bluetooth | ELM327-OBD2-Adapter |
| Foreground-Service | Fahrzeug- und Reichweitenerfassung |
| Benachrichtigungen | Hintergrunddienst auf Android 13 |
| Benachrichtigungszugriff | DAB-Metadaten und Steuerung |

- Unverschlüsseltes HTTP ist deaktiviert.
- OpenFreeMap erhält Kartenanfragen.
- Open-Meteo und Nominatim erhalten gerundete GPS-Koordinaten.
- Es gibt kein Benutzerkonto, keine Werbung und keine eigene Telemetrie.
- Fahrzeug- und OBD-Daten werden lokal verarbeitet und gespeichert.

## Stabilität und Schutzmaßnahmen

- Netzwerk-, Karten-, Datei- und Symbolarbeit außerhalb des UI-Threads
- schwache Activity-Referenzen bei asynchronen Kartenoperationen
- Lebenszyklus-gerechte Timer, Empfänger und MediaCallbacks
- begrenzte Binder-Warteschlangen, Bundles, Hex-Dumps und Antworten
- abgesicherte Verarbeitung exportierter Hersteller-Broadcasts
- monotone Zeitbasis für Datenaktualität
- keine unbekannten Methodenaufrufe auf Herstellerobjekten
- verkleinerte DAB-Grafiken
- Bluetooth-Backoff statt schneller Endlosschleifen
- sichere Kartenaktivierung mit Erhalt der alten Datei
- Plausibilitätsprüfungen gegen NaN, Infinity und unmögliche Werte

## Installation und Einrichtung

1. Debug- oder Release-APK installieren.
2. Den bisherigen Launcher zunächst installiert lassen.
3. Polo Launcher öffnen.
4. Standort-, Bluetooth- und Benachrichtigungsrechte gewähren.
5. Den Benachrichtigungszugriff für „Polo Launcher DAB Medienzugriff“ erlauben.
6. ELM327 in Android koppeln.
7. Den Adapter in den OBD2-Einstellungen auswählen.
8. Optional eine Offline-Karte laden.
9. Kartenstil, Nachtmodus, Zoom und Reichweitenreserve einstellen.
10. Optional den DABdream-Autostart aktivieren.
11. Erst nach erfolgreichem Test Polo Launcher als Standard-`HOME` auswählen.

Für die Erprobung sollte ein zweiter funktionierender Launcher installiert bleiben.

## Projekt bauen

Voraussetzungen sind Android Studio/Android SDK, JDK 17, SDK Platform 35 und Internet für Gradle-/Maven-Abhängigkeiten.

```bash
./gradlew assembleDebug
```

Debug-Ausgabe:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Installation:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Paketnamen:

- Release: `de.ronny.pololauncher`
- Debug: `de.ronny.pololauncher.debug`

Die Debug-Ausgabe kann grundsätzlich neben der Release-Ausgabe installiert werden. Für eine veröffentlichbare Release-APK muss eine Android-Signierung eingerichtet werden.

## Tests

Enthalten sind Tests für Verbrauch, 1-km-Grenze, Durchschnittsglättung, Reichweite, Tankmodell, Nachtanken, ELM327-Resynchronisierung, HCT-Decoder, Binder-Lebenszyklus, Offline-Karten, Straßenkorrektur, Wetter, Ortslogik, Sonnenstand, Nachtmodus und Quelltextverweise.

Letzter dokumentierter Prüfstand:

- 31 von 31 Testgruppen bestanden
- 65 Produktions-Java-Dateien im Syntaxaudit
- 474 statische Methodenaufrufe geprüft
- 38 Konstruktoraufrufe geprüft

Kompilierungsprüfung gegen eine echte `android.jar`:

```bash
ANDROID_JAR=/pfad/zum/android.jar sh tests/compile-check.sh
```

In der bisherigen Arbeitsumgebung konnte Gradle seine Distribution nicht von `services.gradle.org` laden. Deshalb ist keine neu gebaute APK enthalten. Ein Android-Build und der Test am Radio bleiben erforderlich.

## Bekannte Grenzen

- HCT/Microntek-CANBUS ist radio- und fahrzeugspezifisch.
- OBD-Verbrauch ist ein Modell, keine direkte Einspritzmengenmessung.
- Das Modell ist auf den 1.2-CGPB-Benziner und nicht auf TDI ausgelegt.
- Schubabschaltung und ihre Grenzwerte sind nicht am Fahrzeug vermessen.
- Der CAN-Tankstand liegt nur in ganzen Litern vor.
- Ortsnamen hängen von OpenStreetMap-Grenzen und Nominatim ab.
- Android `VALIDATED` garantiert nicht die Erreichbarkeit eines bestimmten Websenders.
- Standby-/Bildschirmereignisse hängen von der Radio-Firmware ab.
- Der Launcher kann DABdream öffnen und steuern, aber keinen Sender auswählen.
- Wetter und Online-Karte benötigen Internet.
- Eine Quelltextprüfung ersetzt keinen Langzeittest während realer Fahrt.

## Technische Daten

| Eigenschaft | Wert |
|---|---|
| Version | 0.31.33 |
| VersionCode | 116 |
| Sprache | Java 17 |
| minSdk / targetSdk / compileSdk | 26 / 33 / 35 |
| Ausrichtung | Querformat |
| Release-Paket | `de.ronny.pololauncher` |
| Debug-Paket | `de.ronny.pololauncher.debug` |
| CPU | `arm64-v8a`, `armeabi-v7a` |
| Gradle Wrapper | 8.9 |
| MapLibre | 11.8.0 |
| Mapsforge | 0.30.0 |
| AndroidSVG | 1.4 |
| Online-Karte | OpenFreeMap |
| Wetter / Ort | Open-Meteo / Nominatim |

## Neu in v0.31.1

- Obere rechte Fahrzeugkachel exakt in Richtung des abgestimmten CarPlay-Glass-Konzepts umgebaut.
- Blaues Polo-Fahrzeugbild links in einer eigenen Glasfläche.
- Rechts groß und aufgeräumt: Tankinhalt + Restreichweite, Bordspannung und Außentemperatur.
- Geschwindigkeit, Drehzahl, Momentanverbrauch und Durchschnitt seit Tanken bleiben als kompakte Zusatzzeile sichtbar.
- Gurt und Scheibenwasser bleiben als Statusfelder am unteren Rand der Fahrzeugkachel erhalten.
- CAN-/OBD2-, Reichweiten-, Karten-, Wetter- und DAB-Logik unverändert.

## Neu in v0.31.0

- Neue CarPlay-inspirierte Glas-Oberfläche mit blauem, halbtransparentem Glassmorphism-Design.
- Feste linke Seitenleiste mit Uhrzeit, Netzstatus, GPS-Status, Bordspannung sowie Schnellzugriffen für Maps, DABdream+, CarPlay und App-Drawer.
- Große Kartenfläche links mit Wetterleiste direkt darunter.
- Rechte Spalte neu aufgebaut: Fahrzeugdaten oben, DABdream+ in der Mitte, Einstellungen/CarPlay/YouTube/Apps unten.
- Fahrzeugdaten bleiben vollständig sichtbar: Tank, Reichweite, Momentanverbrauch, Durchschnitt seit Tanken, Geschwindigkeit, Drehzahl, Außentemperatur, Bordspannung, Gurt und Scheibenwasser.
- App-Seiten übernehmen den Glas-Look; die Seitenleiste bleibt beim Wischen sichtbar.
- Vorhandene Karten-, CAN-/OBD-, Wetter-, DAB- und Reichweitenlogik bleibt unverändert erhalten.

## Neu in v0.30.23

- sichtbaren OBD2-Durchschnitt auf den VW-typischen Speicher **Seit Tanken** umgestellt
- automatischer Neustart dieses Speichers nach einem 30 Sekunden bestätigten Nachtanken
- manueller Durchschnitts-Reset betrifft nur die sichtbare Seit-Tanken-Anzeige
- getrenntes gleitendes 100-km-Fahrprofil für die Restreichweite beibehalten
- Tank- und Reichweitenberechnung nutzt weiterhin CAN-Tank/Kilometerstand plus dieses geglättete OBD2-Profil
- Diagnose unterscheidet Seit-Tanken-Summen und Reichweitenprofil eindeutig
- Persistenz, Migration und Nachtank-Erkennung durch zusätzliche Tests abgesichert

## Neu in v0.30.22

- OBD2-Durchschnitt von einer unbegrenzten Gesamtsumme auf ein gleitendes 100-km-Fenster umgestellt
- neue Fahrstrecke ersetzt sofort den gleich großen ältesten Streckenanteil; keine starren 100-km-Blöcke
- Standverbrauch bleibt korrekt enthalten und verlässt das Fenster später mit seinem Fahrabschnitt
- komplettes Fenster wird über Neustarts und Standby hinweg gespeichert
- bisheriger Durchschnitt aus v0.30.21 wird ohne Anzeigesprung als Startwert migriert
- Diagnose zeigt belegte Strecke und Kraftstoffmenge des aktuellen 100-km-Fensters
- Tests für kontinuierlichen Austausch, 100-km-Grenze, Standgas, Persistenz und beschädigte Daten ergänzt

## Neu in v0.30.21

- CAN-Kontrolllernen einschließlich Startwert, Abschnitten und Rücksetzen vollständig entfernt
- daraus abgeleitete automatische OBD-Modellkorrektur entfernt; das getestete Speed-Density-Modell arbeitet fest
- Seite **Tank und Reichweite** auf Quelle, verwendeten Verbrauch und Reserve gekürzt
- OBD2-Seite gekürzt und überholte Hinweise zur CAN-Kalibrierung entfernt
- **Tank & Verbrauch** und **OBD-Verbindung** zu **Tank, Verbrauch & OBD** zusammengelegt
- doppelten Weg zu Offline-Karten entfernt; Verwaltung bleibt in den Karteneinstellungen
- doppelte GPS-/Medienberechtigungen aus **System & Diagnose** entfernt
- Diagnose von allen CAN-Lernangaben bereinigt
- alte CAN-Lern- und Kalibrierungseinstellungen werden beim Update entfernt

## Neu in v0.30.20

- auswählbaren Reichweitenmodus **Nur CAN** vollständig entfernt
- Reichweitenberechnung fest auf CAN-Tank/Kilometerstand plus OBD2-Durchschnitt vereinheitlicht
- alter gespeicherter Nur-CAN-Schalter wird beim Update entfernt und kann die Berechnung nicht mehr beeinflussen
- Menüschalter für die Moduswahl entfernt und Informationsbereich auf die feste Berechnung angepasst
- CAN-Lernen als getrenntes Kontroll- und Kalibrierungsmodell klar benannt
- CAN-Kontrollwert beeinflusst die Restkilometer niemals direkt
- Diagnose meldet die feste Berechnung und unterscheidet aktuellen, gespeicherten oder noch fehlenden OBD2-Durchschnitt
- kombinierte Berechnung, Neustart, Datenverlust, Tankfortschreibung, Glättung und Migration automatisiert geprüft

## Neu in v0.30.19

- neuer gespeicherter Zoommodus **Direkt an Ortsgrenze** in den Karteneinstellungen
- Direktmodus prüft ungefähr jede Sekunde und ab etwa 5 Metern Bewegung
- Umschaltung nach einer statt zwei übereinstimmenden Gebietserkennungen
- kein zusätzlicher 140-Meter-Randpuffer im Direktmodus
- bisheriger geglätteter **Stabil**-Modus bleibt vollständig erhalten
- Diagnose zeigt die gewählte Umschaltung als `DIREKT` oder `STABIL`
- ein nur noch gespeicherter Tankwert bleibt orange, trägt aber kein zusätzliches `ALT` mehr
- zusätzliche Quellcodeprüfungen sichern Speicherung, Laufzeitumschaltung und Tankdarstellung ab

## Neu in v0.30.18

- Kartenstil und Kartenmodus reagieren nun unmittelbar auf die angetippte Auswahl
- die vorherige Auswahlmarkierung wird sofort entfernt; ein Verlassen des Menüs ist nicht mehr nötig
- beide Auswahlgruppen werden beim Öffnen und bei der Rückkehr ins Kartenmenü mit dem gespeicherten Wert abgeglichen
- zusätzliche Quellcodeprüfungen sichern eindeutige Options-IDs und die sofortige Synchronisierung ab

## Neu in v0.30.17

- deutlich hellerer, grün-grauer Nachtkartenstil für Online- und Offline-Karte
- Liberty beziehungsweise MOTORIDER bleiben als lesbare Basis erhalten
- leichte blaugrüne Nachtabdunklung blockiert keine Kartenbedienung
- automatische Sonnenstandsumschaltung sowie feste Modi Tag und Nacht bleiben unverändert
- Wettersymbole behalten feste natürliche Farben unabhängig vom Cockpit-Skin
- zusätzliche Quellcodeprüfungen gegen eine Rückkehr zum zu schwarzen Kartenstil oder einfarbige Wettertönung

## Neu in v0.30.16

- Mindesthöhe und zusätzliche Skalierungssicherheit für alle Wetter-Vektorsymbole
- sichtbare Wetter-Uhrzeit sowie Status `OFFLINE` oder `FEHLER` bei weiter angezeigten Cache-Daten
- Internet-, Karten-, Orts- und Wetterprüfung aus dem schnellen Fahrzeugtakt ausgelagert
- orange `ALT`-Kennzeichnung für abgelaufene Fahrzeugwerte
- Diagnoseexport als Textdatei über den Android-Dateidialog
- begrenzter, dauerhaft gespeicherter Bericht des letzten Java-Absturzes
- kompakteres Einstellungs- und Skin-Menü
- zehn statt vier Cockpit-Farbschemata

## Neu in v0.30.15

- Kartenmodus Automatisch, Tag oder Nacht
- lokale Sonnenstandsberechnung aus Zeit und GPS
- Sachsen-Ersatzposition bis zum ersten GPS-Fix
- dunkler OpenFreeMap-Stil und Mapsforge-DARK-Thema
- Bereinigung des Offline-Kachelcache beim Themenwechsel
- Erhalt von Kamera, Zoom, Drehung und GPS-Nachführung
- Prüfung einmal pro Minute mit sauberem Activity-Lebenszyklus
- Kartenmodus im Diagnosebericht
- zusätzliche Tests für Sonnenstand, Themenwahl, Cache und Timer

## Lizenz- und Quellenhinweise

Beim Weitergeben oder Veröffentlichen müssen die Lizenz- und Quellenbedingungen aller verwendeten Bibliotheken und Dienste beachtet werden, insbesondere MapLibre, OpenFreeMap/OpenStreetMap, Mapsforge, Open-Meteo und Nominatim.
