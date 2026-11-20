package de.lernapp.ihk.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import de.lernapp.ihk.Ansicht
import de.lernapp.ihk.AppZustand
import de.lernapp.ihk.core.Frage
import de.lernapp.ihk.core.Lernkarte
import de.lernapp.ihk.core.Lernschleife
import de.lernapp.ihk.core.Lernziel
import de.lernapp.ihk.core.Noten
import de.lernapp.ihk.core.Rechnen
import de.lernapp.ihk.core.heute
import kotlinx.coroutines.launch

@Composable
fun LernenAnsicht(z: AppZustand, lzId: String) {
    @Suppress("UNUSED_VARIABLE") val tick = z.version
    val kurs = z.kurs
    val lz = kurs?.lernziel(lzId)
    if (kurs == null || lz == null) {
        Seite("Lernen", { z.ansicht = Ansicht.Start }) { Hinweis("Lernziel nicht gefunden.") }
        return
    }
    var zeigeKarte by remember(lzId) { mutableStateOf(lz.karte != null && lzId !in z.fortschritt.karteGesehen) }
    var karteOffen by remember(lzId) { mutableStateOf(false) }
    var frage by remember(lzId) { mutableStateOf<Frage?>(Lernschleife.naechsteFrage(kurs, z.fortschritt, lzId, null)) }
    var seed by remember(lzId) { mutableLongStateOf(System.nanoTime()) }
    var warSicher by remember(lzId) { mutableStateOf(z.fortschritt.stand(lzId, kurs.fragenZu(lzId).any { it.istPruefung }).sicher) }
    var jetztSicher by remember(lzId) { mutableStateOf(false) }

    Seite(lz.titel, { z.ansicht = Ansicht.Lernziele }) {
        val hatPruefung = kurs.fragenZu(lzId).any { it.istPruefung }
        val st = z.fortschritt.stand(lzId, hatPruefung)
        Klein(
            "Stand: ${st.text} · Serie ${st.serie}/3" +
                (if (hatPruefung) " · Prüfungsaufgabe bestanden: ${if (st.pruefungBestanden > 0) "ja" else "noch nicht"}" else "") +
                (st.letztePruefung?.let { " · zuletzt ${Rechnen.zahl(it, 0)} % (Ziel ≥ 92)" } ?: ""),
        )
        if (lz.kann.isNotBlank()) Klein(lz.kann)

        val karte = lz.karte
        if (zeigeKarte && karte != null) {
            LernkarteAnsicht(z, lz, karte)
            Haupt("Verstanden – los geht's") {
                z.karteGesehen(lzId)
                zeigeKarte = false
            }
            return@Seite
        }

        if (jetztSicher) {
            Hinweis(
                if (st.sehrGut) "✔ Lernziel sitzt – sehr gut. Es kommt morgen zur Festigung wieder." else
                    "✔ Lernziel sicher (3 richtig in Folge mit bestandener Prüfungsaufgabe). Für „sehr gut“ fehlt noch eine Prüfungsaufgabe mit mindestens 92 %.",
                HellGruen,
            )
            Haupt("Nächstes Lernziel") {
                val n = Lernschleife.naechstesLernziel(kurs, z.fortschritt)
                z.ansicht = if (n != null && n.id != lzId) Ansicht.Lernen(n.id) else Ansicht.Start
            }
            Neben("Weiter üben") { jetztSicher = false }
            return@Seite
        }

        if (karte != null) {
            if (karteOffen) {
                LernkarteAnsicht(z, lz, karte)
                Neben("Lernkarte zuklappen") { karteOffen = false }
            } else {
                Neben("Lernkarte ansehen") { karteOffen = true }
            }
        }

        val q = frage
        if (q == null) {
            Hinweis("Zu diesem Lernziel gibt es noch keine Aufgaben.")
            return@Seite
        }
        FrageAnsicht(
            z = z, q = q, lz = lz, abschluss = false, seed = seed,
            onErgebnis = {
                val neu = z.fortschritt.stand(lzId, hatPruefung)
                if (neu.sicher && !warSicher) jetztSicher = true
                warSicher = neu.sicher
            },
            onWeiter = {
                frage = Lernschleife.naechsteFrage(kurs, z.fortschritt, lzId, q.id)
                seed = System.nanoTime()
                karteOffen = false
            },
        )
    }
}

@Composable
fun LernkarteAnsicht(z: AppZustand, lz: Lernziel, k: Lernkarte) {
    var einfach by remember(lz.id) { mutableStateOf("") }
    var laeuft by remember(lz.id) { mutableStateOf(false) }
    Kasten(HellBlau) {
        Klein("Lernkarte · Worum geht's?")
        Text(k.kurz, style = MaterialTheme.typography.bodyMedium)
    }
    if (k.begriffe.isNotEmpty()) {
        Kasten {
            Klein("Fachbegriffe")
            for (b in k.begriffe) {
                Text(b.b, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(b.d, style = MaterialTheme.typography.bodyMedium)
                if (b.x.isNotBlank()) Klein("Beispiel: ${b.x}")
            }
        }
    }
    if (k.geruest.isNotEmpty()) {
        Kasten(HellGelb) {
            Klein("So antwortest du")
            k.geruest.forEachIndexed { i, g -> Text("${i + 1}. $g", style = MaterialTheme.typography.bodyMedium) }
        }
    }
    if (k.falle.isNotBlank()) Hinweis("Typische Falle: ${k.falle}", HellRot)
    if (k.merksatz.isNotBlank()) Hinweis("Merksatz: ${k.merksatz}", HellGruen)
    if (einfach.isNotBlank()) Hinweis(einfach, HellBlau)
    Neben(if (laeuft) "Erklärung entsteht …" else "Einfacher erklären (lokale KI)", enabled = z.lokal.bereit && !laeuft) {
        laeuft = true
        z.scope.launch {
            try {
                einfach = z.einfacherErklaeren(lz) { t -> einfach = t }
            } catch (t: Throwable) {
                z.meldung = t.message
            } finally {
                laeuft = false
            }
        }
    }
}

@Composable
fun AbschlussAnsicht(z: AppZustand) {
    @Suppress("UNUSED_VARIABLE") val tick = z.version
    val kurs = z.kurs
    if (kurs == null) {
        Seite("Abschluss-Quiz", { z.ansicht = Ansicht.Start }) { Hinweis("Kein Kurs geladen.") }
        return
    }
    val liste = remember { Lernschleife.abschluss(kurs, z.fortschritt, heute()) }
    var index by remember { mutableStateOf(0) }
    val ergebnisse = remember { mutableStateListOf<Pair<String, Double>>() }
    val seed = remember { System.nanoTime() }

    Seite("Abschluss-Quiz", { z.ansicht = Ansicht.Start }) {
        if (liste.isEmpty()) {
            Hinweis("Heute wurde noch nichts geübt – erst ein Lernziel bearbeiten.")
            return@Seite
        }
        if (index < liste.size) {
            Klein("Aufgabe ${index + 1} von ${liste.size} · im Prüfungsformat, ohne Lernkarte")
            val q = liste[index]
            FrageAnsicht(
                z = z, q = q, lz = null, abschluss = true, seed = seed + index,
                onErgebnis = { p -> ergebnisse.add(q.lz to p) },
                onWeiter = { index++ },
            )
        } else {
            val jeLz = ergebnisse.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.average() }
            LaunchedEffect(Unit) { z.abschlussAuswerten(jeLz) }
            val gesamt = if (ergebnisse.isEmpty()) 0.0 else ergebnisse.map { it.second }.average()
            Kasten {
                Ueberschrift("Ergebnis: ${Rechnen.zahl(gesamt, 0)} % → Note ${Noten.note(gesamt)}")
                for ((lzId, p) in jeLz) {
                    val titel = kurs.lernziel(lzId)?.titel ?: lzId
                    Text(
                        "${Rechnen.zahl(p, 0)} % · $titel" + if (p < Noten.RICHTIG) " → morgen zuerst" else "",
                        color = farbeFuer(p), style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Klein("Unter 2/3 ist das Lernziel wieder offen und kommt morgen zuerst.")
            Haupt("Fachbegriffe (10 Minuten)") { z.ansicht = Ansicht.Vokabeln }
            Neben("Zur Startseite") { z.ansicht = Ansicht.Start }
        }
    }
}
