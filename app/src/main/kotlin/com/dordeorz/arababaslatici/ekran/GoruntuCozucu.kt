package com.dordeorz.arababaslatici.ekran

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * H.264 çözücü. Düşük gecikme için: KEY_LOW_LATENCY, gerçek zaman önceliği, gelen her kare
 * hemen beslenir ve çıkan her kare beklemeden ekrana basılır (sunum zamanı beklenmez).
 *
 * [besle] okuma iş parçacığından çağrılır ve beklemez; kareler kendi iş parçacığında işlenir.
 * [bitti] her kare çözücüye verildikten sonra çağrılır (Android Auto'ya onay için).
 */
class GoruntuCozucu(
    private val yuzey: Surface,
    private val genislik: Int,
    private val yukseklik: Int,
    private val bitti: (veri: Boolean) -> Unit,
) {
    private class Kare(val b: ByteArray, val bas: Int, val uzunluk: Int, val ayar: Boolean)

    private val kuyruk = LinkedBlockingQueue<Kare>()
    private val bosGirisler = ArrayBlockingQueue<Int>(64)
    private val cikisIs = HandlerThread("GoruntuCikis", android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY).apply { start() }
    private var codec: MediaCodec? = null
    @Volatile private var calisiyor = true
    private var anahtarBekle = true

    /** Ekrana basılan kare sayısı (fps ölçümü için). */
    val basilan = AtomicInteger()
    @Volatile var ilkKareMs = -1L; private set
    @Volatile var codecAdi = ""; private set

    private val besleyici = Thread({ dongu() }, "GoruntuGiris").apply {
        priority = Thread.MAX_PRIORITY
        start()
    }

    fun besle(b: ByteArray, bas: Int, uzunluk: Int, ayar: Boolean) {
        if (calisiyor) kuyruk.put(Kare(b, bas, uzunluk, ayar)) else bitti(!ayar)
    }

    private fun baslat(): MediaCodec {
        val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codecAdi = c.name
        val f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, genislik, yukseklik).apply {
            setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            setInteger(MediaFormat.KEY_PRIORITY, 0) // gerçek zaman
            setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2 * 1024 * 1024)
        }
        c.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) { bosGirisler.offer(index) }
            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                try {
                    codec.releaseOutputBuffer(index, true)
                    if (basilan.incrementAndGet() == 1) ilkKareMs = SystemClock.elapsedRealtime()
                } catch (_: IllegalStateException) {}
            }
            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) { Log.w(TAG, "Çözücü hatası", e) }
            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                Log.i(TAG, "Görüntü biçimi: $format")
            }
        }, Handler(cikisIs.looper))
        c.configure(f, yuzey, null, 0)
        c.start()
        Log.i(TAG, "Çözücü: ${c.name} ${genislik}x$yukseklik")
        return c
    }

    private fun dongu() {
        try {
            val c = baslat().also { codec = it }
            while (calisiyor) {
                val k = kuyruk.poll(200, TimeUnit.MILLISECONDS) ?: continue
                // Yeni çözücüye ilk anahtar kareden önceki kareler verilirse görüntü bozulur.
                if (!k.ayar && anahtarBekle) {
                    if (!anahtarKareMi(k.b, k.bas, k.uzunluk)) { bitti(true); continue }
                    anahtarBekle = false
                }
                var i: Int? = null
                while (calisiyor && i == null) i = bosGirisler.poll(200, TimeUnit.MILLISECONDS)
                if (i == null) { bitti(!k.ayar); break }
                val tampon = c.getInputBuffer(i)!!
                tampon.clear()
                tampon.put(k.b, k.bas, k.uzunluk)
                val bayrak = if (k.ayar) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
                c.queueInputBuffer(i, 0, k.uzunluk, SystemClock.uptimeMillis() * 1000, bayrak)
                bitti(!k.ayar)
            }
        } catch (_: InterruptedException) {
        } catch (e: Exception) {
            Log.w(TAG, "Görüntü döngüsü durdu", e)
        }
    }

    /** H.264 kare SPS ya da IDR dilimi içeriyor mu (ilk dilime kadar bakar). */
    private fun anahtarKareMi(b: ByteArray, bas: Int, uzunluk: Int): Boolean {
        val son = bas + uzunluk
        var i = bas
        while (i + 3 < son) {
            if (b[i].toInt() == 0 && b[i + 1].toInt() == 0 && b[i + 2].toInt() == 1) {
                when (b[i + 3].toInt() and 0x1F) {
                    5, 7 -> return true
                    1 -> return false
                }
                i += 3
            } else i++
        }
        return false
    }

    fun kapat() {
        calisiyor = false
        besleyici.interrupt()
        try { besleyici.join(500) } catch (_: InterruptedException) {}
        try { codec?.stop() } catch (_: Exception) {}
        try { codec?.release() } catch (_: Exception) {}
        cikisIs.quitSafely()
    }

    companion object { const val TAG = AapTasima.TAG }
}
