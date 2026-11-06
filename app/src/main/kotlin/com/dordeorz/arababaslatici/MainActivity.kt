package com.dordeorz.arababaslatici

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import kotlin.concurrent.thread

/** Ana ekran: tek tuşla başlatma ve hazırlık durumu. Ayrıntılar [AyarlarActivity]'de. */
class MainActivity : Activity() {
    private lateinit var ui: Arayuz

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        ui = Arayuz(this)
    }

    override fun onResume() {
        super.onResume()
        OturumIzleyici.yenidenBagla(this)
        if (!Erisilebilirlik.calisiyor(this)) {
            thread { if (Erisilebilirlik.ac(this)) runOnUiThread(::ciz) }
        }
        val eksik = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.RECORD_AUDIO).filterNot(::izinli)
        if (eksik.isNotEmpty()) requestPermissions(eksik.toTypedArray(), 1)
        ciz()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) = ciz()

    private fun izinli(izin: String) = checkSelfPermission(izin) == PackageManager.PERMISSION_GRANTED

    private fun ciz() {
        val kok = ui.sayfa()
        ui.buyukBaslik(kok, "Araba Başlatıcı",
            "Arabaya bağlanınca Android Auto'yu bu telefonda, kendi ekranımızda açar.")

        ui.anaDugme(kok, "Android Auto'yu başlat") { Baslatici.baslat(this) }

        ui.bolum(kok, "Hazırlık")
        val k = ui.kart(kok)
        var ilk = true
        fun satir(baslik: String, tamam: Boolean, aciklama: String, duzelt: (() -> Unit)?) {
            if (!ilk) ui.ayrac(k)
            ilk = false
            ui.satir(k, baslik, aciklama, if (tamam) "✓" else "!", if (tamam) ui.iyi else ui.uyari,
                if (tamam) null else duzelt)
        }

        val servis = Erisilebilirlik.calisiyor(this)
        satir("Sunucuyu otomatik başlatma", servis,
            if (servis) "Android Auto'nun ana birim sunucusu tek tuşla açılır."
            else if (Erisilebilirlik.yetkiVar(this)) "Erişilebilirlik servisi açılıyor…"
            else "Erişilebilirlik'ten \"Araba Başlatıcı\"yı aç (dokun).") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val dinleyici = OturumIzleyici.izinVar(this)
        satir("Otomatik kapatma", dinleyici,
            if (dinleyici) "Oturum bitince ya da araba ayrılınca sunucu kapanır."
            else "Bildirim erişimi gerekli (dokun).") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    OturumIzleyici.bilesen(this).flattenToString()))
        }

        val mikrofon = izinli(Manifest.permission.RECORD_AUDIO)
        satir("Mikrofon", mikrofon, if (mikrofon) "Sesli komutlar için." else "İzin gerekli (dokun).",
            ::uygulamaAyarlari)

        val bildirim = izinli(Manifest.permission.POST_NOTIFICATIONS) && izinli(Manifest.permission.BLUETOOTH_CONNECT)
        val arabalar = Ayarlar.seciliCihazlar(this).size
        satir("Arabaya bağlanınca sor", bildirim && arabalar > 0,
            when {
                !bildirim -> "Bluetooth ve bildirim izni gerekli (dokun)."
                arabalar == 0 -> "Ayarlar'dan arabanın Bluetooth'unu seç (dokun)."
                else -> "$arabalar cihaz seçili: bağlanınca \"Başlatılsın mı?\" bildirimi gelir."
            }) {
            if (!bildirim) uygulamaAyarlari() else startActivity(Intent(this, AyarlarActivity::class.java))
        }

        ui.dugme(kok, "Ayarlar") { startActivity(Intent(this, AyarlarActivity::class.java)) }
    }

    private fun uygulamaAyarlari() = startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)))
}
