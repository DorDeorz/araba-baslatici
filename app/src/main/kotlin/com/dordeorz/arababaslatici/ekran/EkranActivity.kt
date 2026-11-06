package com.dordeorz.arababaslatici.ekran

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import com.dordeorz.arababaslatici.Ayarlar
import com.dordeorz.arababaslatici.OturumIzleyici
import kotlin.math.roundToInt

/**
 * Kendi Android Auto ekranımız. Görüntü tam ekran; Android Auto'nun arayüz alanı ekranı
 * dolduracak şekilde ölçeklenir. Çentik tarafında Android Auto'ya içerik kenar boşluğu
 * bildirilir: arka plan tam ekran kalır, simgeler ve saat çentiğin altından çıkar.
 * O boş şeritte tek dokunuşla çıkış (X) düğmesi durur.
 */
class EkranActivity : Activity(), AapOturum.Dinleyici {
    private lateinit var oturum: AapOturum
    private lateinit var geometri: Geometri
    private lateinit var bilgi: TextView
    private lateinit var cikis: TextView
    private var olcek = 1f
    private val ana = Handler(Looper.getMainLooper())
    private var acilis = 0L
    private var sonSayi = 0
    private var sonGelen = 0
    private var sonDurum = ""
    private var bilgiAcik = false
    private var cikisTakip = false
    private var cikiliyor = false

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
        val yuksek = if (intent.hasExtra(EK_720P)) !intent.getBooleanExtra(EK_720P, false)
            else Ayarlar.yuksekCozunurluk(this)
        val dpi = Ayarlar.dpi(this)
        geometri = Geometri.hesapla(ekranG, ekranY, if (yuksek) dpi else dpi * 2 / 3, yuksek)
        olcek = ekranG.toFloat() / geometri.arayuzG
        bilgiAcik = Ayarlar.bilgiGoster(this)

        val yuzey = SurfaceView(this)
        yuzey.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) = oturum.yuzeyAyarla(h.surface)
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
            override fun surfaceDestroyed(h: SurfaceHolder) = oturum.yuzeyAyarla(null)
        })
        bilgi = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x80000000.toInt())
            textSize = 10f
            alpha = 0.8f
            setPadding(8, 4, 8, 4)
        }
        cikis = TextView(this).apply {
            text = "✕"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x66000000)
                setStroke(dp(1), 0x55FFFFFF)
            }
            contentDescription = "Çık"
            visibility = if (Ayarlar.cikisYeri(this@EkranActivity) == 3) View.GONE else View.VISIBLE
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
                Gravity.BOTTOM or Gravity.END))
            addView(cikis, FrameLayout.LayoutParams(dp(40), dp(40)))
            setOnApplyWindowInsetsListener { v, ins -> centikUygula(ins); v.onApplyWindowInsets(ins) }
        }
        if (!bilgiAcik) bilgi.visibility = View.GONE
        setContentView(kok)
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        oturum = AapOturum(applicationContext, geometri, this)
        oturum.baslat()
        ana.post(sayac)
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { cik() }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    /**
     * Çentik hangi taraftaysa (telefon çevrilince yer değiştirir) Android Auto'ya o tarafta içerik
     * payı bildirilir ve X düğmesi o boş şeride konur.
     */
    private fun centikUygula(ins: WindowInsets) {
        val kesik = ins.displayCutout
        val ayar = Ayarlar.centik(this)
        var sol = 0
        var sag = 0
        when {
            ayar == 0 -> Unit
            ayar > 0 -> if ((kesik?.safeInsetRight ?: 0) > (kesik?.safeInsetLeft ?: 0)) sag = ayar else sol = ayar
            else -> { sol = kesik?.safeInsetLeft ?: 0; sag = kesik?.safeInsetRight ?: 0 }
        }
        Log.i(AapTasima.TAG, "Çentik payı (ekran px): sol=$sol sağ=$sag")
        oturum.centikAyarla((sol / olcek).roundToInt(), (sag / olcek).roundToInt())

        // X: çentik şeridinin ortasına (şerit yoksa köşeye), seçilen köşede.
        val yer = Ayarlar.cikisYeri(this)
        val boyut = dp(40)
        val lp = cikis.layoutParams as FrameLayout.LayoutParams
        val sagda = yer == 2 || (yer != 2 && sag > sol)
        val serit = if (sagda) sag else sol
        val kenar = if (serit > boyut) (serit - boyut) / 2 else dp(8)
        lp.gravity = (if (sagda) Gravity.END else Gravity.START) or (if (yer == 1) Gravity.BOTTOM else Gravity.TOP)
        lp.leftMargin = if (sagda) 0 else kenar
        lp.rightMargin = if (sagda) kenar else 0
        lp.topMargin = dp(10)
        lp.bottomMargin = dp(10)
        cikis.layoutParams = lp
    }

    /** Saniyede bir: ekrana basılan kare/sn ve ilk görüntü süresi (kayda ve isteğe bağlı ekrana). */
    private val sayac = object : Runnable {
        override fun run() {
            val c = oturum.cozucu
            val sayi = c?.basilan?.get() ?: 0
            val fps = sayi - sonSayi
            sonSayi = sayi
            val gelenSayi = c?.gelen?.get() ?: 0
            val gelenFps = gelenSayi - sonGelen
            sonGelen = gelenSayi
            val ilk = c?.ilkKareMs?.takeIf { it > 0 }?.let { "${it - acilis} ms" } ?: "—"
            if (sayi == 0) {
                // Görüntü gelene kadar durum her zaman görünsün (bağlanıyor, sunucu bekleniyor…).
                bilgi.text = sonDurum
                bilgi.visibility = if (sonDurum.isEmpty()) View.GONE else View.VISIBLE
            } else {
                bilgi.text = "$fps fps (gelen $gelenFps) · ${geometri.yukseklik}p · ilk görüntü $ilk"
                bilgi.visibility = if (bilgiAcik) View.VISIBLE else View.GONE
                Log.i(AapTasima.TAG, "Ölçüm: ${geometri.yukseklik}p ekrana $fps fps, AA'dan gelen $gelenFps, " +
                    "bekleyen ${c?.bekleyen}, toplam $sayi kare, ilk görüntü $ilk")
            }
            ana.postDelayed(this, 1000)
        }
    }

    private fun dokunma(e: MotionEvent) {
        // X düğmesine basıldıysa dokunma Android Auto'ya gitmez.
        if (e.actionMasked == MotionEvent.ACTION_DOWN && cikis.visibility == View.VISIBLE) {
            val r = Rect()
            cikis.getHitRect(r)
            r.inset(-dp(12), -dp(12))
            cikisTakip = r.contains(e.x.toInt(), e.y.toInt())
        }
        if (cikisTakip) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> cikis.alpha = 0.5f
                MotionEvent.ACTION_UP -> { cikis.alpha = 1f; cikisTakip = false; cik() }
                MotionEvent.ACTION_CANCEL -> { cikis.alpha = 1f; cikisTakip = false }
            }
            return
        }
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

    override fun durum(metin: String) = ana.post { sonDurum = metin }.let {}

    override fun bitti(neden: String, kullaniciCikti: Boolean) = ana.post {
        if (kullaniciCikti) cik()
        else {
            Toast.makeText(this, neden, Toast.LENGTH_LONG).show()
            finishAndRemoveTask()
        }
    }.let {}

    /**
     * Tek dokunuşla tam çıkış (X, Android Auto'daki "Çık" ya da geri hareketi): oturumu kapatır,
     * ana birim sunucusunu durdurur ve ekranı son uygulamalardan da kaldırır.
     */
    private fun cik() {
        if (cikiliyor) return
        cikiliyor = true
        oturum.kapat()
        val uygulama = applicationContext
        if (Ayarlar.cikinciSunucuyuKapat(this)) {
            ana.postDelayed({ OturumIzleyici.arabadanAyrildi(uygulama) }, 400)
        }
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        ana.removeCallbacks(sayac)
        oturum.kapat()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    companion object {
        /** Ayarı geçersiz kılmak için (deneme): true → 720p. */
        const val EK_720P = "720p"
    }
}
