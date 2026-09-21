# Prüfung v0.27.0

17 lokale Java-Testsuiten bestanden. Produktive Logik wird mit Android-Stubs
kompiliert; zusätzlich wurden alle 48 produktiven Java-Dateien auf Syntax geprüft.

Neue Prüfungen:

- ELM-PID-Dekodierung, unterstützte PIDs, MAP-Verbrauchsformel.
- Strecken-/Kraftstoffmittelwert, unbrauchbare Werte, fehlende Geschwindigkeit,
  Reset und Wiederherstellung.
- Tatsächliche RangeStore-Berechnung in CAN-only und CAN+OBD, gespeicherte Auswahl,
  ungültiger/veralteter OBD-Wert und CAN-Rückfall.
- CAN-Lernen bleibt bei aktiver OBD-Reichweitenberechnung unabhängig aktiv.
- Produktiver OBD-Client mit simulierten ELM-Ein-/Ausgabeströmen: vier Fahrzeug-PIDs,
  Veröffentlichung der Verbrauchswerte, CAN-Tank wird nicht überschrieben,
  Speicherung/Laden, NO DATA, Reset, Socket-Fallback und Reset des Integrationszeitpunkts.
- Verdrahtung von Menü, Verbrauchskachel und gemeinsamer Gurt-/Scheibenwasserkachel.
- Bestehende CAN-, Hintergrund-, GPS-, Paging-, Medien- und Anzeigespeicherprüfungen.

Grenzen: Bluetooth-Stubs sind kein echter ELM327/Radio-Test. Die neue UI wurde
quelltextseitig geprüft, nicht auf einem Android-Display gerendert. Gradle-Build
konnte wegen UnknownHostException für services.gradle.org nicht starten.
Die Standby-Wiederherstellung des Radios ist dadurch ebenfalls nicht bewiesen.
