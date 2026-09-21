# GPS-Nachführung v0.25.1

Der zusätzliche GPS-Vorlauf aus v0.23 wurde entfernt. Seine wechselnde Freigabe
bei Kurven, Bremsen oder geänderter GPS-Genauigkeit konnte das Kameraziel vor
und zurück versetzen. Online läuft die Positionsanimation wieder 950 ms;
offline wieder 85 Prozent des Fix-Abstands (450 bis 2200 ms), wie in v0.22.2.

GPS-Ausreißerfilter, Fahrtrichtungsrotation, Zoomgrenzen, Ortsanalyse, optionaler
Straßenabgleich und automatische Verbrauchslernung bleiben erhalten.
Die Karte kann deshalb wieder etwas nachlaufen. Glatte Darstellung hat Vorrang
vor der experimentellen Vorausberechnung.

Das kurze Video zeigt die Unruhe, beweist jedoch ohne GPS-Log nicht deren
alleinige Ursache. Bitte denselben Streckenabschnitt auf dem Radio testen.
Bei weiterer Unruhe den optionalen Straßenabgleich testweise deaktivieren.

Prüfung: tests/run-tests.sh prüft lokale Java-Logik mit Android-Stubs und
Quelltextverdrahtung. Ein vollständiger Android-Build und ein Gerätetest sind
damit nicht ersetzt.
