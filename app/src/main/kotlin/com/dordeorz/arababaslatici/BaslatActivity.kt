package com.dordeorz.arababaslatici

import android.app.Activity
import android.os.Bundle

/** Bildirimdeki "Başlat" için görünmez ara ekran: [Baslatici]'yı çalıştırıp kapanır. */
class BaslatActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Baslatici.baslat(this)
        finish()
    }
}
