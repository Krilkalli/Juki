package com.example.juki.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.juki.domain.model.GameSettings
import com.example.juki.domain.model.GoldRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class GoldBugGameTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun goldenBugAppearsEveryTwentySecondsWithoutExceedingLimit() {
        instrumentation.runOnMainSync {
            val field = createField()
            field.tick(19.99f)
            assertFalse(bugs(field).any { it.type == BugType.GOLD })
            field.tick(0.02f)
            val first = bugs(field).single()
            assertEquals(BugType.GOLD, first.type)

            field.tick(20f)
            val second = bugs(field).single()
            assertEquals(BugType.GOLD, second.type)
            assertNotEquals(first.id, second.id)
        }
    }

    @Test
    fun hittingGoldenBugAwardsCurrentRateAndDoesNotSpawnAnotherEarly() {
        instrumentation.runOnMainSync {
            val field = createField()
            var awarded = 0
            var hits = 0
            var goldAward = 0
            field.onHit = { awarded += it; hits++ }
            field.onGoldHit = { goldAward = it }
            field.tick(20.25f)
            clearBonus(field)
            field.setGoldRate(rate("20000"))
            val golden = bugs(field).single()
            assertEquals(200, golden.points)
            tap(field, golden.x, golden.y)
            assertEquals(200, awarded)
            assertEquals(200, goldAward)
            assertEquals(1, hits)
            assertFalse(bugs(field).any { it.type == BugType.GOLD })

            field.tick(19.70f)
            assertFalse(bugs(field).any { it.type == BugType.GOLD })
            field.tick(0.10f)
            assertEquals(BugType.GOLD, bugs(field).single().type)
            savePreviewIfRequested(field)
        }
    }

    @Test
    fun missingRateKeepsBugAndDoesNotChangeHitsOrMisses() {
        instrumentation.runOnMainSync {
            val field = createField()
            field.setGoldRate(null)
            var unavailable = 0
            var hits = 0
            var misses = 0
            var awarded = 0
            field.onGoldUnavailable = { unavailable++ }
            field.onHit = { hits++; awarded += it }
            field.onMiss = { misses++ }
            field.tick(20f)
            clearBonus(field)
            val golden = bugs(field).single()
            tap(field, golden.x, golden.y)

            assertEquals(1, unavailable)
            assertEquals(0, hits)
            assertEquals(0, misses)
            assertEquals(golden.id, bugs(field).single().id)

            field.setGoldRate(rate("10482.37"))
            tap(field, golden.x, golden.y)
            assertEquals(1, hits)
            assertEquals(105, awarded)
            assertEquals(0, misses)
        }
    }

    @Test
    fun newRoundResetsGoldenSpawnTimer() {
        instrumentation.runOnMainSync {
            val field = createField()
            field.tick(20f)
            assertTrue(bugs(field).any { it.type == BugType.GOLD })
            field.prepareField()
            field.tick(19.99f)
            assertFalse(bugs(field).any { it.type == BugType.GOLD })
            field.tick(0.02f)
            assertEquals(BugType.GOLD, bugs(field).single().type)
        }
    }

    private fun createField() = BugsGameView(context).apply {
        configure(GameSettings(maxTarakani = 1))
        val exact = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
        measure(exact, exact)
        layout(0, 0, 1000, 1000)
        prepareField()
        setGoldRate(rate("10482.37"))
        setInputEnabled(true)
    }

    @Suppress("UNCHECKED_CAST")
    private fun bugs(field: BugsGameView): List<Bug> = BugsGameView::class.java
        .getDeclaredField("bugs").apply { isAccessible = true }.get(field) as List<Bug>

    private fun clearBonus(field: BugsGameView) {
        val bonus = BugsGameView::class.java.getDeclaredField("bonus")
            .apply { isAccessible = true }.get(field) as TiltBonus?
        bonus?.let { tap(field, it.x, it.y) }
    }

    private fun tap(field: BugsGameView, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            MotionEvent.obtain(now, now, action, x, y, 0).also {
                field.onTouchEvent(it)
                it.recycle()
            }
        }
    }

    private fun rate(price: String) = GoldRate("24.10.2026", BigDecimal(price))

    private fun savePreviewIfRequested(field: BugsGameView) {
        if (InstrumentationRegistry.getArguments().getString("saveGoldPreview") != "true") return
        clearBonus(field)
        bugs(field).single().apply {
            x = 500f
            y = 500f
            vx = 0f
            vy = -150f
        }
        val bitmap = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(3.2f, 3.2f)
        canvas.translate(-440f, -440f)
        field.draw(canvas)
        File(context.getExternalFilesDir(null), "gold-bug-preview.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
