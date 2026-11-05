package com.dordeorz.arababaslatici;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Seçili araba Bluetooth'u bağlanınca bildirimi gösterir, kopunca kaldırır. */
public class BtAlici extends BroadcastReceiver {
    // Bluetooth kısa süreli kopup bağlanırsa aynı bildirimi tekrar tekrar göstermemek için.
    private static final long TEKRAR_SURESI_MS = 2 * 60 * 1000;

    @Override
    public void onReceive(Context c, Intent intent) {
        BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        if (d == null) return;
        SharedPreferences p = Ayarlar.prefs(c);
        if (!Ayarlar.seciliCihazlar(c).contains(d.getAddress())) return;

        String action = intent.getAction();
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            OturumIzleyici.yenidenBagla(c);
            long simdi = System.currentTimeMillis();
            long son = p.getLong("son_bildirim", 0);
            if (simdi - son < TEKRAR_SURESI_MS) return;
            p.edit().putLong("son_bildirim", simdi).apply();
            String ad = null;
            try { ad = d.getName(); } catch (SecurityException ignored) {}
            Bildirim.goster(c, ad);
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            Bildirim.iptal(c);
            OturumIzleyici.arabadanAyrildi(c);
        }
    }
}
