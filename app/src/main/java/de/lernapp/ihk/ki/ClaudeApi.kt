package de.lernapp.ihk.ki

import de.lernapp.ihk.core.Json
import de.lernapp.ihk.core.asObj
import de.lernapp.ihk.core.int
import de.lernapp.ihk.core.liste
import de.lernapp.ihk.core.obj
import de.lernapp.ihk.core.str
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI

/**
 * Claude-API mit eigenem Schlüssel (Bezahlung pro Nutzung, getrennt vom Claude-Abo).
 * Blockierende Aufrufe – nur im Hintergrund (Dispatchers.IO) verwenden.
 * Sparsam: Systemprompt mit cache_control, kurze Anfragen, max_tokens begrenzt.
 */
class ClaudeApi(private val schluessel: String) {

    data class Antwort(
        val text: String,
        val modell: String,
        val eingabe: Int,
        val ausgabe: Int,
        val cacheLesen: Int,
        val cacheSchreiben: Int,
    )

    class ApiFehler(val code: Int, m: String) : Exception(m)

    fun nachricht(modell: String, system: String, nutzer: String, maxTokens: Int = 300): Antwort {
        val body = Json.write(anfrage(modell, system, nutzer, maxTokens))
        val roh = http("POST", "$BASIS/v1/messages", body)
        return antwortAus(Json.parseObj(roh), modell)
    }

    fun anfrage(modell: String, system: String, nutzer: String, maxTokens: Int): Map<String, Any?> = linkedMapOf(
        "model" to modell,
        "max_tokens" to maxTokens,
        "system" to listOf(
            linkedMapOf("type" to "text", "text" to system, "cache_control" to mapOf("type" to "ephemeral")),
        ),
        "messages" to listOf(mapOf("role" to "user", "content" to nutzer)),
    )

    /** Message Batches API: 50 % günstiger, Ergebnis meist innerhalb von Minuten bis 24 h. */
    fun batchSenden(anfragen: List<Pair<String, Map<String, Any?>>>): String {
        val body = Json.write(mapOf("requests" to anfragen.map { (id, params) -> mapOf("custom_id" to id, "params" to params) }))
        val o = Json.parseObj(http("POST", "$BASIS/v1/messages/batches", body))
        return o.str("id")
    }

    /** Liefert (Status, results_url). Status „ended“ heißt fertig. */
    fun batchStatus(id: String): Pair<String, String> {
        val o = Json.parseObj(http("GET", "$BASIS/v1/messages/batches/$id", null))
        return o.str("processing_status") to o.str("results_url")
    }

    /** custom_id → Antwort (oder null bei Fehler je Anfrage). */
    fun batchErgebnisse(url: String, modell: String): Map<String, Antwort?> {
        val roh = http("GET", url, null)
        val erg = LinkedHashMap<String, Antwort?>()
        roh.lineSequence().filter { it.isNotBlank() }.forEach { zeile ->
            try {
                val o = Json.parseObj(zeile)
                val id = o.str("custom_id")
                val r = o.obj("result")
                erg[id] = if (r != null && r.str("type") == "succeeded") r.obj("message")?.let { antwortAus(it, modell) } else null
            } catch (e: Exception) {
                // Zeile überspringen
            }
        }
        return erg
    }

    private fun antwortAus(o: Map<String, Any?>, modell: String): Antwort {
        val text = o.liste("content").mapNotNull { asObj(it) }.filter { it.str("type") == "text" }.joinToString("") { it.str("text") }
        val u = o.obj("usage") ?: emptyMap()
        return Antwort(
            text = text,
            modell = o.str("model", modell),
            eingabe = u.int("input_tokens") ?: 0,
            ausgabe = u.int("output_tokens") ?: 0,
            cacheLesen = u.int("cache_read_input_tokens") ?: 0,
            cacheSchreiben = u.int("cache_creation_input_tokens") ?: 0,
        )
    }

    private fun http(methode: String, adresse: String, body: String?): String {
        if (schluessel.isBlank()) throw ApiFehler(0, "Kein API-Schlüssel eingetragen.")
        val c = URI(adresse).toURL().openConnection() as HttpURLConnection
        try {
            c.requestMethod = methode
            c.connectTimeout = 20_000
            c.readTimeout = 90_000
            c.setRequestProperty("x-api-key", schluessel)
            c.setRequestProperty("anthropic-version", "2023-06-01")
            c.setRequestProperty("content-type", "application/json")
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val strom = if (code in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)
            val text = BufferedReader(InputStreamReader(strom, Charsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) {
                val meldung = try {
                    Json.parseObj(text).obj("error")?.str("message") ?: text
                } catch (e: Exception) {
                    text
                }
                throw ApiFehler(code, "Claude-API Fehler $code: ${meldung.take(300)}")
            }
            return text
        } finally {
            c.disconnect()
        }
    }

    companion object {
        const val BASIS = "https://api.anthropic.com"
    }
}
