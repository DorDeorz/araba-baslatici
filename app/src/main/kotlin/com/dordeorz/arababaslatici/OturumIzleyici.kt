package com.dordeorz.arababaslatici

import com.dordeorz.arababaslatici.ekran.AapOturum
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlin.concurrent.thread

/**
 * Android Auto'nun bildirimlerini izler. Android Auto oturumu bitince ("Aracınıza bağlandı"
 * bildirimi kalkınca) kısa bir süre bekler, oturum geri gelmezse Android Auto'nun
 * "Head unit server running" bildirimindeki "Stop" düğmesine basarak sunucuyu kapatır.
 */
class OturumIzleyici : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val kapat = Runnable {
        // Kendi ekranımız bağlıyken Android Auto oturum bildirimini kaldırabiliyor; o zaman kapatma.
        if (AapOturum.bagli) Log.i(TAG, "Oturum bildirimi yok ama ekranımız bağlı; sunucu açık kalıyor")
        else if (!oturumVar()) sunucuyuDurdur()
    }

    override fun onListenerConnected() {
        calisan = this
        thread { Erisilebilirlik.ac(this) }

        // Dinleyici ölüyken araba ayrıldıysa şimdi kapat.
        val p = Ayarlar.prefs(this)
        val istek = p.getLong("durdur_istegi", 0)
        if (istek != 0L) {
            p.edit().remove("durdur_istegi").apply()
            if (System.currentTimeMillis() - istek < 5 * 60_000) {
                sunucuyuDurdur()
                return
            }
        }
        // Dinleyici ölüyken oturum bittiyse sunucu açık kalmış olabilir: bekleyip kapat.
        if (!oturumVar() && sunucuBildirimiVar()) {
            Log.i(TAG, "Oturum yok ama sunucu açık; ${BEKLEME_MS / 1000} sn sonra kapatılacak")
            handler.postDelayed(kapat, BEKLEME_MS)
        }
    }

    override fun onListenerDisconnected() {
        if (calisan === this) calisan = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (oturumBildirimi(sbn)) {
            handler.removeCallbacks(kapat)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (oturumBildirimi(sbn)) {
            Log.i(TAG, "Android Auto oturumu bitti; ${BEKLEME_MS / 1000} sn sonra sunucu kapatılacak")
            handler.removeCallbacks(kapat)
            handler.postDelayed(kapat, BEKLEME_MS)
        }
    }

    private fun aktifBildirimler(): Array<StatusBarNotification> =
        try { activeNotifications ?: emptyArray() } catch (_: RuntimeException) { emptyArray() }

    private fun oturumVar(): Boolean = aktifBildirimler().any(::oturumBildirimi)

    private fun sunucuBildirimiVar(): Boolean = aktifBildirimler().any {
        it.packageName == GEARHEAD && it.notification.channelId == SUNUCU_KANALI &&
            it.notification.actions != null
    }

    /** "Head unit server running" bildirimindeki Stop düğmesine basar. */
    fun sunucuyuDurdur(): Boolean {
        for (s in aktifBildirimler()) {
            val n = s.notification
            if (s.packageName != GEARHEAD || n.channelId != SUNUCU_KANALI) continue
            val eylemler = n.actions ?: continue
            val durdur = durdurYazisi()
            for (a in eylemler) {
                // Tek eylem varsa o Stop'tur; birden çoksa yazısına göre seç.
                val baslik = a.title?.toString()
                val dogruEylem = eylemler.size == 1 ||
                    baslik.equals(durdur, ignoreCase = true) || baslik.equals("Stop", ignoreCase = true)
                if (dogruEylem && a.actionIntent != null) {
                    try {
                        a.actionIntent.send()
                        sonDurdurma = android.os.SystemClock.elapsedRealtime()
                        Log.i(TAG, "Ana birim sunucusu kapatıldı")
                        return true
                    } catch (e: PendingIntent.CanceledException) {
                        Log.w(TAG, "Stop düğmesi çalışmadı", e)
                    }
                }
            }
        }
        return false
    }

    /**
     * Bildirimdeki "Stop" düğmesinin Android Auto'nun o anki dilindeki yazısı. Kaynak adı
     * bulunamazsa (Android Auto güncellemesi) İngilizceye düşer.
     */
    private fun durdurYazisi(): String = try {
        val r = packageManager.getResourcesForApplication(GEARHEAD)
        val id = r.getIdentifier("developer_head_unit_server_notification_stop", "string", GEARHEAD)
        if (id != 0) r.getString(id) else "Stop"
    } catch (e: Exception) {
        "Stop"
    }

    companion object {
        const val TAG = "AracBaslatici"
        const val GEARHEAD = "com.google.android.projection.gearhead"

        /** "Aracınıza bağlandı": oturum sürdükçe duran bildirimin kanalı. */
        private const val OTURUM_KANALI = "car.default_notification_channel"

        /** "Head unit server running" bildiriminin kanalı. */
        private const val SUNUCU_KANALI = "gearhead_connection_status"

        /** Oturum kısa süre koparsa sunucuyu hemen kapatmamak için bekleme. */
        const val BEKLEME_MS = 15_000L

        @Volatile private var calisan: OturumIzleyici? = null

        private fun oturumBildirimi(sbn: StatusBarNotification): Boolean =
            sbn.packageName == GEARHEAD && sbn.notification.channelId == OTURUM_KANALI

        /** Sunucu bildirimi görünüyor mu; dinleyici bağlı değilse null (bilinmiyor). */
        fun sunucuAcik(): Boolean? = calisan?.sunucuBildirimiVar()

        fun bilesen(c: Context) = ComponentName(c, OturumIzleyici::class.java)

        /**
         * Bildirim erişimi verilmiş mi. Not: bu ROM'da ayar satırını yazmak erişimi açmıyor;
         * kullanıcı Ayarlar'dan açmalı ya da bir kez adb ile
         * `cmd notification allow_listener <paket>/<sınıf>` çalıştırılmalı.
         */
        fun izinVar(c: Context): Boolean =
            c.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(bilesen(c))

        /**
         * Uygulama son uygulamalardan kaydırılıp öldürülünce sistem dinleyiciyi bir daha
         * kendiliğinden bağlamıyor; uygulama her çalıştığında bunu çağırıp yeniden bağlatıyoruz.
         */
        fun yenidenBagla(c: Context) {
            if (calisan != null || !izinVar(c)) return
            // requestRebind() burada işe yaramıyor (yalnızca requestUnbind sonrası çalışıyor).
            // Bileşeni kapatıp açmak sistemin dinleyiciyi yeniden bağlamasını sağlıyor.
            val pm = c.packageManager
            try {
                pm.setComponentEnabledSetting(bilesen(c),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(bilesen(c),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                Log.i(TAG, "Bildirim dinleyicisi yeniden bağlatılıyor")
            } catch (e: RuntimeException) {
                Log.w(TAG, "Dinleyici yeniden bağlanamadı", e)
            }
        }

        /** Sunucunun en son kapatıldığı an (elapsedRealtime). Android Auto hemen ardından yeniden açılırsa takılıyor. */
        @Volatile var sonDurdurma = 0L

        /** Araba Bluetooth'u kopunca: oturum bitmiş say, beklemeden kapat. */
        fun arabadanAyrildi(c: Context) {
            val o = calisan
            if (o != null) {
                o.handler.post { o.sunucuyuDurdur() }
            } else {
                // Dinleyici çalışmıyor: isteği kaydet, bağlanınca yerine getirsin.
                Ayarlar.prefs(c).edit().putLong("durdur_istegi", System.currentTimeMillis()).apply()
                yenidenBagla(c)
            }
        }
    }
}
