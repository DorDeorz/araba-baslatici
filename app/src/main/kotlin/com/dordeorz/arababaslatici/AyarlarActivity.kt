package com.dordeorz.arababaslatici

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.dordeorz.arababaslatici.ekran.AapBaglanti
import java.util.Locale
import kotlin.concurrent.thread

/** Ayarlar: görüntü, çentik, çıkış düğmesi, araba cihazları ve geliştirici araçları. */
class AyarlarActivity : Activity() {
    private lateinit var ui: Arayuz

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        ui = Arayuz(this)
        ciz()
    }

    private fun ciz() {
        val kok = ui.sayfa()
        ui.buyukBaslik(kok, "Ayarlar", "Değişiklikler bir sonraki açılışta geçerli olur.")

        // ---- Görüntü ----
        ui.bolum(kok, "Görüntü")
        val g = ui.kart(kok)
        secenek(g, "Çözünürlük", listOf("1080p (daha net)", "720p (daha akıcı)"),
            if (Ayarlar.yuksekCozunurluk(this)) 0 else 1) { Ayarlar.yuksekCozunurluk(this, it == 0) }
        ui.ayrac(g)
        kaydirici(g, "Yoğunluk (DPI)", "Küçük değer: daha çok şey sığar. Büyük: simgeler iri.",
            160, 400, Ayarlar.dpi(this), { "$it" }) { Ayarlar.dpi(this, it) }

        // ---- Çentik ----
        ui.bolum(kok, "Kamera çentiği")
        val c = ui.kart(kok)
        val centik = Ayarlar.centik(this)
        lateinit var elle: LinearLayout
        secenek(c, "Çentik payı", listOf("Otomatik", "Elle", "Kapalı"),
            when { centik < 0 -> 0; centik > 0 -> 1; else -> 2 }) { i ->
            Ayarlar.centik(this, when (i) { 0 -> -1; 1 -> 90; else -> 0 })
            elle.visibility = if (i == 1) android.view.View.VISIBLE else android.view.View.GONE
        }
        elle = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(elle)
        kaydirici(elle, "Pay genişliği (piksel)", "Görüntü tam ekran kalır; yalnızca simgeler ve saat içeri alınır.",
            20, 250, if (centik > 0) centik else 90, { "$it px" }) { Ayarlar.centik(this, it) }
        elle.visibility = if (centik > 0) android.view.View.VISIBLE else android.view.View.GONE

        // ---- Çıkış ----
        ui.bolum(kok, "Çıkış")
        val x = ui.kart(kok)
        secenek(x, "Çıkış (✕) düğmesi", listOf("Sol üst", "Sol alt", "Sağ üst", "Gizli"),
            Ayarlar.cikisYeri(this)) { Ayarlar.cikisYeri(this, it) }
        ui.ayrac(x)
        anahtar(x, "Çıkınca sunucuyu da kapat", "Android Auto'nun ana birim sunucusu çıkışta durdurulur.",
            Ayarlar.cikinciSunucuyuKapat(this)) { Ayarlar.cikinciSunucuyuKapat(this, it) }

        // ---- Araba ----
        ui.bolum(kok, "Araba olarak sayılacak Bluetooth cihazları")
        cihazlar(ui.kart(kok))

        // ---- Geliştirici ----
        ui.bolum(kok, "Geliştirici")
        val d = ui.kart(kok)
        anahtar(d, "Performans bilgisi", "Ekranın köşesinde fps ve ilk görüntü süresi.",
            Ayarlar.bilgiGoster(this)) { Ayarlar.bilgiGoster(this, it) }
        ui.ayrac(d)
        val sonuc = ui.metin("", 13f, ui.soluk)
        ui.satir(d, "Bağlantıyı dene", "Sunucu açıkken el sıkışmayı ölçer (sonra sunucuyu yeniden başlatman gerekir).", "›") {
            sonuc.text = "Bağlanıyor…"
            thread {
                val s = AapBaglanti(this).dene()
                runOnUiThread {
                    sonuc.text = (if (s.tamam) "✓ " else "✗ ") + s.mesaj +
                        if (s.tamam) "\nTCP ${s.tcpMs} · sürüm ${s.surumMs} · TLS ${s.tlsMs} · ilk mesaj ${s.ilkMesajMs} ms" else ""
                }
            }
        }
        d.addView(sonuc)
    }

    /** Tek seçimli satır: dokundukça seçenekler arasında döner. */
    private fun secenek(ust: ViewGroup, baslik: String, secenekler: List<String>, secili: Int, degisti: (Int) -> Unit) {
        var i = secili.coerceIn(0, secenekler.lastIndex)
        lateinit var satir: LinearLayout
        satir = ui.satir(ust, baslik, null, secenekler[i] + "  ›", ui.vurgu) {
            i = (i + 1) % secenekler.size
            degisti(i)
            ((satir.getChildAt(1)) as TextView).text = secenekler[i] + "  ›"
        }
    }

    private fun anahtar(ust: ViewGroup, baslik: String, aciklama: String, acik: Boolean, degisti: (Boolean) -> Unit) {
        val s = ui.satir(ust, baslik, aciklama)
        s.addView(Switch(this).apply {
            isChecked = acik
            setOnCheckedChangeListener { _, v -> degisti(v) }
        })
    }

    private fun kaydirici(ust: ViewGroup, baslik: String, aciklama: String, en: Int, enCok: Int, deger: Int,
                          yaz: (Int) -> String, degisti: (Int) -> Unit) {
        val s = ui.satir(ust, baslik, aciklama, yaz(deger), ui.vurgu)
        val etiket = s.getChildAt(1) as TextView
        ust.addView(SeekBar(this).apply {
            min = en; max = enCok; progress = deger
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar, v: Int, k: Boolean) { etiket.text = yaz(v); if (k) degisti(v) }
                override fun onStartTrackingTouch(b: SeekBar) {}
                override fun onStopTrackingTouch(b: SeekBar) {}
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(8) })
    }

    @SuppressLint("MissingPermission") // izin aşağıda kontrol ediliyor
    private fun cihazlar(k: LinearLayout) {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ui.satir(k, "Bluetooth izni gerekli", "Ana ekrandan izin ver.")
            return
        }
        val eslesmis = getSystemService(BluetoothManager::class.java).adapter?.bondedDevices.orEmpty()
        val secili = Ayarlar.seciliCihazlar(this)
        // İlk açılışta adında "car"/"araba" geçen cihazları önceden seç.
        if (!Ayarlar.ilkKurulumYapildi(this)) {
            eslesmis.filter { d ->
                val ad = d.name.orEmpty().lowercase(Locale.ROOT)
                listOf("car", "araba", "araç").any { it in ad }
            }.forEach { secili += it.address }
            Ayarlar.kaydet(this, secili)
        }
        if (eslesmis.isEmpty()) { ui.satir(k, "Eşleşmiş Bluetooth cihazı yok"); return }
        for (d in eslesmis) {
            val adres = d.address
            k.addView(CheckBox(this).apply {
                text = d.name ?: "Adsız cihaz"
                setTextColor(ui.yazi)
                textSize = 16f
                isChecked = adres in secili
                setPadding(ui.dp(8), ui.dp(12), 0, ui.dp(12))
                setOnCheckedChangeListener { _, isaretli ->
                    val s = Ayarlar.seciliCihazlar(this@AyarlarActivity)
                    if (isaretli) s += adres else s -= adres
                    Ayarlar.kaydet(this@AyarlarActivity, s)
                }
            })
        }
    }
}
