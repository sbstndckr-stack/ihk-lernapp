package de.lernapp.ihk

import android.app.Application

/** Hält den App-Zustand über Bildschirmdrehungen hinweg (auch die geladene lokale KI). */
class LernApp : Application() {
    val zustand: AppZustand by lazy { AppZustand(this) }
}
