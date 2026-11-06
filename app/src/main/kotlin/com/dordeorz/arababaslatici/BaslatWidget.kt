package com.dordeorz.arababaslatici

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Ana ekran widget'ı: dokununca Android Auto'yu (sunucu + ekran) başlatır. */
class BaslatWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, yonetici: AppWidgetManager, kimlikler: IntArray) {
        val niyet = PendingIntent.getActivity(c, 1,
            Intent(c, BaslatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE)
        val gorunum = RemoteViews(c.packageName, R.layout.widget_baslat).apply {
            setOnClickPendingIntent(R.id.kok, niyet)
        }
        for (k in kimlikler) yonetici.updateAppWidget(k, gorunum)
    }
}
