package de.lernapp.ihk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.lernapp.ihk.AppZustand

val Blau = Color(0xFF1B4F72)
val Gruen = Color(0xFF1E7B4A)
val Gelb = Color(0xFFB7791F)
val Rot = Color(0xFFB03A2E)
val Grau = Color(0xFF5D6D7E)
val HellBlau = Color(0xFFEAF2F8)
val HellGruen = Color(0xFFE9F7EF)
val HellGelb = Color(0xFFFEF5E7)
val HellRot = Color(0xFFFDEDEC)

@Composable
fun LernTheme(inhalt: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blau,
            secondary = Gruen,
            background = Color(0xFFF7F9FB),
            surface = Color.White,
        ),
        content = inhalt,
    )
}

/** Seite mit Kopfzeile und scrollbarem Inhalt. */
@Composable
fun Seite(titel: String, zurueck: (() -> Unit)?, inhalt: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Blau).padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (zurueck != null) {
                TextButton(onClick = zurueck) { Text("‹ Zurück", color = Color.White) }
            } else {
                Spacer(Modifier.padding(start = 8.dp))
            }
            Text(titel, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            inhalt()
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
fun Kasten(farbe: Color = Color.White, inhalt: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = farbe),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { inhalt() }
    }
}

@Composable
fun Ueberschrift(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
fun Klein(text: String, farbe: Color = Grau) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = farbe)
}

@Composable
fun Absatz(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun Hinweis(text: String, farbe: Color = HellBlau) {
    Kasten(farbe) { Text(text, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
fun Haupt(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

@Composable
fun Neben(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

@Composable
fun BoxScope.MeldungsLeiste(z: AppZustand) {
    val m = z.meldung ?: return
    Card(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
    ) {
        Row(modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(m, color = Color.White, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { z.meldung = null }) { Text("OK", color = Color(0xFF90CAF9)) }
        }
    }
}

fun farbeFuer(prozent: Double): Color = when {
    prozent >= 92 -> Gruen
    prozent >= 67 -> Blau
    prozent >= 50 -> Gelb
    else -> Rot
}
