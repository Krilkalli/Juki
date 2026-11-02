package com.example.juki.game

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.juki.MyApplication
import com.example.juki.R
import com.example.juki.data.local.AppDatabase
import com.example.juki.domain.model.Player
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.core.context.GlobalContext
import java.io.File

@RunWith(AndroidJUnit4::class)
class GameLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun backgroundFreezesRoundAndRejectsInputUntilReturning() {
        val intent = Intent(context, GameActivity::class.java).apply {
            putExtra("userId", 1L)
            putExtra("maxBugs", 1)
            putExtra("duration", 120)
        }
        ActivityScenario.launch<GameActivity>(intent).use { scenario ->
            await(scenario) { (it.findViewById<BugsGameView>(R.id.gameField)?.height ?: 0) > 0 }
            lateinit var model: GameViewModel
            scenario.onActivity { model = it.getViewModel() }
            scenario.moveToState(Lifecycle.State.CREATED)
            val before = model.state.value.game
            assertEquals(GameEvent.Ignored, model.tap(0f, 0f))
            SystemClock.sleep(1000)
            val background = model.state.value.game
            assertEquals(before.remainingMs, background.remainingMs)
            assertEquals(before.bonusElapsedSec, background.bonusElapsedSec, 0f)
            assertEquals(before.goldElapsedSec, background.goldElapsedSec, 0f)
            assertEquals(before.score, background.score)
            assertEquals(before.bugs, background.bugs)

            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity {
                assertFalse(model.state.value.game.paused)
                assertTrue(model.state.value.game.remainingMs > before.remainingMs - 500)
            }
            await(scenario) { model.state.value.game.remainingMs < before.remainingMs - 100 }
        }
    }

    @Test
    fun resultSurvivesRotationWithoutDuplicateRecordsAndReplayCreatesAnotherRound() {
        val application = context.applicationContext as MyApplication
        val user = runBlocking {
            application.userRepository.register(Player(
                name = "ЛР8${System.nanoTime()}", surname = "Проверка", otchestvo = "",
                course = 2, difficulty = 20, birthDay = 1, birthMonth = 1,
                birthYear = 2000, gender = "Мужчина", zodiac = "Козерог"
            ))
        }
        val database = GlobalContext.get().get<AppDatabase>()
        try {
            val intent = Intent(context, GameActivity::class.java).apply {
                putExtra("userId", user.id)
                putExtra("difficulty", user.difficulty)
                putExtra("maxBugs", 1)
                putExtra("duration", 2)
            }
            ActivityScenario.launch<GameActivity>(intent).use { scenario ->
                await(scenario) { (it.findViewById<BugsGameView>(R.id.gameField)?.height ?: 0) > 0 }
                var earned = 0
                scenario.onActivity { activity ->
                    val model = activity.getViewModel<GameViewModel>()
                    val bug = model.state.value.game.bugs.single()
                    tap(activity, bug.x, bug.y)
                    earned = bug.points
                    assertEquals(earned, model.state.value.game.score)
                }
                awaitSavedResult(scenario)
                val firstRecords = records(database, user.id)
                assertEquals(1, firstRecords.size)
                assertEquals(earned, firstRecords.values.single())
                lateinit var resultText: String
                scenario.onActivity { activity ->
                    resultText = activity.findViewById<TextView>(R.id.gameResultText).text.toString()
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                await(scenario) { activity ->
                    activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                        activity.findViewById<TextView>(R.id.gameResultText) != null
                }
                scenario.recreate()
                await(scenario) {
                    it.findViewById<TextView>(R.id.gameResultText) != null && it.window.decorView.height > 0
                }
                scenario.onActivity { activity ->
                    assertEquals(resultText, activity.findViewById<TextView>(R.id.gameResultText).text.toString())
                    assertEquals(
                        activity.getString(R.string.score_saved),
                        activity.findViewById<TextView>(R.id.gameSaveStatus).text.toString()
                    )
                    savePreviewIfRequested(activity)
                }
                assertEquals(firstRecords, records(database, user.id))

                scenario.onActivity { activity ->
                    activity.findViewById<Button>(R.id.gameRetry).performClick()
                    val game = activity.getViewModel<GameViewModel>().state.value.game
                    assertEquals(0, game.score)
                    assertEquals(0, game.hits)
                    assertEquals(0, game.misses)
                    assertTrue(game.remainingMs in 1L..2000L)
                    assertTrue(game.bonusElapsedSec < 1f)
                    assertTrue(game.goldElapsedSec < 1f)
                    assertEquals(0f, game.tiltRemainingSec, 0f)
                    assertFalse(game.paused)
                    assertFalse(game.ended)
                }
                awaitSavedResult(scenario)
                val bothRecords = records(database, user.id)
                assertEquals(2, bothRecords.size)
                assertTrue(bothRecords.keys.containsAll(firstRecords.keys))
                assertEquals(setOf(earned, 0), bothRecords.values.toSet())
            }
        } finally {
            // Удаляется только созданный этим тестом игрок и его результаты.
            database.openHelper.writableDatabase.execSQL("DELETE FROM users WHERE id = ?", arrayOf(user.id))
        }
    }

    private fun awaitSavedResult(scenario: ActivityScenario<GameActivity>) = await(scenario) { activity ->
        val state = activity.getViewModel<GameViewModel>().state.value
        state.game.ended && state.saveStatus == ScoreSaveStatus.SAVED &&
            activity.findViewById<TextView>(R.id.gameResultText) != null
    }

    private fun records(database: AppDatabase, userId: Long): Map<Long, Int> {
        val rows = linkedMapOf<Long, Int>()
        database.openHelper.readableDatabase.query(
            "SELECT id, score FROM scores WHERE userId = ?", arrayOf(userId)
        ).use { cursor ->
            while (cursor.moveToNext()) rows[cursor.getLong(0)] = cursor.getInt(1)
        }
        return rows
    }

    private fun await(scenario: ActivityScenario<GameActivity>, condition: (GameActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(50)
        }
        error("Не дождались состояния игрового экрана")
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

    private fun savePreviewIfRequested(activity: GameActivity) {
        if (InstrumentationRegistry.getArguments().getString("saveResultPreview") != "true") return
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(context.getExternalFilesDir(null), "lab8-result-landscape.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
