package com.example.juki.game

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.juki.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.ext.android.getViewModel

@RunWith(AndroidJUnit4::class)
class GameRotationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun hitScoreAndRunningRoundSurviveScreenRotation() {
        launchGame().use { scenario ->
            await(scenario) { it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            lateinit var before: GameState
            lateinit var first: GameViewModel
            scenario.onActivity { activity ->
                first = activity.getViewModel()
                val bug = first.state.value.game.bugs.single()
                tap(activity, bug.x, bug.y)
                before = first.state.value.game
                assertEquals(bug.points, before.score)
                assertEquals(1, before.hits)
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            await(scenario) { it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            scenario.onActivity { activity ->
                val model = activity.getViewModel<GameViewModel>()
                val after = model.state.value.game
                assertSame(first, model)
                assertEquals(before.score, after.score)
                assertEquals(before.hits, after.hits)
                assertEquals(before.bugs.map { it.id }, after.bugs.map { it.id })
                assertTrue(after.remainingMs <= before.remainingMs)
                assertTrue(after.remainingMs > before.remainingMs - 5000)
                assertTrue(after.goldElapsedSec >= before.goldElapsedSec)
                assertEquals(
                    activity.getString(R.string.lab4_stats, after.score, after.hits, after.misses),
                    activity.findViewById<TextView>(R.id.gameStatsText).text.toString()
                )
            }
        }
    }

    @Test
    fun pausedTiltBonusAndTimersSurviveScreenRotation() {
        launchGame().use { scenario ->
            await(scenario, timeoutMs = 20_000) {
                it.getViewModel<GameViewModel>().state.value.game.bonus != null
            }
            lateinit var before: GameState
            scenario.onActivity { activity ->
                val model = activity.getViewModel<GameViewModel>()
                val bonus = model.state.value.game.bonus!!
                tap(activity, bonus.x, bonus.y)
                activity.findViewById<android.widget.Button>(R.id.gamePauseButton).performClick()
                before = model.state.value.game
                assertTrue(before.paused)
                assertTrue(before.tiltRemainingSec > 0f)
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            await(scenario) { it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            scenario.onActivity { activity ->
                val after = activity.getViewModel<GameViewModel>().state.value.game
                assertTrue(after.paused)
                assertEquals(before.remainingMs, after.remainingMs)
                assertEquals(before.tiltRemainingSec, after.tiltRemainingSec, 0f)
                assertEquals(before.bonusElapsedSec, after.bonusElapsedSec, 0f)
                assertEquals(before.goldElapsedSec, after.goldElapsedSec, 0f)
                assertEquals(before.bugs.map { it.id }, after.bugs.map { it.id })
                assertNull(after.bonus)
                assertEquals(
                    activity.getString(R.string.lab4_resume),
                    activity.findViewById<android.widget.Button>(R.id.gamePauseButton).text.toString()
                )
            }
        }
    }

    private fun launchGame(): ActivityScenario<GameActivity> {
        val scenario = ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java).apply {
            putExtra("userId", 1L)
            putExtra("maxBugs", 1)
            putExtra("duration", 120)
        })
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        return scenario
    }

    private fun await(
        scenario: ActivityScenario<GameActivity>,
        timeoutMs: Long = 10_000,
        condition: (GameActivity) -> Boolean
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            var ready = false
            scenario.onActivity {
                ready = (it.findViewById<BugsGameView>(R.id.gameField)?.height ?: 0) > 0 && condition(it)
            }
            if (ready) return
            SystemClock.sleep(50)
        }
        error("Не дождались игрового состояния после поворота")
    }

    private fun tap(activity: GameActivity, x: Float, y: Float) {
        val field = activity.findViewById<BugsGameView>(R.id.gameField)
        val scale = minOf(field.width, field.height) / Bug.FIELD_SIZE
        val now = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            MotionEvent.obtain(now, now, action, x * scale, y * scale, 0).also {
                field.onTouchEvent(it)
                it.recycle()
            }
        }
    }
}
