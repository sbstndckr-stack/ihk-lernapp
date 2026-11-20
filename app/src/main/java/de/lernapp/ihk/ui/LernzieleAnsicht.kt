package de.lernapp.ihk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import de.lernapp.ihk.Ansicht
import de.lernapp.ihk.AppZustand
import de.lernapp.ihk.core.Rechnen

@Composable
fun LernzieleAnsicht(z: AppZustand) {
    @Suppress("UNUSED_VARIABLE") val tick = z.version
    Seite("Alle Lernziele", { z.ansicht = Ansicht.Start }) {
        val kurs = z.kurs
        if (kurs == null) {
            Hinweis("Kein Kurs geladen.")
            return@Seite
        }
        val gruppen = kurs.lernzieleSortiert.groupBy { it.tag.ifBlank { it.thema.ifBlank { "ohne Zuordnung" } } }
        for ((tag, lzs) in gruppen) {
            Ueberschrift(tag)
            for (lz in lzs) {
                val fragen = kurs.fragenZu(lz.id)
                val st = z.fortschritt.stand(lz.id, fragen.any { it.istPruefung })
                val farbe = when {
                    st.sehrGut -> HellGruen
                    st.sicher -> HellBlau
                    st.begonnen -> HellGelb
                    else -> androidx.compose.ui.graphics.Color.White
                }
                Kasten(farbe) {
                    Column(modifier = Modifier.fillMaxWidth().clickable { z.ansicht = Ansicht.Lernen(lz.id) }) {
                        Text(lz.titel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        val pr = st.letztePruefung
                        Klein(
                            "${st.text} · ${fragen.size} Aufgaben · Serie ${st.serie}/3" +
                                (if (pr != null) " · letzte Prüfungsaufgabe ${Rechnen.zahl(pr, 0)} %" else "") +
                                (if (lz.id in z.fortschritt.morgenZuerst) " · heute zuerst" else ""),
                        )
                    }
                }
            }
        }
    }
}
