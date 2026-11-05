package com.dordeorz.arababaslatici

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * PhoneAuto sunucuyu başlatmak için Android Auto ayarlarını açıyor ama kapatmıyor; ayarlar
 * arkada kalıyor ve PhoneAuto'daki çarpıya basınca o ekrana düşülüyor. Bu servis, PhoneAuto'nun
 * son ekranı Android Auto ekranıyken oturum açıkken ya da yeni bitmişken Android Auto ayarları
 * öne gelirse "geri"ye basıp onu kapatır.
 *
 * Sunucuyu başlatma sırasında karışmaz: o sırada ortada açık ya da yeni bitmiş bir oturum yoktur.
 */
class AyarKapatici : AccessibilityService() {

    /** PhoneAuto'da en son öne gelen ekranın sınıfı. */
    private var sonPhoneAutoEkrani: String? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val paket = e.packageName?.toString() ?: return
        val sinif = e.className?.toString() ?: return
        // Diyaloglar, bildirim paneli, klavye vb. "ekran" değildir.
        if (sinif.startsWith("android.") || paket == "com.android.systemui" ||
            "inputmethod" in paket
        ) return

        if (paket == Bildirim.PHONEAUTO) sonPhoneAutoEkrani = sinif
        if (paket != OturumIzleyici.GEARHEAD || !sinif.endsWith("SettingsActivity")) return

        // Sunucu başlatılırken PhoneAuto'nun son ekranı genelde rehberdir ve oturum yoktur.
        val phoneAutodanGeldi = sonPhoneAutoEkrani == Bildirim.PHONEAUTO_EKRAN
        val bitistenBeri = System.currentTimeMillis() - oturumBitis
        val oturumYeniBitti = oturumAcik || bitistenBeri < SURE_MS
        Log.d(TAG, "Android Auto ayarları öne geldi: phoneAutodanGeldi=$phoneAutodanGeldi " +
            "oturumAcik=$oturumAcik bitisOnce=${bitistenBeri}ms")
        if (phoneAutodanGeldi && oturumYeniBitti) {
            Log.i(TAG, "PhoneAuto kapandı, arkada kalan Android Auto ayarları kapatılıyor")
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 300)
        }
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = OturumIzleyici.TAG

        /** Oturum bittikten sonra bu süre içinde öne gelen ayarlar kapatılır. */
        private const val SURE_MS = 5_000L

        /** Oturum açık mı / en son ne zaman bitti (OturumIzleyici günceller). */
        @Volatile var oturumAcik = false
        @Volatile var oturumBitis = 0L
    }
}
