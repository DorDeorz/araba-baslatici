package com.dordeorz.arababaslatici

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Hızlı ayarlar kutucuğu: tek dokunuşla Android Auto'yu (sunucu + ekran) başlatır. */
class BaslatKutucugu : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Android Auto"
            subtitle = "Başlat"
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // eski yol yalnızca Android 13 için
    override fun onClick() {
        val niyet = Intent(this, BaslatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, niyet, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION") // yalnızca Android 13
            startActivityAndCollapse(niyet)
        }
    }
}
