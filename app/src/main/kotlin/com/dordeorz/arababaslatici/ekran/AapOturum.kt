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
        fun bitti(neden: String)
    }

    private val tasima = AapTasima(context)
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
            tasima.baglan()
            baglantiMs = SystemClock.elapsedRealtime() - t0
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
            if (beklenmedik) dinleyici.bitti(neden)
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
                dinleyici.bitti("Android Auto oturumu kapattı")
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
            }
            0x8001 -> oturumNo[m.kanal] = m.pb().int(1)
            0x8002 -> Unit
            0x8007 -> { // görüntü odağı isteği; NATIVE (2) = AA'daki çık düğmesi
                if (m.pb().int(2) == 2) {
                    acik = false
                    dinleyici.bitti("Android Auto'dan çıkıldı")
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

    private fun mikrofon(m: AapTasima.Mesaj) {
        // Mikrofon bir sonraki adımda; şimdilik isteği reddet ki Android Auto beklemesin.
        if (m.tip == 0x8005 && m.pb().bool(1)) {
            tasima.gonder(m.kanal, 0x8006, Pb.yaz { varint(1, -7); varint(2, 0) })
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
            SystemClock.sleep(150)
            tasima.kapat()
        }
    }

    private fun temizle() {
        tasima.kapat()
        yazici.shutdownNow()
        synchronized(this) { cozucu?.kapat(); cozucu = null }
        ses?.release()
        ses = null
    }

    companion object { const val TAG = AapTasima.TAG }
}
