package de.lernapp.ihk.core

import java.security.MessageDigest
import java.time.ZoneId
import java.time.Instant

/** Ergebnis einer Bewertung (lokal, Claude oder selbst). */
data class Bewertung(
    val punkte: List<Double>,
    val begruendung: String,
    val naechster: String,
    val unsicher: Boolean,
    val ausserhalb: Boolean,
    val quelle: String, // lokal, claude, selbst
    val auchRichtig: List<Pair<Int, String>> = emptyList(),
    val lesbar: Boolean = true,
) {
    fun summe(): Double = punkte.sum()
    fun prozent(max: Double): Double = if (max <= 0) 0.0 else 100.0 * summe() / max

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "punkte" to punkte, "begruendung" to begruendung, "naechster" to naechster,
        "unsicher" to unsicher, "ausserhalb" to ausserhalb, "quelle" to quelle,
        "auchRichtig" to auchRichtig.map { mapOf("nr" to it.first, "text" to it.second) },
        "lesbar" to lesbar,
    )

    companion object {
        fun aus(m: Map<String, Any?>) = Bewertung(
            punkte = m.zahlen("punkte"), begruendung = m.str("begruendung"), naechster = m.str("naechster"),
            unsicher = m.bool("unsicher"), ausserhalb = m.bool("ausserhalb"), quelle = m.str("quelle"),
            auchRichtig = m.liste("auchRichtig").mapNotNull { a -> asObj(a)?.let { (it.int("nr") ?: 0) to it.str("text") } },
            lesbar = if (m.containsKey("lesbar")) m.bool("lesbar") else true,
        )

        /**
         * Liest die JSON-Antwort einer KI und hält die Punkte in den Grenzen des Erwartungshorizonts
         * (0 … Höchstpunkte, 0,5er-Schritte). Unlesbar → unsicher.
         */
        fun ausKiText(text: String, erwartung: List<Erwartung>, quelle: String): Bewertung {
            val o = Json.findeObjekt(text)
            val roh = o?.liste("punkte")?.map { p ->
                when (p) {
                    is Number -> p.toDouble()
                    is String -> p.replace(',', '.').toDoubleOrNull()
                    else -> asObj(p)?.num("punkte")
                }
            }
            if (o == null || roh == null || roh.size != erwartung.size || roh.any { it == null }) {
                return Bewertung(
                    punkte = erwartung.map { 0.0 }, begruendung = "Die KI-Antwort war nicht lesbar.", naechster = "",
                    unsicher = true, ausserhalb = false, quelle = quelle, lesbar = false,
                )
            }
            val punkte = roh.mapIndexed { i, p ->
                val max = erwartung[i].punkte
                val halb = Math.round(p!! * 2.0) / 2.0
                halb.coerceIn(0.0, max)
            }
            val auch = o.liste("auchRichtig").mapNotNull { a ->
                asObj(a)?.let { x ->
                    val nr = x.int("nr") ?: return@let null
                    val t = x.str("text").trim()
                    if (nr in 1..erwartung.size && t.isNotEmpty()) nr to t else null
                }
            }
            return Bewertung(
                punkte = punkte,
                begruendung = o.str("begruendung").trim(),
                naechster = o.str("naechster").trim(),
                unsicher = o.bool("unsicher"),
                ausserhalb = o.bool("ausserhalb"),
                quelle = quelle,
                auchRichtig = auch,
            )
        }
    }
}

class Lehrbeispiel(
    val id: String,
    val frageId: String,
    val lz: String,
    val antwort: String,
    val punkte: List<Double>,
    val begruendung: String,
    val datum: Long,
    var aktiv: Boolean,
    var genutzt: Int,
    var treffer: Int,
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "id" to id, "frageId" to frageId, "lz" to lz, "antwort" to antwort, "punkte" to punkte,
        "begruendung" to begruendung, "datum" to datum, "aktiv" to aktiv, "genutzt" to genutzt, "treffer" to treffer,
    )

    companion object {
        fun aus(m: Map<String, Any?>) = Lehrbeispiel(
            m.str("id"), m.str("frageId"), m.str("lz"), m.str("antwort"), m.zahlen("punkte"), m.str("begruendung"),
            m.long("datum") ?: 0L, m.bool("aktiv"), m.int("genutzt") ?: 0, m.int("treffer") ?: 0,
        )
    }
}

/** Vergleich lokale KI ↔ Claude bei derselben Antwort (Check im PDCA). */
class Vergleich(
    val datum: Long,
    val frageId: String,
    val modell: String,
    val lokal: List<Double>,
    val claude: List<Double>,
    val beispiele: List<String>,
) {
    val summeLokal: Double get() = lokal.sum()
    val summeClaude: Double get() = claude.sum()
    val milder: Boolean get() = summeLokal > summeClaude + 0.25
    val treffer: Boolean get() = Math.abs(summeLokal - summeClaude) <= 0.5 && !milder
    val abweichungJeErwartung: Double
        get() = if (lokal.isEmpty() || lokal.size != claude.size) 99.0 else lokal.indices.map { Math.abs(lokal[it] - claude[it]) }.average()

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "datum" to datum, "frageId" to frageId, "modell" to modell, "lokal" to lokal, "claude" to claude, "beispiele" to beispiele,
    )

    companion object {
        fun aus(m: Map<String, Any?>) = Vergleich(
            m.long("datum") ?: 0L, m.str("frageId"), m.str("modell"), m.zahlen("lokal"), m.zahlen("claude"), m.texte("beispiele"),
        )
    }
}

/** Jede automatische Änderung ist sichtbar und einzeln rückgängig zu machen. */
class Aenderung(
    val id: String,
    val datum: Long,
    val art: String, // stichprobe, auchRichtig, lehrbeispielNeu, lehrbeispielAus
    val text: String,
    val daten: Map<String, Any?>,
    var zurueck: Boolean,
) {
    fun toMap(): Map<String, Any?> = linkedMapOf("id" to id, "datum" to datum, "art" to art, "text" to text, "daten" to daten, "zurueck" to zurueck)

    companion object {
        fun aus(m: Map<String, Any?>) = Aenderung(m.str("id"), m.long("datum") ?: 0L, m.str("art"), m.str("text"), m.obj("daten") ?: emptyMap(), m.bool("zurueck"))
    }
}

class AuchRichtig(val id: String, val frageId: String, val nr: Int, val text: String, var aktiv: Boolean) {
    fun toMap(): Map<String, Any?> = linkedMapOf("id" to id, "frageId" to frageId, "nr" to nr, "text" to text, "aktiv" to aktiv)

    companion object {
        fun aus(m: Map<String, Any?>) = AuchRichtig(m.str("id"), m.str("frageId"), m.int("nr") ?: 1, m.str("text"), m.bool("aktiv"))
    }
}

/** Stichprobe oder Selbstbewertung, die gesammelt per Batch von Claude nachgeprüft wird. */
class Stichprobe(
    val id: String,
    val versuchId: String,
    val frageId: String,
    val antwort: String,
    val lokal: List<Double>,
    val modell: String,
    val beispiele: List<String>,
    val grund: String,
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "id" to id, "versuchId" to versuchId, "frageId" to frageId, "antwort" to antwort,
        "lokal" to lokal, "modell" to modell, "beispiele" to beispiele, "grund" to grund,
    )

    companion object {
        fun aus(m: Map<String, Any?>) = Stichprobe(
            m.str("id"), m.str("versuchId"), m.str("frageId"), m.str("antwort"), m.zahlen("lokal"),
            m.str("modell"), m.texte("beispiele"), m.str("grund"),
        )
    }
}

class Verbrauch(
    val datum: Long,
    val modell: String,
    val zweck: String,
    val eingabe: Int,
    val ausgabe: Int,
    val cacheLesen: Int,
    val cacheSchreiben: Int,
    val usd: Double,
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "datum" to datum, "modell" to modell, "zweck" to zweck, "eingabe" to eingabe, "ausgabe" to ausgabe,
        "cacheLesen" to cacheLesen, "cacheSchreiben" to cacheSchreiben, "usd" to usd,
    )

    companion object {
        fun aus(m: Map<String, Any?>) = Verbrauch(
            m.long("datum") ?: 0L, m.str("modell"), m.str("zweck"), m.int("eingabe") ?: 0, m.int("ausgabe") ?: 0,
            m.int("cacheLesen") ?: 0, m.int("cacheSchreiben") ?: 0, m.num("usd") ?: 0.0,
        )
    }
}

/** Einstellungen für lokale KI und Claude-API. Werte sind in der App änderbar. */
class Einstellungen {
    var apiSchluessel: String = ""
    var onlineErlaubt: Boolean = true
    var modellStreng: String = "claude-sonnet-5-5"
    var modellEinfach: String = "claude-haiku-5-5"
    var preisStrengEin: Double = 2.0 // $ je Million Token
    var preisStrengAus: Double = 10.0
    var preisEinfachEin: Double = 0.10
    var preisEinfachAus: Double = 0.50
    var budgetEur: Double = 5.0
    var eurJeUsd: Double = 0.90
    var lokalesModell: String = ""
    var gpu: Boolean = true

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "apiSchluessel" to apiSchluessel, "onlineErlaubt" to onlineErlaubt, "modellStreng" to modellStreng,
        "modellEinfach" to modellEinfach, "preisStrengEin" to preisStrengEin, "preisStrengAus" to preisStrengAus,
        "preisEinfachEin" to preisEinfachEin, "preisEinfachAus" to preisEinfachAus, "budgetEur" to budgetEur,
        "eurJeUsd" to eurJeUsd, "lokalesModell" to lokalesModell, "gpu" to gpu,
    )

    fun preise(modell: String): Pair<Double, Double> =
        if (modell == modellEinfach) preisEinfachEin to preisEinfachAus else preisStrengEin to preisStrengAus

    companion object {
        fun aus(m: Map<String, Any?>): Einstellungen {
            val e = Einstellungen()
            e.apiSchluessel = m.str("apiSchluessel")
            if (m.containsKey("onlineErlaubt")) e.onlineErlaubt = m.bool("onlineErlaubt")
            m.str("modellStreng").takeIf { it.isNotBlank() }?.let { e.modellStreng = it }
            m.str("modellEinfach").takeIf { it.isNotBlank() }?.let { e.modellEinfach = it }
            m.num("preisStrengEin")?.let { e.preisStrengEin = it }
            m.num("preisStrengAus")?.let { e.preisStrengAus = it }
            m.num("preisEinfachEin")?.let { e.preisEinfachEin = it }
            m.num("preisEinfachAus")?.let { e.preisEinfachAus = it }
            m.num("budgetEur")?.let { e.budgetEur = it }
            m.num("eurJeUsd")?.let { e.eurJeUsd = it }
            e.lokalesModell = m.str("lokalesModell")
            if (m.containsKey("gpu")) e.gpu = m.bool("gpu")
            return e
        }
    }
}

/** Qualitätskreis: Lehrbeispiele, Vergleiche, Stichprobenquote, Änderungsliste, Cache, Verbrauch. */
class Qualitaet {
    val lehrbeispiele = ArrayList<Lehrbeispiel>()
    val vergleiche = ArrayList<Vergleich>()
    val aenderungen = ArrayList<Aenderung>()
    val auchRichtig = ArrayList<AuchRichtig>()
    val warteschlange = ArrayList<Stichprobe>()
    val verbrauch = ArrayList<Verbrauch>()
    val cache = LinkedHashMap<String, Map<String, Any?>>()
    var stufe: Int = 1 // Index in STUFEN → 10 %
    var vergleicheSeitAenderung: Int = 0
    var offenerBatch: String = ""
    var offenerBatchSeit: Long = 0L
    val batchInhalt = ArrayList<String>() // Stichproben-IDs im offenen Batch
    var letzterCheck: Long = 0L
    val modelltests = LinkedHashMap<String, Map<String, Any?>>()

    val stichprobenQuote: Double get() = STUFEN[stufe.coerceIn(0, STUFEN.size - 1)]

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "lehrbeispiele" to lehrbeispiele.map { it.toMap() },
        "vergleiche" to vergleiche.map { it.toMap() },
        "aenderungen" to aenderungen.map { it.toMap() },
        "auchRichtig" to auchRichtig.map { it.toMap() },
        "warteschlange" to warteschlange.map { it.toMap() },
        "verbrauch" to verbrauch.map { it.toMap() },
        "cache" to cache,
        "stufe" to stufe,
        "vergleicheSeitAenderung" to vergleicheSeitAenderung,
        "offenerBatch" to offenerBatch,
        "offenerBatchSeit" to offenerBatchSeit,
        "batchInhalt" to batchInhalt,
        "letzterCheck" to letzterCheck,
        "modelltests" to modelltests,
    )

    fun auchRichtigZu(frageId: String): List<AuchRichtig> = auchRichtig.filter { it.frageId == frageId && it.aktiv }

    /** Lehrbeispiele für den Prompt der lokalen KI: erst dieselbe Frage, sonst dasselbe Lernziel. Höchstens 2. */
    fun beispieleFuer(frageId: String, lz: String, antwort: String): List<Lehrbeispiel> {
        val aktiv = lehrbeispiele.filter { it.aktiv }
        val gleich = aktiv.filter { it.frageId == frageId }.sortedByDescending { aehnlichkeit(it.antwort, antwort) }
        if (gleich.isNotEmpty()) return gleich.take(2)
        return aktiv.filter { it.lz == lz }.sortedByDescending { it.datum }.take(1)
    }

    /** Monatsverbrauch in Euro. */
    fun monatEur(e: Einstellungen, jetzt: Long = System.currentTimeMillis()): Double {
        val monat = monatVon(jetzt)
        return verbrauch.filter { monatVon(it.datum) == monat }.sumOf { it.usd } * e.eurJeUsd
    }

    fun budgetErlaubt(e: Einstellungen): Boolean = monatEur(e) < e.budgetEur

    fun budgetWarnung(e: Einstellungen): Boolean = monatEur(e) >= 0.8 * e.budgetEur

    /** Kosten eines Aufrufs in US-Dollar. Batch kostet die Hälfte. */
    fun kosten(e: Einstellungen, modell: String, ein: Int, aus: Int, cacheLesen: Int, cacheSchreiben: Int, batch: Boolean): Double {
        val (pe, pa) = e.preise(modell)
        val usd = (ein * pe + aus * pa + cacheLesen * pe * 0.1 + cacheSchreiben * pe * 1.25) / 1_000_000.0
        return if (batch) usd / 2 else usd
    }

    /**
     * Act im PDCA: Nach jedem neuen Vergleich prüfen. Trifft die lokale KI (≥ 90 % Treffer, nie milder als Claude),
     * sinkt die Stichprobe (10 % → 5 % → 2 %); wird sie schlechter oder milder, steigt sie wieder.
     */
    fun anpassen(jetzt: Long): Aenderung? {
        if (vergleiche.isEmpty()) return null
        val letzte = vergleiche.takeLast(10)
        val n = letzte.size
        val quote = letzte.count { it.treffer }.toDouble() / n
        val mild = vergleiche.takeLast(Math.max(1, vergleicheSeitAenderung)).any { it.milder }
        val alt = stufe
        val neu = when {
            mild -> Math.max(0, stufe - 1)
            n >= 5 && vergleicheSeitAenderung >= 5 && quote < 0.8 -> Math.max(0, stufe - 1)
            n >= 10 && vergleicheSeitAenderung >= 10 && quote >= 0.9 -> Math.min(STUFEN.size - 1, stufe + 1)
            else -> stufe
        }
        if (neu == alt) return null
        stufe = neu
        vergleicheSeitAenderung = 0
        val grund = if (neu < alt) {
            if (mild) "lokale KI war milder als Claude" else "Trefferquote ${(quote * 100).toInt()} % unter 80 %"
        } else {
            "Trefferquote ${(quote * 100).toInt()} % bei den letzten $n Stichproben"
        }
        val a = Aenderung(
            id = neueId("a"), datum = jetzt, art = "stichprobe",
            text = "Stichprobe ${prozent(STUFEN[alt])} → ${prozent(STUFEN[neu])} ($grund)",
            daten = mapOf("alt" to alt, "neu" to neu), zurueck = false,
        )
        aenderungen.add(a)
        return a
    }

    /** Wirkung der Lehrbeispiele messen; schädliche (≥ 3 Einsätze, < 50 % Treffer) automatisch abschalten. */
    fun beispieleAuswerten(v: Vergleich, jetzt: Long): List<Aenderung> {
        val neu = ArrayList<Aenderung>()
        for (id in v.beispiele) {
            val b = lehrbeispiele.firstOrNull { it.id == id } ?: continue
            b.genutzt++
            if (v.treffer) b.treffer++
            if (b.aktiv && b.genutzt >= 3 && b.treffer.toDouble() / b.genutzt < 0.5) {
                b.aktiv = false
                val a = Aenderung(
                    neueId("a"), jetzt, "lehrbeispielAus",
                    "Lehrbeispiel abgeschaltet: half der lokalen KI nicht (${b.treffer} von ${b.genutzt} Treffern)",
                    mapOf("beispiel" to b.id), false,
                )
                aenderungen.add(a)
                neu.add(a)
            }
        }
        return neu
    }

    /** Macht eine Änderung rückgängig. */
    fun rueckgaengig(a: Aenderung) {
        if (a.zurueck) return
        when (a.art) {
            "stichprobe" -> {
                stufe = (a.daten.int("alt") ?: stufe).coerceIn(0, STUFEN.size - 1)
                vergleicheSeitAenderung = 0
            }
            "auchRichtig" -> auchRichtig.firstOrNull { it.id == a.daten.str("auch") }?.aktiv = false
            "lehrbeispielNeu" -> lehrbeispiele.firstOrNull { it.id == a.daten.str("beispiel") }?.aktiv = false
            "lehrbeispielAus" -> lehrbeispiele.firstOrNull { it.id == a.daten.str("beispiel") }?.aktiv = true
        }
        a.zurueck = true
    }

    companion object {
        val STUFEN = doubleArrayOf(0.20, 0.10, 0.05, 0.02)

        fun prozent(x: Double): String = if (x >= 0.1 || x * 100 == Math.floor(x * 100)) "${(x * 100).toInt()} %" else "${x * 100} %"

        fun aus(m: Map<String, Any?>): Qualitaet {
            val q = Qualitaet()
            m.liste("lehrbeispiele").forEach { x -> asObj(x)?.let { q.lehrbeispiele.add(Lehrbeispiel.aus(it)) } }
            m.liste("vergleiche").forEach { x -> asObj(x)?.let { q.vergleiche.add(Vergleich.aus(it)) } }
            m.liste("aenderungen").forEach { x -> asObj(x)?.let { q.aenderungen.add(Aenderung.aus(it)) } }
            m.liste("auchRichtig").forEach { x -> asObj(x)?.let { q.auchRichtig.add(AuchRichtig.aus(it)) } }
            m.liste("warteschlange").forEach { x -> asObj(x)?.let { q.warteschlange.add(Stichprobe.aus(it)) } }
            m.liste("verbrauch").forEach { x -> asObj(x)?.let { q.verbrauch.add(Verbrauch.aus(it)) } }
            m.obj("cache")?.forEach { (k, v) -> asObj(v)?.let { q.cache[k] = it } }
            q.stufe = (m.int("stufe") ?: 1).coerceIn(0, STUFEN.size - 1)
            q.vergleicheSeitAenderung = m.int("vergleicheSeitAenderung") ?: 0
            q.offenerBatch = m.str("offenerBatch")
            q.offenerBatchSeit = m.long("offenerBatchSeit") ?: 0L
            q.batchInhalt.addAll(m.texte("batchInhalt"))
            q.letzterCheck = m.long("letzterCheck") ?: 0L
            m.obj("modelltests")?.forEach { (k, v) -> asObj(v)?.let { q.modelltests[k] = it } }
            return q
        }
    }
}

/** Eskalationsregeln: Wann fragt die App Claude statt nur der lokalen KI? */
object Eskalation {
    val GRENZEN = listOf(Noten.BESTANDEN, Noten.RICHTIG, Noten.SEHR_GUT)
    const val ABSTAND = 4.0

    /** Sofort an Claude (eilig) – oder null, wenn die lokale Bewertung bleiben darf. */
    fun grund(b: Bewertung, max: Double): String? {
        if (!b.lesbar) return "Antwort der lokalen KI nicht lesbar"
        if (b.unsicher) return "lokale KI unsicher"
        if (b.ausserhalb) return "Antwort enthält Inhalte außerhalb der Musterlösung"
        val p = b.prozent(max)
        val g = GRENZEN.firstOrNull { Math.abs(p - it) <= ABSTAND }
        if (g != null) return "knapp an der Grenze ${g.toInt()} %"
        return null
    }

    fun stichprobe(quote: Double, zufall: Double): Boolean = zufall < quote
}

fun neueId(praefix: String): String = praefix + "-" + System.currentTimeMillis().toString(36) + "-" + (Math.random() * 1e6).toLong().toString(36)

fun hash(vararg teile: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    teile.forEach { md.update(it.toByteArray(Charsets.UTF_8)); md.update(0) }
    return md.digest().take(12).joinToString("") { "%02x".format(it) }
}

/** Antwort vergleichbar machen: Kleinbuchstaben, Leerraum zusammenfassen. */
fun normalisiere(t: String): String = t.lowercase().replace(Regex("\\s+"), " ").trim()

/** Grobe Ähnlichkeit zweier Antworten (Anteil gemeinsamer Wörter). */
fun aehnlichkeit(a: String, b: String): Double {
    val wa = normalisiere(a).split(' ').filter { it.length > 3 }.toSet()
    val wb = normalisiere(b).split(' ').filter { it.length > 3 }.toSet()
    if (wa.isEmpty() || wb.isEmpty()) return 0.0
    return wa.intersect(wb).size.toDouble() / wa.union(wb).size
}

fun monatVon(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString().substring(0, 7)

fun tagVon(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()
