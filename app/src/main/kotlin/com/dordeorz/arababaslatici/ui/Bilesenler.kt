package com.dordeorz.arababaslatici.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Ayar satırları: Kronometre'deki SectionTitle / SwitchRow / ClickRow kalıbı.

@Composable
fun BolumBasligi(metin: String) {
    Text(
        text = metin,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Metinler(baslik: String, aciklama: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(baslik, style = MaterialTheme.typography.bodyLarge)
        if (aciklama != null) {
            Text(aciklama, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AnahtarSatiri(baslik: String, aciklama: String?, acik: Boolean, degisti: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = acik, onValueChange = degisti, role = Role.Switch)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Metinler(baslik, aciklama, Modifier.weight(1f))
        Switch(checked = acik, onCheckedChange = null)
    }
}

@Composable
fun TiklaSatiri(baslik: String, aciklama: String?, sag: String? = null, tikla: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tikla != null) Modifier.clickable(onClick = tikla) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Metinler(baslik, aciklama, Modifier.weight(1f))
        if (sag != null) {
            Text(sag, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Seçili değeri açıklamada gösterir; dokununca radyo düğmeli pencere açılır. */
@Composable
fun SecimSatiri(baslik: String, secenekler: List<String>, secili: Int, degisti: (Int) -> Unit) {
    var acik by remember { mutableStateOf(false) }
    val i = secili.coerceIn(0, secenekler.lastIndex)
    TiklaSatiri(baslik, secenekler[i]) { acik = true }
    if (acik) {
        AlertDialog(
            onDismissRequest = { acik = false },
            title = { Text(baslik) },
            text = {
                Column(Modifier.selectableGroup()) {
                    secenekler.forEachIndexed { j, s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = j == i, role = Role.RadioButton, onClick = {
                                    degisti(j)
                                    acik = false
                                })
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            RadioButton(selected = j == i, onClick = null)
                            Text(s, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { acik = false }) { Text("Kapat") } },
        )
    }
}

/** Radyo düğmeleri satır satır (tema seçimi gibi kısa listeler için). */
@Composable
fun RadyoGrubu(secenekler: List<String>, secili: Int, degisti: (Int) -> Unit) {
    Column(Modifier.selectableGroup()) {
        secenekler.forEachIndexed { i, s ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = i == secili, role = Role.RadioButton, onClick = { degisti(i) })
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RadioButton(selected = i == secili, onClick = null)
                Text(s, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun KaydiriciSatiri(
    baslik: String,
    aciklama: String?,
    deger: Int,
    aralik: IntRange,
    yaz: (Int) -> String,
    degisti: (Int) -> Unit,
) {
    var v by remember(deger) { mutableFloatStateOf(deger.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Metinler(baslik, aciklama, Modifier.weight(1f))
            Text(
                yaz(v.roundToInt()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Slider(
            value = v,
            onValueChange = { v = it },
            valueRange = aralik.first.toFloat()..aralik.last.toFloat(),
            onValueChangeFinished = { degisti(v.roundToInt()) },
        )
    }
}
