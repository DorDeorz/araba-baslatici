package com.dordeorz.arababaslatici.ekran

import android.content.Context
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Açık kalan Android Auto oturumu: bağlanır, kontrol mesajlarına cevap verir, görüntüyü
 * [GoruntuCozucu]'ya, sistem sesini AudioTrack'e verir.
 */
class AapOturum(
    private val context: Context,
    private val geometri: Geometri,
    private val dinleyici: Dinleyici,
) {
    interface Dinleyici {
        fun durum(metin: String)
        /** Android Auto kendi "çık" düğmesine basıldığını bildirdi ya da oturum bitti. */
        fun bitti(neden: String, kullaniciCikti: Boolean)
    }

    @Volatile private var tasima = AapTasima(context)
    @Volatile private var acik = true
    private var okuyucu: Thread? = null

    private val oturumNo = IntArray(16)
    /** Android Auto'nun gönderdiği son SPS/PPS; yüzey yenilenince yeni çözücüye tekrar verilir. */
    @Volatile private var sonAyar: ByteArray? = null
    @Volatile private var goruntuHazir = false // MEDIA_SETUP geldi
    @Volatile private var yuzey: Surface? = null
    @Volatile var cozucu: GoruntuCozucu? = null; private set
    private var ses: AudioTrack? = null

    private var t0 = 0L
    @Volatile var baglantiMs = -1L; private set
    @Volatile var ilkVeriMs = -1L; private set

    fun baslat() {
        t0 = SystemClock.elapsedRealtime()
        okuyucu = thread(name = "AapOkuyucu", priority = Thread.MAX_PRIORITY) { calis() }
    }

    private fun calis() {
        var neden = "Bağlantı kapandı"
        try {
            dinleyici.durum("Bağlanıyor…")
            // Sunucu yeni başlatıldıysa birkaç saniye içinde açılır: kapalıysa tekrar dene.
            val son = SystemClock.elapsedRealtime() + 8_000
            while (true) {
                try { tasima.baglan(); break } catch (e: java.io.IOException) {
                    if (!acik || SystemClock.elapsedRealtime() > son || "Sunucu kapalı" !in (e.message ?: "")) throw e
                    dinleyici.durum("Ana birim sunucusu bekleniyor…")
                    tasima = AapTasima(context)
                    SystemClock.sleep(300)
                }
            }
            baglantiMs = SystemClock.elapsedRealtime() - t0
            bagli = true
            dinleyici.durum("Bağlandı (${baglantiMs} ms), görüntü bekleniyor…")
            tasima.okumaZamanAsimi(10_000)
            while (acik) isle(tasima.al())
        } catch (e: SocketTimeoutException) {
            neden = "Android Auto 10 sn cevap vermedi"
        } catch (e: Exception) {
            if (acik) {
                Log.w(TAG, "Oturum hatası", e)
                neden = "Hata: ${e.javaClass.simpleName}: ${e.message}"
            }
        } finally {
            val beklenmedik = acik
            temizle()
            if (beklenmedik) dinleyici.bitti(neden, kullaniciCikti = false)
        }
    }

    // ---- Gelen mesajlar ----

    private fun isle(m: AapTasima.Mesaj) {
        if (m.tip == 7 && (m.kontrol || m.kanal == 0)) { kanalAc(m); return }
        if (m.kanal == Kanal.KONTROL || m.kontrol) { kontrol(m); return }
        when (m.kanal) {
            Kanal.GORUNTU -> goruntu(m)
            Kanal.SES_SISTEM -> sesMesaji(m)
            Kanal.MIKROFON -> mikrofon(m)
            Kanal.SENSOR -> sensor(m)
            Kanal.GIRIS -> if (m.tip == 0x8002) tasima.gonder(m.kanal, 0x8003, Pb.yaz { varint(1, 0) })
            else -> Log.d(TAG, "Kanal ${m.kanal} tip ${m.tip} (${m.uzunluk} B) atlandı")
        }
    }

    private fun kontrol(m: AapTasima.Mesaj) {
        when (m.tip) {
            5 -> { // servis keşfi isteği
                Log.i(TAG, "Servis keşfi isteği; cevap: $geometri")
                tasima.gonder(0, 6, Kesif.cevap(geometri))
            }
            11 -> tasima.gonder(0, 12, Pb.yaz { varint(1, m.pb().sayi(1)) }) // ping
            13 -> tasima.gonder(0, 14, Pb.yaz { varint(1, 2) }) // navigasyon odağı: yansıtılan
            15 -> { // güle güle isteği
                tasima.gonder(0, 16, Pb.yaz {})
                acik = false
                dinleyici.bitti("Android Auto oturumu kapattı", kullaniciCikti = true)
            }
            16 -> acik = false
            18 -> { // ses odağı isteği: hep ver
                val durum = when (m.pb().int(1)) {
                    1 -> 1    // GAIN
                    2 -> 2    // GAIN_TRANSIENT
                    3 -> 7    // MAY_DUCK → GAIN_TRANSIENT_GUIDANCE_ONLY
                    4 -> 3    // RELEASE → LOSS
                    else -> 1
                }
                tasima.gonder(0, 19, Pb.yaz { varint(1, durum); bool(2, false) })
            }
            9, 17 -> Unit // kanal kapandı, sesli komut oturumu
            else -> Log.d(TAG, "Kontrol tip ${m.tip} kanal ${m.kanal} atlandı")
        }
    }

    private fun kanalAc(m: AapTasima.Mesaj) {
        Log.i(TAG, "Kanal açılıyor: ${m.kanal}")
        tasima.gonder(m.kanal, 8, Pb.yaz { varint(1, 0) })
        if (m.kanal == Kanal.SENSOR) surusDurumu()
    }

    private fun sensor(m: AapTasima.Mesaj) {
        if (m.tip != 0x8001) return
        tasima.gonder(m.kanal, 0x8002, Pb.yaz { varint(1, 0) })
        when (m.pb().int(1)) {
            13 -> surusDurumu()
            10 -> geceModu()
        }
    }

    private fun surusDurumu() =
        tasima.gonder(Kanal.SENSOR, 0x8003, Pb.yaz { mesaj(13) { varint(1, 0) } })

    fun geceModu() {
        val gece = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        tasima.gonder(Kanal.SENSOR, 0x8003, Pb.yaz { mesaj(10) { bool(1, gece) } })
    }

    private fun medyaKur(m: AapTasima.Mesaj, enFazlaOnaysiz: Int) {
        tasima.gonder(m.kanal, 0x8003, Pb.yaz {
            varint(1, 2) // READY
            varint(2, enFazlaOnaysiz)
            varint(3, 0)
        })
    }

    private fun goruntu(m: AapTasima.Mesaj) {
        when (m.tip) {
            0, 1 -> {
                val c = cozucu
                if (c == null) {
                    if (m.tip == 1) sonAyar = m.veri.copyOfRange(m.bas, m.veri.size)
                    if (m.tip == 0) onay(m.kanal)
                    return
                }
                if (ilkVeriMs < 0) ilkVeriMs = SystemClock.elapsedRealtime() - t0
                // DATA: [8 bayt zaman damgası][H.264]; CODEC_CONFIG: doğrudan SPS/PPS.
                val bas = if (m.tip == 0) m.bas + 8 else m.bas
                if (m.tip == 1) sonAyar = m.veri.copyOfRange(bas, m.veri.size)
                c.besle(m.veri, bas, m.veri.size - bas, ayar = m.tip == 1)
            }
            0x8000 -> {
                medyaKur(m, 16)
                goruntuHazir = true
                odakVer()
                arayuzAyari()
            }
            0x8001 -> oturumNo[m.kanal] = m.pb().int(1)
            0x8002 -> Unit
            0x800A -> Log.i(TAG, "Arayüz ayarı cevabı: ${m.pb()}")
            0x8007 -> { // görüntü odağı isteği; NATIVE (2) = AA'daki çık düğmesi
                if (m.pb().int(2) == 2) {
                    // Android Auto'daki "Çık": PhoneAuto'daki X gibi her şeyi kapat.
                    acik = false
                    dinleyici.bitti("Android Auto'dan çıkıldı", kullaniciCikti = true)
                    try { tasima.gonder(0, 15, Pb.yaz { varint(1, 1) }) } catch (_: Exception) {}
                    tasima.kapat()
                } else odakVer()
            }
            else -> Log.d(TAG, "Görüntü tip ${m.tip} atlandı")
        }
    }

    private fun sesMesaji(m: AapTasima.Mesaj) {
        when (m.tip) {
            0 -> {
                sesCalar().write(m.veri, m.bas + 8, m.veri.size - m.bas - 8, AudioTrack.WRITE_NON_BLOCKING)
                onay(m.kanal)
            }
            0x8000 -> {
                medyaKur(m, 16)
                tasima.gonder(0, 19, Pb.yaz { varint(1, 1); bool(2, true) })
            }
            0x8001 -> { oturumNo[m.kanal] = m.pb().int(1); sesCalar().play() }
            0x8002 -> ses?.pause()
        }
    }

    private val mikrofon = Mikrofon { veri ->
        if (acik) try { tasima.gonder(Kanal.MIKROFON, 0, veri) } catch (_: Exception) {}
    }
    private var mikrofonOturumu = 0

    private fun mikrofon(m: AapTasima.Mesaj) {
        when (m.tip) {
            0x8005 -> { // mikrofon isteği: aç (alan 1), en fazla onaysız (alan 4)
                val p = m.pb()
                if (p.bool(1)) {
                    val izin = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    val tamam = izin && mikrofon.ac(if (p.varMi(4)) p.int(4) else 2)
                    if (!izin) Log.w(TAG, "Mikrofon izni yok")
                    mikrofonOturumu++
                    tasima.gonder(m.kanal, 0x8006, Pb.yaz {
                        varint(1, if (tamam) 0 else -7); varint(2, mikrofonOturumu)
                    })
                } else {
                    mikrofon.kapat()
                    tasima.gonder(m.kanal, 0x8006, Pb.yaz { varint(1, 0); varint(2, mikrofonOturumu) })
                }
            }
            0x8004 -> mikrofon.onay(m.pb().int(2, 1))
        }
    }

    private fun sesCalar(): AudioTrack = ses ?: AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(16000)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(16000 * 2)
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .build().also { ses = it }

    private fun onay(kanal: Int) {
        tasima.gonder(kanal, 0x8004, Pb.yaz { varint(1, oturumNo[kanal]); varint(2, 1) })
    }

    // ---- Ekran tarafı ----

    /** Yüzey hazır (ya da gitti). Çözücü yüzeye bağlı; yüzey değişince yeniden kurulur. */
    @Synchronized
    fun yuzeyAyarla(s: Surface?) {
        cozucu?.kapat()
        cozucu = null
        yuzey = s
        if (s != null) {
            cozucu = GoruntuCozucu(s, geometri.genislik, geometri.yukseklik) { veri ->
                if (veri) onay(Kanal.GORUNTU)
            }.also { c -> sonAyar?.let { c.besle(it, 0, it.size, ayar = true) } }
            // Yeni çözücü anahtar kare bekler; istenmemiş odak bildirimi Android Auto'dan onu ister.
            yazici.execute { odakVer() }
        }
    }

    /** Çentik payı (arayüz pikseli): Android Auto içeriği bu kadar içeri alır, görüntü tam ekran kalır. */
    @Volatile private var payS = 0
    @Volatile private var payS2 = 0

    fun centikAyarla(sol: Int, sag: Int) {
        if (sol == payS && sag == payS2) return
        payS = sol; payS2 = sag
        yazici.execute { arayuzAyari() }
    }

    /** UPDATE_UI_CONFIG: içerik kenar boşlukları (content_insets). Kenar boşluğu (margins) değişmez. */
    private fun arayuzAyari() {
        if (!goruntuHazir || !acik) return
        val sol = payS; val sag = payS2
        // Pay yoksa hiç gönderme (servis keşfindeki düzen geçerli kalır).
        if (sol == 0 && sag == 0) return
        // Kenar boşlukları (margins) servis keşfindekiyle aynı gönderilmeli: eksik kalırsa
        // Android Auto onları 0 sayıp arayüzü bütün kareye yayar, üst/alt kırpılır.
        val mY = geometri.boslukY / 2; val mG = geometri.boslukG / 2
        Log.i(TAG, "Arayüz ayarı: içerik kenar boşluğu sol=$sol sağ=$sag")
        try {
            tasima.gonder(Kanal.GORUNTU, 0x8009, Pb.yaz {
                mesaj(1) {
                    mesaj(1) { varint(1, mY); varint(2, geometri.boslukY - mY); varint(3, mG); varint(4, geometri.boslukG - mG) }
                    mesaj(2) { varint(1, 0); varint(2, 0); varint(3, sol); varint(4, sag) }
                    mesaj(3) { varint(1, 0); varint(2, 0); varint(3, sol); varint(4, sag) }
                }
            })
        } catch (e: Exception) { Log.w(TAG, "Arayüz ayarı gönderilemedi", e) }
    }

    private fun odakVer() {
        if (!goruntuHazir || yuzey == null || !acik) return
        // Yeni yüzey anahtar kare ister; istenmemiş odak bildirimi bunu sağlar.
        tasima.gonder(Kanal.GORUNTU, 0x8008, Pb.yaz { varint(1, 1); bool(2, true) })
    }

    /**
     * Dokunma. [eylem] AAP değeri (0 bas, 1 bırak, 2 sürükle, 5/6 ikinci parmak), [noktalar]
     * (kimlik, x, y) arayüz koordinatlarında.
     */
    fun dokun(eylem: Int, indeks: Int, noktalar: List<Triple<Int, Int, Int>>) {
        if (!acik) return
        val zaman = SystemClock.elapsedRealtimeNanos()
        val veri = Pb.yaz {
            varint(1, zaman)
            mesaj(3) {
                noktalar.forEach { (id, x, y) -> mesaj(1) { varint(1, x); varint(2, y); varint(3, id) } }
                varint(2, indeks)
                varint(3, eylem)
            }
        }
        // Ana iş parçacığında soket yazılamaz (NetworkOnMainThreadException): yazıcıya devret.
        yazici.execute { try { tasima.gonder(Kanal.GIRIS, 0x8001, veri) } catch (e: Exception) { Log.w(TAG, "Dokunma gönderilemedi", e) } }
    }

    private val yazici = Executors.newSingleThreadExecutor { r -> Thread(r, "AapDokunma").apply { priority = Thread.MAX_PRIORITY } }

    /** Kullanıcı çıktı: Android Auto'ya haber ver ve kapat. */
    fun kapat() {
        if (!acik) { tasima.kapat(); return }
        acik = false
        thread {
            try { tasima.gonder(0, 15, Pb.yaz { varint(1, 1) }) } catch (_: Exception) {}
            // Android Auto'nun oturumu kendi tarafında kapatmasına zaman tanı.
            SystemClock.sleep(500)
            tasima.kapat()
        }
    }

    private fun temizle() {
        if (bagli) sonKapanis = SystemClock.elapsedRealtime()
        bagli = false
        tasima.kapat()
        yazici.shutdownNow()
        mikrofon.kapat()
        synchronized(this) { cozucu?.kapat(); cozucu = null }
        ses?.release()
        ses = null
    }

    companion object {
        const val TAG = AapTasima.TAG

        /** Kendi ekranımız şu an Android Auto'ya bağlı mı (sunucu bu sırada kapatılmamalı). */
        @Volatile var bagli = false; private set

        /** Son oturumun kapandığı an (elapsedRealtime). */
        @Volatile var sonKapanis = 0L; private set
    }
}
