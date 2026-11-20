package de.lernapp.ihk.core

/**
 * Kleiner Excel-Rechner wie im Lern-Monitor: Zahlen (auch mit Komma), Zellbezüge (V1, B3 …),
 * + − * / ^ %, Klammern und Funktionen SUMME, MITTELWERT, MIN, MAX, RUNDEN, ABS, ANZAHL, WURZEL
 * (deutsch und englisch). Trennzeichen für Argumente: ; oder ,
 */
object Formel {

    private sealed class Tok {
        data class Zahl(val v: Double) : Tok()
        data class Bezug(val name: String) : Tok()
        data class Fn(val name: String) : Tok()
        data class Op(val c: Char) : Tok()
    }

    fun rechne(quelle: String, zelle: (String) -> Double?): Double {
        var s = quelle.trim()
        if (s.startsWith("=")) s = s.substring(1)
        if (s.isEmpty()) throw FormelFehler("Die Formel ist leer.")
        val tk = zerlege(s)
        val p = Rechner(tk, zelle)
        val v = p.ausdruck()
        if (p.pos < tk.size) throw FormelFehler("Die Formel ist nach Stelle ${p.pos} nicht vollständig lesbar.")
        if (v.isNaN() || v.isInfinite()) throw FormelFehler("Das Ergebnis ist keine Zahl (z. B. Division durch 0).")
        return v
    }

    fun rechneOderNull(quelle: String, zelle: (String) -> Double?): Double? =
        try {
            rechne(quelle, zelle)
        } catch (e: FormelFehler) {
            null
        }

    private val reBezug = Regex("^\\$?([A-Za-z]{1,2})\\$?(\\d{1,4})(?![A-Za-z0-9(])")
    private val reFn = Regex("^([A-Za-zÄÖÜäöü][A-Za-zÄÖÜäöü0-9.]*)\\s*\\(")
    private val reZahl = Regex("^\\d+(?:[.,]\\d+)?")

    private fun zerlege(s: String): List<Tok> {
        val t = ArrayList<Tok>()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch.isWhitespace()) {
                i++
                continue
            }
            val rest = s.substring(i)
            val mF = reFn.find(rest)
            if (mF != null) {
                t.add(Tok.Fn(mF.groupValues[1].uppercase()))
                i += mF.value.length
                continue
            }
            val mB = reBezug.find(rest)
            if (mB != null) {
                t.add(Tok.Bezug((mB.groupValues[1] + mB.groupValues[2]).uppercase()))
                i += mB.value.length
                continue
            }
            val mZ = reZahl.find(rest)
            if (mZ != null) {
                t.add(Tok.Zahl(mZ.value.replace(',', '.').toDouble()))
                i += mZ.value.length
                continue
            }
            when (ch) {
                '+', '-', '*', '/', '^', '(', ')', ';', ',', '%' -> t.add(Tok.Op(ch))
                '−', '–' -> t.add(Tok.Op('-'))
                '×', '·' -> t.add(Tok.Op('*'))
                ':' -> throw FormelFehler("Ein Doppelpunkt (Bereich) wird hier nicht unterstützt.")
                else -> throw FormelFehler("Das Zeichen „$ch“ kenne ich in einer Formel nicht.")
            }
            i++
        }
        return t
    }

    private class Rechner(val tk: List<Tok>, val zelle: (String) -> Double?) {
        var pos = 0

        fun istOp(c: Char): Boolean {
            val t = tk.getOrNull(pos)
            return t is Tok.Op && t.c == c
        }

        fun ausdruck(): Double {
            var v = term()
            while (true) {
                v = when {
                    istOp('+') -> { pos++; v + term() }
                    istOp('-') -> { pos++; v - term() }
                    else -> return v
                }
            }
        }

        fun term(): Double {
            var v = potenz()
            while (true) {
                v = when {
                    istOp('*') -> { pos++; v * potenz() }
                    istOp('/') -> {
                        pos++
                        val d = potenz()
                        if (d == 0.0) throw FormelFehler("Division durch 0.")
                        v / d
                    }
                    else -> return v
                }
            }
        }

        fun potenz(): Double {
            var v = vorzeichen()
            while (istOp('^')) {
                pos++
                v = Math.pow(v, vorzeichen())
            }
            return v
        }

        fun vorzeichen(): Double {
            if (istOp('-')) {
                pos++
                return -vorzeichen()
            }
            if (istOp('+')) {
                pos++
                return vorzeichen()
            }
            var v = grund()
            while (istOp('%')) {
                pos++
                v /= 100.0
            }
            return v
        }

        fun grund(): Double {
            val t = tk.getOrNull(pos) ?: throw FormelFehler("Die Formel endet zu früh.")
            when (t) {
                is Tok.Zahl -> {
                    pos++
                    return t.v
                }
                is Tok.Bezug -> {
                    pos++
                    return zelle(t.name) ?: throw FormelFehler("Zelle ${t.name} ist leer oder keine Zahl.")
                }
                is Tok.Fn -> {
                    pos++
                    val args = ArrayList<Double>()
                    if (istOp(')')) {
                        pos++
                    } else {
                        while (true) {
                            args.add(ausdruck())
                            if (istOp(';') || istOp(',')) {
                                pos++
                                continue
                            }
                            if (istOp(')')) {
                                pos++
                                break
                            }
                            throw FormelFehler("In ${t.name}( fehlt eine schließende Klammer.")
                        }
                    }
                    return funktion(t.name, args)
                }
                is Tok.Op -> {
                    if (t.c == '(') {
                        pos++
                        val v = ausdruck()
                        if (!istOp(')')) throw FormelFehler("Eine schließende Klammer fehlt.")
                        pos++
                        return v
                    }
                    throw FormelFehler("Unerwartetes Zeichen „${t.c}“.")
                }
            }
        }

        fun funktion(name: String, a: List<Double>): Double {
            fun mind1() {
                if (a.isEmpty()) throw FormelFehler("$name braucht mindestens eine Zahl.")
            }
            return when (name) {
                "SUMME", "SUM" -> a.sum()
                "MITTELWERT", "AVERAGE" -> { mind1(); a.sum() / a.size }
                "MIN" -> { mind1(); a.minOrNull()!! }
                "MAX" -> { mind1(); a.maxOrNull()!! }
                "ANZAHL", "COUNT" -> a.size.toDouble()
                "ABS" -> { mind1(); Math.abs(a[0]) }
                "WURZEL", "SQRT" -> { mind1(); Math.sqrt(a[0]) }
                "RUNDEN", "ROUND" -> {
                    mind1()
                    val n = if (a.size > 1) a[1].toInt() else 0
                    val k = Math.pow(10.0, n.toDouble())
                    Math.signum(a[0]) * Math.round(Math.abs(a[0]) * k) / k
                }
                else -> throw FormelFehler("Die Funktion $name kenne ich hier nicht. Erlaubt sind SUMME, MITTELWERT, MIN, MAX, RUNDEN, ABS, ANZAHL, WURZEL.")
            }
        }
    }
}

class FormelFehler(m: String) : Exception(m)
