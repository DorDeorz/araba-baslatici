package com.dordeorz.arababaslatici;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import java.util.ArrayList;
import java.util.List;

/**
 * PhoneAuto'nun erişilebilirlik servisini kontrol eder ve gerekirse yeniden açar.
 *
 * PhoneAuto son uygulamalardan kaydırılınca servis "çöktü" sayılıp bağlantısı kopar;
 * zorla durdurulunca da etkin servisler listesinden tamamen çıkarılır. İkisinde de
 * servisi listeden çıkarıp geri eklemek Android'in onu yeniden bağlamasını sağlar.
 * Bunun için WRITE_SECURE_SETTINGS izni gerekir (bir kez adb ile verilir).
 */
final class Erisilebilirlik {
    static final String SERVIS =
            "com.tarplay.phonescreen/com.google.android.projection.sink.ui.HeadUnitServerAccessibilityService";
    private static final String PAKET_ONEKI = Bildirim.PHONEAUTO + "/";

    private Erisilebilirlik() {}

    static boolean yetkiVar(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Servis şu an gerçekten bağlı mı (yalnızca ayarlarda açık görünmesi yetmez). */
    static boolean calisiyor(Context c) {
        return bagli(c, Bildirim.PHONEAUTO);
    }

    /** Araba Başlatıcı'nın kendi erişilebilirlik servisi (AyarKapatici) bağlı mı. */
    static boolean kendiServisiCalisiyor(Context c) {
        return bagli(c, c.getPackageName());
    }

    private static boolean bagli(Context c, String paket) {
        AccessibilityManager am = c.getSystemService(AccessibilityManager.class);
        for (AccessibilityServiceInfo i :
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            ServiceInfo si = i.getResolveInfo() != null ? i.getResolveInfo().serviceInfo : null;
            if (si != null && paket.equals(si.packageName)) return true;
        }
        return false;
    }

    /**
     * PhoneAuto'nun servisi çalışmıyorsa yeniden açar. Bloklar (yaklaşık 2 sn), ana iş
     * parçacığında çağırma.
     * @return servis sonunda çalışıyorsa true
     */
    static boolean onar(Context c) {
        if (calisiyor(c)) return true;
        yenidenAc(c, PAKET_ONEKI, SERVIS);
        for (int i = 0; i < 10 && !calisiyor(c); i++) bekle(300);
        return calisiyor(c);
    }

    /** Kendi servisimizi (AyarKapatici) gerekirse açar/yeniden bağlar. Bloklar. */
    static boolean kendiServisiniAc(Context c) {
        if (kendiServisiCalisiyor(c)) return true;
        String bilesen = new android.content.ComponentName(c, AyarKapatici.class).flattenToString();
        yenidenAc(c, c.getPackageName() + "/", bilesen);
        for (int i = 0; i < 10 && !kendiServisiCalisiyor(c); i++) bekle(300);
        return kendiServisiCalisiyor(c);
    }

    private static void yenidenAc(Context c, String oneki, String bilesen) {
        if (!yetkiVar(c)) return;
        ContentResolver cr = c.getContentResolver();
        String mevcut = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        List<String> digerleri = new ArrayList<>();
        boolean listedeydi = false;
        if (mevcut != null) {
            for (String s : mevcut.split(":")) {
                if (s.isEmpty()) continue;
                if (s.startsWith(oneki)) listedeydi = true; else digerleri.add(s);
            }
        }
        if (listedeydi) {
            // Listedeyken bağlı değilse "çöktü" sayılmıştır: önce çıkar ki işaret temizlensin...
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    String.join(":", digerleri));
            bekle(800);
        }
        // ...sonra geri ekle; Android servisi yeniden bağlar.
        List<String> yeni = new ArrayList<>(digerleri);
        yeni.add(bilesen);
        Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                String.join(":", yeni));
        Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
    }

    private static void bekle(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
