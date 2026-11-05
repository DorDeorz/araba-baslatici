package com.dordeorz.arababaslatici.ekran

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.window.OnBackInvokedDispatcher
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Kendi Android Auto ekranımız. Görüntü tam ekran; Android Auto'nun arayüz alanı ekranı
 * dolduracak şekilde ölçeklenir, kenar boşlukları ekran dışında kalır.
 */
class EkranActivity : Activity(), AapOturum.Dinleyici {
    private lateinit var oturum: AapOturum
    private lateinit var geometri: Geometri
    private lateinit var bilgi: TextView
    private var olcek = 1f
    private val ana = Handler(Looper.getMainLooper())
    private var acilis = 0L
    private var sonSayi = 0
    private var sonDurum = ""

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        acilis = SystemClock.elapsedRealtime()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        window.setDecorFitsSystemWindows(false)

        val sinir = windowManager.maximumWindowMetrics.bounds
        val ekranG = maxOf(sinir.width(), sinir.height())
        val ekranY = minOf(sinir.width(), sinir.height())
        geometri = Geometri.hesapla(ekranG, ekranY, DPI)
        olcek = ekranG.toFloat() / geometri.arayuzG

        val yuzey = SurfaceView(this)
        yuzey.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) = oturum.yuzeyAyarla(h.surface)
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
            override fun surfaceDestroyed(h: SurfaceHolder) = oturum.yuzeyAyarla(null)
        })
        bilgi = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x80000000.toInt())
            textSize = 12f
            setPadding(12, 6, 12, 6)
        }
        val kok = object : FrameLayout(this) {
            override fun dispatchTouchEvent(e: MotionEvent): Boolean { dokunma(e); return true }
        }.apply {
            setBackgroundColor(Color.BLACK)
            clipChildren = true
            // Tüm görüntü ölçeklenince arayüz alanı ekranı doldurur; boşluklar ekran dışına taşar.
            val g = (geometri.genislik * olcek).roundToInt()
            val y = (geometri.yukseklik * olcek).roundToInt()
            addView(yuzey, FrameLayout.LayoutParams(g, y).apply {
                leftMargin = -(geometri.boslukG / 2 * olcek).roundToInt()
                topMargin = -(geometri.boslukY / 2 * olcek).roundToInt()
            })
            addView(bilgi, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END))
        }
        setContentView(kok)
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        oturum = AapOturum(applicationContext, geometri, this)
        oturum.baslat()
        ana.post(sayac)
        // Geri: oturumu kapatıp çık (tek dokunuşla çıkış düğmesi sonraki adımda).
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
            oturum.kapat()
            finish()
        }
    }

    /** Saniyede bir: ekrana basılan kare/sn ve ilk görüntü süresi. */
    private val sayac = object : Runnable {
        override fun run() {
            val c = oturum.cozucu
            val sayi = c?.basilan?.get() ?: 0
            val fps = sayi - sonSayi
            sonSayi = sayi
            val ilk = c?.ilkKareMs?.takeIf { it > 0 }?.let { "${it - acilis} ms" } ?: "—"
            bilgi.text = if (sayi == 0) sonDurum
                else "$fps fps · ilk görüntü $ilk · bağlantı ${oturum.baglantiMs} ms\n${c?.codecAdi}"
            if (sayi > 0) android.util.Log.i(AapTasima.TAG, "Ölçüm: $fps fps, toplam $sayi kare, ilk görüntü $ilk")
            ana.postDelayed(this, 1000)
        }
    }

    private fun dokunma(e: MotionEvent) {
        val eylem = when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> 0
            MotionEvent.ACTION_UP -> 1
            MotionEvent.ACTION_MOVE -> 2
            MotionEvent.ACTION_POINTER_DOWN -> 5
            MotionEvent.ACTION_POINTER_UP -> 6
            MotionEvent.ACTION_CANCEL -> 1
            else -> return
        }
        val noktalar = List(e.pointerCount) { i ->
            Triple(
                e.getPointerId(i),
                (e.getX(i) / olcek).roundToInt().coerceIn(0, geometri.arayuzG),
                (e.getY(i) / olcek).roundToInt().coerceIn(0, geometri.arayuzY),
            )
        }
        oturum.dokun(eylem, e.actionIndex, noktalar)
    }

    override fun durum(metin: String) = ana.post { sonDurum = metin; bilgi.text = metin }.let {}

    override fun bitti(neden: String) = ana.post {
        android.widget.Toast.makeText(this, neden, android.widget.Toast.LENGTH_LONG).show()
        finish()
    }.let {}

    override fun onDestroy() {
        ana.removeCallbacks(sayac)
        oturum.kapat()
        super.onDestroy()
    }

    companion object {
        /** 1920×1080 yatayda telefonda iyi görünen yoğunluk (Open Headunit testinde 260). */
        const val DPI = 260
    }
}
