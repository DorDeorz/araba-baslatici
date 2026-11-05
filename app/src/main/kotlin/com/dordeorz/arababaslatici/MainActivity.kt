package com.dordeorz.arababaslatici

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dordeorz.arababaslatici.ekran.AapBaglanti
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/** Durum ekranı ve hangi araba Bluetooth'unda bildirim gösterileceğini seçme. */
class MainActivity : Activity() {
    private lateinit var kok: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        kok = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(kok)
        })
    }

    override fun onResume() {
        super.onResume()
        OturumIzleyici.yenidenBagla(this)
        if (!Erisilebilirlik.kendiServisiCalisiyor(this)) {
            thread { if (Erisilebilirlik.kendiServisiniAc(this)) runOnUiThread(::ciz) }
        }
        if (!izinli(Manifest.permission.BLUETOOTH_CONNECT) || !izinli(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        ciz()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        ciz()
    }

    private fun izinli(izin: String) = checkSelfPermission(izin) == PackageManager.PERMISSION_GRANTED

    private fun ciz() {
        kok.removeAllViews()
        baslik("Araba Başlatıcı")
        yazi("Telefon seçtiğin araba Bluetooth'una bağlanınca \"Android Auto başlatılsın mı?\" " +
            "bildirimi gelir. \"Başlat\"a basınca PhoneAuto açılır ve Android Auto kendiliğinden başlar.")

        if (!izinli(Manifest.permission.BLUETOOTH_CONNECT)) {
            yazi("⚠ Yakındaki cihazlar (Bluetooth) izni gerekli. Uygulama ayarlarından izin ver.")
            return
        }
        if (!izinli(Manifest.permission.POST_NOTIFICATIONS)) {
            yazi("⚠ Bildirim izni kapalı, bildirim gösterilemez.")
        }
        try {
            packageManager.getPackageInfo(Bildirim.PHONEAUTO, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            yazi("⚠ PhoneAuto yüklü değil.")
        }

        baslik("PhoneAuto servisi")
        val calisiyor = Erisilebilirlik.calisiyor(this)
        yazi(if (calisiyor) "✓ Çalışıyor" else "✗ Çalışmıyor (PhoneAuto kapatılmış olabilir)")
        yazi(if (Erisilebilirlik.yetkiVar(this))
            "✓ Otomatik onarma açık: Başlat'a basınca gerekirse servis kendiliğinden yeniden açılır."
        else "⚠ Otomatik onarma izni yok. Bilgisayardan bir kez şu komutu çalıştır:\n" +
            "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
        if (!calisiyor && Erisilebilirlik.yetkiVar(this)) {
            dugme("Şimdi onar") { v ->
                v.isEnabled = false
                thread {
                    Erisilebilirlik.onar(this)
                    runOnUiThread(::ciz)
                }
            }
        }
        yazi(if (Erisilebilirlik.kendiServisiCalisiyor(this))
            "✓ PhoneAuto kapanınca arkada kalan Android Auto ayarları kapatılır."
        else "✗ Ayarları kapatma servisi çalışmıyor.")

        baslik("Otomatik kapatma")
        if (OturumIzleyici.izinVar(this)) {
            yazi("✓ Açık: Android Auto oturumu bitince (${OturumIzleyici.BEKLEME_MS / 1000} sn sonra) " +
                "ya da araba Bluetooth'u kopunca ana birim sunucusu kapatılır.")
        } else {
            yazi("⚠ Bildirim erişimi kapalı, sunucu otomatik kapatılamaz.")
            dugme("Bildirim erişimini aç") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        OturumIzleyici.bilesen(this).flattenToString()))
            }
        }

        cihazListesi()

        dugme("Bildirimi şimdi dene", ustBosluk = dp(24)) { Bildirim.goster(this, null) }

        baslik("Kendi ekranımız (geliştirme)")
        yazi("Android Auto'nun ana birim sunucusuna bağlanıp el sıkışmayı dener. Önce sunucuyu "
            + "başlat; PhoneAuto ya da Open Headunit o sırada bağlı olmasın.")
        val sonuc = TextView(this)
        dugme("Bağlantıyı dene") { b ->
            b.isEnabled = false
            sonuc.text = "Bağlanıyor…"
            thread {
                val s = AapBaglanti(this).dene()
                runOnUiThread {
                    b.isEnabled = true
                    sonuc.text = (if (s.tamam) "✓ " else "✗ ") + s.mesaj +
                        if (s.tamam) "\nTCP ${s.tcpMs} ms · sürüm ${s.surumMs} ms · TLS ${s.tlsMs} ms · ilk mesaj ${s.ilkMesajMs} ms" else ""
                }
            }
        }
        kok.addView(sonuc)
    }

    // Yalnızca ciz()'den, BLUETOOTH_CONNECT izni kontrol edildikten sonra çağrılır.
    @SuppressLint("MissingPermission")
    private fun cihazListesi() {
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

        baslik("Araba olarak sayılacak cihazlar")
        if (eslesmis.isEmpty()) {
            yazi("Eşleşmiş Bluetooth cihazı yok.")
            return
        }
        for (d in eslesmis) {
            val adres = d.address
            kok.addView(CheckBox(this).apply {
                text = "${d.name ?: "Adsız cihaz"}\n$adres"
                isChecked = adres in secili
                setPadding(0, dp(8), 0, dp(8))
                setOnCheckedChangeListener { _, isaretli ->
                    val s = Ayarlar.seciliCihazlar(this@MainActivity)
                    if (isaretli) s += adres else s -= adres
                    Ayarlar.kaydet(this@MainActivity, s)
                }
            })
        }
    }

    private fun baslik(s: String) = kok.addView(TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(16), 0, dp(8))
    })

    private fun yazi(s: String) = kok.addView(TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(0, dp(4), 0, dp(4))
    })

    private fun dugme(s: String, ustBosluk: Int = 0, tikla: (Button) -> Unit) {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = ustBosluk }
        kok.addView(Button(this).apply {
            text = s
            setOnClickListener { tikla(this) }
        }, lp)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
