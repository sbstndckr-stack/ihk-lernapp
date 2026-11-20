package de.lernapp.ihk.daten

import android.content.Context
import java.io.File

/** Einfache Dateiablage im privaten App-Speicher (nur diese App kann lesen). Atomisch: erst .tmp, dann umbenennen. */
class Speicher(private val ctx: Context) {

    private val ordner: File get() = ctx.filesDir

    fun lies(name: String): String? {
        val f = File(ordner, name)
        return if (f.exists()) f.readText(Charsets.UTF_8) else null
    }

    @Synchronized
    fun schreibe(name: String, text: String) {
        val ziel = File(ordner, name)
        val tmp = File(ordner, "$name.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(ziel)) {
            ziel.delete()
            tmp.renameTo(ziel)
        }
    }

    fun loesche(name: String) {
        File(ordner, name).delete()
    }

    fun asset(name: String): String = ctx.assets.open(name).use { it.readBytes().toString(Charsets.UTF_8) }
}
