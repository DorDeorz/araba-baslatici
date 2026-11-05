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
}
