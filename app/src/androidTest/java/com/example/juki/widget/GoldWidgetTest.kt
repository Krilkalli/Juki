package com.example.juki.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.juki.R
import com.example.juki.domain.model.GoldRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class GoldWidgetTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val rate = GoldRate("24.10.2026", BigDecimal("10482.37"))

    @Test
    fun providerIsRegisteredWithHomeScreenMetadata() {
        val component = ComponentName(context, GoldWidgetProvider::class.java)
        val provider = AppWidgetManager.getInstance(context).installedProviders
            .firstOrNull { it.provider == component }

        assertNotNull(provider)
        assertEquals(0, provider!!.updatePeriodMillis)
        assertEquals(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, provider.widgetCategory)
    }

    @Test
    fun remoteViewsInflateAndShowPriceDateAndRoundBall() {
        instrumentation.runOnMainSync {
            val view = render(rate, GoldWidgetStatus.READY)
            val price = view.findViewById<TextView>(R.id.widgetGoldPrice)
            val date = view.findViewById<TextView>(R.id.widgetGoldDate)
            val ball = view.findViewById<ImageView>(R.id.widgetGoldBall)
            assertEquals("10482,37", price.text.toString().replace("\u00a0", "").replace(" ", ""))
            assertTrue(date.text.contains("24.10.2026"))
            assertEquals(ball.width, ball.height)
            assertTrue(price.layout.getEllipsisCount(0) == 0)

            if (InstrumentationRegistry.getArguments().getString("saveWidgetPreview") == "true") {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File(context.getExternalFilesDir(null), "gold-widget-preview.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
    }

    @Test
    fun failedRefreshKeepsCachedPriceAndDateAndExplainsFailure() {
        instrumentation.runOnMainSync {
            val view = render(rate, GoldWidgetStatus.ERROR)
            assertTrue(view.findViewById<TextView>(R.id.widgetGoldPrice).text.contains("482,37"))
            assertTrue(view.findViewById<TextView>(R.id.widgetGoldDate).text.contains(rate.date))
            assertEquals(
                context.getString(R.string.widget_gold_error_cached),
                view.findViewById<TextView>(R.id.widgetGoldStatus).text.toString()
            )
        }
    }

    @Test
    fun failedFirstLoadDoesNotShowAnInventedPrice() {
        instrumentation.runOnMainSync {
            val view = render(null, GoldWidgetStatus.ERROR)
            assertEquals("—", view.findViewById<TextView>(R.id.widgetGoldPrice).text.toString())
            assertEquals(
                context.getString(R.string.widget_gold_error_empty),
                view.findViewById<TextView>(R.id.widgetGoldStatus).text.toString()
            )
        }
    }

    private fun render(rate: GoldRate?, status: GoldWidgetStatus): View {
        val view = GoldWidgetRenderer.createViews(context, rate, status).apply(context, null)
        val side = (180 * context.resources.displayMetrics.density).toInt()
        val exact = View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY)
        view.measure(exact, exact)
        view.layout(0, 0, side, side)
        return view
    }
}
