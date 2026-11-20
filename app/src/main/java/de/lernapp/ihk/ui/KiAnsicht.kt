package de.lernapp.ihk.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.lernapp.ihk.Ansicht
import de.lernapp.ihk.AppZustand
import de.lernapp.ihk.core.Rechnen
import de.lernapp.ihk.ki.ModellVerwaltung
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun KiAnsicht(z: AppZustand) {
    @Suppress("UNUSED_VARIABLE") val tick = z.version
    var downloads by remember { mutableStateOf<List<ModellVerwaltung.Laden>>(emptyList()) }
    var dateien by remember { mutableStateOf<List<File>>(z.modelle.vorhandene()) }
    var kopieren by remember { mutableStateOf<String?>(null) }
    var testText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            downloads = z.modelle.pruefen()
            dateien = z.modelle.vorhandene()
            delay(1500)
        }
    }
    val waehlen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            kopieren = "Wird übernommen …"
            z.scope.launch {
                try {
                    val f = z.modelle.uebernehmen(uri) { n -> kopieren = "Übernommen: ${n / 1_000_000} MB …" }
                    kopieren = null
                    dateien = z.modelle.vorhandene()
                    z.meldung = "${f.name} übernommen."
                } catch (t: Throwable) {
                    kopieren = null
                    z.meldung = t.message
                }
            }
        }
    }

    Seite("KI & Einstellungen", { z.ansicht = Ansicht.Start }) {
        Klein("Gerät: ${z.geraetText()}")

        // ---------- Lokale KI ----------
        Ueberschrift("Lokale KI (offline, kostenlos)")
        Kasten(if (z.lokal.bereit) HellGruen else HellGelb) {
            Absatz(z.kiStatus)
            if (z.lokal.bereit) {
                OutlinedButton(onClick = { z.kiEntladen() }) { Text("Entladen (Speicher freigeben)") }
                OutlinedButton(onClick = {
                    testText = "…"
                    z.scope.launch {
                        val t0 = System.currentTimeMillis()
                        testText = try {
                            val a = z.lokal.frage("Antworte auf Deutsch in einem Satz.", "Was regelt § 5 Arbeitszeitgesetz?", 0.2) { t -> testText = t }
                            a + "\n(${(System.currentTimeMillis() - t0) / 1000.0} s)"
                        } catch (t: Throwable) {
                            "Fehler: ${t.message}"
                        }
                    }
                }) { Text("Kurztest") }
                if (testText.isNotBlank()) Klein(testText)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = z.einstellungen.gpu, onCheckedChange = { an -> z.einstellungenAendern { it.gpu = an } })
                Text("  GPU verwenden (sonst CPU)", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (dateien.isEmpty()) {
            Klein("Noch kein Modell auf dem Handy.")
        }
        for (f in dateien) {
            Kasten {
                Text(f.name, fontWeight = FontWeight.Medium)
                Klein("${f.length() / 1_000_000} MB" + (z.qualitaet.modelltests[f.name]?.let { mt ->
                    val abw = (mt["abweichung"] as? Number)?.toDouble()
                    " · Modelltest: " + (if (abw != null) "Ø Abweichung ${Rechnen.zahl(abw, 2)} P, " else "") +
                        "${(mt["treffer"] as? Number)?.toInt() ?: 0} von ${(mt["n"] as? Number)?.toInt() ?: 0} Treffer, " +
                        "${(mt["milder"] as? Number)?.toInt() ?: 0}× milder"
                } ?: ""))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { z.kiLaden(f.absolutePath) }, enabled = !z.kiLaedt) { Text("Laden") }
                    OutlinedButton(onClick = {
                        z.modelle.loeschen(f)
                        dateien = z.modelle.vorhandene()
                    }, enabled = z.lokal.modellName != f.name) { Text("Löschen") }
                }
            }
        }
        for (d in downloads.filter { !it.fertig && !it.fehler }) {
            Kasten(HellBlau) {
                Text("Lädt: ${d.datei}")
                LinearProgressIndicator(progress = { d.anteil }, modifier = Modifier.fillMaxWidth())
                Klein("${d.geladen / 1_000_000} von ${d.gesamt / 1_000_000} MB")
                OutlinedButton(onClick = { z.modelle.abbrechen(d.id) }) { Text("Abbrechen") }
            }
        }
        for (d in downloads.filter { it.fehler }) Hinweis("Download fehlgeschlagen: ${d.datei}. Alternative: Datei am PC laden und unten übernehmen.", HellRot)

        Kasten {
            Klein("Modell herunterladen (direkt von Hugging Face, WLAN empfohlen)")
            for (a in z.modelle.angebote) {
                val vorhanden = dateien.any { it.name == a.datei }
                OutlinedButton(
                    onClick = {
                        z.modelle.herunterladen(a)
                        z.meldung = "Download gestartet: ${a.name} (${a.groesse}). Fortschritt auch in der Benachrichtigung."
                    },
                    enabled = !vorhanden && downloads.none { it.datei == a.datei && !it.fertig && !it.fehler },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("${a.name} · ${a.groesse}" + if (vorhanden) " ✔" else "") }
                Klein(a.hinweis)
            }
            OutlinedButton(onClick = { waehlen.launch(arrayOf("*/*")) }, enabled = kopieren == null, modifier = Modifier.fillMaxWidth()) {
                Text("Modelldatei vom Handy übernehmen (.litertlm)")
            }
            kopieren?.let { Klein(it) }
        }

        // ---------- Claude ----------
        Ueberschrift("Claude-Rückfall (nur wenn die lokale KI überfordert ist)")
        ClaudeEinstellungen(z)

        Ueberschrift("Kurs")
        Klein(z.kurs?.let { "${it.titel} · ${it.lernziele.size} Lernziele · ${it.fragen.size} Fragen · ${it.vokabeln.size} Fachbegriffe" } ?: "Kein Kurs geladen.")
        Neben("Probe-Paket laden (ersetzt den Kurs, Fortschritt bleibt)") { z.probePaketLaden() }
    }
}

@Composable
private fun ClaudeEinstellungen(z: AppZustand) {
    val e = z.einstellungen
    var schluessel by remember { mutableStateOf(e.apiSchluessel) }
    var budget by remember { mutableStateOf(Rechnen.zahl(e.budgetEur, 2)) }
    var kurs by remember { mutableStateOf(Rechnen.zahl(e.eurJeUsd, 3)) }
    var streng by remember { mutableStateOf(e.modellStreng) }
    var einfach by remember { mutableStateOf(e.modellEinfach) }
    var pse by remember { mutableStateOf(Rechnen.zahl(e.preisStrengEin, 3)) }
    var psa by remember { mutableStateOf(Rechnen.zahl(e.preisStrengAus, 3)) }
    var pee by remember { mutableStateOf(Rechnen.zahl(e.preisEinfachEin, 3)) }
    var pea by remember { mutableStateOf(Rechnen.zahl(e.preisEinfachAus, 3)) }
    Kasten {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = e.onlineErlaubt, onCheckedChange = { an -> z.einstellungenAendern { it.onlineErlaubt = an } })
            Text("  Online-Rückfall erlaubt", style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
            value = schluessel, onValueChange = { schluessel = it.trim() },
            label = { Text("API-Schlüssel (console.anthropic.com)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        Klein("Eigener Schlüssel, Bezahlung pro Nutzung – getrennt vom Claude-Abo. Wird nur in dieser App gespeichert.")
        OutlinedTextField(value = budget, onValueChange = { budget = it }, label = { Text("Monatsbudget in €") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = kurs, onValueChange = { kurs = it }, label = { Text("Euro je US-Dollar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = streng, onValueChange = { streng = it.trim() }, label = { Text("Modell für strenge Bewertung") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = pse, onValueChange = { pse = it }, label = { Text("\$ / Mio. ein") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = psa, onValueChange = { psa = it }, label = { Text("\$ / Mio. aus") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = einfach, onValueChange = { einfach = it.trim() }, label = { Text("Modell für Erklärungen") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = pee, onValueChange = { pee = it }, label = { Text("\$ / Mio. ein") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = pea, onValueChange = { pea = it }, label = { Text("\$ / Mio. aus") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Haupt("Speichern") {
            z.einstellungenAendern {
                it.apiSchluessel = schluessel
                Rechnen.leseZahl(budget)?.let { b -> it.budgetEur = b }
                Rechnen.leseZahl(kurs)?.let { k -> it.eurJeUsd = k }
                if (streng.isNotBlank()) it.modellStreng = streng
                if (einfach.isNotBlank()) it.modellEinfach = einfach
                Rechnen.leseZahl(pse)?.let { p -> it.preisStrengEin = p }
                Rechnen.leseZahl(psa)?.let { p -> it.preisStrengAus = p }
                Rechnen.leseZahl(pee)?.let { p -> it.preisEinfachEin = p }
                Rechnen.leseZahl(pea)?.let { p -> it.preisEinfachAus = p }
            }
            z.meldung = "Gespeichert."
        }
        val q = z.qualitaet
        Klein(
            "Diesen Monat: ${Rechnen.zahl(q.monatEur(e), 2)} € von ${Rechnen.zahl(e.budgetEur, 2)} € · " +
                "Warnung ab 80 %, bei 100 % nur noch lokal.",
        )
    }
}
