package com.dordeorz.arababaslatici.ekran

import java.io.DataInputStream
import java.io.IOException

/**
 * Android Auto protokolünün (AAP) taşıma çerçevesi.
 *
 * Her çerçeve 4 baytlık başlıkla başlar: kanal, bayraklar, gövde uzunluğu (2 bayt, büyük uçlu).
 * Bayraklar: 0x01 ilk parça, 0x02 son parça, 0x04 kontrol mesajı, 0x08 şifreli.
 * İlk parça ama son değilse (0x09) başlıktan sonra 4 baytlık toplam düz metin uzunluğu gelir.
 * Düz metin [tip (2 bayt)][yük] biçimindedir; devam parçalarında tip yoktur.
 *
 * Biçim Open Headunit'ten (AGPL-3.0) öğrenildi: aap/AapMessageFraming.kt, AapMessage.kt.
 */
object Cerceve {
    const val BASLIK = 4

    const val ILK = 0x01
    const val SON = 0x02
    const val KONTROL = 0x04
    const val SIFRELI = 0x08

    /** Tek parçalı, şifresiz mesaj (el sıkışma öncesi). */
    const val DUZ_TAM = ILK or SON
    /** Tek parçalı, şifreli mesaj. */
    const val SIFRELI_TAM = ILK or SON or SIFRELI

    // Kontrol kanalı (0) mesaj tipleri.
    const val SURUM_ISTEGI = 1
    const val SURUM_CEVABI = 2
    const val SSL_EL_SIKISMA = 3
    const val KIMLIK_TAMAM = 4
    const val SERVIS_KESFI_ISTEGI = 5

    /** [kanal][bayrak][uzunluk][tip][veri] — şifresiz mesaj. */
    fun duz(kanal: Int, bayrak: Int, tip: Int, veri: ByteArray): ByteArray {
        val b = ByteArray(BASLIK + 2 + veri.size)
        b[0] = kanal.toByte()
        b[1] = bayrak.toByte()
        kisa(b, 2, veri.size + 2)
        kisa(b, 4, tip)
        veri.copyInto(b, 6)
        return b
    }

    /** Şifreli mesajın dış zarfı: [kanal][bayrak|SIFRELI][TLS uzunluğu] + TLS baytları. */
    fun zarf(kanal: Int, bayrak: Int, tls: ByteArray): ByteArray {
        val b = ByteArray(BASLIK + tls.size)
        b[0] = kanal.toByte()
        b[1] = (bayrak or SIFRELI).toByte()
        kisa(b, 2, tls.size)
        tls.copyInto(b, BASLIK)
        return b
    }

    /** Okunan bir çerçeve. [govde] şifreliyse TLS baytları, değilse [tip][yük]. */
    class Okunan(val kanal: Int, val bayrak: Int, val toplam: Int, val govde: ByteArray) {
        val sifreli get() = bayrak and SIFRELI != 0
        val ilk get() = bayrak and ILK != 0
        val son get() = bayrak and SON != 0
        /** Yalnızca şifresiz çerçevelerde anlamlı. */
        val tip get() = if (govde.size >= 2) uKisa(govde, 0) else -1
    }

    @Throws(IOException::class)
    fun oku(giris: DataInputStream): Okunan {
        val baslik = ByteArray(BASLIK)
        giris.readFully(baslik)
        val kanal = baslik[0].toInt() and 0xFF
        val bayrak = baslik[1].toInt() and 0xFF
        val uzunluk = uKisa(baslik, 2)
        var toplam = 0
        if (bayrak and ILK != 0 && bayrak and SON == 0) toplam = giris.readInt()
        val govde = ByteArray(uzunluk)
        giris.readFully(govde)
        return Okunan(kanal, bayrak, toplam, govde)
    }

    fun kisa(b: ByteArray, i: Int, deger: Int) {
        b[i] = (deger shr 8).toByte()
        b[i + 1] = deger.toByte()
    }

    fun uKisa(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
}
