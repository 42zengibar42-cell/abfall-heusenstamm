# Abfall Heusenstamm

Android-App: Straße wählen → alle Abholtermine (Restabfall, Bioabfall, Altpapier, Gelber Sack, Grünschnitt, Schadstoffe, Weihnachtsbäume) und Erinnerung am Vorabend.

Daten: Abfallkalender 2026 der Stadt Heusenstamm (PDF), ausgelesen mit den Skripten in `tools/`.

## APK

Jeder Push auf `main` baut die APK über GitHub Actions. Download unter **Releases**.

## Neues Jahr hinzufügen (z. B. 2027)

1. PDF der Stadt herunterladen, Seiten rendern: `pdftoppm -r 200 -png abfall.pdf hi`
2. `python3 tools/extract_calendar.py abfall.pdf hi-1.png hi-2.png > raw.json`
3. In `tools/build_events.py` das Jahr anpassen, dann `python3 tools/build_events.py raw.json > events.json`
4. `python3 tools/extract_streets.py abfall.pdf > streets.json`
5. In `app/src/main/assets/data.json` unter `jahre` einen neuen Eintrag `"2027"` mit `strassen` und `termine` anlegen (das alte Jahr kann bleiben).
6. Feiertage in `index.html` (`HOLIDAYS`) ergänzen. Ergebnis mit dem gedruckten Kalender vergleichen, dann pushen.
