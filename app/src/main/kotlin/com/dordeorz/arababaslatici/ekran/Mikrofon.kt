package com.dordeorz.arababaslatici.ekran

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android Auto'nun istediği mikrofon: 16 kHz, mono, 16 bit PCM. Her parça
 * [zaman damgası (µs, 8 bayt)][PCM] olarak gönderilir. Android Auto en fazla [enFazlaOnaysiz]
 * parçayı onaysız kabul eder; sınır dolarsa parça atlanır (gecikme birikmesin).
 */
class Mikrofon(private val gonder: (ByteArray) -> Unit) {
    private var kayitIsi: Thread? = null
    @Volatile private var acik = false
    private val onaysiz = AtomicInteger()
    @Volatile private var enFazlaOnaysiz = 4

    /** İzin yoksa ya da kayıt başlamazsa false. */
    @SuppressLint("MissingPermission") // çağıran izni kontrol ediyor
    fun ac(enFazla: Int): Boolean {
        kapat()
        enFazlaOnaysiz = enFazla.coerceAtLeast(1)
        onaysiz.set(0)
        val tampon = maxOf(AudioRecord.getMinBufferSize(HIZ, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT), PARCA * 4)
        val kayit = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, HIZ,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, tampon)
        } catch (e: Exception) {
            Log.w(TAG, "Mikrofon açılamadı", e); return false
        }
        if (kayit.state != AudioRecord.STATE_INITIALIZED) { kayit.release(); return false }
        acik = true
        kayitIsi = Thread({
            try {
                kayit.startRecording()
                val pcm = ByteArray(PARCA)
                while (acik) {
                    var n = 0
                    while (n < PARCA && acik) {
                        val r = kayit.read(pcm, n, PARCA - n)
                        if (r <= 0) break
                        n += r
                    }
                    if (n <= 0) break
                    if (onaysiz.get() >= enFazlaOnaysiz) continue
                    val zaman = SystemClock.elapsedRealtimeNanos() / 1000
                    val veri = ByteArray(8 + n)
                    for (i in 0 until 8) veri[i] = (zaman shr (56 - 8 * i)).toByte()
                    pcm.copyInto(veri, 8, 0, n)
                    onaysiz.incrementAndGet()
                    gonder(veri)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mikrofon döngüsü durdu", e)
            } finally {
                try { kayit.stop() } catch (_: Exception) {}
                kayit.release()
            }
        }, "AapMikrofon").apply { priority = Thread.MAX_PRIORITY; start() }
        Log.i(TAG, "Mikrofon açıldı (en fazla $enFazlaOnaysiz onaysız)")
        return true
    }

    /** Android Auto [adet] parçayı onayladı. */
    fun onay(adet: Int) {
        onaysiz.updateAndGet { maxOf(0, it - adet.coerceAtLeast(1)) }
    }

    fun kapat() {
        if (!acik) return
        acik = false
        kayitIsi?.join(300)
        kayitIsi = null
        Log.i(TAG, "Mikrofon kapandı")
    }

    companion object {
        const val TAG = AapTasima.TAG
        const val HIZ = 16000
        /** 20 ms'lik parça: 16000 × 2 bayt × 0,02. */
        const val PARCA = 640
    }
}
