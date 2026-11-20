package de.lernapp.ihk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.lernapp.ihk.Ansicht
import de.lernapp.ihk.AppZustand
import de.lernapp.ihk.core.Leitner
import de.lernapp.ihk.core.heute
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Fachbegriffe im Leitner-Kasten: Begriff → Bedeutung → Beispiel und umgekehrt. 10 Minuten am Tag. */
@Composable
fun VokabelAnsicht(z: AppZustand) {
    @Suppress("UNUSED_VARIABLE") val tick = z.version
    val kurs = z.kurs
    if (kurs == null) {
        Seite("Fachbegriffe", { z.ansicht = Ansicht.Start }) { Hinweis("Kein Kurs geladen.") }
        return
    }
    val start = remember { System.currentTimeMillis() }
    var sekunden by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            sekunden = (System.currentTimeMillis() - start) / 1000
            delay(1000)
        }
    }
    var runde by remember { mutableIntStateOf(0) }
    var aufgedeckt by remember { mutableStateOf(false) }
    var geschafft by remember { mutableIntStateOf(0) }
    val faellig = remember(runde) { Leitner.faellige(kurs, z.fortschritt, heute()) }
    val karte = faellig.firstOrNull()
    val umgekehrt = remember(runde) { Random.nextInt(3) == 0 }

    Seite("Fachbegriffe", { z.ansicht = Ansicht.Start }) {
        Klein("${sekunden / 60}:${(sekunden % 60).toString().padStart(2, '0')} von 10:00 · heute geschafft: $geschafft · noch fällig: ${faellig.size}")
        if (sekunden >= 600) Hinweis("10 Minuten geschafft – du kannst aufhören oder weitermachen.", HellGruen)
        if (karte == null) {
            Hinweis("Für heute ist nichts mehr fällig. Morgen geht es weiter.", HellGruen)
            Haupt("Zur Startseite") { z.ansicht = Ansicht.Start }
            return@Seite
        }
        val fach = z.fortschritt.leitner[karte.id]?.box ?: 0
        Kasten {
            Klein(if (fach == 0) "neue Karte" else "Fach $fach von 5")
            if (umgekehrt) {
                Klein("Welcher Fachbegriff ist das?")
                Text(karte.bedeutung, style = MaterialTheme.typography.bodyLarge)
            } else {
                Klein("Was bedeutet …?")
                Text(karte.begriff, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!aufgedeckt) {
            Haupt("Aufdecken") { aufgedeckt = true }
        } else {
            Kasten(HellBlau) {
                if (umgekehrt) {
                    Text(karte.begriff, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                } else {
                    Text(karte.bedeutung, style = MaterialTheme.typography.bodyLarge)
                }
                if (karte.beispiel.isNotBlank()) Klein("Beispiel: ${karte.beispiel}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = {
                    Leitner.antwort(z.fortschritt, karte.id, false, heute())
                    z.speichern()
                    aufgedeckt = false
                    runde++
                }, modifier = Modifier.weight(1f)) { Text("Nicht gewusst") }
                Button(onClick = {
                    Leitner.antwort(z.fortschritt, karte.id, true, heute())
                    z.speichern()
                    geschafft++
                    aufgedeckt = false
                    runde++
                }, modifier = Modifier.weight(1f)) { Text("Gewusst") }
            }
        }
    }
}
