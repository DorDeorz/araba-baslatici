package com.dordeorz.arababaslatici

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Android Auto'nun ana birim sunucusunu başlatır. Sunucu yalnızca Android Auto ayarlarındaki
 * ⋮ → "Ana birim sunucusunu başlat" menüsünden açılabiliyor (servis dışa kapalı). Bu servis
 * ayarları açar, menüye basar ve ayarları GERİ ile kapatır.
 *
 * Yazılar Android Auto'nun kendi kaynaklarından o anki dilde okunur; Türkçe de çalışır.
 */
class SunucuBaslatici : AccessibilityService() {

    private enum class Adim { BOS, MENU, OGE }

    private val h = Handler(Looper.getMainLooper())
    private var adim = Adim.BOS
    private var sonra: ((Boolean) -> Unit)? = null
    private val zamanAsimi = Runnable { bitir(false, "zaman aşımı") }
    private val tekrar = Runnable { dene() }

    override fun onServiceConnected() { calisan = this }
    override fun onDestroy() { if (calisan === this) calisan = null; super.onDestroy() }
    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (adim == Adim.BOS || e.packageName != OturumIzleyici.GEARHEAD) return
        h.removeCallbacks(tekrar)
        h.postDelayed(tekrar, 120)
    }

    private fun baslat(geri: (Boolean) -> Unit) {
        h.removeCallbacksAndMessages(null)
        sonra = geri
        adim = Adim.MENU
        h.postDelayed(zamanAsimi, 10_000)
        startActivity(Intent(AYARLAR_EYLEMI).setPackage(OturumIzleyici.GEARHEAD)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        Log.i(TAG, "Sunucu başlatılıyor: Android Auto ayarları açıldı")
        h.postDelayed(tekrar, 700)
    }

    private fun dene() {
        when (adim) {
            Adim.BOS -> return
            Adim.MENU -> {
                // Menü zaten açıksa doğrudan öğeye geç.
                if (bul { metinEs(it, baslatYazisi) || metinEs(it, durdurYazisi) } != null) {
                    adim = Adim.OGE; dene(); return
                }
                val menu = bul { aciklamaEs(it, menuYazisi) } ?: run { h.postDelayed(tekrar, 300); return }
                if (tikla(menu)) adim = Adim.OGE
                h.postDelayed(tekrar, 400)
            }
            Adim.OGE -> {
                val baslatOgesi = bul { metinEs(it, baslatYazisi) }
                if (baslatOgesi != null) {
                    tikla(baslatOgesi)
                    Log.i(TAG, "Ana birim sunucusunu başlat'a basıldı")
                    kapatVeBitir(true, "başlatıldı")
                    return
                }
                if (bul { metinEs(it, durdurYazisi) } != null) {
                    // Sunucu zaten açık: menüyü ve ayarları kapat.
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    kapatVeBitir(true, "zaten açıktı")
                    return
                }
                h.postDelayed(tekrar, 300)
            }
        }
    }

    private fun kapatVeBitir(tamam: Boolean, neden: String) {
        adim = Adim.BOS
        h.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 350)
        h.postDelayed({ bitir(tamam, neden) }, 700)
    }

    private fun bitir(tamam: Boolean, neden: String) {
        Log.i(TAG, "Sunucu başlatma bitti: $neden")
        h.removeCallbacks(zamanAsimi)
        h.removeCallbacks(tekrar)
        if (adim != Adim.BOS && !tamam) performGlobalAction(GLOBAL_ACTION_BACK)
        adim = Adim.BOS
        val s = sonra
        sonra = null
        s?.invoke(tamam)
    }

    /** Android Auto'nun bütün pencerelerinde koşula uyan ilk düğümü arar. */
    private fun bul(kosul: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val kokler = windows.mapNotNull { it.root }.ifEmpty { listOfNotNull(rootInActiveWindow) }
        for (k in kokler) {
            if (k.packageName != OturumIzleyici.GEARHEAD) continue
            ara(k, kosul)?.let { return it }
        }
        return null
    }

    private fun ara(n: AccessibilityNodeInfo, kosul: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (kosul(n)) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            ara(c, kosul)?.let { return it }
        }
        return null
    }

    /** Düğüm ya da tıklanabilir ilk atası. */
    private fun tikla(n: AccessibilityNodeInfo): Boolean {
        var d: AccessibilityNodeInfo? = n
        while (d != null) {
            if (d.isClickable) return d.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            d = d.parent
        }
        return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun metinEs(n: AccessibilityNodeInfo, y: List<String>) =
        n.text?.toString()?.trim()?.let { t -> y.any { it.equals(t, ignoreCase = true) } } == true

    private fun aciklamaEs(n: AccessibilityNodeInfo, y: List<String>) =
        n.contentDescription?.toString()?.trim()?.let { t -> y.any { it.equals(t, ignoreCase = true) } } == true

    private val menuYazisi get() = yazi("abc_action_menu_overflow_description", "More options")
    private val baslatYazisi get() = yazi("menu_action_developer_head_unit_network_server", "Start head unit server")
    private val durdurYazisi get() = yazi("menu_action_developer_head_unit_network_server_stop", "Stop head unit server")

    /** Android Auto'nun kaynağından o anki dildeki yazı + İngilizce yedek. */
    private fun yazi(ad: String, ingilizce: String): List<String> {
        val yerel = try {
            val r = packageManager.getResourcesForApplication(OturumIzleyici.GEARHEAD)
            val id = r.getIdentifier(ad, "string", OturumIzleyici.GEARHEAD)
            if (id != 0) r.getString(id) else null
        } catch (_: Exception) { null }
        return listOfNotNull(yerel, ingilizce)
    }

    companion object {
        private const val TAG = OturumIzleyici.TAG
        private const val AYARLAR_EYLEMI = "com.google.android.projection.gearhead.SETTINGS"

        @Volatile private var calisan: SunucuBaslatici? = null

        val hazir get() = calisan != null

        /** Sunucuyu başlatır; [sonra] ana iş parçacığında, bitince çağrılır. Servis bağlı değilse false. */
        fun baslat(c: Context, sonra: (Boolean) -> Unit): Boolean {
            val s = calisan ?: return false
            Handler(Looper.getMainLooper()).post { s.baslat(sonra) }
            return true
        }
    }
}
