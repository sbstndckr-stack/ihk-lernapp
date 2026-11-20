package de.lernapp.ihk.core

import java.time.LocalDate
import kotlin.random.Random

/** Ein Antwortversuch. Für die Statistik zählt die erste Bewertung; Claude kann sie später korrigieren. */
class Versuch(
    val id: String,
    val frageId: String,
    val lz: String,
    val typ: String,
    val zeit: Long,
    val tag: String,
    var prozent: Double,
    var richtig: Boolean,
    var bewertetVon: String, // regel, lokal, claude, selbst
    val antwort: String,
    var punkte: List<Double>,
    var begruendung: String,
    var vorlaeufig: Boolean,
    val abschluss: Boolean,
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "id" to id, "frageId" to frageId, "lz" to lz, "typ" to typ, "zeit" to zeit, "tag" to tag,
        "prozent" to prozent, "richtig" to richtig, "bewertetVon" to bewertetVon, "antwort" to antwort,
        "punkte" to punkte, "begruendung" to begruendung, "vorlaeufig" to vorlaeufig, "abschluss" to abschluss,
    )

    companion object {
        fun aus(m: Map<String, Any?>) = Versuch(
            id = m.str("id"), frageId = m.str("frageId"), lz = m.str("lz"), typ = m.str("typ"),
            zeit = m.long("zeit") ?: 0L, tag = m.str("tag"), prozent = m.num("prozent") ?: 0.0,
            richtig = m.bool("richtig"), bewertetVon = m.str("bewertetVon"), antwort = m.str("antwort"),
            punkte = m.zahlen("punkte"), begruendung = m.str("begruendung"), vorlaeufig = m.bool("vorlaeufig"),
            abschluss = m.bool("abschluss"),
        )
    }
}

class KartenStand(var box: Int, var faellig: String)

data class LzStand(
    val versuche: Int,
    val serie: Int,
    val pruefungBestanden: Int,
    val letztePruefung: Double?,
    val sicher: Boolean,
    val sehrGut: Boolean,
    val zuletzt: Long,
) {
    val begonnen: Boolean get() = versuche > 0
    val text: String
        get() = when {
            sehrGut -> "sitzt (sehr gut)"
            sicher -> "sicher"
            begonnen -> "in Arbeit"
            else -> "offen"
        }
}

class Fortschritt {
    val versuche = ArrayList<Versuch>()
    val karteGesehen = LinkedHashSet<String>()
    val leitner = LinkedHashMap<String, KartenStand>()
    val morgenZuerst = LinkedHashSet<String>()
    val energie = LinkedHashMap<String, Int>()
    var neueKartenTag: String = ""
    var neueKartenAnzahl: Int = 0

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "versuche" to versuche.map { it.toMap() },
        "karteGesehen" to karteGesehen.toList(),
        "leitner" to leitner.mapValues { (_, k) -> mapOf("box" to k.box, "faellig" to k.faellig) },
        "morgenZuerst" to morgenZuerst.toList(),
        "energie" to energie,
        "neueKartenTag" to neueKartenTag,
        "neueKartenAnzahl" to neueKartenAnzahl,
    )

    fun versucheZu(lz: String): List<Versuch> = versuche.filter { it.lz == lz }

    /** Stand eines Lernziels, immer aus dem Verlauf berechnet – so wirken spätere Korrekturen automatisch. */
    fun stand(lz: String, hatPruefungsfragen: Boolean): LzStand {
        val vs = versucheZu(lz).sortedBy { it.zeit }
        var serie = 0
        for (v in vs.asReversed()) {
            if (v.richtig) serie++ else break
        }
        val inSerie = vs.takeLast(serie)
        val verschieden = inSerie.map { it.frageId }.toSet().size >= 2 || inSerie.any { it.typ == "rechnen" }
        val pruef = vs.filter { it.typ == "ihk" || it.typ == "rechnen" }
        val bestanden = pruef.count { it.prozent >= Noten.RICHTIG }
        val letzte = pruef.lastOrNull()?.prozent
        val sicher = serie >= 3 && verschieden && (!hatPruefungsfragen || bestanden >= 1)
        val sehrGut = sicher && (!hatPruefungsfragen || (letzte != null && letzte >= Noten.SEHR_GUT))
        return LzStand(vs.size, serie, bestanden, letzte, sicher, sehrGut, vs.lastOrNull()?.zeit ?: 0L)
    }

    companion object {
        fun aus(m: Map<String, Any?>): Fortschritt {
            val f = Fortschritt()
            m.liste("versuche").forEach { v -> asObj(v)?.let { f.versuche.add(Versuch.aus(it)) } }
            f.karteGesehen.addAll(m.texte("karteGesehen"))
            m.obj("leitner")?.forEach { (k, v) ->
                asObj(v)?.let { f.leitner[k] = KartenStand(it.int("box") ?: 1, it.str("faellig")) }
            }
            f.morgenZuerst.addAll(m.texte("morgenZuerst"))
            m.obj("energie")?.forEach { (k, v) -> (v as? Number)?.let { f.energie[k] = it.toInt() } }
            f.neueKartenTag = m.str("neueKartenTag")
            f.neueKartenAnzahl = m.int("neueKartenAnzahl") ?: 0
            return f
        }
    }
}

fun heute(): String = LocalDate.now().toString()

fun plusTage(tag: String, n: Int): String = LocalDate.parse(tag).plusDays(n.toLong()).toString()

/** Auswahl der nächsten Aufgabe – Regel-Code, ohne KI. */
object Lernschleife {

    fun naechsteFrage(kurs: Kurs, f: Fortschritt, lz: String, letzteFrageId: String?, zufall: Random = Random.Default): Frage? {
        val qs = kurs.fragenZu(lz)
        if (qs.isEmpty()) return null
        val vs = f.versucheZu(lz)
        val mc = qs.filter { it.istAnkreuzen }
        val ex = qs.filter { it.istPruefung }
        val mcVersuche = vs.count { it.typ == "single" || it.typ == "multi" }
        val exVersuche = vs.count { it.typ == "ihk" || it.typ == "rechnen" }
        // Einstieg mit 1–2 Ankreuzfragen, dann Prüfungsformat. Ohne Prüfungsfragen bleibt nur Ankreuzen.
        val pool = if (ex.isEmpty() || (mc.isNotEmpty() && exVersuche == 0 && mcVersuche < 2)) mc else ex
        val kandidaten = pool.filter { it.id != letzteFrageId }.ifEmpty { pool }
        val zuletzt = HashMap<String, Long>()
        vs.forEach { v -> if ((zuletzt[v.frageId] ?: 0L) < v.zeit) zuletzt[v.frageId] = v.zeit }
        // Neues zuerst, dann das am längsten nicht Gesehene; bei Gleichstand zufällig.
        return kandidaten.shuffled(zufall).minByOrNull { zuletzt[it.id] ?: 0L }
    }

    fun naechstesLernziel(kurs: Kurs, f: Fortschritt): Lernziel? {
        val mitInhalt = kurs.lernzieleSortiert.filter { kurs.fragenZu(it.id).isNotEmpty() || it.karte != null }
        fun stand(l: Lernziel) = f.stand(l.id, kurs.fragenZu(l.id).any { it.istPruefung })
        f.morgenZuerst.mapNotNull { kurs.lernziel(it) }.firstOrNull { !stand(it).sicher }?.let { return it }
        mitInhalt.firstOrNull { val s = stand(it); s.begonnen && !s.sicher }?.let { return it }
        return mitInhalt.firstOrNull { !stand(it).begonnen }
    }

    /** Abschluss-Quiz: je heute geübtem Lernziel eine Prüfungsaufgabe, höchstens 2 Ankreuzfragen, 5–8 Aufgaben. */
    fun abschluss(kurs: Kurs, f: Fortschritt, tag: String, zufall: Random = Random.Default): List<Frage> {
        val heuteLz = f.versuche.filter { it.tag == tag && !it.abschluss }.map { it.lz }.distinct()
        val lzs = if (heuteLz.isNotEmpty()) heuteLz else f.versuche.sortedByDescending { it.zeit }.map { it.lz }.distinct().take(5)
        val heuteBeantwortet = f.versuche.filter { it.tag == tag }.map { it.frageId }.toSet()
        val liste = ArrayList<Frage>()
        var mc = 0
        for (lz in lzs) {
            if (liste.size >= 8) break
            val qs = kurs.fragenZu(lz)
            val ex = qs.filter { it.istPruefung }.shuffled(zufall).sortedBy { if (it.id in heuteBeantwortet) 1 else 0 }
            val wahl = ex.firstOrNull() ?: if (mc < 2) qs.filter { it.istAnkreuzen }.shuffled(zufall).firstOrNull()?.also { mc++ } else null
            if (wahl != null) liste.add(wahl)
        }
        return liste
    }
}

/** Leitner-Kasten für Fachbegriffe: was sitzt, kommt seltener; was nicht sitzt, morgen wieder. */
object Leitner {
    private val ABSTAND = intArrayOf(0, 1, 3, 7, 14, 30) // Tage je Fach 1–5
    const val NEU_PRO_TAG = 10

    fun faellige(kurs: Kurs, f: Fortschritt, tag: String): List<Vokabel> {
        val alt = kurs.vokabeln.filter { v -> f.leitner[v.id]?.let { it.faellig <= tag } ?: false }
            .sortedBy { f.leitner[it.id]?.box ?: 1 }
        val schonNeu = if (f.neueKartenTag == tag) f.neueKartenAnzahl else 0
        val neu = kurs.vokabeln.filter { it.id !in f.leitner }.take(Math.max(0, NEU_PRO_TAG - schonNeu))
        return alt + neu
    }

    fun anzahlFaellig(kurs: Kurs, f: Fortschritt, tag: String): Int = faellige(kurs, f, tag).size

    fun antwort(f: Fortschritt, id: String, gewusst: Boolean, tag: String) {
        val st = f.leitner[id]
        if (st == null) {
            if (f.neueKartenTag != tag) {
                f.neueKartenTag = tag
                f.neueKartenAnzahl = 0
            }
            f.neueKartenAnzahl++
        }
        val box = if (gewusst) Math.min(5, (st?.box ?: 0) + 1) else 1
        f.leitner[id] = KartenStand(box, plusTage(tag, if (gewusst) ABSTAND[box] else 1))
    }
}

data class Gesamtstand(val lernziele: Int, val begonnen: Int, val sicher: Int, val sehrGut: Int, val schnittPruefung: Double?)

fun gesamtstand(kurs: Kurs, f: Fortschritt): Gesamtstand {
    val lzs = kurs.lernziele.filter { kurs.fragenZu(it.id).isNotEmpty() }
    val st = lzs.map { f.stand(it.id, kurs.fragenZu(it.id).any { q -> q.istPruefung }) }
    val pr = st.mapNotNull { it.letztePruefung }
    return Gesamtstand(
        lernziele = lzs.size,
        begonnen = st.count { it.begonnen },
        sicher = st.count { it.sicher },
        sehrGut = st.count { it.sehrGut },
        schnittPruefung = if (pr.isEmpty()) null else pr.average(),
    )
}
