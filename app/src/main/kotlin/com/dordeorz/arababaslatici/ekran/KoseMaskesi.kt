package com.dordeorz.arababaslatici.ekran

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * Android Auto kendi arayüzünün köşelerini yuvarlatıyor; köşelerin arkasında duvar kağıdı
 * görünüyor. Bu görünüm arayüz alanının (çentik şeritleri hariç) dört köşesine siyah çeyrek
 * daireler çizer.
 */
class KoseMaskesi(c: Context) : View(c) {
    private val boya = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val yol = Path()
    private var sol = 0
    private var sag = 0
    private var r = 0f

    init { setWillNotDraw(false) }

    fun ayarla(sol: Int, sag: Int, yaricap: Float) {
        this.sol = sol; this.sag = sag; r = yaricap
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (r <= 0f) return
        val x0 = sol.toFloat()
        val x1 = (width - sag).toFloat()
        val h = height.toFloat()
        val d = 2 * r
        yol.rewind()
        // Sol üst
        yol.moveTo(x0, 0f); yol.lineTo(x0 + r, 0f)
        yol.arcTo(RectF(x0, 0f, x0 + d, d), 270f, -90f, false); yol.close()
        // Sağ üst
        yol.moveTo(x1, 0f); yol.lineTo(x1 - r, 0f)
        yol.arcTo(RectF(x1 - d, 0f, x1, d), 270f, 90f, false); yol.close()
        // Sol alt
        yol.moveTo(x0, h); yol.lineTo(x0, h - r)
        yol.arcTo(RectF(x0, h - d, x0 + d, h), 180f, -90f, false); yol.close()
        // Sağ alt
        yol.moveTo(x1, h); yol.lineTo(x1, h - r)
        yol.arcTo(RectF(x1 - d, h - d, x1, h), 0f, 90f, false); yol.close()
        c.drawPath(yol, boya)
    }
}
