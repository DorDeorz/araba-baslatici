package com.dordeorz.arababaslatici;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Bildirimdeki "Hayır" düğmesi. */
public class IptalAlici extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Bildirim.iptal(c);
    }
}
