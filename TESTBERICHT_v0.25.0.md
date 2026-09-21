# Prüfung v0.25.0 – Verbrauchslernung

Datum: 17.09.2026. Basis: v0.24.0. Automatik standardmäßig aktiviert,
Startverbrauch weiterhin 6,8 l/100 km, sofern kein anderer Grundverbrauch eingestellt ist.

## Lokal ausgeführt

14 Testsuiten bestehen mit Java-17-Kompilierung; 20 ausgewählte Produktionsklassen mitkompiliert.
42 Produktions-Java-Dateien ohne Syntaxfehler geparst. Android-Abhängigkeiten der lokalen
Tests verwenden Test-Doubles; die UI-Einstellungsseite wurde nicht auf einem Android-Gerät ausgeführt.

Neue Tests: `ConsumptionLearnerTest` und `RangeLearningStoreTest`.

- Start mit Grundverbrauch, erst stabilisierte echte CAN-Tankwerte als Messbasis.
- 100 km / 8 L ergibt Messverbrauch 8,0 und vorsichtige Anpassung von 6,8 auf 7,1 l/100 km.
- Mindeststrecke 100 km, Mindestverlust 5 L; kürzere/kleinere Abschnitte werden nicht gelernt.
- Wiederholte unveränderte Tankwerte erzeugen keine zusätzlichen Lernproben.
- Neue Abschnitte überschneiden sich nicht mit bereits übernommenen Abschnitten.
- Kleine Aufwärtsschwankungen werden nicht als Verbrauch gelernt.
- Schrittweises Nachtanken erkannt; vorhandener Lernwert bleibt erhalten.
- Unplausibler Tankverlust im Stand, Kilometer-Sprung, ungültige Daten und Bestätigungslücke geprüft.
- Anpassung pro Abschnitt auf 0,5 l/100 km begrenzt; Wert bleibt im Bereich 3–20.
- Lernanker über Neustart gespeichert; erster Lernabschnitt nach Neustart funktioniert.
- Lernwert wird gespeichert und tatsächlich in der Restkilometerberechnung verwendet.
- Automatik aus: manueller Grundverbrauch aktiv; Automatik an: alter Lernwert wieder aktiv, neuer Abschnitt.
- Reset bei bereits existierendem RangeStore: spätere save()-Aufrufe schreiben keine alten Lernwerte zurück.
- Grundverbrauch bleibt nach Reset einstellbar; fehlende CAN-Daten verbergen weiter die Reichweite.

Die bisherigen 12 Testsuiten aus v0.24.0 bestehen weiterhin: GPS-Vorlauf/Filter,
Reichweitenmodell, CAN-Framing/Broadcast/Bindings, Bildmodell, Kartensicherung,
Seiten-Pager und Quellcode-Prüfung. Vorheriger Bericht bleibt enthalten.

## Buildgrenze

Erneut versucht: `bash ./gradlew :app:assembleDebug`.
Abbruch beim Gradle-Download: `java.net.UnknownHostException: services.gradle.org`.
Keine APK erzeugt; kein erfolgreicher Android-Build oder Radiotest behauptet.
PowerShell-Starter nicht ausgeführt; Shell-Starter ausgeführt.

## Grenzen der Schätzung

Whole-Litre-Tankwerte erlauben keinen exakten Verbrauch auf kurzen Strecken. Es kann
mehrere Fahrten dauern, bis die erste Lernprobe entsteht. Ein exakt stabiler Tankwert
von 30 Sekunden ist absichtlich konservativ. Kleinere Nachfüllmengen und nicht beobachtete
Tankvorgänge lassen sich nicht immer von normalen Tankänderungen unterscheiden.
Lernfunktion läuft während der Launcher aktiv CAN-Meldungen verarbeitet. Über Neustarts
werden Weg-/Tank-Anker weiterverwendet, sofern die nächsten Werte plausibel sind.

## Auf dem Radio prüfen

Update ohne Deinstallation installieren. Unter Tank und Reichweite Automatik/Startwert
prüfen. Nach ausreichender Fahrt Status/Lernabschnitte und Verbrauch kontrollieren.
Automatik ausschalten: Grundverbrauch muss wieder für die Reichweite gelten.
Beim nächsten Nachtanken sollte ein neuer Abschnitt beginnen, ohne den Lernwert zu löschen.
