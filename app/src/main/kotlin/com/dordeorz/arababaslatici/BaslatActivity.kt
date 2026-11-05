package com.dordeorz.arababaslatici

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import kotlin.concurrent.thread

/**
 * Bildirimdeki "Başlat": önce PhoneAuto'nun erişilebilirlik servisini gerekirse onarır,
 * sonra PhoneAuto'yu Android Auto'yu başlatan ekranında açar. Görünmez bir ara ekrandır.
 */
class BaslatActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Bildirim.iptal(this)
        OturumIzleyici.yenidenBagla(this)
        thread {
            Erisilebilirlik.kendiServisiniAc(this)
            val zatenCalisiyor = Erisilebilirlik.calisiyor(this)
            val tamam = zatenCalisiyor || Erisilebilirlik.onar(this)
            runOnUiThread {
                if (!zatenCalisiyor) {
                    val mesaj = if (tamam) "PhoneAuto servisi yeniden açıldı"
                    else "PhoneAuto servisi açılamadı; Erişilebilirlik'ten aç"
                    Toast.makeText(this, mesaj, Toast.LENGTH_LONG).show()
                }
                startActivity(Bildirim.phoneAutoIntent())
                finish()
            }
        }
    }
}
