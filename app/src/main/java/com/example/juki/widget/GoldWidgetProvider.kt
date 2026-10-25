package com.example.juki.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class GoldWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        GoldWidgetRenderer.updateAll(context, GoldWidgetStatus.LOADING)
        GoldWidgetWorker.schedulePeriodic(context)
        GoldWidgetWorker.enqueueRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            GoldWidgetRenderer.updateAll(context, GoldWidgetStatus.LOADING)
            GoldWidgetWorker.enqueueRefresh(context)
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        GoldWidgetRenderer.updateAll(context)
    }

    override fun onDisabled(context: Context) {
        GoldWidgetWorker.cancel(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.example.juki.widget.REFRESH_GOLD"
    }
}
