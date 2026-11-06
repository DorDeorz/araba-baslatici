package com.dordeorz.arababaslatici

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kalıcı olay kaydı: başlatma, bağlantı, hata ve çıkış satırları bir dosyaya yazılır.
 * logcat birkaç dakikada silindiği için sorunlar saatler sonra da buradan okunabilir.
 */
object Olaylar {
    private const val DOSYA = "olaylar.txt"
    private const val EN_COK = 300
    private val bicim = SimpleDateFormat("dd.MM HH:mm:ss", Locale.ROOT)

    /** Uygulamanın dış klasörü: adb ile de okunabilsin (/sdcard/Android/data/<paket>/files). */
    private fun dosya(c: Context) = File(c.applicationContext.getExternalFilesDir(null) ?: c.applicationContext.filesDir, DOSYA)

    @Synchronized
    fun yaz(c: Context, metin: String) {
        Log.i(OturumIzleyici.TAG, "Olay: $metin")
        try {
            val f = dosya(c)
            f.appendText("${bicim.format(Date())}  $metin\n")
            // Dosya büyümesin: son EN_COK satırı tut.
            if (f.length() > 64_000) f.writeText(f.readLines().takeLast(EN_COK).joinToString("\n", postfix = "\n"))
        } catch (_: Exception) {}
    }

    /** En yeni en üstte. */
    @Synchronized
    fun oku(c: Context, adet: Int = 100): List<String> = try {
        dosya(c).readLines().takeLast(adet).reversed()
    } catch (_: Exception) { emptyList() }

    @Synchronized
    fun temizle(c: Context) { dosya(c).delete() }
}
