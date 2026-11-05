package com.dordeorz.arababaslatici;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * PhoneAuto sunucuyu başlatmak için Android Auto ayarlarını açıyor ama kapatmıyor; ayarlar
 * arkada kalıyor ve PhoneAuto'daki çarpıya basınca o ekrana düşülüyor. Bu servis, öndeki ekran
 * PhoneAuto'nun Android Auto ekranıyken oturum biter bitmez Android Auto ayarları öne gelirse
 * "geri"ye basıp onu kapatır.
 *
 * Sunucuyu başlatma sırasında karışmaz: o sırada ortada açık ya da yeni bitmiş bir oturum yoktur.
 */
public class AyarKapatici extends AccessibilityService {
    static final String TAG = OturumIzleyici.TAG;
    /** Oturum bittikten sonra bu süre içinde öne gelen ayarlar kapatılır. */
    private static final long SURE_MS = 5_000;

    /** Oturum açık mı / en son ne zaman bitti (OturumIzleyici günceller). */
    static volatile boolean oturumAcik;
    static volatile long oturumBitis;

    /** PhoneAuto'da en son öne gelen ekranın sınıfı. */
    private String sonPhoneAutoEkrani;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || e.getPackageName() == null || e.getClassName() == null) return;
        String paket = e.getPackageName().toString();
        String sinif = e.getClassName().toString();
        // Diyaloglar, bildirim paneli, klavye vb. "ekran" değildir.
        if (sinif.startsWith("android.") || paket.equals("com.android.systemui")
                || paket.contains("inputmethod")) return;

        if (Bildirim.PHONEAUTO.equals(paket)) sonPhoneAutoEkrani = sinif;
        if (!OturumIzleyici.GEARHEAD.equals(paket) || !sinif.endsWith("SettingsActivity")) return;

        // Sunucu başlatılırken PhoneAuto'nun son ekranı genelde rehberdir ve oturum yoktur.
        boolean phoneAutodanGeldi = Bildirim.PHONEAUTO_EKRAN.equals(sonPhoneAutoEkrani);
        boolean oturumYeniBitti = oturumAcik
                || System.currentTimeMillis() - oturumBitis < SURE_MS;
        Log.d(TAG, "Android Auto ayarları öne geldi: phoneAutodanGeldi=" + phoneAutodanGeldi
                + " oturumAcik=" + oturumAcik
                + " bitisOnce=" + (System.currentTimeMillis() - oturumBitis) + "ms");
        if (phoneAutodanGeldi && oturumYeniBitti) {
            Log.i(TAG, "PhoneAuto kapandı, arkada kalan Android Auto ayarları kapatılıyor");
            handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_BACK), 300);
        }
    }

    @Override
    public void onInterrupt() {}
}
