package com.dordeorz.arababaslatici.ekran

import android.view.KeyEvent

/** Kanal numaraları: servis keşfinde bildirdiğimiz servis kimlikleri. */
object Kanal {
    const val KONTROL = 0
    const val SENSOR = 1
    const val GORUNTU = 2
    const val GIRIS = 3
    const val SES_SISTEM = 5
    const val MIKROFON = 7
    const val MEDYA_DURUMU = 9
    const val NAVIGASYON = 10
}

/**
 * Android Auto'ya bildirdiğimiz görüntü düzeni. Görüntü [genislik]×[yukseklik] kodlanır; AA
 * arayüzünü kenar boşlukları dışında kalan ortadaki alana çizer. Boşlukları ekranın en-boy
 * oranına göre seçeriz, böylece ortadaki alan ekranı tam doldurur.
 */
data class Geometri(
    val cozunurlukTipi: Int,
    val genislik: Int,
    val yukseklik: Int,
    val boslukG: Int,
    val boslukY: Int,
    val dpi: Int,
) {
    val arayuzG get() = genislik - boslukG
    val arayuzY get() = yukseklik - boslukY

    companion object {
        /** Yatay ekran (ekranG ≥ ekranY varsayılır) için 1920×1080 düzeni. */
        fun hesapla(ekranG: Int, ekranY: Int, dpi: Int, yuksek: Boolean = true): Geometri {
            val (tip, g, y) = if (yuksek) Triple(3, 1920, 1080) else Triple(2, 1280, 720)
            val ekranOran = ekranG.toDouble() / ekranY
            val oran = g.toDouble() / y
            var bg = 0
            var by = 0
            // Ekran 16:9'dan genişse üstten-alttan, darsa yanlardan boşluk.
            if (ekranOran > oran) by = (y - g / ekranOran).toInt() and 1.inv()
            else bg = (g - y * ekranOran).toInt() and 1.inv()
            return Geometri(tip, g, y, bg, by, dpi)
        }
    }
}

/** Servis keşfi cevabı (ServiceDiscoveryResponse) — hangi servisleri sunduğumuz. */
object Kesif {
    private const val PCM = 1
    private const val H264_BP = 3
    private const val SES_SISTEM = 2
    private const val SENSOR_GECE = 10
    private const val SENSOR_SURUS = 13

    private val tuslar = listOf(
        KeyEvent.KEYCODE_SOFT_LEFT, KeyEvent.KEYCODE_SOFT_RIGHT, KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_CALL,
        KeyEvent.KEYCODE_MUSIC, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_HOME,
    )

    fun cevap(g: Geometri): ByteArray = Pb.yaz {
        // Sensörler: sürüş durumu (kısıtsız) ve gece modu.
        mesaj(1) {
            varint(1, Kanal.SENSOR)
            mesaj(2) {
                mesaj(1) { varint(1, SENSOR_SURUS) }
                mesaj(1) { varint(1, SENSOR_GECE) }
            }
        }
        // Görüntü: H.264, 60 fps.
        mesaj(1) {
            varint(1, Kanal.GORUNTU)
            mesaj(3) {
                varint(1, H264_BP)
                varint(2, 0) // ses türü: yok
                mesaj(4) {
                    varint(1, g.cozunurlukTipi)
                    varint(2, 1) // 60 fps
                    varint(3, g.boslukG)
                    varint(4, g.boslukY)
                    varint(5, g.dpi)
                    varint(8, 10000) // kare piksel
                    varint(10, H264_BP)
                }
                bool(5, true)
            }
        }
        // Dokunmatik ekran ve tuşlar.
        mesaj(1) {
            varint(1, Kanal.GIRIS)
            mesaj(4) {
                tuslar.forEach { varint(1, it) }
                mesaj(2) { varint(1, g.genislik); varint(2, g.yukseklik) }
            }
        }
        // Sistem sesi (16 kHz mono 16 bit). Kendi kendine yansıtmada medya/konuşma sesi telefondan çıkar.
        mesaj(1) {
            varint(1, Kanal.SES_SISTEM)
            mesaj(3) {
                varint(1, PCM)
                varint(2, SES_SISTEM)
                mesaj(3) { varint(1, 16000); varint(2, 16); varint(3, 1) }
            }
        }
        // Mikrofon: bildirilmezse Android Auto oturumu bırakıyor.
        mesaj(1) {
            varint(1, Kanal.MIKROFON)
            mesaj(5) {
                varint(1, PCM)
                mesaj(2) { varint(1, 16000); varint(2, 16); varint(3, 1) }
            }
        }
        mesaj(1) { varint(1, Kanal.MEDYA_DURUMU); mesaj(9) {} }
        mesaj(1) {
            varint(1, Kanal.NAVIGASYON)
            mesaj(8) { varint(1, 1000); varint(2, 2) }
        }
        metin(2, MARKA)
        metin(3, MODEL)
        metin(4, "2026")
        metin(5, ARAC_KIMLIGI)
        varint(6, 0) // sürücü solda
        metin(7, MARKA)
        metin(8, MODEL)
        metin(9, "1")
        metin(10, "1.0")
        bool(11, false)
        bool(12, false)
        varint(13, 0)
        metin(14, "Araba Başlatıcı")
        mesaj(17) {
            metin(1, MARKA)
            metin(2, MODEL)
            metin(3, "2026")
            metin(4, ARAC_KIMLIGI)
            metin(5, MARKA)
            metin(6, MODEL)
            metin(7, "1")
            metin(8, "1.0")
            varint(9, 1) // araba
        }
    }

    private const val MARKA = "DorDeorz"
    private const val MODEL = "Araba Baslatici"
    private const val ARAC_KIMLIGI = "araba-baslatici-1"
}
