package de.lernapp.ihk.ki

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Modelle herunterladen (Hugging Face, direkt aufs Handy) oder vom Handy-Speicher übernehmen. */
class ModellVerwaltung(private val ctx: Context) {

    data class Angebot(val name: String, val datei: String, val url: String, val groesse: String, val hinweis: String)

    data class Laden(val id: Long, val datei: String, val geladen: Long, val gesamt: Long, val status: Int) {
        val fertig: Boolean get() = status == DownloadManager.STATUS_SUCCESSFUL
        val fehler: Boolean get() = status == DownloadManager.STATUS_FAILED
        val anteil: Float get() = if (gesamt > 0) (geladen.toFloat() / gesamt).coerceIn(0f, 1f) else 0f
    }

    val angebote = listOf(
        Angebot(
            "Gemma 4 E4B (empfohlen)", "gemma-4-E4B-it.litertlm",
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true",
            "3,7 GB", "genauer beim Bewerten; läuft auf GPU und CPU",
        ),
        Angebot(
            "Gemma 4 E4B, GPU-Fassung", "gemma-4-E4B-it-gpu.litertlm",
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it-gpu.litertlm?download=true",
            "3,3 GB", "für die GPU optimiert; zum Vergleich im Modelltest",
        ),
        Angebot(
            "Gemma 4 E2B (Reserve)", "gemma-4-E2B-it.litertlm",
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
            "2,6 GB", "schneller und sparsamer, aber ungenauer",
        ),
    )

    val ordner: File
        get() {
            val f = ctx.getExternalFilesDir("modelle") ?: File(ctx.filesDir, "modelle")
            f.mkdirs()
            return f
        }

    private val prefs get() = ctx.getSharedPreferences("downloads", Context.MODE_PRIVATE)

    fun vorhandene(): List<File> =
        ordner.listFiles()?.filter { it.isFile && it.name.endsWith(".litertlm") }?.sortedBy { it.name } ?: emptyList()

    fun herunterladen(a: Angebot): Long {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val teil = a.datei + ".part"
        File(ordner, teil).delete()
        val req = DownloadManager.Request(Uri.parse(a.url))
            .setTitle("KI-Modell: ${a.name}")
            .setDescription(a.groesse)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(ctx, "modelle", teil)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val id = dm.enqueue(req)
        prefs.edit().putString(id.toString(), a.datei).apply()
        return id
    }

    /** Stand aller gemerkten Downloads. Fertige werden umbenannt und vergessen. */
    fun pruefen(): List<Laden> {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val liste = ArrayList<Laden>()
        for ((k, v) in prefs.all) {
            val id = k.toLongOrNull() ?: continue
            val datei = v as? String ?: continue
            val c = dm.query(DownloadManager.Query().setFilterById(id))
            var eintrag: Laden? = null
            c?.use {
                if (it.moveToFirst()) {
                    val geladen = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val gesamt = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    eintrag = Laden(id, datei, geladen, gesamt, status)
                }
            }
            val e = eintrag
            if (e == null) {
                prefs.edit().remove(k).apply()
                continue
            }
            if (e.fertig) {
                val teil = File(ordner, "$datei.part")
                val ziel = File(ordner, datei)
                if (teil.exists()) {
                    ziel.delete()
                    teil.renameTo(ziel)
                }
                prefs.edit().remove(k).apply()
            } else if (e.fehler) {
                prefs.edit().remove(k).apply()
            }
            liste.add(e)
        }
        return liste
    }

    fun abbrechen(id: Long) {
        ctx.getSystemService(DownloadManager::class.java).remove(id)
        prefs.edit().remove(id.toString()).apply()
    }

    fun loeschen(f: File) {
        f.delete()
    }

    /** Übernimmt eine .litertlm-Datei, die schon auf dem Handy liegt (z. B. per USB in „Download“ kopiert). */
    suspend fun uebernehmen(uri: Uri, fortschritt: (Long) -> Unit): File = withContext(Dispatchers.IO) {
        var name = "modell.litertlm"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0) ?: name
        }
        if (!name.endsWith(".litertlm")) throw IllegalArgumentException("Bitte eine .litertlm-Datei wählen (gewählt: $name).")
        val teil = File(ordner, "$name.part")
        val ziel = File(ordner, name)
        val ein = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Datei lässt sich nicht öffnen.")
        ein.use { input ->
            teil.outputStream().use { out ->
                val puffer = ByteArray(1 shl 20)
                var summe = 0L
                var zuletzt = 0L
                while (true) {
                    val n = input.read(puffer)
                    if (n < 0) break
                    out.write(puffer, 0, n)
                    summe += n
                    if (summe - zuletzt > (64L shl 20)) {
                        zuletzt = summe
                        fortschritt(summe)
                    }
                }
            }
        }
        ziel.delete()
        if (!teil.renameTo(ziel)) throw IllegalStateException("Umbenennen fehlgeschlagen.")
        ziel
    }
}
