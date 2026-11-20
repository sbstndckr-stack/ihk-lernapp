package de.lernapp.ihk.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.lernapp.ihk.Ansicht
import de.lernapp.ihk.AppZustand
import de.lernapp.ihk.core.Qualitaet
import de.lernapp.ihk.core.Rechnen
import de.lernapp.ihk.core.tagVon
import kotlinx.coroutines.launch

/** Qualitätskreis nach PDCA: Check (Übereinstimmung lokal ↔ Claude), Act (Stichprobe, Lehrbeispiele), Änderungsliste. */
@Composable
fun QualitaetAnsicht(z: AppZustand) {
    @Suppress("UNUSED_VARIABLE") val tick = z.version
    var status by remember { mutableStateOf<String?>(null) }
    val q = z.qualitaet
    val e = z.einstellungen

    Seite("Qualitätskreis (PDCA)", { z.ansicht = Ansicht.Start }) {
        // Check
        val letzte = q.vergleiche.takeLast(10)
        Kasten {
            Ueberschrift("Check: trifft die lokale KI?")
            if (letzte.isEmpty()) {
                Klein("Noch keine Vergleiche. Sie entstehen, wenn Claude eine Antwort nachprüft, die die lokale KI schon bewertet hat.")
            } else {
                val treffer = letzte.count { it.treffer }
                val milder = letzte.count { it.milder }
                val abw = letzte.map { it.abweichungJeErwartung }.average()
                Absatz("Letzte ${letzte.size} Vergleiche: $treffer Treffer · $milder× milder als Claude · Ø Abweichung ${Rechnen.zahl(abw, 2)} Punkte je Erwartung")
                Klein("Treffer = Gesamtpunkte höchstens 0,5 daneben und nicht milder. Milder als Claude zählt immer als Fehler.")
            }
            Absatz("Stichprobe jetzt: ${Qualitaet.prozent(q.stichprobenQuote)} der lokal bewerteten Antworten")
            Klein("Act: ≥ 90 % Treffer bei 10 Vergleichen → Stichprobe sinkt (10 → 5 → 2 %). Unter 80 % oder einmal milder → sie steigt wieder.")
        }

        // Batch
        Kasten {
            Ueberschrift("Nachprüfung durch Claude (gesammelt, 50 % günstiger)")
            Absatz("${q.warteschlange.size} Antworten vorgemerkt (Stichproben, Selbstbewertungen, Ausfälle).")
            if (q.letzterCheck > 0) Klein("Letzter Check gesendet: ${tagVon(q.letzterCheck)}. Automatisch alle 7 Tage beim App-Start.")
            if (q.offenerBatch.isNotBlank()) {
                Klein("Check läuft seit ${tagVon(q.offenerBatchSeit)} (${q.batchInhalt.size} Antworten).")
                OutlinedButton(onClick = {
                    status = "Frage Ergebnis ab …"
                    z.scope.launch {
                        status = try { z.batchAbholen() } catch (t: Throwable) { "Fehler: ${t.message}" }
                    }
                }) { Text("Ergebnis abholen") }
            } else {
                OutlinedButton(onClick = {
                    status = "Sende …"
                    z.scope.launch {
                        status = try { z.stichprobenSenden() } catch (t: Throwable) { "Fehler: ${t.message}" }
                    }
                }, enabled = q.warteschlange.isNotEmpty() && z.onlineMoeglich()) { Text("Jetzt nachprüfen lassen") }
            }
            status?.let { Klein(it) }
        }

        // Modelltest
        Kasten {
            Ueberschrift("Modelltest (Prüfdatensatz)")
            val n = q.lehrbeispiele.size
            Klein("Das geladene Modell bewertet bis zu 30 von Claude bewertete Antworten neu ($n vorhanden). Ergebnis steht unter „KI & Einstellungen“ beim Modell. So entscheidest du, welches Modell bleibt.")
            var testStatus by remember { mutableStateOf<String?>(null) }
            OutlinedButton(onClick = {
                testStatus = "Startet …"
                z.scope.launch {
                    testStatus = try { z.modelltest { s -> testStatus = s } } catch (t: Throwable) { "Fehler: ${t.message}" }
                }
            }, enabled = z.lokal.bereit && n >= 3 && testStatus?.startsWith("Modelltest ") != true) { Text("Modelltest starten") }
            testStatus?.let { Klein(it) }
        }

        // Verbrauch
        Kasten {
            Ueberschrift("Verbrauch Claude-API")
            val monat = q.monatEur(e)
            Absatz("Diesen Monat ${Rechnen.zahl(monat, 2)} € von ${Rechnen.zahl(e.budgetEur, 2)} € (${q.verbrauch.size} Aufrufe gesamt)")
            val nachZweck = q.verbrauch.groupBy { it.zweck }
            for ((zweck, liste) in nachZweck) {
                Klein("$zweck: ${liste.size}× · ${liste.sumOf { it.eingabe + it.cacheLesen + it.cacheSchreiben }} Token ein, ${liste.sumOf { it.ausgabe }} aus · ${Rechnen.zahl(liste.sumOf { it.usd } * e.eurJeUsd, 3)} €")
            }
        }

        // Änderungsliste
        Ueberschrift("Änderungsliste (Act) – jede einzeln rückgängig")
        if (q.aenderungen.isEmpty()) Klein("Noch keine automatischen Änderungen.")
        for (a in q.aenderungen.asReversed().take(40)) {
            Kasten(if (a.zurueck) HellGelb else androidx.compose.ui.graphics.Color.White) {
                Klein(tagVon(a.datum) + if (a.zurueck) " · rückgängig gemacht" else "")
                Text(a.text, style = MaterialTheme.typography.bodyMedium)
                if (!a.zurueck) {
                    OutlinedButton(onClick = { z.rueckgaengig(a) }) { Text("Rückgängig") }
                }
            }
        }
        Klein("Nicht automatisch: Änderungen am Programmcode (nur mit deinem OK) und Neutraining des Modells.")
    }
}
