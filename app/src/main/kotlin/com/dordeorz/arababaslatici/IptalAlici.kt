package com.dordeorz.arababaslatici

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Bildirimdeki "Hayır" düğmesi. */
class IptalAlici : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        Bildirim.iptal(c)
    }
}
