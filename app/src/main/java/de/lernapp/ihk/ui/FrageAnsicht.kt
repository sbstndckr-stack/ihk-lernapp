package de.lernapp.ihk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.lernapp.ihk.AppZustand
import de.lernapp.ihk.IhkErgebnis
import de.lernapp.ihk.core.Bewertung
import de.lernapp.ihk.core.Diagnose
import de.lernapp.ihk.core.Frage
import de.lernapp.ihk.core.Lernziel
import de.lernapp.ihk.core.Noten
import de.lernapp.ihk.core.Operatoren
import de.lernapp.ihk.core.Rechnen
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Zeigt eine Aufgabe jeder Art. onErgebnis(Prozent) meldet die Bewertung, onWeiter fordert die nächste Aufgabe an.
 * Der Zustand hängt an (Frage, seed) – neue Aufgabe = frischer Zustand.
 */
@Composable
fun FrageAnsicht(
    z: AppZustand,
    q: Frage,
    lz: Lernziel?,
    abschluss: Boolean,
    seed: Long,
    onErgebnis: (Double) -> Unit,
    onWeiter: () -> Unit,
) {
    androidx.compose.runtime.key(q.id, seed) {
        when (q.typ) {
            "single", "multi" -> Ankreuzen(z, q, lz, abschluss, seed, onErgebnis, onWeiter)
            "rechnen" -> Rechenaufgabe(z, q, lz, abschluss, seed, onErgebnis, onWeiter)
            else -> IhkAufgabe(z, q, lz, abschluss, onErgebnis, onWeiter)
        }
    }
}

@Composable
private fun Ankreuzen(
    z: AppZustand, q: Frage, lz: Lernziel?, abschluss: Boolean, seed: Long,
    onErgebnis: (Double) -> Unit, onWeiter: () -> Unit,
) {
    val reihenfolge = remember { q.options.indices.shuffled(Random(seed)) }
    val gewaehlt = remember { mutableStateListOf<Int>() }
    var richtig by remember { mutableStateOf<Boolean?>(null) }
    val multi = q.typ == "multi"
    Kasten {
        Klein(if (multi) "Einstieg · mehrere Antworten können richtig sein" else "Einstieg · eine Antwort ist richtig")
        Text(q.prompt, style = MaterialTheme.typography.bodyLarge)
    }
    for (i in reihenfolge) {
        val an = i in gewaehlt
        val farbe = when {
            richtig == null -> androidx.compose.ui.graphics.Color.White
            i in q.correct -> HellGruen
            an -> HellRot
            else -> androidx.compose.ui.graphics.Color.White
        }
        Kasten(farbe) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(enabled = richtig == null) {
                    if (multi) {
                        if (an) gewaehlt.remove(i) else gewaehlt.add(i)
                    } else {
                        gewaehlt.clear()
                        gewaehlt.add(i)
                    }
                },
            ) {
                if (multi) {
                    Checkbox(checked = an, onCheckedChange = null)
                } else {
                    RadioButton(selected = an, onClick = null)
                }
                Text(q.options[i], modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    val r = richtig
    if (r == null) {
        Haupt("Prüfen", enabled = gewaehlt.isNotEmpty()) {
            val ok = gewaehlt.toSet() == q.correct.toSet()
            richtig = ok
            z.regelVersuch(q, gewaehlt.sorted().joinToString(",") { q.options[it] }, ok, abschluss)
            onErgebnis(if (ok) 100.0 else 0.0)
        }
    } else {
        Hinweis((if (r) "✔ Richtig. " else "✘ Nicht ganz. ") + q.explanation, if (r) HellGruen else HellRot)
        if (!r) ErklaerBox(z, lz, q, gewaehlt.joinToString("; ") { q.options[it] })
        Haupt("Weiter") { onWeiter() }
    }
}

@Composable
private fun Rechenaufgabe(
    z: AppZustand, q: Frage, lz: Lernziel?, abschluss: Boolean, seed: Long,
    onErgebnis: (Double) -> Unit, onWeiter: () -> Unit,
) {
    val v = remember { Rechnen.variante(q, seed) }
    var weg by remember { mutableStateOf("") }
    var ergebnis by remember { mutableStateOf("") }
    var diag by remember { mutableStateOf<Diagnose?>(null) }
    Kasten {
        Klein("Rechenaufgabe · Rechenweg ist Pflicht")
        Text(v.prompt, style = MaterialTheme.typography.bodyLarge)
        for (w in v.werte) {
            Text("• ${w.label}: ${Rechnen.zahl(w.wert)} ${w.einheit}", style = MaterialTheme.typography.bodyMedium)
        }
    }
    OutlinedTextField(
        value = weg, onValueChange = { weg = it }, enabled = diag == null,
        label = { Text("Rechenweg: Formel → eingesetzt → Ergebnis") },
        modifier = Modifier.fillMaxWidth(), minLines = 3,
    )
    OutlinedTextField(
        value = ergebnis, onValueChange = { ergebnis = it }, enabled = diag == null,
        label = { Text("Ergebnis" + if (q.unit.isNotBlank()) " in ${q.unit}" else "") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(), singleLine = true,
    )
    val d = diag
    if (d == null) {
        Haupt("Prüfen", enabled = weg.isNotBlank() && ergebnis.isNotBlank()) {
            val x = Rechnen.leseZahl(ergebnis)
            if (x == null) {
                z.meldung = "Das Ergebnis ist keine Zahl. Beispiel: 8,5"
            } else {
                val dg = Rechnen.pruefe(v, x)
                diag = dg
                z.regelVersuch(q, "Ergebnis: $ergebnis\nRechenweg: $weg", dg.richtig, abschluss)
                onErgebnis(if (dg.richtig) 100.0 else 0.0)
            }
        }
    } else {
        Hinweis(
            (if (d.richtig) "✔ " else "✘ ") + d.meldung +
                "\n\nRichtiges Ergebnis: ${Rechnen.zahl(v.antwort)} ${q.unit}" +
                (if (v.loesung.isNotBlank()) "\nMusterrechenweg: ${v.loesung}" else "") +
                (if (v.erklaerung.isNotBlank()) "\n\n${v.erklaerung}" else ""),
            if (d.richtig) HellGruen else HellRot,
        )
        Klein("Danach dieselbe Aufgabe in Excel nachrechnen (Formel mit Zellbezügen).")
        if (!d.richtig) ErklaerBox(z, lz, q, "Ergebnis $ergebnis, Rechenweg: $weg")
        Haupt("Weiter") { onWeiter() }
    }
}

@Composable
private fun IhkAufgabe(
    z: AppZustand, q: Frage, lz: Lernziel?, abschluss: Boolean,
    onErgebnis: (Double) -> Unit, onWeiter: () -> Unit,
) {
    var antwort by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var erg by remember { mutableStateOf<IhkErgebnis?>(null) }
    var wahl by remember { mutableStateOf(false) }
    var selbst by remember { mutableStateOf(false) }
    var geruest by remember { mutableStateOf(false) }
    var tipp by remember { mutableStateOf("") }
    var muster by remember { mutableStateOf(false) }
    var einspruchOffen by remember { mutableStateOf(false) }
    var einspruchText by remember { mutableStateOf("") }

    if (q.situation.isNotBlank()) {
        Kasten(HellBlau) {
            Klein("Situation")
            Text(q.situation, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Kasten {
        Klein("IHK-Aufgabe · ${Rechnen.zahl(q.maxPunkte)} Punkte · ${q.erwartung.size} erwartete Aspekte")
        Text(q.aufgabe, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        for ((op, bedeutung) in Operatoren.finde(q.aufgabe)) {
            Klein("Operator „$op“: $bedeutung")
        }
    }
    val karte = lz?.karte
    if (karte != null && karte.geruest.isNotEmpty() && erg == null) {
        if (geruest) {
            Kasten(HellGelb) {
                Klein("Gerüst (so antwortest du)")
                karte.geruest.forEachIndexed { i, g -> Text("${i + 1}. $g", style = MaterialTheme.typography.bodySmall) }
            }
        } else {
            Neben("Gerüst anzeigen (Hilfe)") { geruest = true }
        }
    }

    OutlinedTextField(
        value = antwort, onValueChange = { antwort = it }, enabled = erg == null && status == null && !selbst,
        label = { Text("Deine Antwort – Häkchen ✔ je Aspekt, Urteil zuerst") },
        modifier = Modifier.fillMaxWidth(), minLines = 8,
    )

    val e = erg
    if (e == null && !selbst) {
        val st = status
        if (st != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                Text(st, style = MaterialTheme.typography.bodyMedium)
            }
        } else if (wahl) {
            Hinweis("Die lokale KI ist nicht geladen. Wie soll bewertet werden?", HellGelb)
            Haupt("Selbst bewerten (Claude prüft später gesammelt nach)") { selbst = true }
            Neben("Jetzt von Claude bewerten lassen (≈ 0,3 Cent)", enabled = z.onlineMoeglich()) {
                status = "Claude bewertet …"
                z.scope.launch {
                    try {
                        val r = z.claudeDirekt(q, antwort, abschluss)
                        erg = r
                        onErgebnis(r.bewertung.prozent(q.maxPunkte))
                    } catch (t: Throwable) {
                        z.meldung = "Claude: ${t.message}"
                    } finally {
                        status = null
                    }
                }
            }
        } else {
            if (tipp.isNotBlank()) Hinweis("Tipp: $tipp", HellGelb)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        tipp = "…"
                        z.scope.launch {
                            try {
                                tipp = z.tipp(q, antwort) { t -> tipp = t }
                            } catch (t: Throwable) {
                                tipp = karte?.geruest?.firstOrNull() ?: "Lies den Operator und zähle die geforderten Aspekte."
                            }
                        }
                    },
                    enabled = z.lokal.bereit,
                    modifier = Modifier.weight(1f),
                ) { Text("Tipp") }
                Button(
                    onClick = {
                        status = "Wird bewertet …"
                        z.scope.launch {
                            try {
                                val r = z.bewerteIhk(q, antwort, abschluss) { s -> status = s }
                                if (r.brauchtWahl) {
                                    wahl = true
                                } else {
                                    erg = r
                                    onErgebnis(r.bewertung.prozent(q.maxPunkte))
                                }
                            } catch (t: Throwable) {
                                z.meldung = "Bewertung fehlgeschlagen: ${t.message}"
                            } finally {
                                status = null
                            }
                        }
                    },
                    enabled = antwort.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("Abgeben") }
            }
        }
    }

    if (selbst && e == null) {
        SelbstBewertung(q) { punkte ->
            val r = z.selbstBewertet(q, antwort, punkte, abschluss)
            erg = r
            selbst = false
            onErgebnis(r.bewertung.prozent(q.maxPunkte))
        }
    }

    if (e != null) {
        ErgebnisTabelle(q, e.bewertung)
        Klein(e.hinweis)
        if (muster) {
            Kasten(HellGruen) {
                Klein("Musterlösung")
                Text(q.musterloesung, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Neben("Musterlösung zeigen") { muster = true }
        }
        if (e.versuchId.isNotBlank() && z.onlineMoeglich()) {
            if (einspruchOffen) {
                OutlinedTextField(
                    value = einspruchText, onValueChange = { einspruchText = it },
                    label = { Text("Warum ist die Bewertung falsch?") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
                Neben("Einspruch an Claude senden", enabled = status == null) {
                    status = "Claude prüft den Einspruch …"
                    z.scope.launch {
                        try {
                            val r = z.einspruch(q, e.versuchId, antwort, einspruchText)
                            erg = r
                            einspruchOffen = false
                        } catch (t: Throwable) {
                            z.meldung = "Einspruch: ${t.message}"
                        } finally {
                            status = null
                        }
                    }
                }
                status?.let { Klein(it) }
            } else {
                Neben("Einspruch") { einspruchOffen = true }
            }
        }
        if (e.bewertung.prozent(q.maxPunkte) < Noten.SEHR_GUT) ErklaerBox(z, lz, q, antwort)
        Haupt("Weiter") { onWeiter() }
    }
}

@Composable
private fun SelbstBewertung(q: Frage, fertig: (List<Double>) -> Unit) {
    val punkte = remember { mutableStateListOf<Double>().apply { repeat(q.erwartung.size) { add(-1.0) } } }
    Hinweis("Vergleiche deine Antwort ehrlich mit jeder Erwartung. Fachbegriff fehlt → höchstens die Hälfte.", HellGelb)
    q.erwartung.forEachIndexed { i, e ->
        Kasten {
            Text("${i + 1}. ${e.text}", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val stufen = listOf(0.0, Math.round(e.punkte) / 2.0, e.punkte).distinct()
                for (s in stufen) {
                    val an = punkte[i] == s
                    if (an) {
                        Button(onClick = { punkte[i] = s }, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text(Rechnen.zahl(s) + " P") }
                    } else {
                        OutlinedButton(onClick = { punkte[i] = s }, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text(Rechnen.zahl(s) + " P") }
                    }
                }
            }
        }
    }
    Haupt("Selbstbewertung speichern", enabled = punkte.none { it < 0 }) { fertig(punkte.toList()) }
}

@Composable
fun ErgebnisTabelle(q: Frage, b: Bewertung) {
    val p = b.prozent(q.maxPunkte)
    Kasten(if (p >= 92) HellGruen else if (p >= 50) HellBlau else HellRot) {
        val quelle = when (b.quelle) {
            "claude" -> "Claude (streng)"
            "lokal" -> "lokale KI (vorläufig)"
            "selbst" -> "Selbstbewertung (vorläufig)"
            else -> b.quelle
        }
        Klein("Bewertung: $quelle")
        Text(
            "${Rechnen.zahl(b.summe())} von ${Rechnen.zahl(q.maxPunkte)} Punkten · ${Rechnen.zahl(p, 0)} % · Note ${Noten.note(p)} (${Noten.stufe(p)})",
            color = farbeFuer(p), fontWeight = FontWeight.SemiBold,
        )
        q.erwartung.forEachIndexed { i, e ->
            val pt = b.punkte.getOrElse(i) { 0.0 }
            val zeichen = when {
                pt >= e.punkte -> "✔"
                pt > 0 -> "◐"
                else -> "✘"
            }
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text("$zeichen ${Rechnen.zahl(pt)} / ${Rechnen.zahl(e.punkte)} P", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                Klein(e.text)
            }
        }
        if (b.begruendung.isNotBlank()) Text("Begründung: ${b.begruendung}", style = MaterialTheme.typography.bodyMedium)
        if (b.naechster.isNotBlank()) Text("Nächster Schritt: ${b.naechster}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** „Anders erklären“: lokal (kostenlos) oder Claude Haiku (wenige Zehntel Cent). */
@Composable
fun ErklaerBox(z: AppZustand, lz: Lernziel?, q: Frage?, antwort: String) {
    var text by remember { mutableStateOf("") }
    var laeuft by remember { mutableStateOf(false) }
    if (text.isNotBlank()) Hinweis(text, HellBlau)
    if (laeuft) {
        Klein("Erklärung entsteht …")
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    laeuft = true
                    z.scope.launch {
                        try {
                            text = z.erklaeren(lz, q, antwort, false) { t -> text = t }
                        } catch (t: Throwable) {
                            z.meldung = t.message
                        } finally {
                            laeuft = false
                        }
                    }
                },
                enabled = z.lokal.bereit,
                modifier = Modifier.weight(1f),
            ) { Text("Anders erklären") }
            OutlinedButton(
                onClick = {
                    laeuft = true
                    z.scope.launch {
                        try {
                            text = z.erklaeren(lz, q, antwort, true) { t -> text = t }
                        } catch (t: Throwable) {
                            z.meldung = t.message
                        } finally {
                            laeuft = false
                        }
                    }
                },
                enabled = z.onlineMoeglich(),
                modifier = Modifier.weight(1f),
            ) { Text("Claude erklärt") }
        }
    }
}
