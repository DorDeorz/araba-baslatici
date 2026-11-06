package com.dordeorz.arababaslatici

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.dordeorz.arababaslatici.ekran.EkranActivity
import kotlin.concurrent.thread

/**
 * Tek tuşla başlatma: ana birim sunucusu kapalıysa açar, sonra kendi ekranımızı getirir.
 * Bildirimdeki "Başlat" ve ana ekrandaki büyük düğme bunu çağırır.
 */
object Baslatici {
    @Volatile private var calisiyor = false

    fun baslat(c: Context) {
        val app = c.applicationContext
        if (calisiyor) return
        calisiyor = true
        Bildirim.iptal(app)
        OturumIzleyici.yenidenBagla(app)
        thread {
            if (OturumIzleyici.sunucuAcik() == true) {
                Log.i(TAG, "Sunucu zaten açık, ekran açılıyor")
                ana { ekraniAc(app) }
                return@thread
            }
            val servis = Erisilebilirlik.ac(app)
            ana {
                if (!servis || !SunucuBaslatici.baslat(app) { tamam ->
                        if (!tamam) Toast.makeText(app, "Ana birim sunucusu başlatılamadı. " +
                            "Android Auto'da geliştirici modu açık mı?", Toast.LENGTH_LONG).show()
                        // Başarısız da olsa ekranı aç: sunucu elle açılmışsa bağlanır.
                        ekraniAc(app)
                    }) {
                    calisiyor = false
                    Toast.makeText(app, "Erişilebilirlik servisi çalışmıyor; Araba Başlatıcı'yı aç.",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun ekraniAc(c: Context) {
        calisiyor = false
        c.startActivity(Intent(c, EkranActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    private fun ana(is_: () -> Unit) { Handler(Looper.getMainLooper()).post(is_) }

    private const val TAG = OturumIzleyici.TAG
}
