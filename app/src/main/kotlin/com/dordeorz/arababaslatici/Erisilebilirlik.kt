package com.dordeorz.arababaslatici

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * Erişilebilirlik servislerini (PhoneAuto'nunki ve kendi AyarKapatici'miz) kontrol eder ve
 * gerekirse yeniden açar.
 *
 * Uygulama son uygulamalardan kaydırılınca servisi "çöktü" sayılıp bağlantısı kopar;
 * zorla durdurulunca da etkin servisler listesinden tamamen çıkarılır. İkisinde de
 * servisi listeden çıkarıp geri eklemek Android'in onu yeniden bağlamasını sağlar.
 * Bunun için WRITE_SECURE_SETTINGS izni gerekir (bir kez adb ile verilir).
 */
object Erisilebilirlik {
    private const val PHONEAUTO_SERVISI =
        "com.tarplay.phonescreen/com.google.android.projection.sink.ui.HeadUnitServerAccessibilityService"

    fun yetkiVar(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** PhoneAuto'nun servisi şu an gerçekten bağlı mı (ayarlarda açık görünmesi yetmez). */
    fun calisiyor(c: Context): Boolean = bagli(c, Bildirim.PHONEAUTO)

    /** Kendi erişilebilirlik servisimiz (AyarKapatici) bağlı mı. */
    fun kendiServisiCalisiyor(c: Context): Boolean = bagli(c, c.packageName)

    private fun bagli(c: Context, paket: String): Boolean =
        c.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.packageName == paket }

    /**
     * PhoneAuto'nun servisi çalışmıyorsa yeniden açar. Bloklar (yaklaşık 2 sn), ana iş
     * parçacığında çağırma.
     * @return servis sonunda çalışıyorsa true
     */
    fun onar(c: Context): Boolean {
        if (calisiyor(c)) return true
        yenidenAc(c, Bildirim.PHONEAUTO + "/", PHONEAUTO_SERVISI)
        return bekleVe { calisiyor(c) }
    }

    /** Kendi servisimizi gerekirse açar/yeniden bağlar. Bloklar. */
    fun kendiServisiniAc(c: Context): Boolean {
        if (kendiServisiCalisiyor(c)) return true
        val bilesen = ComponentName(c, AyarKapatici::class.java).flattenToString()
        yenidenAc(c, c.packageName + "/", bilesen)
        return bekleVe { kendiServisiCalisiyor(c) }
    }

    /**
     * Ayar satırını okuyup geri yazar; birden çok iş parçacığı (BaslatActivity, MainActivity,
     * OturumIzleyici) aynı anda çağırabildiği için birbirinin değişikliğini silmesinler diye kilitli.
     */
    @Synchronized
    private fun yenidenAc(c: Context, oneki: String, bilesen: String) {
        if (!yetkiVar(c)) return
        val cr = c.contentResolver
        val mevcut = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(":").filter { it.isNotEmpty() }
        val digerleri = mevcut.filterNot { it.startsWith(oneki) }
        if (digerleri.size != mevcut.size) {
            // Listedeyken bağlı değilse "çöktü" sayılmıştır: önce çıkar ki işaret temizlensin...
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                digerleri.joinToString(":"))
            Thread.sleep(800)
        }
        // ...sonra geri ekle; Android servisi yeniden bağlar.
        Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            (digerleri + bilesen).joinToString(":"))
        Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
    }

    /** Koşul sağlanana kadar en fazla ~3 sn bekler. */
    private fun bekleVe(kosul: () -> Boolean): Boolean {
        repeat(10) {
            if (kosul()) return true
            Thread.sleep(300)
        }
        return kosul()
    }
}
