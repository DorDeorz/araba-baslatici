package com.dordeorz.arababaslatici.ekran

import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * Tek seferlik bağlantı denemesi: bağlanır, el sıkışır, Android Auto'nun ilk şifreli mesajını
 * (servis keşfi isteği) bekler ve kapatır. Ayarlar ekranındaki "Bağlantıyı dene" için.
 */
class AapBaglanti(private val context: Context) {

    /** Bağlantı denemesinin sonucu ve ölçülen süreler (ms). */
    data class Sonuc(
        val tamam: Boolean,
        val mesaj: String,
        val tcpMs: Long = -1,
        val surumMs: Long = -1,
        val tlsMs: Long = -1,
        val ilkMesajMs: Long = -1,
    )

    /** Bloklar; ana iş parçacığında çağırma. */
    fun dene(): Sonuc {
        val t0 = SystemClock.elapsedRealtime()
        val t = AapTasima(context)
        try {
            t.baglan()
            val ilk = t.al()
            val ilkMs = SystemClock.elapsedRealtime() - t0
            Log.i(TAG, "İlk şifreli mesaj: kanal=${ilk.kanal} tip=${ilk.tip} ${ilk.uzunluk} B ($ilkMs ms)")
            val ad = if (ilk.kanal == 0 && ilk.tip == Cerceve.SERVIS_KESFI_ISTEGI) "servis keşfi isteği" else "tip ${ilk.tip}"
            return Sonuc(true, "Bağlandı: AAP ${t.surum}, ${t.protokol}, ilk mesaj $ad",
                t.tcpMs, t.surumMs, t.tlsMs, ilkMs)
        } catch (e: Exception) {
            Log.w(TAG, "Bağlantı denemesi başarısız", e)
            return Sonuc(false, "Hata: ${e.javaClass.simpleName}: ${e.message}", t.tcpMs, t.surumMs, t.tlsMs)
        } finally {
            t.kapat()
        }
    }

    companion object {
        const val TAG = AapTasima.TAG
    }
}
