package de.lernapp.ihk.core

/**
 * Prompts für lokale KI und Claude. Grundsatz Token-Sparen: an Claude gehen nur Aufgabe,
 * Erwartungshorizont und Antwort – keine Lernkarte, kein Kapitel, kein Verlauf.
 * Feste Teile stehen im Systemprompt (bei Claude gecacht).
 */
object Prompts {

    // ---------- Lokale KI ----------

    const val LOKAL_PRUEFER = """Du bist ein strenger IHK-Prüfer für die Prüfung „Fachwirt/in im Gesundheits- und Sozialwesen“.
Bewerte die Antwort NUR gegen den Erwartungshorizont.
Regeln:
- Für jede Erwartung 0 bis Höchstpunkte, in Schritten von 0,5.
- Volle Punkte nur, wenn der Inhalt vollständig und in Fachsprache da ist.
- Alltagssprache statt Fachbegriff, fehlender Paragraf oder fehlende Begründung: höchstens die Hälfte.
- Fehlt ein Teil der Aufgabe, gibt es dafür 0 Punkte. Im Zweifel weniger Punkte.
- Steht in der Antwort etwas fachlich Richtiges, das nicht im Erwartungshorizont steht: "ausserhalb": true.
- Bist du dir bei einer Erwartung nicht sicher: "unsicher": true.
Antworte NUR mit diesem JSON, ohne weiteren Text:
{"punkte":[Zahl je Erwartung],"begruendung":"ein Satz","naechster":"genau ein nächster Schritt für den Lernenden","unsicher":false,"ausserhalb":false}"""

    fun lokalBewerten(q: Frage, antwort: String, auch: List<AuchRichtig>, beispiele: List<Lehrbeispiel>): String {
        val sb = StringBuilder()
        if (q.situation.isNotBlank()) sb.append("Situation: ").append(q.situation.trim()).append("\n")
        sb.append("Aufgabe: ").append(q.aufgabe.trim()).append("\n\n")
        sb.append(erwartungshorizont(q, auch))
        for ((i, b) in beispiele.withIndex()) {
            sb.append("\nLehrbeispiel ${i + 1} (so hat der Oberprüfer bewertet):\n")
            sb.append("Antwort: ").append(kuerze(b.antwort, 600)).append("\n")
            sb.append("Punkte: ").append(Json.write(b.punkte)).append(" – ").append(b.begruendung).append("\n")
        }
        sb.append("\nZu bewertende Antwort:\n").append(kuerze(antwort, 2500))
        return sb.toString()
    }

    const val LOKAL_ERKLAERER = """Du bist Lerncoach für einen erfahrenen Notfallsanitäter und Gruppenführer im Rettungsdienst, der den IHK-Fachwirt im Gesundheits- und Sozialwesen macht.
Erkläre einfach und genau: höchstens 6 kurze Sätze, ein Beispiel aus dem Rettungsdienst, die Fachbegriffe wörtlich nennen.
Keine Einleitung, keine Aufzählung von Quellen, kein Markdown."""

    fun lokalAndersErklaeren(lz: Lernziel?, q: Frage?, antwort: String): String {
        val sb = StringBuilder()
        lz?.karte?.let { k ->
            sb.append("Lernkarte:\n").append(k.kurz).append("\n")
            k.begriffe.take(6).forEach { sb.append("- ").append(it.b).append(": ").append(it.d).append("\n") }
            if (k.falle.isNotBlank()) sb.append("Typische Falle: ").append(k.falle).append("\n")
        }
        if (q != null) {
            sb.append("\nAufgabe: ").append(if (q.typ == "ihk") q.aufgabe else q.prompt).append("\n")
            if (q.typ == "ihk") sb.append(erwartungshorizont(q, emptyList()))
            if (q.explanation.isNotBlank()) sb.append("Erklärung zur Lösung: ").append(q.explanation).append("\n")
        }
        if (antwort.isNotBlank()) sb.append("\nSeine Antwort war: ").append(kuerze(antwort, 800)).append("\n")
        sb.append("\nErkläre die Sache anders als die Lernkarte – mit einem neuen Bild aus dem Rettungsdienst – und sag, was in seiner Antwort fehlt.")
        return sb.toString()
    }

    fun lokalEinfacher(lz: Lernziel): String {
        val k = lz.karte
        val sb = StringBuilder("Lernziel: ").append(lz.titel).append("\n")
        if (k != null) {
            sb.append(k.kurz).append("\n")
            k.begriffe.take(6).forEach { sb.append("- ").append(it.b).append(": ").append(it.d).append("\n") }
        }
        sb.append("\nErkläre das Thema noch einfacher, als würdest du es einem neuen Kollegen auf der Wache erklären.")
        return sb.toString()
    }

    const val LOKAL_TIPP = """Du bist Lerncoach. Gib einen Denkanstoß zu einer IHK-Aufgabe, OHNE die Lösung zu verraten.
Sag in höchstens 3 Sätzen: was der Operator verlangt, wie viele Aspekte gefragt sind und nach welchem Fachbegriff er suchen soll (Begriff nicht nennen, nur umschreiben). Kein Markdown."""

    fun lokalTipp(q: Frage, entwurf: String): String {
        val sb = StringBuilder()
        if (q.situation.isNotBlank()) sb.append("Situation: ").append(kuerze(q.situation, 800)).append("\n")
        sb.append("Aufgabe: ").append(q.aufgabe).append("\n")
        sb.append("(Nur für dich, nicht verraten) ").append(erwartungshorizont(q, emptyList()))
        if (entwurf.isNotBlank()) sb.append("\nSein bisheriger Entwurf: ").append(kuerze(entwurf, 800))
        return sb.toString()
    }

    // ---------- Claude ----------

    /** Fester Systemprompt für die strenge Bewertung – wird gecacht. */
    const val CLAUDE_PRUEFER = """Du bist Oberprüfer für die IHK-Prüfung „Geprüfte/r Fachwirt/in im Gesundheits- und Sozialwesen“ und bewertest freie Antworten streng nach IHK-Maßstab.

Bewertungsregeln:
- Bewerte jede Erwartung des Erwartungshorizonts einzeln: 0 bis Höchstpunkte, in Schritten von 0,5.
- Volle Punkte nur bei vollständigem Inhalt in exakter Fachsprache (Fachbegriff, Paragraf, Formel, Präzisionswörter wie „mindestens“, „höchstens“).
- Inhalt richtig verstanden, aber Fachbegriff fehlt oder Alltagssprache: höchstens die Hälfte.
- Operator beachten: „nennen“ verlangt Aspekte, „erläutern“ Aspekt + Erklärung + Bezug, „beurteilen“ ein Urteil mit Begründung (Urteil zuerst). Falscher Operator (z. B. Ablauf statt Merkmale): deutlich Abzug.
- Fehlende Aufgabenteile, fehlendes Urteil, fehlende Maßnahme: 0 Punkte für diese Erwartung.
- Inhalt, der richtig ist, aber nicht im Erwartungshorizont steht, zählt, wenn er die Erwartung fachlich erfüllt. Dann trage die Formulierung in "auchRichtig" ein (Nummer der Erwartung, kurzer Text), damit sie künftig als richtig gilt.
- Bewerte nie milder, als ein IHK-Prüfer es täte.

IHK-Notenschlüssel (Prozent der Punkte): 92–100 sehr gut, 81–91 gut, 67–80 befriedigend, 50–66 ausreichend, 30–49 mangelhaft, unter 30 ungenügend.

Antworte ausschließlich mit JSON in dieser Form:
{"punkte":[Zahl je Erwartung],"begruendung":"ein bis zwei Sätze: was fehlt zu sehr gut","naechster":"genau ein nächster Schritt","auchRichtig":[{"nr":1,"text":"…"}]}"""

    fun claudeBewerten(q: Frage, antwort: String, auch: List<AuchRichtig>, einspruch: String = ""): String {
        val sb = StringBuilder()
        if (q.situation.isNotBlank()) sb.append("Situation: ").append(q.situation.trim()).append("\n")
        sb.append("Aufgabe: ").append(q.aufgabe.trim()).append("\n\n")
        sb.append(erwartungshorizont(q, auch))
        sb.append("\nAntwort des Prüflings:\n").append(kuerze(antwort, 4000))
        if (einspruch.isNotBlank()) sb.append("\n\nEinspruch des Prüflings gegen die Vorbewertung: ").append(kuerze(einspruch, 800))
        return sb.toString()
    }

    const val CLAUDE_ERKLAERER = """Du erklärst Prüfungsstoff des IHK-Fachwirts im Gesundheits- und Sozialwesen für einen erfahrenen Notfallsanitäter (Gruppenführer). Höchstens 6 Sätze, ein Beispiel aus dem Rettungsdienst, Fachbegriffe wörtlich. Kein Markdown."""

    // ---------- gemeinsam ----------

    fun erwartungshorizont(q: Frage, auch: List<AuchRichtig>): String {
        val sb = StringBuilder("Erwartungshorizont (")
        sb.append(Rechnen.zahl(q.maxPunkte)).append(" Punkte):\n")
        q.erwartung.forEachIndexed { i, e ->
            sb.append(i + 1).append(". (").append(Rechnen.zahl(e.punkte)).append(" P) ").append(e.text.trim()).append("\n")
            auch.filter { it.nr == i + 1 }.forEach { sb.append("   auch richtig: ").append(it.text).append("\n") }
        }
        return sb.toString()
    }

    fun kuerze(t: String, max: Int): String = if (t.length <= max) t else t.substring(0, max) + " …"
}
