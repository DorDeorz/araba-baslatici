package com.dordeorz.arababaslatici

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Seçili araba Bluetooth'u bağlanınca ayara göre sorar ya da doğrudan başlatır; kopunca kapatır. */
class BtAlici : BroadcastReceiver() {

    override fun onReceive(c: Context, intent: Intent) {
        val d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            ?: return
        if (d.address !in Ayarlar.seciliCihazlar(c)) return
        val p = Ayarlar.prefs(c)

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                OturumIzleyici.yenidenBagla(c)
                val simdi = System.currentTimeMillis()
                if (simdi - p.getLong("son_bildirim", 0) < TEKRAR_SURESI_MS) return
                p.edit().putLong("son_bildirim", simdi).apply()
                val ad = try { d.name } catch (_: SecurityException) { null }
                when (Ayarlar.baglaninca(c)) {
                    0 -> Bildirim.goster(c, ad)
                    1 -> {
                        Olaylar.yaz(c, "Araba bağlandı (${ad ?: d.address}): otomatik başlatılıyor")
                        Baslatici.baslat(c)
                    }
                    else -> Unit
                }
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                Bildirim.iptal(c)
                OturumIzleyici.arabadanAyrildi(c)
            }
        }
    }

    private companion object {
        // Bluetooth kısa süreli kopup bağlanırsa aynı bildirimi tekrar tekrar göstermemek için.
        const val TEKRAR_SURESI_MS = 2 * 60 * 1000L
    }
}
