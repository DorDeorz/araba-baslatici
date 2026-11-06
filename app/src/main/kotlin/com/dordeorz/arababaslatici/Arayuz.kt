package com.dordeorz.arababaslatici

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/** Ekranlar için ortak, sade görünüm: kartlar, başlıklar, büyük düğme. Gece/gündüz uyumlu. */
class Arayuz(private val a: Activity) {
    val gece = a.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val arkaPlan = if (gece) 0xFF101114.toInt() else 0xFFF3F4F7.toInt()
    val kartRengi = if (gece) 0xFF1C1E23.toInt() else Color.WHITE
    val yazi = if (gece) 0xFFECEDEF.toInt() else 0xFF15171A.toInt()
    val soluk = if (gece) 0xFF9AA0A8.toInt() else 0xFF5F6670.toInt()
    val vurgu = 0xFF1A73E8.toInt()
    val uyari = 0xFFE8A317.toInt()
    val iyi = 0xFF34A853.toInt()

    /** Kaydırılabilir sayfa; içerik [LinearLayout]'a eklenir. */
    fun sayfa(): LinearLayout {
        val kok = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }
        a.setContentView(ScrollView(a).apply {
            fitsSystemWindows = true
            setBackgroundColor(arkaPlan)
            addView(kok)
        })
        a.window.statusBarColor = arkaPlan
        a.window.navigationBarColor = arkaPlan
        return kok
    }

    fun buyukBaslik(ust: ViewGroup, s: String, alt: String? = null) {
        ust.addView(metin(s, 28f, yazi, kalin = true))
        if (alt != null) ust.addView(metin(alt, 15f, soluk).apply { setPadding(0, dp(4), 0, dp(16)) })
    }

    fun bolum(ust: ViewGroup, s: String) =
        ust.addView(metin(s.uppercase(), 12f, soluk, kalin = true).apply { setPadding(dp(4), dp(20), 0, dp(8)) })

    fun kart(ust: ViewGroup): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(kartRengi) }
        setPadding(dp(16), dp(8), dp(16), dp(8))
        ust.addView(this, LinearLayout.LayoutParams(-1, -2))
    }

    /** Kart içinde bir satır: başlık, açıklama, sağda durum işareti. Tıklanabilir. */
    fun satir(ust: ViewGroup, baslik: String, aciklama: String? = null, isaret: String? = null,
              isaretRengi: Int = soluk, tikla: (() -> Unit)? = null): LinearLayout {
        val s = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            if (tikla != null) {
                isClickable = true
                background = RippleDrawable(ColorStateList.valueOf(0x22000000), null, null)
                setOnClickListener { tikla() }
            }
        }
        val sol = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        sol.addView(metin(baslik, 16f, yazi))
        if (aciklama != null) sol.addView(metin(aciklama, 13f, soluk).apply { setPadding(0, dp(2), 0, 0) })
        s.addView(sol, LinearLayout.LayoutParams(0, -2, 1f))
        if (isaret != null) s.addView(metin(isaret, 15f, isaretRengi, kalin = true).apply { setPadding(dp(12), 0, 0, 0) })
        ust.addView(s)
        return s
    }

    fun ayrac(ust: ViewGroup) = ust.addView(View(a).apply {
        setBackgroundColor(if (gece) 0x22FFFFFF else 0x14000000)
    }, LinearLayout.LayoutParams(-1, 1))

    /** Sayfanın ana eylemi: geniş, yuvarlak, renkli. */
    fun anaDugme(ust: ViewGroup, s: String, tikla: (TextView) -> Unit): TextView {
        val d = metin(s, 18f, Color.WHITE, kalin = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(18), dp(16), dp(18))
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(vurgu) }, null)
            isClickable = true
            setOnClickListener { tikla(this) }
        }
        ust.addView(d, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(8) })
        return d
    }

    /** İkincil düğme: çerçeveli. */
    fun dugme(ust: ViewGroup, s: String, tikla: (TextView) -> Unit): TextView {
        val d = metin(s, 16f, vurgu, kalin = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = RippleDrawable(ColorStateList.valueOf(0x221A73E8),
                GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setStroke(dp(1), vurgu) }, null)
            isClickable = true
            setOnClickListener { tikla(this) }
        }
        ust.addView(d, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return d
    }

    fun metin(s: String, boy: Float, renk: Int, kalin: Boolean = false) = TextView(a).apply {
        text = s
        setTextColor(renk)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, boy)
        if (kalin) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun dp(v: Int) = (v * a.resources.displayMetrics.density).roundToInt()
}
