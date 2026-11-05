package com.dordeorz.arababaslatici;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** "Android Auto başlatılsın mı?" bildirimini gösterir ve PhoneAuto'yu açar. */
final class Bildirim {
    static final String KANAL = "araba_baglandi";
    static final int ID = 1;
    static final String PHONEAUTO = "com.tarplay.phonescreen";
    // Bu ekran açılınca PhoneAuto, gerekirse ana birim sunucusunu kendisi başlatıp bağlanıyor.
    static final String PHONEAUTO_EKRAN = "com.google.android.projection.sink.ui.MainActivity";

    private Bildirim() {}

    static Intent phoneAutoIntent() {
        Intent i = new Intent();
        i.setComponent(new ComponentName(PHONEAUTO, PHONEAUTO_EKRAN));
        // Eski bir PhoneAuto ekranı öne getirilmesin, her seferinde temiz başlasın.
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return i;
    }

    static void goster(Context c, String cihazAdi) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(KANAL, "Arabaya bağlanınca",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Araba Bluetooth'una bağlanınca Android Auto'yu başlatmayı sorar");
        nm.createNotificationChannel(ch);

        Intent araEkran = new Intent(c, BaslatActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent baslat = PendingIntent.getActivity(c, 1, araEkran,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent hayir = PendingIntent.getBroadcast(c, 2,
                new Intent(c, IptalAlici.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String baslik = cihazAdi != null ? cihazAdi + " bağlandı" : "Arabaya bağlandın";
        Notification n = new Notification.Builder(c, KANAL)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(baslik)
                .setContentText("Android Auto başlatılsın mı?")
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .setContentIntent(baslat)
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(null, "Başlat", baslat).build())
                .addAction(new Notification.Action.Builder(null, "Hayır", hayir).build())
                .build();
        nm.notify(ID, n);
    }

    static void iptal(Context c) {
        c.getSystemService(NotificationManager.class).cancel(ID);
    }
}
