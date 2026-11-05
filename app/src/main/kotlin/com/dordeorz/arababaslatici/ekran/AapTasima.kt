package com.dordeorz.arababaslatici.ekran

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 5277'ye bağlanır, sürüm + TLS el sıkışmasını yapar, sonra şifreli mesaj gönderip alır.
 * Parçalı mesajları kanal başına birleştirir; [al] her zaman tam bir mesaj döndürür.
 */
class AapTasima(private val context: Context) {

    /** Tam (birleştirilmiş, çözülmüş) mesaj. [veri] tipten sonraki yüktür. */
    class Mesaj(val kanal: Int, val kontrol: Boolean, val tip: Int, val veri: ByteArray, val bas: Int) {
        val uzunluk get() = veri.size - bas
        fun pb(): Pb.Okunan = Pb.oku(veri, bas)
    }

    private val soket = Socket()
    private lateinit var giris: DataInputStream
    private lateinit var cikis: BufferedOutputStream
    private lateinit var tls: AapTls
    private val parcalar = HashMap<Int, ByteArrayOutputStream>()
    private val parcaKontrol = HashMap<Int, Boolean>()

    var tcpMs = -1L; private set
    var surumMs = -1L; private set
    var tlsMs = -1L; private set
    var surum = ""; private set
    val protokol get() = tls.protokol

    @Volatile var kapali = false; private set

    /** Bağlanır ve el sıkışır. Hata olursa [IOException] fırlatır. Bloklar. */
    fun baglan() {
        val t0 = SystemClock.elapsedRealtime()
        fun gecen() = SystemClock.elapsedRealtime() - t0
        soket.tcpNoDelay = true
        soket.receiveBufferSize = 1 shl 20
        try {
            soket.connect(InetSocketAddress("127.0.0.1", PORT), 2000)
        } catch (e: IOException) {
            throw IOException("Sunucu kapalı (127.0.0.1:$PORT): ${e.message}", e)
        }
        soket.soTimeout = 5000
        tcpMs = gecen()
        giris = DataInputStream(soket.getInputStream().buffered(256 * 1024))
        cikis = BufferedOutputStream(soket.getOutputStream(), 64 * 1024)

        // 1. Sürüm: 1.2 istenir, telefon kendi seçtiği minor'ı döndürür.
        yazHam(Cerceve.duz(0, Cerceve.DUZ_TAM, Cerceve.SURUM_ISTEGI, byteArrayOf(0, 1, 0, 2)))
        var cevap = Cerceve.oku(giris)
        while (cevap.sifreli || cevap.kanal != 0 || cevap.tip != Cerceve.SURUM_CEVABI) cevap = Cerceve.oku(giris)
        val g = cevap.govde
        val major = Cerceve.uKisa(g, 2)
        val minor = Cerceve.uKisa(g, 4)
        val durum = Cerceve.uKisa(g, 6).toShort().toInt()
        surum = "$major.$minor"
        surumMs = gecen()
        if (durum == -1) throw IOException("Android Auto sürümü reddetti ($surum)")

        // 2. TLS el sıkışması, SSL_EL_SIKISMA çerçeveleri içinde.
        tls = AapTls(context)
        tls.elSikis(
            gonder = { yazHam(Cerceve.duz(0, Cerceve.DUZ_TAM, Cerceve.SSL_EL_SIKISMA, it)) },
            al = {
                var c = Cerceve.oku(giris)
                while (c.sifreli || c.kanal != 0 || c.tip != Cerceve.SSL_EL_SIKISMA) c = Cerceve.oku(giris)
                c.govde.copyOfRange(2, c.govde.size)
            },
        )
        // KIMLIK_TAMAM: status = OK.
        yazHam(Cerceve.duz(0, Cerceve.DUZ_TAM, Cerceve.KIMLIK_TAMAM, byteArrayOf(8, 0)))
        tlsMs = gecen()
        Log.i(TAG, "Bağlandı: AAP $surum, ${tls.protokol}; TCP $tcpMs, sürüm $surumMs, TLS $tlsMs ms")
    }

    /** Oturum sırasında okuma beklemesi (ping zaten saniyede bir gelir). */
    fun okumaZamanAsimi(ms: Int) { soket.soTimeout = ms }

    /** Bir sonraki tam mesajı döndürür. Bloklar. */
    fun al(): Mesaj {
        while (true) {
            val c = Cerceve.oku(giris)
            val duz = if (c.sifreli) tls.coz(c.govde) else c.govde
            if (c.ilk && c.son) {
                if (duz.size < 2) continue
                return Mesaj(c.kanal, c.bayrak and Cerceve.KONTROL != 0, Cerceve.uKisa(duz, 0), duz, 2)
            }
            if (c.ilk) {
                parcalar[c.kanal] = ByteArrayOutputStream(if (c.toplam > 0) c.toplam else 64 * 1024).also { it.write(duz) }
                parcaKontrol[c.kanal] = c.bayrak and Cerceve.KONTROL != 0
                continue
            }
            val b = parcalar[c.kanal] ?: continue // başı kayıp parça
            b.write(duz)
            if (!c.son) continue
            parcalar.remove(c.kanal)
            val tum = b.toByteArray()
            if (tum.size < 2) continue
            return Mesaj(c.kanal, parcaKontrol.remove(c.kanal) == true, Cerceve.uKisa(tum, 0), tum, 2)
        }
    }

    /**
     * Şifreli mesaj gönderir. 1–26 arası tipler kanal 0 dışında kontrol bayrağıyla gider.
     * Şifreleme ve yazma aynı kilit altında: TLS kayıt sırası telde de korunmalı.
     */
    fun gonder(kanal: Int, tip: Int, veri: ByteArray) {
        if (kapali) return
        val duz = ByteArray(2 + veri.size)
        Cerceve.kisa(duz, 0, tip)
        veri.copyInto(duz, 2)
        var bayrak = Cerceve.DUZ_TAM
        if (kanal != 0 && tip in 1..26) bayrak = bayrak or Cerceve.KONTROL
        synchronized(this) {
            val sifreli = tls.sifrele(duz)
            yazHam(Cerceve.zarf(kanal, bayrak, sifreli))
        }
    }

    fun kapat() {
        kapali = true
        try { soket.close() } catch (_: IOException) {}
    }

    private fun yazHam(b: ByteArray) {
        synchronized(cikis) { cikis.write(b); cikis.flush() }
    }

    companion object {
        const val TAG = "AracEkran"
        const val PORT = 5277
    }
}
