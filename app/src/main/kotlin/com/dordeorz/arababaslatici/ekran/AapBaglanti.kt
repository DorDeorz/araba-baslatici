package com.dordeorz.arababaslatici.ekran

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Android Auto'nun ana birim sunucusuna (127.0.0.1:5277) bağlanan AAP istemcisi.
 *
 * Sıra: TCP → sürüm isteği/cevabı (şifresiz) → TLS el sıkışması (SSL_EL_SIKISMA çerçeveleri içinde)
 * → KIMLIK_TAMAM → bundan sonra her şey şifreli. İlk şifreli mesaj Android Auto'nun
 * SERVIS_KESFI_ISTEGI'dir; ona cevap (kanal listesi) sonraki aşamada eklenecek.
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

    /**
     * Bağlanır, el sıkışır ve Android Auto'dan ilk şifreli mesajı bekler, sonra bağlantıyı kapatır.
     * Bloklar; ana iş parçacığında çağırma.
     */
    fun dene(): Sonuc {
        val t0 = SystemClock.elapsedRealtime()
        fun gecen() = SystemClock.elapsedRealtime() - t0
        val soket = Socket()
        try {
            soket.tcpNoDelay = true
            try {
                soket.connect(InetSocketAddress("127.0.0.1", PORT), 2000)
            } catch (e: IOException) {
                return Sonuc(false, "Sunucu kapalı (127.0.0.1:$PORT): ${e.message}")
            }
            soket.soTimeout = 5000
            val tcp = gecen()
            Log.i(TAG, "TCP bağlandı: $tcp ms")
            val giris = DataInputStream(soket.getInputStream().buffered(64 * 1024))
            val cikis = BufferedOutputStream(soket.getOutputStream(), 64 * 1024)
            fun yaz(b: ByteArray) { cikis.write(b); cikis.flush() }

            // 1. Sürüm: 1.2 istenir, telefon kendi seçtiği minor'ı döndürür.
            yaz(Cerceve.duz(0, Cerceve.DUZ_TAM, Cerceve.SURUM_ISTEGI, byteArrayOf(0, 1, 0, 2)))
            var cevap = Cerceve.oku(giris)
            while (cevap.sifreli || cevap.kanal != 0 || cevap.tip != Cerceve.SURUM_CEVABI) {
                Log.w(TAG, "Sürüm cevabı beklenirken kanal=${cevap.kanal} tip=${cevap.tip} geldi, atlanıyor")
                cevap = Cerceve.oku(giris)
            }
            val g = cevap.govde
            val major = Cerceve.uKisa(g, 2)
            val minor = Cerceve.uKisa(g, 4)
            val durum = Cerceve.uKisa(g, 6).toShort().toInt()
            val surum = gecen()
            Log.i(TAG, "Sürüm cevabı: $major.$minor durum=$durum ($surum ms)")
            if (durum == -1) return Sonuc(false, "Android Auto sürümü reddetti ($major.$minor)", tcp, surum)

            // 2. TLS el sıkışması.
            val tls = AapTls(context)
            tls.elSikis(
                gonder = { yaz(Cerceve.duz(0, Cerceve.DUZ_TAM, Cerceve.SSL_EL_SIKISMA, it)) },
                al = {
                    var c = Cerceve.oku(giris)
                    while (c.sifreli || c.kanal != 0 || c.tip != Cerceve.SSL_EL_SIKISMA) {
                        Log.w(TAG, "El sıkışmada beklenmeyen çerçeve kanal=${c.kanal} tip=${c.tip}, atlanıyor")
                        c = Cerceve.oku(giris)
                    }
                    c.govde.copyOfRange(2, c.govde.size)
                },
            )
            // KIMLIK_TAMAM: status = OK (protobuf: alan 1 = 0).
            yaz(Cerceve.duz(0, Cerceve.DUZ_TAM, Cerceve.KIMLIK_TAMAM, byteArrayOf(8, 0)))
            val tlsMs = gecen()
            Log.i(TAG, "TLS tamam (${tls.protokol}): $tlsMs ms")

            // 3. İlk şifreli mesaj: Android Auto servis keşfi ister.
            val ilk = Cerceve.oku(giris)
            if (!ilk.sifreli) return Sonuc(false, "Şifreli mesaj bekleniyordu", tcp, surum, tlsMs)
            val duz = tls.coz(ilk.govde)
            val tip = if (duz.size >= 2) Cerceve.uKisa(duz, 0) else -1
            val ilkMs = gecen()
            Log.i(TAG, "İlk şifreli mesaj: kanal=${ilk.kanal} tip=$tip ${duz.size} B ($ilkMs ms)")
            val ad = if (ilk.kanal == 0 && tip == Cerceve.SERVIS_KESFI_ISTEGI) "servis keşfi isteği" else "tip $tip"
            return Sonuc(true, "Bağlandı: AAP $major.$minor, ${tls.protokol}, ilk mesaj $ad",
                tcp, surum, tlsMs, ilkMs)
        } catch (e: Exception) {
            Log.w(TAG, "Bağlantı denemesi başarısız", e)
            return Sonuc(false, "Hata: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            try { soket.close() } catch (_: IOException) {}
        }
    }

    companion object {
        const val TAG = "AracEkran"
        const val PORT = 5277
    }
}
