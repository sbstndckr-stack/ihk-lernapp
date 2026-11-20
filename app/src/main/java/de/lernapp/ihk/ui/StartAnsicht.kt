package de.lernapp.ihk.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.lernapp.ihk.Ansicht
import de.lernapp.ihk.AppZustand
import de.lernapp.ihk.core.Leitner
import de.lernapp.ihk.core.Lernschleife
import de.lernapp.ihk.core.Noten
import de.lernapp.ihk.core.Rechnen
import de.lernapp.ihk.core.gesamtstand
import de.lernapp.ihk.core.heute

@Composable
fun StartAnsicht(z: AppZustand) {
    @Suppress("UNUSED_VARIABLE") val tick = z.version
    val oeffnen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) z.kursAusDatei(uri)
    }
    Seite("IHK Lernapp", null) {
        val kurs = z.kurs
        if (kurs == null) {
            Hinweis("Noch kein Kurs geladen. Lade das Kurs-Paket aus dem Lern-Monitor („Kurs-Paket herunterladen“) oder zum Ausprobieren das Probe-Paket.")
            Haupt("Kurs-Paket importieren (JSON)") { oeffnen.launch(arrayOf("application/json", "text/plain", "*/*")) }
            Neben("Probe-Paket laden") { z.probePaketLaden() }
            Neben("KI & Einstellungen") { z.ansicht = Ansicht.Ki }
            return@Seite
        }
        val f = z.fortschritt
        val g = gesamtstand(kurs, f)
        Kasten {
            Text(kurs.titel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val anteil = if (g.lernziele > 0) g.sicher.toFloat() / g.lernziele else 0f
            LinearProgressIndicator(progress = { anteil }, modifier = Modifier.fillMaxWidth())
            Absatz("Lernziele sicher: ${g.sicher} von ${g.lernziele} · sehr gut: ${g.sehrGut} · begonnen: ${g.begonnen}")
            val s = g.schnittPruefung
            if (s != null) {
                Text(
                    "Prüfungsaufgaben im Schnitt: ${Rechnen.zahl(s, 0)} % → Note ${Noten.note(s)} (${Noten.stufe(s)}). Ziel: 95.",
                    color = farbeFuer(s),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Klein("Noch keine Prüfungsaufgabe bewertet.")
            }
        }

        // Start-Check: Energie
        Kasten {
            Ueberschrift("Start-Check: Energie heute")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                for (e in 1..5) {
                    val gewaehlt = z.energieHeute == e
                    if (gewaehlt) {
                        Button(onClick = { z.energieSetzen(e) }, modifier = Modifier.weight(1f), contentPadding = ButtonDefaults.TextButtonContentPadding) { Text("$e") }
                    } else {
                        OutlinedButton(onClick = { z.energieSetzen(e) }, modifier = Modifier.weight(1f), contentPadding = ButtonDefaults.TextButtonContentPadding) { Text("$e") }
                    }
                }
            }
            val emp = when (z.energieHeute) {
                null -> "Tippe deine Energie an – die App schlägt dann das Pensum vor."
                1, 2 -> "Energie 1–2: nur 10 Minuten Fachbegriffe. Der Rest kommt morgen zuerst."
                3 -> "Energie 3: zwei Lernziele, dann Abschluss-Quiz."
                else -> "Energie 4–5: ganzer Lernblock – Lernziele, Abschluss-Quiz, Fachbegriffe."
            }
            Klein(emp)
        }

        val naechstes = Lernschleife.naechstesLernziel(kurs, f)
        if (naechstes != null) {
            Haupt("Weiter lernen: ${naechstes.titel}") { z.ansicht = Ansicht.Lernen(naechstes.id) }
        } else {
            Hinweis("Alle Lernziele mit Inhalt sind sicher. Wiederhole in „Alle Lernziele“ oder importiere neue Inhalte.", HellGruen)
        }
        val faellig = Leitner.anzahlFaellig(kurs, f, heute())
        Neben("Fachbegriffe ($faellig fällig)") { z.ansicht = Ansicht.Vokabeln }
        val heuteGeuebt = f.versuche.any { it.tag == heute() && !it.abschluss }
        Neben("Einheit beenden: Abschluss-Quiz", enabled = heuteGeuebt) { z.ansicht = Ansicht.Abschluss }
        Neben("Alle Lernziele") { z.ansicht = Ansicht.Lernziele }

        // KI-Stand
        Kasten(if (z.lokal.bereit) HellGruen else HellGelb) {
            Ueberschrift("KI")
            Absatz(z.kiStatus)
            val q = z.qualitaet
            val e = z.einstellungen
            if (e.apiSchluessel.isNotBlank()) {
                Klein(
                    "Claude-Rückfall: ${Rechnen.zahl(q.monatEur(e), 2)} € von ${Rechnen.zahl(e.budgetEur, 2)} € im Monat · " +
                        "Stichprobe ${(q.stichprobenQuote * 100).toInt()} % · ${q.warteschlange.size} zur Nachprüfung vorgemerkt",
                )
            } else {
                Klein("Claude-Rückfall nicht eingerichtet (nur lokale KI und Selbstbewertung).")
            }
        }
        Neben("KI & Einstellungen") { z.ansicht = Ansicht.Ki }
        Neben("Qualitätskreis (PDCA)") { z.ansicht = Ansicht.Qualitaetskreis }
        Neben("Neues Kurs-Paket importieren") { oeffnen.launch(arrayOf("application/json", "text/plain", "*/*")) }
    }
}
