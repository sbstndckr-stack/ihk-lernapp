# IHK Lernapp (Android, Version 0.1)

Lern-App für den Fachwirt im Gesundheits- und Sozialwesen – **lokale KI zuerst, Claude nur als Rückfall** (Konzept 6c vom 20.11.2026).

## Wer macht was

| Stufe | Aufgaben | Kosten |
|---|---|---|
| Regel-Code (ohne KI) | Ankreuzen, Rechnen mit wechselnden Zahlen, Fachbegriffe (Leitner), Lernschleife, Notenschlüssel, Abschluss-Quiz | 0 Token |
| Lokale KI (Gemma 4 über LiteRT-LM, offline) | IHK-Antworten vorläufig bewerten, Tipps, „anders erklären“, „einfacher erklären“ | kostenlos |
| Claude-API (eigener Schlüssel) | strenge Bewertung nach den Eskalationsregeln, Einspruch, Stichproben per Batch | ≈ 0,3 Cent je Bewertung, Batch halb so viel |

**Eskalation an Claude:** lokale KI unsicher oder unlesbar · Ergebnis knapp (± 4 Prozentpunkte) an 50, 67 oder 92 % · Inhalte außerhalb der Musterlösung · Einspruch · Stichprobe (10 % → 5 % → 2 %, steigt wieder bei Fehlern).

**Qualitätskreis (PDCA):** Jede Claude-Bewertung wird Lehrbeispiel für die lokale KI (bis 2 im Prompt, kein Neutraining). Richtige Formulierungen außerhalb der Musterlösung → „auch richtig“. Lokale KI milder als Claude = Fehler → Stichprobe steigt. Jede automatische Änderung steht in der Änderungsliste und ist einzeln rückgängig. Programmcode ändert sich nie automatisch.

**Token sparen:** gleiche Antwort → Ergebnis aus dem Speicher · an Claude nur Aufgabe, Erwartungshorizont, Antwort · fester Prüfer-Systemprompt mit `cache_control` · Antwort nur JSON, `max_tokens` 300 · Erklärungen mit dem kleinen Modell · Stichproben gesammelt über die Batch-API · Monatsbudget (Standard 5 €), Warnung ab 80 %, bei 100 % nur noch lokal.

## Bauen

**Über GitHub (ohne Android Studio):** Code in ein privates Repository schieben → Reiter *Actions* → Lauf „APK bauen“ → unten *Artifacts* → `IHK-Lernapp-apk` herunterladen, entpacken, die `.apk` aufs Handy und installieren („Unbekannte Apps installieren“ für den Browser/Dateimanager erlauben).

**Mit Android Studio:** Ordner öffnen → Gradle-Sync abwarten → Handy per USB (Entwickleroptionen → USB-Debugging) → ▶ Run.

Versionen: AGP 9.0.1, Gradle 9.2.1, Kotlin 2.3.21, Compose BOM 2026.02.00, LiteRT-LM 0.17.1, minSdk 31.
Falls der Bau an LiteRT-LM scheitert: in `app/build.gradle.kts` die Version auf `0.11.0` setzen (die Fassung, die Googles Gallery-App nutzt).

## Erster Start auf dem Handy

1. **KI & Einstellungen** → „Gemma 4 E4B (empfohlen)“ herunterladen (3,7 GB, WLAN) → **Laden** → **Kurztest**.
2. Startseite → **Probe-Paket laden** (2 Lernziele Personal W02 zum Ausprobieren) oder **Kurs-Paket importieren** (JSON aus dem Lern-Monitor, siehe `MONITOR_EXPORT.md`).
3. Optional: Claude-API-Schlüssel eintragen (console.anthropic.com) und Monatsbudget prüfen. Modellnamen und Preise sind dort änderbar.
4. Nach ein paar Claude-Bewertungen: **Qualitätskreis → Modelltest** – zeigt Abweichung je Erwartung, Treffer und „milder als Claude“. Danach E2B gegen E4B vergleichen.

## Ehrliche Grenzen

- Die lokale KI (4B-Modell) irrt bei Paragrafen und strenger Bewertung häufiger und neigt zu milder Bewertung. Ihre Punkte sind deshalb **vorläufig**; der Qualitätskreis misst, wann sie reicht.
- Ministral 3 (Kandidat aus dem Konzept) läuft nicht mit LiteRT-LM, sondern nur mit llama.cpp. Kommt als zweite Laufzeit, falls Gemma 4 im Modelltest nicht reicht.
- Der API-Schlüssel liegt im privaten App-Speicher (nur diese App kann ihn lesen), nicht verschlüsselt.
- Das Kurs-Paket mit Pflichtbuch ist nur für dich – APK und Paket nicht weitergeben.

## Aufbau

```
app/src/main/java/de/lernapp/ihk/
  core/   Regel-Kern ohne Android (Json, Formel, Rechnen, Noten, Lernschleife, Leitner, Qualitätskreis, Prompts)
  ki/     LokaleKi (LiteRT-LM), ClaudeApi (Messages + Batches), ModellVerwaltung (Download/Übernahme)
  daten/  Speicher (Dateien im privaten App-Speicher)
  ui/     Compose-Oberflächen
  AppZustand.kt  Bewertungsablauf, Batch-Check, PDCA, Budget
```
