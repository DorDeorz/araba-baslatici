package com.dordeorz.arababaslatici

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * Kendi erişilebilirlik servisimizi ([SunucuBaslatici]) kontrol eder ve gerekirse yeniden açar.
 *
 * Uygulama son uygulamalardan kaydırılınca servis "çöktü" sayılıp bağlantısı kopar;
 * zorla durdurulunca da etkin servisler listesinden tamamen çıkarılır. İkisinde de
 * servisi listeden çıkarıp geri eklemek Android'in onu yeniden bağlamasını sağlar.
 * Bunun için WRITE_SECURE_SETTINGS izni gerekir (bir kez adb ile verilir).
 */
object Erisilebilirlik {

    fun yetkiVar(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Servisimiz bağlı mı (ayarlarda açık görünmesi yetmez). */
    fun calisiyor(c: Context): Boolean = SunucuBaslatici.hazir ||
        c.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.packageName == c.packageName }

    /** Servisimizi gerekirse açar/yeniden bağlar. Bloklar (en çok ~4 sn); ana iş parçacığında çağırma. */
    fun ac(c: Context): Boolean {
        if (SunucuBaslatici.hazir) return true
        yenidenAc(c, ComponentName(c, SunucuBaslatici::class.java).flattenToString())
        return bekleVe { SunucuBaslatici.hazir }
    }

    /**
     * Ayar satırını okuyup geri yazar. Birden çok iş parçacığı aynı anda çağırabildiği için
     * birbirinin değişikliğini silmesinler diye kilitli.
     */
    @Synchronized
    private fun yenidenAc(c: Context, bilesen: String) {
        if (!yetkiVar(c)) return
        val cr = c.contentResolver
        val oneki = c.packageName + "/"
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

    /** Koşul sağlanana kadar en fazla ~4 sn bekler. */
    private fun bekleVe(kosul: () -> Boolean): Boolean {
        repeat(14) {
            if (kosul()) return true
            Thread.sleep(300)
        }
        return kosul()
    }
}
