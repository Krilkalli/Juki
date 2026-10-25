package com.example.juki.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.juki.MyApplication
import com.example.juki.R
import com.example.juki.domain.model.GoldRate
import java.text.NumberFormat
import java.util.Locale

enum class GoldWidgetStatus { READY, LOADING, ERROR }

object GoldWidgetRenderer {
    fun widgetIds(context: Context): IntArray = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, GoldWidgetProvider::class.java))

    fun updateAll(context: Context, status: GoldWidgetStatus = GoldWidgetStatus.READY) {
        val ids = widgetIds(context)
        if (ids.isEmpty()) return
        val repository = (context.applicationContext as MyApplication).goldRateRepository
        AppWidgetManager.getInstance(context).updateAppWidget(
            ids, createViews(context, repository.rate.value, status)
        )
    }

    internal fun createViews(
        context: Context,
        rate: GoldRate?,
        status: GoldWidgetStatus
    ): RemoteViews {
        val price = rate?.let {
            NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU")).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }.format(it.rublesPerGram)
        } ?: context.getString(R.string.widget_gold_no_price)
        val date = rate?.let { context.getString(R.string.widget_gold_date, it.date) }
            ?: context.getString(R.string.widget_gold_no_data)
        val statusText = context.getString(
            when (status) {
                GoldWidgetStatus.LOADING -> R.string.widget_gold_loading
                GoldWidgetStatus.READY -> R.string.widget_gold_refresh
                GoldWidgetStatus.ERROR -> if (rate == null) {
                    R.string.widget_gold_error_empty
                } else {
                    R.string.widget_gold_error_cached
                }
            }
        )
        val refreshIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, GoldWidgetProvider::class.java)
                .setAction(GoldWidgetProvider.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteViews(context.packageName, R.layout.widget_gold).apply {
            setTextViewText(R.id.widgetGoldPrice, price)
            setTextViewText(R.id.widgetGoldDate, date)
            setTextViewText(R.id.widgetGoldStatus, statusText)
            setContentDescription(
                R.id.widgetGoldRoot,
                context.getString(R.string.widget_gold_content_description, price, date, statusText)
            )
            setOnClickPendingIntent(R.id.widgetGoldRoot, refreshIntent)
        }
    }
}
