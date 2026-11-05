package com.dordeorz.arababaslatici;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/**
 * Android Auto'nun bildirimlerini izler. Android Auto oturumu bitince ("Aracınıza bağlandı"
 * bildirimi kalkınca) kısa bir süre bekler, oturum geri gelmezse Android Auto'nun
 * "Head unit server running" bildirimindeki "Stop" düğmesine basarak sunucuyu kapatır.
 */
public class OturumIzleyici extends NotificationListenerService {
    static final String TAG = "AracBaslatici";
    static final String GEARHEAD = "com.google.android.projection.gearhead";
    /** "Aracınıza bağlandı": oturum sürdükçe duran bildirimin kanalı. */
    static final String OTURUM_KANALI = "car.default_notification_channel";
    /** "Head unit server running" bildiriminin kanalı. */
    static final String SUNUCU_KANALI = "gearhead_connection_status";
    /** PhoneAuto kısa süre koparsa sunucuyu hemen kapatmamak için bekleme. */
    static final long BEKLEME_MS = 15_000;

    private static volatile OturumIzleyici calisan;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable kapat = () -> {
        if (oturumVar()) return;
        sunucuyuDurdur();
    };

    @Override
    public void onListenerConnected() {
        calisan = this;
        AyarKapatici.oturumAcik = oturumVar();
        new Thread(() -> Erisilebilirlik.kendiServisiniAc(this)).start();
        // Dinleyici ölüyken araba ayrıldıysa şimdi kapat.
        long istek = Ayarlar.prefs(this).getLong("durdur_istegi", 0);
        if (istek != 0) {
            Ayarlar.prefs(this).edit().remove("durdur_istegi").apply();
            if (System.currentTimeMillis() - istek < 5 * 60_000) {
                sunucuyuDurdur();
                return;
            }
        }
        // Dinleyici ölüyken oturum bittiyse sunucu açık kalmış olabilir: bekleyip kapat.
        if (!oturumVar() && sunucuBildirimiVar()) {
            Log.i(TAG, "Oturum yok ama sunucu açık; " + BEKLEME_MS / 1000 + " sn sonra kapatılacak");
            handler.postDelayed(kapat, BEKLEME_MS);
        }
    }

    private boolean sunucuBildirimiVar() {
        try {
            for (StatusBarNotification s : getActiveNotifications()) {
                if (GEARHEAD.equals(s.getPackageName())
                        && SUNUCU_KANALI.equals(s.getNotification().getChannelId())
                        && s.getNotification().actions != null) return true;
            }
        } catch (RuntimeException ignored) {}
        return false;
    }

    /**
     * Uygulama son uygulamalardan kaydırılıp öldürülünce sistem dinleyiciyi bir daha
     * kendiliğinden bağlamıyor; uygulama her çalıştığında bunu çağırıp yeniden bağlatıyoruz.
     */
    static void yenidenBagla(Context c) {
        if (calisan != null || !izinVar(c)) return;
        // requestRebind() burada işe yaramıyor (yalnızca requestUnbind sonrası çalışıyor).
        // Bileşeni kapatıp açmak sistemin dinleyiciyi yeniden bağlamasını sağlıyor.
        android.content.pm.PackageManager pm = c.getPackageManager();
        ComponentName b = bilesen(c);
        try {
            pm.setComponentEnabledSetting(b,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(b,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
            Log.i(TAG, "Bildirim dinleyicisi yeniden bağlatılıyor");
        } catch (RuntimeException e) {
            Log.w(TAG, "Dinleyici yeniden bağlanamadı", e);
        }
    }

    @Override
    public void onListenerDisconnected() {
        if (calisan == this) calisan = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (oturumBildirimi(sbn)) {
            AyarKapatici.oturumAcik = true;
            handler.removeCallbacks(kapat);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (oturumBildirimi(sbn)) {
            AyarKapatici.oturumAcik = false;
            AyarKapatici.oturumBitis = System.currentTimeMillis();
            Log.i(TAG, "Android Auto oturumu bitti; " + BEKLEME_MS / 1000 + " sn sonra sunucu kapatılacak");
            handler.removeCallbacks(kapat);
            handler.postDelayed(kapat, BEKLEME_MS);
        }
    }

    private static boolean oturumBildirimi(StatusBarNotification sbn) {
        return GEARHEAD.equals(sbn.getPackageName())
                && OTURUM_KANALI.equals(sbn.getNotification().getChannelId());
    }

    private boolean oturumVar() {
        try {
            for (StatusBarNotification s : getActiveNotifications()) {
                if (oturumBildirimi(s)) return true;
            }
        } catch (RuntimeException ignored) {}
        return false;
    }

    /** "Head unit server running" bildirimindeki Stop düğmesine basar. */
    boolean sunucuyuDurdur() {
        StatusBarNotification[] hepsi;
        try {
            hepsi = getActiveNotifications();
        } catch (RuntimeException e) {
            return false;
        }
        for (StatusBarNotification s : hepsi) {
            Notification n = s.getNotification();
            if (!GEARHEAD.equals(s.getPackageName()) || !SUNUCU_KANALI.equals(n.getChannelId())
                    || n.actions == null) continue;
            String durdurYazisi = durdurYazisi();
            for (Notification.Action a : n.actions) {
                // Tek eylem varsa o Stop'tur; birden çoksa yazısına göre seç.
                boolean dogruEylem = n.actions.length == 1 || (a.title != null
                        && (a.title.toString().equalsIgnoreCase(durdurYazisi)
                            || a.title.toString().equalsIgnoreCase("Stop")));
                if (dogruEylem && a.actionIntent != null) {
                    try {
                        a.actionIntent.send();
                        Log.i(TAG, "Ana birim sunucusu kapatıldı");
                        return true;
                    } catch (PendingIntent.CanceledException e) {
                        Log.w(TAG, "Stop düğmesi çalışmadı", e);
                    }
                }
            }
        }
        return false;
    }

    /**
     * Bildirimdeki "Stop" düğmesinin Android Auto'nun o anki dilindeki yazısı. Kaynak adı
     * bulunamazsa (Android Auto güncellemesi) İngilizceye düşer.
     */
    private String durdurYazisi() {
        try {
            android.content.res.Resources r = getPackageManager().getResourcesForApplication(GEARHEAD);
            int id = r.getIdentifier("developer_head_unit_server_notification_stop", "string", GEARHEAD);
            if (id != 0) return r.getString(id);
        } catch (Exception ignored) {}
        return "Stop";
    }

    /** Araba Bluetooth'u kopunca: oturum bitmiş say, beklemeden kapat. */
    static void arabadanAyrildi(Context c) {
        OturumIzleyici o = calisan;
        if (o != null) {
            o.handler.post(o::sunucuyuDurdur);
        } else {
            // Dinleyici çalışmıyor: isteği kaydet, bağlanınca yerine getirsin.
            Ayarlar.prefs(c).edit().putLong("durdur_istegi", System.currentTimeMillis()).apply();
            yenidenBagla(c);
        }
    }

    static ComponentName bilesen(Context c) {
        return new ComponentName(c, OturumIzleyici.class);
    }

    /**
     * Bildirim erişimi verilmiş mi. Not: bu ROM'da ayar satırını yazmak erişimi açmıyor;
     * kullanıcı Ayarlar'dan açmalı ya da bir kez adb ile
     * `cmd notification allow_listener <paket>/<sınıf>` çalıştırılmalı.
     */
    static boolean izinVar(Context c) {
        return c.getSystemService(android.app.NotificationManager.class)
                .isNotificationListenerAccessGranted(bilesen(c));
    }
}
