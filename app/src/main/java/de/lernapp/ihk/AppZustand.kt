package de.lernapp.ihk

import android.app.ActivityManager
import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.lernapp.ihk.core.Aenderung
import de.lernapp.ihk.core.AuchRichtig
import de.lernapp.ihk.core.Bewertung
import de.lernapp.ihk.core.Einstellungen
import de.lernapp.ihk.core.Eskalation
import de.lernapp.ihk.core.Fortschritt
import de.lernapp.ihk.core.Frage
import de.lernapp.ihk.core.Json
import de.lernapp.ihk.core.Kurs
import de.lernapp.ihk.core.KursLeser
import de.lernapp.ihk.core.Lehrbeispiel
import de.lernapp.ihk.core.Lernziel
import de.lernapp.ihk.core.Noten
import de.lernapp.ihk.core.Prompts
import de.lernapp.ihk.core.Qualitaet
import de.lernapp.ihk.core.Stichprobe
import de.lernapp.ihk.core.Verbrauch
import de.lernapp.ihk.core.Vergleich
import de.lernapp.ihk.core.Versuch
import de.lernapp.ihk.core.aehnlichkeit
import de.lernapp.ihk.core.hash
import de.lernapp.ihk.core.heute
import de.lernapp.ihk.core.neueId
import de.lernapp.ihk.core.normalisiere
import de.lernapp.ihk.daten.Speicher
import de.lernapp.ihk.ki.ClaudeApi
import de.lernapp.ihk.ki.LokaleKi
import de.lernapp.ihk.ki.ModellVerwaltung
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class Ansicht {
    object Start : Ansicht()
    object Lernziele : Ansicht()
    data class Lernen(val lzId: String) : Ansicht()
    object Abschluss : Ansicht()
    object Vokabeln : Ansicht()
    object Ki : Ansicht()
    object Qualitaetskreis : Ansicht()
}

/** Ergebnis einer IHK-Bewertung für die Anzeige. */
class IhkErgebnis(
    val bewertung: Bewertung,
    val versuchId: String,
    val hinweis: String,
    val brauchtWahl: Boolean,
)

/**
 * Zentrale Logik der App: Daten laden/speichern, Bewertungsablauf (Regel → lokal → Claude nach Eskalationsregeln),
 * Stichproben per Batch, Qualitätskreis (PDCA), Budget.
 * Alle Änderungen laufen auf dem Haupt-Thread; Rechenarbeit und Netz im Hintergrund.
 */
class AppZustand(private val app: Application) {

    val speicher = Speicher(app)
    val lokal = LokaleKi(app)
    val modelle = ModellVerwaltung(app)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val haupt = Handler(Looper.getMainLooper())

    /** Wird bei jeder Datenänderung erhöht – Oberflächen lesen diesen Wert und zeichnen neu. */
    var version by mutableIntStateOf(0)
        private set

    var ansicht by mutableStateOf<Ansicht>(Ansicht.Start)
    var meldung by mutableStateOf<String?>(null)
    var kiStatus by mutableStateOf("Lokale KI nicht geladen")
    var kiLaedt by mutableStateOf(false)
    var energieHeute by mutableStateOf<Int?>(null)

    var kurs: Kurs? = null
        private set
    var fortschritt = Fortschritt()
        private set
    var qualitaet = Qualitaet()
        private set
    var einstellungen = Einstellungen()
        private set

    init {
        ladeAlles()
        energieHeute = fortschritt.energie[heute()]
        // Offene Stichproben-Prüfung abholen bzw. wöchentlich senden
        scope.launch { automatischerCheck() }
        // Zuletzt benutztes Modell automatisch laden
        if (einstellungen.lokalesModell.isNotBlank() && File(einstellungen.lokalesModell).exists()) {
            kiLaden(einstellungen.lokalesModell)
        }
    }

    fun geaendert() {
        version++
    }

    // ---------------- Laden & Speichern ----------------

    private fun ladeAlles() {
        kurs = try {
            speicher.lies(KURS)?.let { KursLeser.lies(it) }
        } catch (e: Exception) {
            meldung = "Kurs-Paket ließ sich nicht lesen: ${e.message}"
            null
        }
        fortschritt = try {
            speicher.lies(FORTSCHRITT)?.let { Fortschritt.aus(Json.parseObj(it)) } ?: Fortschritt()
        } catch (e: Exception) {
            Fortschritt()
        }
        qualitaet = try {
            speicher.lies(QUALITAET)?.let { Qualitaet.aus(Json.parseObj(it)) } ?: Qualitaet()
        } catch (e: Exception) {
            Qualitaet()
        }
        einstellungen = try {
            speicher.lies(EINSTELLUNGEN)?.let { Einstellungen.aus(Json.parseObj(it)) } ?: Einstellungen()
        } catch (e: Exception) {
            Einstellungen()
        }
    }

    /** Speichert im Hintergrund. Der Text wird hier (Haupt-Thread) erzeugt, damit sich nichts überschneidet. */
    fun speichern() {
        val f = Json.write(fortschritt.toMap())
        val q = Json.write(qualitaet.toMap())
        val e = Json.write(einstellungen.toMap())
        geaendert()
        scope.launch(Dispatchers.IO) {
            speicher.schreibe(FORTSCHRITT, f)
            speicher.schreibe(QUALITAET, q)
            speicher.schreibe(EINSTELLUNGEN, e)
        }
    }

    fun kursImportieren(text: String, quelle: String) {
        try {
            val k = KursLeser.lies(text)
            // Pflichtbuch (falls im Paket) getrennt ablegen – bleibt in der App
            scope.launch(Dispatchers.IO) { speicher.schreibe(KURS, text) }
            kurs = k
            meldung = "$quelle geladen: ${k.lernziele.size} Lernziele, ${k.fragen.size} Fragen, ${k.vokabeln.size} Fachbegriffe."
            geaendert()
        } catch (e: Exception) {
            meldung = "Import fehlgeschlagen: ${e.message}"
        }
    }

    fun kursAusDatei(uri: Uri) {
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } ?: throw IllegalStateException("Datei lässt sich nicht öffnen.")
                kursImportieren(text, "Kurs-Paket")
            } catch (e: Exception) {
                meldung = "Import fehlgeschlagen: ${e.message}"
            }
        }
    }

    fun probePaketLaden() {
        kursImportieren(speicher.asset("probe-paket.json"), "Probe-Paket")
    }

    fun einstellungenAendern(aenderung: (Einstellungen) -> Unit) {
        aenderung(einstellungen)
        speichern()
    }

    fun energieSetzen(wert: Int) {
        fortschritt.energie[heute()] = wert
        energieHeute = wert
        speichern()
    }

    // ---------------- Gerät ----------------

    fun geraetText(): String {
        val am = app.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val gb = mi.totalMem / 1_000_000_000.0
        return "${Build.MANUFACTURER} ${Build.MODEL} · ${"%.1f".format(gb)} GB RAM · Android ${Build.VERSION.RELEASE}"
    }

    // ---------------- Lokale KI ----------------

    fun kiLaden(pfad: String) {
        if (kiLaedt) return
        kiLaedt = true
        kiStatus = "Lädt ${File(pfad).name} … (bis zu 30 Sekunden)"
        scope.launch {
            val start = System.currentTimeMillis()
            try {
                val text = lokal.laden(pfad, einstellungen.gpu)
                val s = (System.currentTimeMillis() - start) / 1000
                kiStatus = "$text (geladen in $s s)"
                einstellungen.lokalesModell = pfad
                speichern()
            } catch (t: Throwable) {
                kiStatus = "Laden fehlgeschlagen: ${t.message}"
            } finally {
                kiLaedt = false
                geaendert()
            }
        }
    }

    fun kiEntladen() {
        scope.launch {
            lokal.schliessen()
            kiStatus = "Lokale KI entladen"
            geaendert()
        }
    }

    // ---------------- Versuche ----------------

    fun karteGesehen(lzId: String) {
        fortschritt.karteGesehen.add(lzId)
        speichern()
    }

    fun regelVersuch(q: Frage, antwort: String, richtig: Boolean, abschluss: Boolean): Versuch {
        val v = Versuch(
            neueId("v"), q.id, q.lz, q.typ, System.currentTimeMillis(), heute(),
            if (richtig) 100.0 else 0.0, richtig, "regel", antwort, emptyList(), "", false, abschluss,
        )
        fortschritt.versuche.add(v)
        nachVersuch(q)
        speichern()
        return v
    }

    private fun bewerteterVersuch(q: Frage, antwort: String, b: Bewertung, abschluss: Boolean): Versuch {
        val p = b.prozent(q.maxPunkte)
        val v = Versuch(
            neueId("v"), q.id, q.lz, q.typ, System.currentTimeMillis(), heute(),
            p, p >= Noten.RICHTIG, b.quelle, antwort, b.punkte, b.begruendung, b.quelle != "claude", abschluss,
        )
        fortschritt.versuche.add(v)
        nachVersuch(q)
        return v
    }

    private fun nachVersuch(q: Frage) {
        val st = fortschritt.stand(q.lz, kurs?.fragenZu(q.lz)?.any { it.istPruefung } ?: false)
        if (st.sicher) fortschritt.morgenZuerst.remove(q.lz)
    }

    fun abschlussAuswerten(ergebnisse: Map<String, Double>) {
        for ((lz, p) in ergebnisse) {
            if (p < Noten.RICHTIG) fortschritt.morgenZuerst.add(lz) else fortschritt.morgenZuerst.remove(lz)
        }
        speichern()
    }

    // ---------------- Claude ----------------

    fun onlineMoeglich(): Boolean =
        einstellungen.onlineErlaubt && einstellungen.apiSchluessel.isNotBlank() && qualitaet.budgetErlaubt(einstellungen)

    private fun verbrauchErfassen(a: ClaudeApi.Antwort, zweck: String, batch: Boolean) {
        val usd = qualitaet.kosten(einstellungen, a.modell.ifBlank { einstellungen.modellStreng }, a.eingabe, a.ausgabe, a.cacheLesen, a.cacheSchreiben, batch)
        qualitaet.verbrauch.add(Verbrauch(System.currentTimeMillis(), a.modell, zweck, a.eingabe, a.ausgabe, a.cacheLesen, a.cacheSchreiben, usd))
        if (qualitaet.budgetWarnung(einstellungen)) {
            meldung = "Achtung: ${"%.2f".format(qualitaet.monatEur(einstellungen))} € von ${"%.2f".format(einstellungen.budgetEur)} € Monatsbudget verbraucht."
        }
    }

    private suspend fun claudeBewertung(q: Frage, antwort: String, einspruch: String): Bewertung {
        val api = ClaudeApi(einstellungen.apiSchluessel)
        val modell = einstellungen.modellStreng
        val nutzer = Prompts.claudeBewerten(q, antwort, qualitaet.auchRichtigZu(q.id), einspruch)
        val a = withContext(Dispatchers.IO) { api.nachricht(modell, Prompts.CLAUDE_PRUEFER, nutzer, 300) }
        verbrauchErfassen(a, if (einspruch.isBlank()) "bewerten" else "einspruch", false)
        val b = Bewertung.ausKiText(a.text, q.erwartung, "claude")
        if (!b.lesbar) throw IllegalStateException("Claudes Antwort war nicht lesbar.")
        return b
    }

    // ---------------- Bewertungsablauf IHK ----------------

    /**
     * 1. Gleiche Antwort schon bewertet? → aus dem Speicher (0 Token).
     * 2. Lokale KI bewertet (vorläufig).
     * 3. Eskalation (unsicher, knapp an 50/67/92 %, außerhalb der Musterlösung, unlesbar) → Claude sofort.
     * 4. Sonst Stichprobe mit aktueller Quote → gesammelt per Batch an Claude.
     * Ohne lokale KI: Wahl zwischen Selbstbewertung und Claude.
     */
    suspend fun bewerteIhk(q: Frage, antwort: String, abschluss: Boolean, status: (String) -> Unit): IhkErgebnis {
        val schluessel = hash(q.id, normalisiere(antwort))
        qualitaet.cache[schluessel]?.let { m ->
            val b = Bewertung.aus(m)
            val v = bewerteterVersuch(q, antwort, b, abschluss)
            speichern()
            return IhkErgebnis(b, v.id, "Diese Antwort wurde schon einmal bewertet – Ergebnis aus dem Speicher (0 Token).", false)
        }
        if (!lokal.bereit) {
            return IhkErgebnis(Bewertung(q.erwartung.map { 0.0 }, "", "", true, false, "keine"), "", "Die lokale KI ist nicht geladen.", true)
        }
        status("Lokale KI bewertet …")
        val beispiele = qualitaet.beispieleFuer(q.id, q.lz, antwort)
        val roh = try {
            lokal.frage(Prompts.LOKAL_PRUEFER, Prompts.lokalBewerten(q, antwort, qualitaet.auchRichtigZu(q.id), beispiele), 0.1)
        } catch (t: Throwable) {
            ""
        }
        val lb = Bewertung.ausKiText(roh, q.erwartung, "lokal")
        val grund = Eskalation.grund(lb, q.maxPunkte)
        if (grund == null) {
            val v = bewerteterVersuch(q, antwort, lb, abschluss)
            var hinweis = "Vorläufig bewertet von der lokalen KI (${lokal.modellName})."
            if (einstellungen.apiSchluessel.isNotBlank() && Eskalation.stichprobe(qualitaet.stichprobenQuote, Math.random())) {
                qualitaet.warteschlange.add(Stichprobe(neueId("s"), v.id, q.id, antwort, lb.punkte, lokal.modellName, beispiele.map { it.id }, "Stichprobe"))
                hinweis += " Als Stichprobe vorgemerkt: Claude prüft gesammelt nach."
            }
            qualitaet.cache[schluessel] = lb.toMap()
            speichern()
            return IhkErgebnis(lb, v.id, hinweis, false)
        }
        if (onlineMoeglich()) {
            status("Claude prüft nach ($grund) …")
            try {
                val cb = claudeBewertung(q, antwort, "")
                val v = bewerteterVersuch(q, antwort, cb, abschluss)
                if (lb.lesbar) vergleichErfassen(q.id, lokal.modellName, lb.punkte, cb.punkte, beispiele.map { it.id })
                lernenAus(q, antwort, cb)
                qualitaet.cache[schluessel] = cb.toMap()
                speichern()
                return IhkErgebnis(cb, v.id, "Von Claude nachgeprüft, weil: $grund.", false)
            } catch (t: Throwable) {
                meldung = "Claude nicht erreichbar: ${t.message}"
            }
        }
        if (lb.lesbar) {
            val v = bewerteterVersuch(q, antwort, lb, abschluss)
            if (einstellungen.apiSchluessel.isNotBlank()) {
                qualitaet.warteschlange.add(Stichprobe(neueId("s"), v.id, q.id, antwort, lb.punkte, lokal.modellName, beispiele.map { it.id }, grund))
            }
            speichern()
            return IhkErgebnis(lb, v.id, "Vorläufig von der lokalen KI ($grund). Claude war gerade nicht verfügbar – zur Nachprüfung vorgemerkt.", false)
        }
        return IhkErgebnis(Bewertung(q.erwartung.map { 0.0 }, "", "", true, false, "keine"), "", "Die lokale KI konnte die Antwort nicht bewerten.", true)
    }

    /** Claude sofort (auf Wunsch, wenn die lokale KI fehlt). */
    suspend fun claudeDirekt(q: Frage, antwort: String, abschluss: Boolean): IhkErgebnis {
        val cb = claudeBewertung(q, antwort, "")
        val v = bewerteterVersuch(q, antwort, cb, abschluss)
        lernenAus(q, antwort, cb)
        qualitaet.cache[hash(q.id, normalisiere(antwort))] = cb.toMap()
        speichern()
        return IhkErgebnis(cb, v.id, "Bewertet von Claude.", false)
    }

    /** Selbstbewertung – zählt vorläufig und wird gesammelt von Claude nachgeprüft. */
    fun selbstBewertet(q: Frage, antwort: String, punkte: List<Double>, abschluss: Boolean): IhkErgebnis {
        val b = Bewertung(punkte, "Selbst bewertet.", "", false, false, "selbst")
        val v = bewerteterVersuch(q, antwort, b, abschluss)
        var hinweis = "Selbstbewertung gespeichert (vorläufig)."
        if (einstellungen.apiSchluessel.isNotBlank()) {
            qualitaet.warteschlange.add(Stichprobe(neueId("s"), v.id, q.id, antwort, punkte, "selbst", emptyList(), "Selbstbewertung"))
            hinweis += " Claude prüft sie beim nächsten gesammelten Check nach."
        }
        speichern()
        return IhkErgebnis(b, v.id, hinweis, false)
    }

    /** Einspruch: Claude bewertet neu, mit der Begründung des Lernenden. */
    suspend fun einspruch(q: Frage, versuchId: String, antwort: String, begruendung: String): IhkErgebnis {
        val v = fortschritt.versuche.firstOrNull { it.id == versuchId }
        val vorher = v?.punkte ?: emptyList()
        val vorherQuelle = v?.bewertetVon ?: ""
        val cb = claudeBewertung(q, antwort, begruendung)
        if (v != null) korrigiere(v, q, cb)
        if (vorherQuelle == "lokal" && vorher.size == cb.punkte.size) vergleichErfassen(q.id, lokal.modellName, vorher, cb.punkte, emptyList())
        lernenAus(q, antwort, cb)
        qualitaet.cache[hash(q.id, normalisiere(antwort))] = cb.toMap()
        speichern()
        return IhkErgebnis(cb, versuchId, "Einspruch geprüft von Claude.", false)
    }

    private fun korrigiere(v: Versuch, q: Frage, cb: Bewertung) {
        v.punkte = cb.punkte
        v.prozent = cb.prozent(q.maxPunkte)
        v.richtig = v.prozent >= Noten.RICHTIG
        v.begruendung = cb.begruendung
        v.bewertetVon = "claude"
        v.vorlaeufig = false
        nachVersuch(q)
    }

    // ---------------- Qualitätskreis (PDCA) ----------------

    private fun vergleichErfassen(frageId: String, modell: String, lokalP: List<Double>, claudeP: List<Double>, beispiele: List<String>) {
        if (lokalP.size != claudeP.size) return
        val jetzt = System.currentTimeMillis()
        val v = Vergleich(jetzt, frageId, modell, lokalP, claudeP, beispiele)
        qualitaet.vergleiche.add(v)
        qualitaet.vergleicheSeitAenderung++
        qualitaet.beispieleAuswerten(v, jetzt)
        qualitaet.anpassen(jetzt)?.let { meldung = "Qualitätskreis: ${it.text}" }
    }

    /** Jede Claude-Bewertung wird Lehrbeispiel; richtige Formulierungen außerhalb der Musterlösung → „auch richtig“. */
    private fun lernenAus(q: Frage, antwort: String, cb: Bewertung) {
        val jetzt = System.currentTimeMillis()
        val doppelt = qualitaet.lehrbeispiele.any { it.frageId == q.id && normalisiere(it.antwort) == normalisiere(antwort) }
        if (!doppelt) {
            val b = Lehrbeispiel(neueId("b"), q.id, q.lz, antwort, cb.punkte, cb.begruendung, jetzt, true, 0, 0)
            qualitaet.lehrbeispiele.add(b)
            qualitaet.aenderungen.add(
                Aenderung(neueId("a"), jetzt, "lehrbeispielNeu", "Lehrbeispiel aufgenommen: ${kurzTitel(q)}", mapOf("beispiel" to b.id), false),
            )
            // Höchstens 5 aktive Beispiele je Frage – das älteste wird abgeschaltet
            val aktive = qualitaet.lehrbeispiele.filter { it.frageId == q.id && it.aktiv }.sortedBy { it.datum }
            if (aktive.size > 5) aktive.first().aktiv = false
        }
        for ((nr, text) in cb.auchRichtig) {
            val schon = qualitaet.auchRichtig.any { it.frageId == q.id && it.nr == nr && aehnlichkeit(it.text, text) > 0.6 }
            if (schon) continue
            val ar = AuchRichtig(neueId("r"), q.id, nr, text, true)
            qualitaet.auchRichtig.add(ar)
            qualitaet.aenderungen.add(
                Aenderung(neueId("a"), jetzt, "auchRichtig", "„Auch richtig“ ergänzt (${kurzTitel(q)}, Erwartung $nr): $text", mapOf("auch" to ar.id), false),
            )
        }
    }

    private fun kurzTitel(q: Frage): String {
        val t = q.aufgabe.ifBlank { q.prompt }
        return if (t.length > 50) t.substring(0, 50) + " …" else t
    }

    fun rueckgaengig(a: Aenderung) {
        qualitaet.rueckgaengig(a)
        speichern()
    }

    // ---------------- Stichproben per Batch ----------------

    private suspend fun automatischerCheck() {
        if (einstellungen.apiSchluessel.isBlank()) return
        try {
            if (qualitaet.offenerBatch.isNotBlank()) {
                batchAbholen()
            } else if (qualitaet.warteschlange.isNotEmpty() && System.currentTimeMillis() - qualitaet.letzterCheck > SIEBEN_TAGE) {
                stichprobenSenden()
            }
        } catch (t: Throwable) {
            // still – beim nächsten Start erneut
        }
    }

    /** Wöchentlicher Check: gesammelte Stichproben und Selbstbewertungen als Batch an Claude (50 % günstiger). */
    suspend fun stichprobenSenden(): String {
        val k = kurs ?: return "Kein Kurs geladen."
        if (qualitaet.offenerBatch.isNotBlank()) return "Es läuft schon ein Check – Ergebnis abholen."
        if (!onlineMoeglich()) return "Claude ist nicht eingerichtet oder das Budget ist erreicht."
        val auswahl = qualitaet.warteschlange.filter { k.frage(it.frageId) != null }.take(50)
        if (auswahl.isEmpty()) return "Keine Stichproben in der Warteschlange."
        val api = ClaudeApi(einstellungen.apiSchluessel)
        val anfragen = auswahl.map { s ->
            val q = k.frage(s.frageId)!!
            s.id to api.anfrage(einstellungen.modellStreng, Prompts.CLAUDE_PRUEFER, Prompts.claudeBewerten(q, s.antwort, qualitaet.auchRichtigZu(q.id)), 300)
        }
        val id = withContext(Dispatchers.IO) { api.batchSenden(anfragen) }
        qualitaet.offenerBatch = id
        qualitaet.offenerBatchSeit = System.currentTimeMillis()
        qualitaet.batchInhalt.clear()
        qualitaet.batchInhalt.addAll(auswahl.map { it.id })
        qualitaet.letzterCheck = System.currentTimeMillis()
        speichern()
        return "${auswahl.size} Antworten an Claude geschickt. Ergebnis meist in wenigen Minuten, spätestens in 24 Stunden."
    }

    suspend fun batchAbholen(): String {
        val k = kurs ?: return "Kein Kurs geladen."
        val id = qualitaet.offenerBatch
        if (id.isBlank()) return "Kein offener Check."
        val api = ClaudeApi(einstellungen.apiSchluessel)
        val (status, url) = withContext(Dispatchers.IO) { api.batchStatus(id) }
        if (status != "ended") return "Claude arbeitet noch (Status: $status)."
        val ergebnisse = withContext(Dispatchers.IO) { api.batchErgebnisse(url, einstellungen.modellStreng) }
        var geprueft = 0
        var korrigiert = 0
        val erledigt = HashSet<String>()
        for (sid in qualitaet.batchInhalt) {
            val s = qualitaet.warteschlange.firstOrNull { it.id == sid }
            val a = ergebnisse[sid]
            if (s == null) continue
            erledigt.add(sid)
            if (a == null) continue
            verbrauchErfassen(a, "stichprobe", true)
            val q = k.frage(s.frageId) ?: continue
            val cb = Bewertung.ausKiText(a.text, q.erwartung, "claude")
            if (!cb.lesbar) continue
            geprueft++
            val v = fortschritt.versuche.firstOrNull { it.id == s.versuchId }
            if (v != null) {
                if (Math.abs(v.punkte.sum() - cb.summe()) >= 0.5) korrigiert++
                korrigiere(v, q, cb)
            }
            if (s.grund != "Selbstbewertung" && s.lokal.size == cb.punkte.size) {
                vergleichErfassen(q.id, s.modell, s.lokal, cb.punkte, s.beispiele)
            }
            lernenAus(q, s.antwort, cb)
            qualitaet.cache[hash(q.id, normalisiere(s.antwort))] = cb.toMap()
        }
        qualitaet.warteschlange.removeAll { it.id in erledigt }
        qualitaet.offenerBatch = ""
        qualitaet.batchInhalt.clear()
        speichern()
        val text = "Check fertig: $geprueft Antworten nachgeprüft, $korrigiert davon korrigiert."
        meldung = text
        return text
    }

    // ---------------- Erklären & Tipps ----------------

    suspend fun erklaeren(lz: Lernziel?, q: Frage?, antwort: String, online: Boolean, live: (String) -> Unit): String {
        val nutzer = Prompts.lokalAndersErklaeren(lz, q, antwort)
        if (!online && lokal.bereit) {
            return lokal.frage(Prompts.LOKAL_ERKLAERER, nutzer, 0.6) { t -> haupt.post { live(t) } }
        }
        if (!onlineMoeglich()) throw IllegalStateException("Weder lokale KI geladen noch Claude eingerichtet.")
        val api = ClaudeApi(einstellungen.apiSchluessel)
        val a = withContext(Dispatchers.IO) { api.nachricht(einstellungen.modellEinfach, Prompts.CLAUDE_ERKLAERER, nutzer, 400) }
        verbrauchErfassen(a, "erklären", false)
        speichern()
        return a.text
    }

    suspend fun einfacherErklaeren(lz: Lernziel, live: (String) -> Unit): String {
        if (!lokal.bereit) throw IllegalStateException("Die lokale KI ist nicht geladen.")
        return lokal.frage(Prompts.LOKAL_ERKLAERER, Prompts.lokalEinfacher(lz), 0.6) { t -> haupt.post { live(t) } }
    }

    suspend fun tipp(q: Frage, entwurf: String, live: (String) -> Unit): String {
        if (!lokal.bereit) throw IllegalStateException("Die lokale KI ist nicht geladen.")
        return lokal.frage(Prompts.LOKAL_TIPP, Prompts.lokalTipp(q, entwurf), 0.4) { t -> haupt.post { live(t) } }
    }

    // ---------------- Modelltest ----------------

    /** Prüft das geladene Modell an allen von Claude bewerteten Antworten (Prüfdatensatz). */
    suspend fun modelltest(fortschrittText: (String) -> Unit): String {
        val k = kurs ?: return "Kein Kurs geladen."
        if (!lokal.bereit) return "Erst ein Modell laden."
        val daten = qualitaet.lehrbeispiele.filter { k.frage(it.frageId) != null }.takeLast(30)
        if (daten.size < 3) return "Zu wenig Prüfdaten: mindestens 3 von Claude bewertete Antworten nötig (jetzt ${daten.size})."
        var abw = 0.0
        var treffer = 0
        var milder = 0
        var n = 0
        val start = System.currentTimeMillis()
        for ((i, b) in daten.withIndex()) {
            fortschrittText("Modelltest ${i + 1} von ${daten.size} …")
            val q = k.frage(b.frageId)!!
            val roh = try {
                lokal.frage(Prompts.LOKAL_PRUEFER, Prompts.lokalBewerten(q, b.antwort, qualitaet.auchRichtigZu(q.id), emptyList()), 0.1)
            } catch (t: Throwable) {
                ""
            }
            val lb = Bewertung.ausKiText(roh, q.erwartung, "lokal")
            if (!lb.lesbar || lb.punkte.size != b.punkte.size) continue
            val v = Vergleich(0, q.id, lokal.modellName, lb.punkte, b.punkte, emptyList())
            abw += v.abweichungJeErwartung
            if (v.treffer) treffer++
            if (v.milder) milder++
            n++
        }
        val dauer = (System.currentTimeMillis() - start) / 1000.0
        val ergebnis = linkedMapOf<String, Any?>(
            "datum" to System.currentTimeMillis(), "n" to n, "von" to daten.size,
            "abweichung" to if (n > 0) abw / n else null, "treffer" to treffer, "milder" to milder,
            "sekundenJeAntwort" to if (daten.isNotEmpty()) dauer / daten.size else null,
            "backend" to lokal.backendName,
        )
        qualitaet.modelltests[lokal.modellName] = ergebnis
        speichern()
        return if (n == 0) "Das Modell lieferte keine lesbaren Bewertungen." else
            "${lokal.modellName}: Ø Abweichung ${"%.2f".format(abw / n)} Punkte je Erwartung, $treffer von $n Treffer, $milder× milder als Claude."
    }

    companion object {
        const val KURS = "kurs.json"
        const val FORTSCHRITT = "fortschritt.json"
        const val QUALITAET = "qualitaet.json"
        const val EINSTELLUNGEN = "einstellungen.json"
        const val SIEBEN_TAGE = 7L * 24 * 3600 * 1000
    }
}
