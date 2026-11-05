package com.dordeorz.arababaslatici;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

final class Ayarlar {
    private Ayarlar() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE);
    }

    /** Bildirim gösterilecek Bluetooth cihazlarının MAC adresleri. */
    static Set<String> seciliCihazlar(Context c) {
        return new HashSet<>(prefs(c).getStringSet("cihazlar", new HashSet<>()));
    }

    static void kaydet(Context c, Set<String> adresler) {
        prefs(c).edit().putStringSet("cihazlar", new HashSet<>(adresler)).putBoolean("ilk_kurulum", true).apply();
    }

    static boolean ilkKurulumYapildi(Context c) {
        return prefs(c).getBoolean("ilk_kurulum", false);
    }
}
