package de.lernapp.ihk.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Prüft den Regel-Kern (läuft auf GitHub vor jedem APK-Bau). */
class KernTest {

    private val kurs: Kurs by lazy { KursLeser.lies(File("src/main/assets/probe-paket.json").readText()) }

    @Test
    fun jsonUndKiAntwort() {
        val text = "Bewertung:\n```json\n{\"punkte\": [2, 1.5, 0.5,], \"begruendung\": \"ok\"}\n```"
        assertEquals(listOf(2.0, 1.5, 0.5), Json.findeObjekt(text)!!.zahlen("punkte"))
    }

    @Test
    fun formelUndZahlen() {
        val z: (String) -> Double? = { mapOf("V1" to 40.0, "V2" to 4.5, "V3" to 38.0, "V4" to 2.5, "V5" to 1.0)[it] }
        assertEquals(8.0, Formel.rechne("=V1+V2-V3+V4-V5", z), 1e-9)
        assertEquals(30.0, Formel.rechne("=200*15%", z), 1e-9)
        assertEquals(1234.56, Rechnen.leseZahl("1.234,56")!!, 1e-9)
        assertEquals("12.500", Rechnen.zahl(12500.0))
        assertEquals("1,4", Noten.note(92.0))
    }

    @Test
    fun rechenaufgabeMitWechselndenZahlen() {
        val q = kurs.frage("probe-pers-w02-2-rechnen-vary")!!
        val ergebnisse = HashSet<Double>()
        for (s in 1L..20L) {
            val v = Rechnen.variante(q, s)
            val w = v.werte.map { it.wert }
            assertEquals(Rechnen.runde(w[0] + w[1] - w[2] + w[3] - w[4], 2), v.antwort, 1e-9)
            assertTrue(Rechnen.pruefe(v, v.antwort).richtig)
            ergebnisse.add(v.antwort)
        }
        assertTrue(ergebnisse.size > 5)
    }

    @Test
    fun lernschleifeUndStand() {
        val f = Fortschritt()
        val lz = "gen-pers-w02-1-mw8y2bxi"
        assertTrue(Lernschleife.naechsteFrage(kurs, f, lz, null)!!.istAnkreuzen)
        var t = 1L
        fun dazu(id: String, p: Double) {
            val q = kurs.frage(id)!!
            f.versuche.add(Versuch("v$t", q.id, q.lz, q.typ, t++, heute(), p, p >= 67, "regel", "", emptyList(), "", false, false))
        }
        dazu("gen-gen-pers-w02-1-mw8y2bxi-mw8yb30u", 100.0)
        dazu("gen-gen-pers-w02-1-mw8y2bxi-mw8yc1wq", 100.0)
        assertTrue(Lernschleife.naechsteFrage(kurs, f, lz, null)!!.istPruefung)
        dazu("probe-pers-w02-1-ihk-1", 75.0)
        assertTrue(f.stand(lz, true).sicher)
        assertFalse(f.stand(lz, true).sehrGut)
        dazu("probe-pers-w02-1-ihk-1", 40.0)
        assertFalse(f.stand(lz, true).sicher)
    }

    @Test
    fun eskalationsregeln() {
        val q = kurs.frage("gen-gen-pers-w02-2-mw8y2bxi-mw8zkko7")!!
        val knapp = Bewertung.ausKiText("{\"punkte\":[2,1.5,1]}", q.erwartung, "lokal") // 90 %
        assertNotNull(Eskalation.grund(knapp, q.maxPunkte))
        val klar = Bewertung.ausKiText("{\"punkte\":[2,2,1]}", q.erwartung, "lokal")
        assertNull(Eskalation.grund(klar, q.maxPunkte))
        val unlesbar = Bewertung.ausKiText("weiß nicht", q.erwartung, "lokal")
        assertNotNull(Eskalation.grund(unlesbar, q.maxPunkte))
    }

    @Test
    fun pdcaStichprobe() {
        val qu = Qualitaet()
        repeat(10) {
            qu.vergleiche.add(Vergleich(it.toLong(), "f", "m", listOf(2.0, 1.0), listOf(2.0, 1.0), emptyList()))
            qu.vergleicheSeitAenderung++
            qu.anpassen(it.toLong())
        }
        assertEquals(0.05, qu.stichprobenQuote, 1e-9)
        qu.vergleiche.add(Vergleich(20, "f", "m", listOf(2.0, 2.0), listOf(2.0, 1.0), emptyList()))
        qu.vergleicheSeitAenderung++
        val a = qu.anpassen(20)!!
        assertEquals(0.10, qu.stichprobenQuote, 1e-9)
        qu.rueckgaengig(a)
        assertEquals(0.05, qu.stichprobenQuote, 1e-9)
    }
}
