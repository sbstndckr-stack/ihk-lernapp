package de.lernapp.ihk.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.random.Random

/** Eine Rechenaufgabe mit konkreten Zahlen (bei vary=true jedes Mal neue). */
data class RechenVariante(
    val frage: Frage,
    val prompt: String,
    val werte: List<Wert>,
    val antwort: Double,
    val fehlerWerte: List<Pair<Double, String>>,
    val loesung: String,
    val erklaerung: String,
)

data class Diagnose(val richtig: Boolean, val meldung: String)

object Rechnen {

    fun variante(q: Frage, seed: Long): RechenVariante {
        val zufall = Random(seed)
        val variabel = q.vary && q.werte.any { it.bereich != null } && q.formel.isNotBlank()
        if (variabel) {
            repeat(25) {
                val w = q.werte.map { x ->
                    val b = x.bereich
                    if (b == null) {
                        x
                    } else {
                        val lo = b[0]
                        val hi = b[1]
                        val st = if (b.size > 2 && b[2] > 0) b[2] else 1.0
                        val n = Math.floor((hi - lo) / st + 1e-9).toInt()
                        x.copy(wert = runde(lo + st * zufall.nextInt(n + 1), 6))
                    }
                }
                val erg = berechne(q, w)
                if (erg != null) return erg
            }
        }
        return berechne(q, q.werte, q.answer) ?: RechenVariante(
            frage = q,
            prompt = q.prompt,
            werte = q.werte,
            antwort = q.answer ?: 0.0,
            fehlerWerte = emptyList(),
            loesung = q.loesung,
            erklaerung = q.explanation,
        )
    }

    private fun berechne(q: Frage, w: List<Wert>, festeAntwort: Double? = null): RechenVariante? {
        val d = q.runden
        val zelle: (String) -> Double? = { ref ->
            val m = Regex("^V(\\d+)$").find(ref)
            if (m != null) w.getOrNull(m.groupValues[1].toInt() - 1)?.wert else null
        }
        val ev: (String) -> Double? = { f -> Formel.rechneOderNull(f, zelle) }
        val roh = festeAntwort ?: (if (q.formel.isNotBlank()) ev(q.formel) else null) ?: return null
        val ans = runde(roh, d)
        val e = q.ergebnis
        if (festeAntwort == null && e != null && (ans < e[0] || ans > e[1])) return null
        if (festeAntwort == null && q.pruefe.any { f -> val v = ev(f); v == null || v <= 0 }) return null

        fun fuelle(t: String): String {
            var s = Regex("\\{=([^}]+)\\}").replace(t) { mr ->
                val v = ev("=" + mr.groupValues[1])
                if (v == null) "?" else zahl(runde(v, d))
            }
            s = Regex("\\{V(\\d+)\\}").replace(s) { mr ->
                val x = w.getOrNull(mr.groupValues[1].toInt() - 1)
                if (x == null) "?" else zahl(x.wert)
            }
            return s.replace("{E}", zahl(ans))
        }

        val tol = toleranz(q, ans)
        val gesehen = arrayListOf(ans)
        val fehlerWerte = q.fehler.mapNotNull { f ->
            val v0 = ev(f.formel) ?: return@mapNotNull null
            val v = runde(v0, d)
            if (gesehen.any { Math.abs(v - it) <= Math.max(tol, Math.abs(it) * 0.0005) }) return@mapNotNull null
            gesehen.add(v)
            v to fuelle(f.msg)
        }
        return RechenVariante(
            frage = q,
            prompt = if (q.promptVorlage.isNotBlank()) fuelle(q.promptVorlage) else q.prompt,
            werte = w,
            antwort = ans,
            fehlerWerte = fehlerWerte,
            loesung = if (q.loesungVorlage.isNotBlank()) fuelle(q.loesungVorlage) else q.loesung,
            erklaerung = if (q.erklaerungVorlage.isNotBlank()) fuelle(q.erklaerungVorlage) else q.explanation,
        )
    }

    fun toleranz(q: Frage, ans: Double): Double = q.tol ?: Math.max(0.005, Math.abs(ans) * 0.0005)

    fun pruefe(v: RechenVariante, x: Double): Diagnose {
        val ans = v.antwort
        val tol = toleranz(v.frage, ans)
        fun nah(a: Double, b: Double, t: Double) = Math.abs(a - b) <= t
        if (nah(x, ans, tol)) return Diagnose(true, "Richtig.")
        for ((wert, msg) in v.fehlerWerte) {
            if (nah(x, wert, Math.max(tol, Math.abs(wert) * 0.0005))) return Diagnose(false, msg)
        }
        if (ans != 0.0 && nah(x, -ans, tol)) return Diagnose(false, "Der Betrag stimmt, aber das Vorzeichen nicht. Prüfe, was du wovon abziehst.")
        if (ans != 0.0 && (nah(x * 100, ans, tol) || nah(x / 100, ans, tol))) return Diagnose(false, "Um den Faktor 100 daneben: Prozent und Dezimalzahl verwechselt? 25 % = 0,25.")
        if (ans != 0.0 && (nah(x * 1000, ans, tol) || nah(x / 1000, ans, tol))) return Diagnose(false, "Um den Faktor 1000 daneben: Ist ein Tausenderpunkt verrutscht?")
        if (ans != 0.0 && Math.abs(x - ans) / Math.abs(ans) < 0.02) return Diagnose(false, "Ganz knapp daneben. Prüfe das Runden oder einen Zwischenwert.")
        return Diagnose(false, "Das Ergebnis stimmt nicht.")
    }

    /** Liest „1.234,56“, „8,5 VK“, „-3“, „12 %“ (als 12). */
    fun leseZahl(s: String): Double? {
        var t = s.trim().replace('−', '-').replace('–', '-').replace(" ", "").replace(" ", "")
        t = Regex("^[^0-9+\\-.,]*").replace(t, "")
        val m = Regex("^[+-]?[0-9.,]+").find(t) ?: return null
        var z = m.value
        val hatKomma = z.contains(',')
        val hatPunkt = z.contains('.')
        z = when {
            hatKomma && hatPunkt -> if (z.lastIndexOf(',') > z.lastIndexOf('.')) z.replace(".", "").replace(',', '.') else z.replace(",", "")
            hatKomma -> z.replace(',', '.')
            hatPunkt && Regex("^[+-]?\\d{1,3}(\\.\\d{3})+$").matches(z) -> z.replace(".", "")
            else -> z
        }
        return z.toDoubleOrNull()
    }

    fun runde(v: Double, d: Int): Double {
        if (v.isNaN() || v.isInfinite()) return v
        return BigDecimal.valueOf(v).setScale(d, RoundingMode.HALF_UP).toDouble()
    }

    /** Deutsche Schreibweise: Komma, Tausenderpunkte, ohne überflüssige Nullen. */
    fun zahl(v: Double, maxStellen: Int = 4): String {
        if (v.isNaN() || v.isInfinite()) return "?"
        val bd = BigDecimal.valueOf(v).setScale(maxStellen, RoundingMode.HALF_UP).stripTrailingZeros()
        val s = bd.toPlainString()
        val neg = s.startsWith("-")
        val ohne = if (neg) s.substring(1) else s
        val teile = ohne.split('.')
        val ganz = teile[0]
        val sb = StringBuilder()
        for ((i, c) in ganz.withIndex()) {
            if (i > 0 && (ganz.length - i) % 3 == 0 && ganz.length > 4) sb.append('.')
            sb.append(c)
        }
        val ergebnis = if (teile.size > 1) sb.toString() + "," + teile[1] else sb.toString()
        return if (neg && ergebnis != "0") "-$ergebnis" else ergebnis
    }
}

/** IHK-Notenschlüssel (Punkte von 100 → Note), wie im Lern-Monitor. */
object Noten {
    private val tabelle: List<Pair<Int, String>> = listOf(
        100 to "1,0", 98 to "1,1", 96 to "1,2", 94 to "1,3", 92 to "1,4", 91 to "1,5", 90 to "1,6", 89 to "1,7",
        88 to "1,8", 87 to "1,9", 85 to "2,0", 84 to "2,1", 83 to "2,2", 82 to "2,3", 81 to "2,4", 79 to "2,5",
        78 to "2,6", 77 to "2,7", 75 to "2,8", 74 to "2,9", 72 to "3,0", 71 to "3,1", 70 to "3,2", 68 to "3,3",
        67 to "3,4", 65 to "3,5", 63 to "3,6", 62 to "3,7", 60 to "3,8", 58 to "3,9", 56 to "4,0", 55 to "4,1",
        53 to "4,2", 51 to "4,3", 50 to "4,4", 48 to "4,5", 46 to "4,6", 44 to "4,7", 42 to "4,8", 40 to "4,9",
        38 to "5,0", 36 to "5,1", 34 to "5,2", 32 to "5,3", 30 to "5,4", 25 to "5,5", 20 to "5,6", 15 to "5,7",
        10 to "5,8", 5 to "5,9", 0 to "6,0",
    )

    fun note(prozent: Double): String {
        val p = Math.floor(prozent + 1e-9).toInt()
        return tabelle.firstOrNull { p >= it.first }?.second ?: "6,0"
    }

    fun stufe(prozent: Double): String = when {
        prozent >= 92 -> "sehr gut"
        prozent >= 81 -> "gut"
        prozent >= 67 -> "befriedigend"
        prozent >= 50 -> "ausreichend"
        prozent >= 30 -> "mangelhaft"
        else -> "ungenügend"
    }

    const val SEHR_GUT = 92.0
    const val RICHTIG = 67.0
    const val BESTANDEN = 50.0
}
