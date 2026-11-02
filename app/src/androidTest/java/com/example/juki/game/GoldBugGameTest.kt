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
import org.junit.Assert.*
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
            field.advance(19_990)
            assertFalse(field.state.bugs.any { it.type == BugType.GOLD })
            field.advance(20)
            val first = field.state.bugs.single()
            assertEquals(BugType.GOLD, first.type)
            field.advance(20_000)
            val second = field.state.bugs.single()
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
            field.onEvent = { event ->
                if (event is GameEvent.BugHit) {
                    awarded += event.points
                    hits++
                    assertEquals(BugType.GOLD, event.type)
                }
            }
            field.advance(20_250)
            field.clearBonus()
            field.setRate("20000")
            val golden = field.state.bugs.single()
            assertEquals(200, golden.points)
            field.tap(golden.x, golden.y)
            assertEquals(200, awarded)
            assertEquals(1, hits)
            assertFalse(field.state.bugs.any { it.type == BugType.GOLD })
            field.advance(19_700)
            assertFalse(field.state.bugs.any { it.type == BugType.GOLD })
            field.advance(100)
            assertEquals(BugType.GOLD, field.state.bugs.single().type)
            savePreviewIfRequested(field)
        }
    }

    @Test
    fun missingRateKeepsBugAndDoesNotChangeHitsOrMisses() {
        instrumentation.runOnMainSync {
            val field = createField()
            field.setRate(null)
            var unavailable = 0
            field.onEvent = { if (it == GameEvent.GoldRateUnavailable) unavailable++ }
            field.advance(20_000)
            field.clearBonus()
            val golden = field.state.bugs.single()
            field.tap(golden.x, golden.y)
            assertEquals(1, unavailable)
            assertEquals(0, field.state.hits)
            assertEquals(0, field.state.misses)
            assertEquals(golden.id, field.state.bugs.single().id)
            field.setRate("10482.37")
            field.tap(golden.x, golden.y)
            assertEquals(1, field.state.hits)
            assertEquals(105, field.state.score)
            assertEquals(0, field.state.misses)
        }
    }

    @Test
    fun newRoundResetsGoldenSpawnTimer() {
        instrumentation.runOnMainSync {
            val field = createField()
            field.advance(20_000)
            assertTrue(field.state.bugs.any { it.type == BugType.GOLD })
            field.engine.restart()
            field.advance(19_990)
            assertFalse(field.state.bugs.any { it.type == BugType.GOLD })
            field.advance(20)
            assertEquals(BugType.GOLD, field.state.bugs.single().type)
        }
    }

    private fun createField(): FieldFixture {
        val fixture = FieldFixture(GameEngine(GameSettings(maxTarakani = 1)), BugsGameView(context))
        fixture.view.apply {
            onFieldTap = { x, y ->
                val event = fixture.engine.tap(x, y)
                fixture.onEvent?.invoke(event)
                render(fixture.state)
            }
            onFieldSizeChanged = { width, height ->
                fixture.engine.resizeField(width, height)
                render(fixture.state)
            }
            val exact = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
            measure(exact, exact)
            layout(0, 0, 1000, 1000)
            setInputEnabled(true)
        }
        fixture.setRate("10482.37")
        return fixture
    }

    private class FieldFixture(val engine: GameEngine, val view: BugsGameView) {
        val state get() = engine.snapshot()
        var onEvent: ((GameEvent) -> Unit)? = null

        fun advance(millis: Long) {
            engine.advance(millis)
            view.render(state)
        }

        fun setRate(price: String?) {
            engine.setGoldRate(price?.let { GoldRate("24.10.2026", BigDecimal(it)) })
            view.render(state)
        }

        fun clearBonus() {
            state.bonus?.let { tap(it.x, it.y) }
        }

        fun tap(x: Float, y: Float) {
            val now = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                MotionEvent.obtain(now, now, action, x, y, 0).also {
                    view.onTouchEvent(it)
                    it.recycle()
                }
            }
        }
    }

    private fun savePreviewIfRequested(field: FieldFixture) {
        if (InstrumentationRegistry.getArguments().getString("saveGoldPreview") != "true") return
        field.clearBonus()
        val game = field.state
        field.view.render(game.copy(bugs = game.bugs.map { it.copy(x = 500f, y = 500f, vx = 0f, vy = -150f) }))
        val bitmap = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(3.2f, 3.2f)
        canvas.translate(-440f, -440f)
        field.view.draw(canvas)
        File(context.getExternalFilesDir(null), "gold-bug-preview.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
