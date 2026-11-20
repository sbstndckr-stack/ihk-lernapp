package de.lernapp.ihk.core

/**
 * Kleiner JSON-Leser und -Schreiber ohne Abhängigkeiten.
 * Objekte werden zu Map<String, Any?>, Listen zu List<Any?>, Zahlen zu Double.
 * Der nachsichtige Modus akzeptiert Kommas vor schließenden Klammern (häufig bei kleinen KI-Modellen).
 */
object Json {

    fun parse(text: String): Any? = Leser(text, nachsichtig = false).alles()

    fun parseObj(text: String): Map<String, Any?> = asObj(parse(text)) ?: throw JsonFehler("Kein JSON-Objekt")

    /** Sucht das erste vollständige {...}-Objekt in einem freien Text (z. B. Antwort einer KI). */
    fun findeObjekt(text: String): Map<String, Any?>? {
        var start = text.indexOf('{')
        while (start >= 0) {
            val ende = passendeKlammer(text, start)
            if (ende > start) {
                try {
                    val v = Leser(text.substring(start, ende + 1), nachsichtig = true).alles()
                    val o = asObj(v)
                    if (o != null) return o
                } catch (e: JsonFehler) {
                    // weiter suchen
                }
            }
            start = text.indexOf('{', start + 1)
        }
        return null
    }

    private fun passendeKlammer(s: String, von: Int): Int {
        var tiefe = 0
        var inString = false
        var i = von
        while (i < s.length) {
            val c = s[i]
            if (inString) {
                if (c == '\\') i++
                else if (c == '"') inString = false
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> tiefe++
                    '}' -> {
                        tiefe--
                        if (tiefe == 0) return i
                    }
                }
            }
            i++
        }
        return -1
    }

    fun write(v: Any?): String {
        val sb = StringBuilder()
        schreibe(sb, v)
        return sb.toString()
    }

    private fun schreibe(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> text(sb, v)
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Number -> zahl(sb, v.toDouble())
            is Map<*, *> -> {
                sb.append('{')
                var erst = true
                for ((k, w) in v) {
                    if (!erst) sb.append(',')
                    erst = false
                    text(sb, k.toString())
                    sb.append(':')
                    schreibe(sb, w)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var erst = true
                for (w in v) {
                    if (!erst) sb.append(',')
                    erst = false
                    schreibe(sb, w)
                }
                sb.append(']')
            }
            is Array<*> -> schreibe(sb, v.toList())
            is DoubleArray -> schreibe(sb, v.toList())
            is IntArray -> schreibe(sb, v.toList())
            else -> text(sb, v.toString())
        }
    }

    private fun zahl(sb: StringBuilder, d: Double) {
        if (d.isNaN() || d.isInfinite()) {
            sb.append("null")
        } else if (d == Math.floor(d) && Math.abs(d) < 1e15) {
            sb.append(d.toLong().toString())
        } else {
            sb.append(d.toString())
        }
    }

    private fun text(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') {
                    sb.append("\\u")
                    sb.append(String.format("%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
    }

    private class Leser(val s: String, val nachsichtig: Boolean) {
        var i = 0

        fun alles(): Any? {
            leer()
            val v = wert()
            leer()
            if (i < s.length) fehler("Zusätzliche Zeichen")
            return v
        }

        fun fehler(m: String): Nothing = throw JsonFehler("$m an Stelle $i")

        fun leer() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t' || s[i] == '﻿')) i++
        }

        fun wert(): Any? {
            leer()
            if (i >= s.length) fehler("Unerwartetes Ende")
            return when (val c = s[i]) {
                '{' -> objekt()
                '[' -> liste()
                '"' -> zeichenkette()
                't' -> wort("true", true)
                'f' -> wort("false", false)
                'n' -> wort("null", null)
                else -> if (c == '-' || c.isDigit()) nummer() else fehler("Unerwartetes Zeichen '$c'")
            }
        }

        fun wort(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) fehler("Erwartet $w")
            i += w.length
            return v
        }

        fun objekt(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            leer()
            if (i < s.length && s[i] == '}') {
                i++
                return m
            }
            while (true) {
                leer()
                if (nachsichtig && i < s.length && s[i] == '}') {
                    i++
                    return m
                }
                if (i >= s.length || s[i] != '"') fehler("Schlüssel erwartet")
                val k = zeichenkette()
                leer()
                if (i >= s.length || s[i] != ':') fehler("':' erwartet")
                i++
                m[k] = wert()
                leer()
                if (i >= s.length) fehler("Unerwartetes Ende im Objekt")
                when (s[i]) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return m
                    }
                    else -> fehler("',' oder '}' erwartet")
                }
            }
        }

        fun liste(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            leer()
            if (i < s.length && s[i] == ']') {
                i++
                return l
            }
            while (true) {
                leer()
                if (nachsichtig && i < s.length && s[i] == ']') {
                    i++
                    return l
                }
                l.add(wert())
                leer()
                if (i >= s.length) fehler("Unerwartetes Ende in der Liste")
                when (s[i]) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return l
                    }
                    else -> fehler("',' oder ']' erwartet")
                }
            }
        }

        fun zeichenkette(): String {
            val sb = StringBuilder()
            i++
            while (true) {
                if (i >= s.length) fehler("Unerwartetes Ende im Text")
                val c = s[i]
                if (c == '"') {
                    i++
                    return sb.toString()
                }
                if (c == '\\') {
                    i++
                    if (i >= s.length) fehler("Unerwartetes Ende im Text")
                    when (val e = s[i]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (i + 4 >= s.length) fehler("Kurzes \\u")
                            val hex = s.substring(i + 1, i + 5)
                            sb.append(hex.toIntOrNull(16)?.toChar() ?: fehler("Falsches \\u"))
                            i += 4
                        }
                        else -> if (nachsichtig) sb.append(e) else fehler("Falsches Escape")
                    }
                    i++
                } else {
                    sb.append(c)
                    i++
                }
            }
        }

        fun nummer(): Double {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            return s.substring(start, i).toDoubleOrNull() ?: fehler("Falsche Zahl")
        }
    }
}

class JsonFehler(m: String) : Exception(m)

@Suppress("UNCHECKED_CAST")
fun asObj(v: Any?): Map<String, Any?>? = v as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun asList(v: Any?): List<Any?> = (v as? List<Any?>) ?: emptyList()

fun Map<String, Any?>.str(k: String, def: String = ""): String = when (val v = this[k]) {
    null -> def
    is String -> v
    is Double -> if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()
    else -> v.toString()
}

fun Map<String, Any?>.num(k: String): Double? = when (val v = this[k]) {
    is Number -> v.toDouble()
    is String -> v.replace(',', '.').trim().toDoubleOrNull()
    else -> null
}

fun Map<String, Any?>.int(k: String): Int? = num(k)?.toInt()

fun Map<String, Any?>.long(k: String): Long? = num(k)?.toLong()

fun Map<String, Any?>.bool(k: String): Boolean = when (val v = this[k]) {
    is Boolean -> v
    is String -> v == "true" || v == "ja"
    is Number -> v.toDouble() != 0.0
    else -> false
}

fun Map<String, Any?>.liste(k: String): List<Any?> = asList(this[k])

fun Map<String, Any?>.obj(k: String): Map<String, Any?>? = asObj(this[k])

fun Map<String, Any?>.texte(k: String): List<String> = liste(k).mapNotNull { it?.toString() }

fun Map<String, Any?>.zahlen(k: String): List<Double> = liste(k).mapNotNull { (it as? Number)?.toDouble() }
