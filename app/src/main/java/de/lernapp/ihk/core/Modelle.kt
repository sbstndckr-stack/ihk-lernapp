package de.lernapp.ihk.core

/*
 * Kurs-Paket (Format „lernapp-kurs/1“) – dieselben Felder wie im Lern-Monitor:
 * { format, kurs:{id,titel}, lernziele:[…], fragen:[…], vokabeln:[…], buch? }
 * Unbekannte Felder werden ignoriert, damit ältere und neuere Pakete lesbar bleiben.
 */

data class Begriff(val b: String, val d: String, val x: String)

data class Lernkarte(
    val kurz: String,
    val begriffe: List<Begriff>,
    val geruest: List<String>,
    val falle: String,
    val merksatz: String,
)

data class Lernziel(
    val id: String,
    val titel: String,
    val thema: String,
    val tag: String,
    val kann: String,
    val order: Double,
    val karte: Lernkarte?,
)

data class Erwartung(val text: String, val punkte: Double)

data class Wert(val label: String, val wert: Double, val einheit: String, val bereich: List<Double>?)

data class FehlerFormel(val formel: String, val msg: String)

data class Frage(
    val id: String,
    val lz: String,
    val typ: String, // single, multi, ihk, rechnen
    val prompt: String,
    val options: List<String>,
    val correct: List<Int>,
    val explanation: String,
    val situation: String,
    val aufgabe: String,
    val erwartung: List<Erwartung>,
    val musterloesung: String,
    val punkte: Double,
    val werte: List<Wert>,
    val formel: String,
    val answer: Double?,
    val unit: String,
    val tol: Double?,
    val loesung: String,
    val vary: Boolean,
    val runden: Int,
    val ergebnis: List<Double>?,
    val pruefe: List<String>,
    val fehler: List<FehlerFormel>,
    val loesungVorlage: String,
    val erklaerungVorlage: String,
    val promptVorlage: String,
) {
    val istPruefung: Boolean get() = typ == "ihk" || typ == "rechnen"
    val istAnkreuzen: Boolean get() = typ == "single" || typ == "multi"
    val maxPunkte: Double get() = if (erwartung.isNotEmpty()) erwartung.sumOf { it.punkte } else if (punkte > 0) punkte else 1.0
}

data class Vokabel(
    val id: String,
    val begriff: String,
    val bedeutung: String,
    val beispiel: String,
    val lz: String,
    val deck: String,
    val thema: String,
)

data class Kurs(
    val id: String,
    val titel: String,
    val erstellt: String,
    val lernziele: List<Lernziel>,
    val fragen: List<Frage>,
    val vokabeln: List<Vokabel>,
) {
    private val lzIndex: Map<String, Lernziel> by lazy { lernziele.associateBy { it.id } }
    private val fragenNachLz: Map<String, List<Frage>> by lazy { fragen.groupBy { it.lz } }
    private val fragenIndex: Map<String, Frage> by lazy { fragen.associateBy { it.id } }

    fun lernziel(id: String): Lernziel? = lzIndex[id]
    fun frage(id: String): Frage? = fragenIndex[id]
    fun fragenZu(lz: String): List<Frage> = fragenNachLz[lz] ?: emptyList()

    /** Lernziele, zu denen es Fragen gibt, in Lernreihenfolge. */
    val lernzieleSortiert: List<Lernziel> by lazy {
        lernziele.sortedWith(compareBy<Lernziel>({ it.tag }, { it.order }, { it.titel }))
    }
}

object KursLeser {

    fun lies(text: String): Kurs {
        val o = Json.parseObj(text)
        val kurs = o.obj("kurs") ?: emptyMap()
        val lz = o.liste("lernziele").mapNotNull { asObj(it)?.let(::lernziel) }
        val fr = o.liste("fragen").mapNotNull { asObj(it)?.let(::frage) }.filter { gueltig(it) }
        val vo = o.liste("vokabeln").mapNotNull { asObj(it)?.let(::vokabel) }.filter { it.begriff.isNotBlank() }
        if (lz.isEmpty() && fr.isEmpty() && vo.isEmpty()) throw JsonFehler("Das Paket enthält keine Lernziele, Fragen oder Fachbegriffe.")
        return Kurs(
            id = kurs.str("id", "kurs"),
            titel = kurs.str("titel", "Kurs"),
            erstellt = o.str("erstellt"),
            lernziele = lz,
            fragen = fr,
            vokabeln = vo,
        )
    }

    private fun gueltig(q: Frage): Boolean = when (q.typ) {
        "single", "multi" -> q.options.size >= 2 && q.correct.isNotEmpty()
        "ihk" -> q.aufgabe.isNotBlank() && q.erwartung.isNotEmpty()
        "rechnen" -> q.formel.isNotBlank() || q.answer != null
        else -> false
    }

    fun lernziel(m: Map<String, Any?>): Lernziel? {
        val id = m.str("id")
        if (id.isBlank()) return null
        val k = m.obj("lernkarte")
        val karte = k?.let {
            Lernkarte(
                kurz = it.str("kurz"),
                begriffe = it.liste("begriffe").mapNotNull { b -> asObj(b)?.let { x -> Begriff(x.str("b"), x.str("d"), x.str("x")) } },
                geruest = it.texte("geruest"),
                falle = it.str("falle"),
                merksatz = it.str("merksatz"),
            )
        }
        return Lernziel(
            id = id,
            titel = m.str("titel", id),
            thema = m.str("thema"),
            tag = m.str("tag"),
            kann = m.str("kann"),
            order = m.num("order") ?: 0.0,
            karte = karte,
        )
    }

    fun frage(m: Map<String, Any?>): Frage? {
        val id = m.str("id")
        if (id.isBlank()) return null
        val corr: List<Int> = when (val c = m["correct"]) {
            is Number -> listOf(c.toInt())
            is List<*> -> c.mapNotNull { (it as? Number)?.toInt() }
            else -> emptyList()
        }
        val erw = m.liste("erwartung").mapNotNull { e ->
            asObj(e)?.let { x -> Erwartung(x.str("text"), x.num("punkte") ?: 1.0) }
        }
        val werte = m.liste("werte").mapNotNull { w ->
            asObj(w)?.let { x ->
                val b = x.zahlen("bereich")
                Wert(x.str("label"), x.num("wert") ?: 0.0, x.str("einheit"), if (b.size >= 2) b else null)
            }
        }
        val fehler = m.liste("fehler").mapNotNull { f ->
            asObj(f)?.let { x -> FehlerFormel(x.str("formel"), x.str("msg")) }
        }.filter { it.formel.isNotBlank() }
        val erg = m.zahlen("ergebnis")
        return Frage(
            id = id,
            lz = m.str("lz"),
            typ = m.str("typ"),
            prompt = m.str("prompt"),
            options = m.texte("options"),
            correct = corr,
            explanation = m.str("explanation"),
            situation = m.str("situation"),
            aufgabe = m.str("aufgabe"),
            erwartung = erw,
            musterloesung = m.str("musterloesung"),
            punkte = m.num("punkte") ?: erw.sumOf { it.punkte },
            werte = werte,
            formel = m.str("formel"),
            answer = m.num("answer"),
            unit = m.str("unit"),
            tol = m.num("tol"),
            loesung = m.str("loesung"),
            vary = m.bool("vary"),
            runden = m.int("runden") ?: 2,
            ergebnis = if (erg.size >= 2) erg else null,
            pruefe = m.texte("pruefe"),
            fehler = fehler,
            loesungVorlage = m.str("loesungVorlage"),
            erklaerungVorlage = m.str("erklaerungVorlage"),
            promptVorlage = m.str("promptVorlage"),
        )
    }

    fun vokabel(m: Map<String, Any?>): Vokabel? {
        val id = m.str("id")
        if (id.isBlank()) return null
        return Vokabel(
            id = id,
            begriff = m.str("begriff"),
            bedeutung = m.str("bedeutung"),
            beispiel = m.str("beispiel"),
            lz = m.str("lz"),
            deck = m.str("deck"),
            thema = m.str("thema"),
        )
    }
}

/** IHK-Operatoren – regelbasierte Hilfe ohne KI. */
object Operatoren {
    val liste: List<Pair<String, String>> = listOf(
        "nennen" to "Aspekte ohne Erklärung aufzählen – Stichworte reichen, aber Fachbegriffe.",
        "aufzählen" to "Wie nennen: Punkte untereinander, ohne Erklärung.",
        "beschreiben" to "Sachverhalt mit eigenen Worten im Zusammenhang wiedergeben – noch ohne Begründung.",
        "darstellen" to "Sachverhalt strukturiert und zusammenhängend wiedergeben.",
        "erläutern" to "Beschreiben UND mit Beispiel oder Begründung verständlich machen – je Aspekt: Begriff + Erklärung + Bezug zur Einrichtung.",
        "erklären" to "Ursachen und Zusammenhänge verständlich machen (warum/wie).",
        "begründen" to "Aussage mit Argumenten stützen: Behauptung → weil → Folge.",
        "beurteilen" to "Urteil zuerst, dann Kriterien und Begründung – Ergebnis klar benennen.",
        "bewerten" to "Wie beurteilen, mit eigener, begründeter Wertung (Vor- und Nachteile → Fazit).",
        "entwickeln" to "Eigenen Vorschlag oder Plan ausarbeiten, passend zur Situation.",
        "vergleichen" to "Gemeinsamkeiten und Unterschiede nach Kriterien gegenüberstellen (Tabelle hilft).",
        "analysieren" to "Bestandteile und Ursachen systematisch untersuchen.",
        "berechnen" to "Formel → Zahlen eingesetzt → Ergebnis mit Einheit → Deutung.",
        "ermitteln" to "Ergebnis rechnerisch oder aus den Angaben herleiten, Weg zeigen.",
        "unterscheiden" to "Merkmale nennen, an denen sich die Begriffe trennen lassen.",
        "definieren" to "Begriff exakt in Fachsprache bestimmen (Oberbegriff + Merkmal).",
        "skizzieren" to "In Grundzügen darstellen – das Wesentliche, knapp.",
        "prüfen" to "Sachverhalt an Kriterien oder Paragrafen messen und Ergebnis nennen.",
    )

    fun finde(aufgabe: String): List<Pair<String, String>> {
        val a = aufgabe.lowercase()
        return liste.filter { (op, _) ->
            val stamm = op.removeSuffix("en").removeSuffix("n")
            a.contains(stamm)
        }
    }
}
