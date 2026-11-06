package com.dordeorz.arababaslatici

import android.content.Context
import android.content.SharedPreferences

object Ayarlar {
    fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE)

    /** Bildirim gösterilecek Bluetooth cihazlarının MAC adresleri. */
    fun seciliCihazlar(c: Context): MutableSet<String> =
        prefs(c).getStringSet("cihazlar", emptySet()).orEmpty().toMutableSet()

    fun kaydet(c: Context, adresler: Set<String>) {
        prefs(c).edit()
            .putStringSet("cihazlar", HashSet(adresler))
            .putBoolean("ilk_kurulum", true)
            .apply()
    }

    fun ilkKurulumYapildi(c: Context): Boolean = prefs(c).getBoolean("ilk_kurulum", false)

    // ---- Ekran ayarları ----

    /** true: 1920×1080, false: 1280×720. */
    fun yuksekCozunurluk(c: Context) = prefs(c).getBoolean("1080p", true)
    fun yuksekCozunurluk(c: Context, v: Boolean) = prefs(c).edit().putBoolean("1080p", v).apply()

    /** Android Auto'ya bildirilen yoğunluk (1080p için; 720p'de oranlanır). */
    fun dpi(c: Context) = prefs(c).getInt("dpi", 260)
    fun dpi(c: Context, v: Int) = prefs(c).edit().putInt("dpi", v).apply()

    /** Çentik payı: -1 otomatik (ekranın çentiğinden), 0 kapalı, >0 elle (ekran pikseli). */
    fun centik(c: Context) = prefs(c).getInt("centik", -1)
    fun centik(c: Context, v: Int) = prefs(c).edit().putInt("centik", v).apply()

    /** Çıkış (X) düğmesi: 0 sol üst, 1 sol alt, 2 sağ üst, 3 gizli. */
    fun cikisYeri(c: Context) = prefs(c).getInt("cikis_yeri", 0)
    fun cikisYeri(c: Context, v: Int) = prefs(c).edit().putInt("cikis_yeri", v).apply()

    /** Ekranda fps/gecikme bilgisini göster. */
    fun bilgiGoster(c: Context) = prefs(c).getBoolean("bilgi", false)
    fun bilgiGoster(c: Context, v: Boolean) = prefs(c).edit().putBoolean("bilgi", v).apply()

    /** Çıkınca ana birim sunucusunu da kapat. */
    fun cikinciSunucuyuKapat(c: Context) = prefs(c).getBoolean("cikis_sunucu", true)
    fun cikinciSunucuyuKapat(c: Context, v: Boolean) = prefs(c).edit().putBoolean("cikis_sunucu", v).apply()

    // ---- Görünüm ----

    /** 0 sistem, 1 açık, 2 koyu. */
    fun tema(c: Context) = prefs(c).getInt("tema", 0)
    fun tema(c: Context, v: Int) = prefs(c).edit().putInt("tema", v).apply()

    /** Renkleri duvar kağıdından al (Android 12+). */
    fun dinamikRenk(c: Context) = prefs(c).getBoolean("dinamik_renk", true)
    fun dinamikRenk(c: Context, v: Boolean) = prefs(c).edit().putBoolean("dinamik_renk", v).apply()

    /** İlk açılıştaki karşılama ekranı geçildi mi. */
    fun karsilamaGoruldu(c: Context) = prefs(c).getBoolean("karsilama", false)
    fun karsilamaGoruldu(c: Context, v: Boolean) = prefs(c).edit().putBoolean("karsilama", v).apply()

    /** Ana ekrandaki pil yardımı kartı kapatıldı mı. */
    fun pilYardimiGizli(c: Context) = prefs(c).getBoolean("pil_yardimi_gizli", false)
    fun pilYardimiGizli(c: Context, v: Boolean) = prefs(c).edit().putBoolean("pil_yardimi_gizli", v).apply()
}
