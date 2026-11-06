package com.dordeorz.arababaslatici.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dordeorz.arababaslatici.Ayarlar
import com.dordeorz.arababaslatici.BuildConfig
import com.dordeorz.arababaslatici.Olaylar
import com.dordeorz.arababaslatici.ekran.AapBaglanti
import java.util.Locale
import kotlin.concurrent.thread

private const val DEPO = "https://github.com/DorDeorz/araba-baslatici"

@Composable
private fun Ayrac() = HorizontalDivider(Modifier.padding(vertical = 8.dp))

/**
 * Ayarlar: Kronometre'deki sıra (Görünüm → uygulamaya özel → arka plan → güncellemeler → hakkında).
 * Ekran ayarları bir sonraki açılışta geçerli olur.
 */
@Composable
fun AyarlarEkrani(
    geri: () -> Unit,
    tema: Int,
    temaDegisti: (Int) -> Unit,
    dinamik: Boolean,
    dinamikDegisti: (Boolean) -> Unit,
) {
    val c = LocalContext.current
    Sayfa("Ayarlar", geri = geri) {
        // ---- Görünüm ----
        BolumBasligi("Tema")
        RadyoGrubu(listOf("Sistem varsayılanı", "Açık", "Koyu"), tema, temaDegisti)
        AnahtarSatiri("Telefonun renklerini kullan",
            "Renkleri duvar kağıdından alır. Kapalıyken yeşil tema kullanılır.", dinamik, dinamikDegisti)
        Ayrac()

        // ---- Android Auto ekranı ----
        BolumBasligi("Android Auto ekranı")
        var yuksek by remember { mutableStateOf(Ayarlar.yuksekCozunurluk(c)) }
        SecimSatiri("Çözünürlük", listOf("1080p (daha net)", "720p (daha akıcı)"), if (yuksek) 0 else 1) {
            yuksek = it == 0; Ayarlar.yuksekCozunurluk(c, yuksek)
        }
        KaydiriciSatiri("Yoğunluk (DPI)", "Küçük: daha çok şey sığar. Büyük: simgeler iri.",
            Ayarlar.dpi(c), 160..400, { "$it" }) { Ayarlar.dpi(c, it) }
        Text(
            "Değişiklikler Android Auto bir sonraki açılışta geçerli olur.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Ayrac()

        // ---- Çentik ----
        BolumBasligi("Kamera çentiği")
        var centik by remember { mutableIntStateOf(Ayarlar.centik(c)) }
        SecimSatiri("Çentik payı", listOf("Otomatik", "Elle", "Kapalı"),
            when { centik < 0 -> 0; centik > 0 -> 1; else -> 2 }) { i ->
            centik = when (i) { 0 -> -1; 1 -> 90; else -> 0 }
            Ayarlar.centik(c, centik)
        }
        if (centik > 0) {
            KaydiriciSatiri("Pay genişliği", "Android Auto görüntüsü çentiğin yanından başlar; şerit siyah kalır.",
                centik, 20..250, { "$it px" }) { centik = it; Ayarlar.centik(c, it) }
        }
        Ayrac()

        // ---- Çıkış ----
        BolumBasligi("Çıkış")
        var yer by remember { mutableIntStateOf(Ayarlar.cikisYeri(c)) }
        SecimSatiri("Çıkış (✕) düğmesi", listOf("Sol üst", "Sol alt", "Sağ üst", "Gizli"), yer) {
            yer = it; Ayarlar.cikisYeri(c, it)
        }
        var sunucuKapat by remember { mutableStateOf(Ayarlar.cikinciSunucuyuKapat(c)) }
        AnahtarSatiri("Çıkınca sunucuyu da kapat", "Android Auto'nun ana birim sunucusu çıkışta durdurulur.",
            sunucuKapat) { sunucuKapat = it; Ayarlar.cikinciSunucuyuKapat(c, it) }
        Ayrac()

        // ---- Araba ----
        BolumBasligi("Araba")
        var baglaninca by remember { mutableIntStateOf(Ayarlar.baglaninca(c)) }
        SecimSatiri("Arabaya bağlanınca",
            listOf("Bildirimle sor", "Doğrudan başlat", "Hiçbir şey yapma"), baglaninca) {
            baglaninca = it; Ayarlar.baglaninca(c, it)
        }
        Text(
            "Araba olarak sayılacak Bluetooth cihazları:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        Cihazlar(c)
        Ayrac()

        // ---- Arka plan ----
        BolumBasligi("Arka planda çalışma")
        var pilAcik by remember { mutableStateOf(false) }
        TiklaSatiri("Pil yardımını göster",
            "Telefon uygulamayı uyutursa arabaya bağlanınca bildirim gelmez. Marka için adımlar.") {
            pilAcik = !pilAcik
            Ayarlar.pilYardimiGizli(c, false)
        }
        if (pilAcik) PilYardimiKarti(Marka.bu(), kapat = { pilAcik = false }, modifier = Modifier.padding(16.dp))
        Ayrac()

        // ---- Geliştirici ----
        BolumBasligi("Geliştirici")
        var bilgi by remember { mutableStateOf(Ayarlar.bilgiGoster(c)) }
        AnahtarSatiri("Performans bilgisi", "Ekranın köşesinde fps ve ilk görüntü süresi.", bilgi) {
            bilgi = it; Ayarlar.bilgiGoster(c, it)
        }
        var sonuc by remember { mutableStateOf<String?>(null) }
        TiklaSatiri("Bağlantıyı dene",
            sonuc ?: "Sunucu açıkken el sıkışmayı ölçer (sonra sunucuyu yeniden başlatman gerekir).") {
            sonuc = "Bağlanıyor…"
            thread {
                val s = AapBaglanti(c).dene()
                sonuc = (if (s.tamam) "✓ " else "✗ ") + s.mesaj +
                    if (s.tamam) "\nTCP ${s.tcpMs} · sürüm ${s.surumMs} · TLS ${s.tlsMs} · ilk mesaj ${s.ilkMesajMs} ms" else ""
            }
        }
        var olaylarAcik by remember { mutableStateOf(false) }
        TiklaSatiri("Son olaylar", "Başlatma, bağlantı, hata ve çıkış kayıtları (en yeni üstte).") {
            olaylarAcik = !olaylarAcik
        }
        if (olaylarAcik) {
            val satirlar = remember { Olaylar.oku(c) }
            Text(
                if (satirlar.isEmpty()) "Henüz kayıt yok." else satirlar.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Ayrac()

        // ---- Hakkında ----
        BolumBasligi("Hakkında")
        TiklaSatiri("GitHub sayfası", "Kaynak kod, derlemeler ve hata bildirimi") {
            c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEPO)))
        }
        TiklaSatiri("Sürüm", null, sag = BuildConfig.VERSION_NAME, tikla = null)
    }
}

@SuppressLint("MissingPermission") // izin hemen aşağıda kontrol ediliyor
@Composable
private fun Cihazlar(c: Context) {
    if (c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        TiklaSatiri("Bluetooth izni gerekli", "Ana ekrandaki Hazırlık listesinden izin ver.", tikla = null)
        return
    }
    val eslesmis = remember {
        c.getSystemService(BluetoothManager::class.java).adapter?.bondedDevices.orEmpty().toList()
    }
    var secili by remember {
        val s = Ayarlar.seciliCihazlar(c)
        // İlk açılışta adında "car"/"araba" geçen cihazları önceden seç.
        if (!Ayarlar.ilkKurulumYapildi(c)) {
            eslesmis.filter { d ->
                val ad = d.name.orEmpty().lowercase(Locale.ROOT)
                listOf("car", "araba", "araç").any { it in ad }
            }.forEach { s += it.address }
            Ayarlar.kaydet(c, s)
        }
        mutableStateOf(s.toSet())
    }
    if (eslesmis.isEmpty()) {
        TiklaSatiri("Eşleşmiş Bluetooth cihazı yok", null, tikla = null)
        return
    }
    for (d in eslesmis) {
        val adres = d.address
        val isaretli = adres in secili
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = isaretli, role = Role.Checkbox, onValueChange = { v ->
                    secili = if (v) secili + adres else secili - adres
                    Ayarlar.kaydet(c, secili)
                })
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = isaretli, onCheckedChange = null)
            Text(d.name ?: "Adsız cihaz", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
