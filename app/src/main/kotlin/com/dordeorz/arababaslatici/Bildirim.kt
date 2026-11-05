package com.dordeorz.arababaslatici

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** "Android Auto başlatılsın mı?" bildirimini gösterir ve PhoneAuto'yu açar. */
object Bildirim {
    private const val KANAL = "araba_baglandi"
    private const val ID = 1
    const val PHONEAUTO = "com.tarplay.phonescreen"

    // Bu ekran açılınca PhoneAuto, gerekirse ana birim sunucusunu kendisi başlatıp bağlanıyor.
    const val PHONEAUTO_EKRAN = "com.google.android.projection.sink.ui.MainActivity"

    fun phoneAutoIntent(): Intent = Intent()
        .setComponent(ComponentName(PHONEAUTO, PHONEAUTO_EKRAN))
        // Eski bir PhoneAuto ekranı öne getirilmesin, her seferinde temiz başlasın.
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    fun goster(c: Context, cihazAdi: String?) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val kanal = NotificationChannel(KANAL, "Arabaya bağlanınca", NotificationManager.IMPORTANCE_HIGH)
        kanal.description = "Araba Bluetooth'una bağlanınca Android Auto'yu başlatmayı sorar"
        nm.createNotificationChannel(kanal)

        val bayrak = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val araEkran = Intent(c, BaslatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val baslat = PendingIntent.getActivity(c, 1, araEkran, bayrak)
        val hayir = PendingIntent.getBroadcast(c, 2, Intent(c, IptalAlici::class.java), bayrak)

        val n = Notification.Builder(c, KANAL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(if (cihazAdi != null) "$cihazAdi bağlandı" else "Arabaya bağlandın")
            .setContentText("Android Auto başlatılsın mı?")
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setContentIntent(baslat)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Başlat", baslat).build())
            .addAction(Notification.Action.Builder(null, "Hayır", hayir).build())
            .build()
        nm.notify(ID, n)
    }

    fun iptal(c: Context) {
        c.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
