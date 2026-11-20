package de.lernapp.ihk.ki

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Lokale KI über LiteRT-LM (Google AI Edge), offline auf dem Handy.
 * Modelle im Format .litertlm (z. B. Gemma 4 E4B). Kostenlos, keine Daten verlassen das Gerät.
 */
class LokaleKi(private val context: Context) {

    @Volatile
    private var engine: Engine? = null
    private val mutex = Mutex()

    @Volatile
    var modellName: String = ""
        private set

    @Volatile
    var backendName: String = ""
        private set

    val bereit: Boolean get() = engine != null

    /** Lädt das Modell. Versucht zuerst die GPU, bei Fehler die CPU. Dauert je nach Modell 5–30 Sekunden. */
    suspend fun laden(pfad: String, gpu: Boolean): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            schliessenIntern()
            val datei = File(pfad)
            if (!datei.exists()) throw IllegalStateException("Modelldatei fehlt: ${datei.name}")
            var letzterFehler: Throwable? = null
            val versuche = if (gpu) listOf("GPU", "CPU") else listOf("CPU")
            for (art in versuche) {
                try {
                    val cfg = EngineConfig(
                        modelPath = pfad,
                        backend = if (art == "GPU") Backend.GPU() else Backend.CPU(),
                        cacheDir = context.cacheDir.path,
                    )
                    val e = Engine(cfg)
                    e.initialize()
                    engine = e
                    modellName = datei.name
                    backendName = art
                    return@withLock "${datei.name} läuft auf $art"
                } catch (t: Throwable) {
                    letzterFehler = t
                }
            }
            throw IllegalStateException("Modell ließ sich nicht laden: ${letzterFehler?.message ?: "unbekannter Fehler"}")
        }
    }

    suspend fun schliessen() = withContext(Dispatchers.IO) { mutex.withLock { schliessenIntern() } }

    private fun schliessenIntern() {
        try {
            engine?.close()
        } catch (t: Throwable) {
            // ignorieren
        }
        engine = null
        modellName = ""
        backendName = ""
    }

    /**
     * Eine Frage ohne Verlauf: neues Gespräch mit Systemanweisung, eine Nachricht, Antwort als Text.
     * beiTeil bekommt den bisherigen Text (für Live-Anzeige); er wird auf einem Hintergrund-Thread aufgerufen.
     */
    suspend fun frage(
        system: String,
        nachricht: String,
        temperatur: Double = 0.2,
        beiTeil: ((String) -> Unit)? = null,
    ): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val e = engine ?: throw IllegalStateException("Die lokale KI ist nicht geladen.")
            val gespraech = e.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(Content.Text(system)),
                    samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = temperatur),
                ),
            )
            try {
                suspendCancellableCoroutine<String> { cont ->
                    val sb = StringBuilder()
                    gespraech.sendMessageAsync(
                        Contents.of(Content.Text(nachricht)),
                        object : MessageCallback {
                            override fun onMessage(message: Message) {
                                sb.append(message.toString())
                                beiTeil?.invoke(sb.toString())
                            }

                            override fun onDone() {
                                if (cont.isActive) cont.resume(sb.toString())
                            }

                            override fun onError(throwable: Throwable) {
                                if (!cont.isActive) return
                                if (throwable is CancellationException) cont.resume(sb.toString()) else cont.resumeWithException(throwable)
                            }
                        },
                    )
                }
            } finally {
                try {
                    gespraech.close()
                } catch (t: Throwable) {
                    // ignorieren
                }
            }
        }
    }
}
