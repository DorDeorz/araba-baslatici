package com.dordeorz.arababaslatici.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dordeorz.arababaslatici.Ayarlar
import com.dordeorz.arababaslatici.Baslatici
import com.dordeorz.arababaslatici.Erisilebilirlik
import com.dordeorz.arababaslatici.OturumIzleyici
import com.dordeorz.arababaslatici.R

/** Hazırlık listesindeki bir adım: tamam değilse dokununca düzeltme ekranı açılır. */
class HazirlikAdimi(val baslik: String, val tamam: Boolean, val aciklama: String, val duzelt: () -> Unit)

private fun izinli(c: Context, izin: String) = c.checkSelfPermission(izin) == PackageManager.PERMISSION_GRANTED

private fun uygulamaAyarlari(c: Context) = c.startActivity(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", c.packageName, null)))

fun hazirlik(c: Context, ayarlariAc: () -> Unit): List<HazirlikAdimi> {
    val servis = Erisilebilirlik.calisiyor(c)
    val dinleyici = OturumIzleyici.izinVar(c)
    val mikrofon = izinli(c, Manifest.permission.RECORD_AUDIO)
    val bildirim = izinli(c, Manifest.permission.POST_NOTIFICATIONS) && izinli(c, Manifest.permission.BLUETOOTH_CONNECT)
    val arabalar = Ayarlar.seciliCihazlar(c).size
    return listOf(
        HazirlikAdimi(
            "Sunucuyu otomatik başlatma", servis,
            when {
                servis -> "Android Auto'nun ana birim sunucusu tek tuşla açılır."
                Erisilebilirlik.yetkiVar(c) -> "Erişilebilirlik servisi açılıyor…"
                else -> "Erişilebilirlik'ten \"Araba Başlatıcı\"yı aç."
            },
        ) { c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        HazirlikAdimi(
            "Otomatik kapatma", dinleyici,
            if (dinleyici) "Oturum bitince ya da araba ayrılınca sunucu kapanır." else "Bildirim erişimi gerekli.",
        ) {
            c.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    OturumIzleyici.bilesen(c).flattenToString()))
        },
        HazirlikAdimi("Mikrofon", mikrofon, if (mikrofon) "Sesli komutlar için." else "İzin gerekli.") {
            uygulamaAyarlari(c)
        },
        HazirlikAdimi(
            "Arabaya bağlanınca sor", bildirim && arabalar > 0,
            when {
                !bildirim -> "Bluetooth ve bildirim izni gerekli."
                arabalar == 0 -> "Ayarlar'dan arabanın Bluetooth'unu seç."
                else -> "$arabalar cihaz seçili: bağlanınca \"Başlatılsın mı?\" bildirimi gelir."
            },
        ) { if (!bildirim) uygulamaAyarlari(c) else ayarlariAc() },
    )
}

@Composable
fun HazirlikKarti(adimlar: List<HazirlikAdimi>) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        adimlar.forEachIndexed { i, a ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (a.tamam) Modifier else Modifier.clickable(onClick = a.duzelt))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    painterResource(if (a.tamam) R.drawable.ic_tamam else R.drawable.ic_uyari),
                    contentDescription = if (a.tamam) "Hazır" else "Eksik",
                    tint = if (a.tamam) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Column(Modifier.weight(1f)) {
                    Text(a.baslik, style = MaterialTheme.typography.bodyLarge)
                    Text(a.aciklama, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Sayfa iskeleti: solda büyük başlık, sağda isteğe bağlı simge düğmesi, altta kaydırılan içerik. */
@Composable
fun Sayfa(
    baslik: String,
    geri: (() -> Unit)? = null,
    ayarlar: (() -> Unit)? = null,
    icerik: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = if (geri != null) 4.dp else 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (geri != null) {
                IconButton(onClick = geri) { Icon(painterResource(R.drawable.ic_geri), contentDescription = "Geri") }
            }
            Text(baslik, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (ayarlar != null) {
                IconButton(onClick = ayarlar) { Icon(painterResource(R.drawable.ic_ayarlar), contentDescription = "Ayarlar") }
            }
        }
        icerik()
    }
}

@Composable
fun AnaEkran(yenile: Int, ayarlariAc: () -> Unit, pilYardimiKapat: () -> Unit) {
    val c = LocalContext.current
    // yenile değişince (ekrana dönülünce, izin verilince) durumlar yeniden okunur.
    val adimlar = remember(yenile) { hazirlik(c, ayarlariAc) }
    val eksik = adimlar.count { !it.tamam }
    val marka = Marka.bu()
    val pilKarti = remember(yenile) { !Ayarlar.pilYardimiGizli(c) && !PilYardimi.muaf(c) }
    Sayfa("Araba Başlatıcı", ayarlar = ayarlariAc) {
        // Adımsayar'daki halka gibi: büyük yuvarlak başlat düğmesi.
        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(244.dp).border(12.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    onClick = { Baslatici.baslat(c) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(196.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(painterResource(R.drawable.ic_araba), contentDescription = null, modifier = Modifier.size(56.dp))
                        Text("Başlat", style = MaterialTheme.typography.headlineMedium)
                        Text("Android Auto", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Text(
            if (eksik == 0) "Her şey hazır" else "$eksik adım eksik",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Arabaya bağlanınca Android Auto'yu bu telefonda, tam ekran açar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp),
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (pilKarti) PilYardimiKarti(marka, kapat = pilYardimiKapat)
            BolumBasligiSade("Hazırlık")
            HazirlikKarti(adimlar)
        }
    }
}

@Composable
private fun BolumBasligiSade(metin: String) {
    Text(metin, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
}

/** İlk açılış: ne yaptığını anlatır, izinleri tek listede verdirir. */
@Composable
fun Karsilama(yenile: Int, ayarlariAc: () -> Unit, devam: () -> Unit) {
    val c = LocalContext.current
    val adimlar = remember(yenile) { hazirlik(c, ayarlariAc) }
    Column(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f)) {
    Sayfa("Hoş geldin") {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Araba Başlatıcı, arabana bağlandığında Android Auto'yu bu telefonda tam ekran açar. " +
                    "Tek dokunuşla hem Android Auto'nun sunucusunu başlatır hem ekranı getirir; X ile her şeyi kapatır.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                "Başlamadan önce birkaç izin gerekiyor. Eksik olana dokun:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            HazirlikKarti(adimlar)
            if (remember(yenile) { !PilYardimi.muaf(c) }) PilYardimiKarti(Marka.bu(), kapat = null)
            Spacer(Modifier.height(16.dp))
        }
    }
    }
    // Düğme hep görünsün: kaydırılan içeriğin altında sabit.
    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 2.dp) {
        Button(
            onClick = devam,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp)
                .height(56.dp),
        ) {
            Text(if (adimlar.all { it.tamam }) "Başla" else "Şimdilik geç", style = MaterialTheme.typography.titleMedium)
        }
    }
    }
}
