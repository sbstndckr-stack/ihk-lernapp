package de.lernapp.ihk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import de.lernapp.ihk.ui.AbschlussAnsicht
import de.lernapp.ihk.ui.KiAnsicht
import de.lernapp.ihk.ui.LernenAnsicht
import de.lernapp.ihk.ui.LernzieleAnsicht
import de.lernapp.ihk.ui.LernTheme
import de.lernapp.ihk.ui.MeldungsLeiste
import de.lernapp.ihk.ui.QualitaetAnsicht
import de.lernapp.ihk.ui.StartAnsicht
import de.lernapp.ihk.ui.VokabelAnsicht

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val z = (application as LernApp).zustand
        setContent {
            LernTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        BackHandler(enabled = z.ansicht != Ansicht.Start) {
                            z.ansicht = when (z.ansicht) {
                                is Ansicht.Lernen -> Ansicht.Lernziele
                                else -> Ansicht.Start
                            }
                        }
                        when (val a = z.ansicht) {
                            is Ansicht.Start -> StartAnsicht(z)
                            is Ansicht.Lernziele -> LernzieleAnsicht(z)
                            is Ansicht.Lernen -> LernenAnsicht(z, a.lzId)
                            is Ansicht.Abschluss -> AbschlussAnsicht(z)
                            is Ansicht.Vokabeln -> VokabelAnsicht(z)
                            is Ansicht.Ki -> KiAnsicht(z)
                            is Ansicht.Qualitaetskreis -> QualitaetAnsicht(z)
                        }
                        MeldungsLeiste(z)
                    }
                }
            }
        }
    }
}
