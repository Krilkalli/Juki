package com.example.juki.campaign

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.viewpager2.widget.ViewPager2
import com.example.juki.MainActivity
import com.example.juki.MyApplication
import com.example.juki.R
import com.example.juki.data.local.AppDatabase
import com.example.juki.domain.model.Campaign
import com.example.juki.domain.model.Player
import com.example.juki.domain.model.User
import com.example.juki.domain.repository.CampaignRepository
import com.example.juki.game.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class CampaignFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val koin get() = GlobalContext.get()
    private val now = AtomicLong(0)
    private lateinit var originalClock: GameClock
    private lateinit var user: User
    private var previousUserId = 0L

    @Before fun prepare() = runBlocking {
        previousUserId = context.getSharedPreferences("player", 0).getLong("userId", 0)
        originalClock = koin.get()
        // Ускоряем только часы: настоящий экран, движок и Room остаются в работе.
        loadKoinModules(module { single<GameClock> { GameClock { now.get() } } })
        user = (context.applicationContext as MyApplication).userRepository.register(Player(
            name = "Проверка", surname = "Кампания", otchestvo = "", course = 2, difficulty = 20,
            birthDay = 1, birthMonth = 1, birthYear = 2000, gender = "Мужчина", zodiac = "Козерог"
        ))
    }

    @After fun cleanup() {
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<GameActivity>().forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
        loadKoinModules(module { single<GameClock> { originalClock } })
        koin.get<AppDatabase>().openHelper.writableDatabase.execSQL(
            "DELETE FROM users WHERE id = ?", arrayOf(user.id)
        )
        context.getSharedPreferences("player", 0).edit().putLong("userId", previousUserId).commit()
    }

    @Test fun campaignCanBeCompletedInOrderAndReplayedAfterReopeningMenu() {
        ActivityScenario.launch(MainActivity::class.java).use { menu ->
            awaitMenu(menu) { it.findViewById<Button>(R.id.btnStartGame).isEnabled }
            menu.onActivity {
                it.currentUser = user
                it.findViewById<ViewPager2>(R.id.viewPager).setCurrentItem(5, false)
            }
            awaitMenu(menu) { levelButtons(it).size == 3 && levelButtons(it).all { button -> button.width > 0 && button.isShown } }
            menu.onActivity {
                assertTrue(levelButtons(it)[0].isEnabled)
                assertFalse(levelButtons(it)[1].isEnabled)
                assertFalse(levelButtons(it)[2].isEnabled)
                preview(it, "campaign-levels-start.png")
            }

            Campaign.levels.forEach { level ->
                menu.onActivity { levelButtons(it)[level.number - 1].performClick() }
                var game = awaitGame { (it.findViewById<BugsGameView>(R.id.gameField)?.height ?: 0) > 0 }
                instrumentation.runOnMainSync {
                    val model = game.getViewModel<GameViewModel>()
                    assertEquals(level.number, model.state.value.campaignLevel!!.number)
                    repeat(50) { if (model.state.value.game.score < level.targetScore) tapBug(game) }
                    assertTrue(model.state.value.game.score >= level.targetScore)
                    if (level.number == 1) {
                        preview(game, "campaign-game-portrait.png")
                        model.togglePause()
                        game.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                }
                if (level.number == 1) {
                    game = awaitGame {
                        it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                            (it.findViewById<BugsGameView>(R.id.gameField)?.height ?: 0) > 0
                    }
                    instrumentation.runOnMainSync {
                        val model = game.getViewModel<GameViewModel>()
                        assertTrue(model.state.value.game.paused)
                        assertTrue(model.state.value.game.score >= level.targetScore)
                        assertNotNull(game.findViewById<TextView>(R.id.campaignGoal))
                        preview(game, "campaign-game-landscape.png")
                        model.togglePause()
                    }
                }
                finishRound(game)
                awaitGame {
                    it.getViewModel<GameViewModel>().state.value.saveStatus == ScoreSaveStatus.SAVED &&
                        (it.findViewById<TextView>(R.id.campaignResult)?.height ?: 0) > 0
                }
                instrumentation.runOnMainSync {
                    assertEquals(game.getString(R.string.campaign_passed),
                        game.findViewById<TextView>(R.id.campaignResult).text.toString())
                    preview(game, "campaign-result.png")
                    game.finish()
                }
                awaitMenu(menu) {
                    val buttons = levelButtons(it)
                    buttons.size == 3 && buttons[level.number - 1].text.toString().contains("пройден") &&
                        (level.number == 3 || buttons[level.number].isEnabled)
                }
            }
            menu.recreate()
            awaitMenu(menu) { it.findViewById<Button>(R.id.btnStartGame).isEnabled }
            menu.onActivity { it.findViewById<ViewPager2>(R.id.viewPager).setCurrentItem(5, false) }
            awaitMenu(menu) {
                it.findViewById<TextView>(R.id.campaignStatus)?.text == it.getString(R.string.campaign_complete) &&
                    levelButtons(it).size == 3 && levelButtons(it).all { button -> button.width > 0 && button.isShown }
            }
            // Даём закончиться анимации появления Fragment перед снимком экрана.
            SystemClock.sleep(500)
            instrumentation.waitForIdleSync()
            val repository = koin.get<CampaignRepository>()
            val before = runBlocking { repository.getProgress(user.id) }
            menu.onActivity {
                assertTrue(levelButtons(it).all { button -> button.isEnabled })
                preview(it, "campaign-levels-complete.png")
                levelButtons(it)[0].performClick()
            }
            val replay = awaitGame { (it.findViewById<BugsGameView>(R.id.gameField)?.height ?: 0) > 0 }
            instrumentation.runOnMainSync {
                assertEquals(0, replay.getViewModel<GameViewModel>().state.value.game.score)
            }
            finishRound(replay)
            awaitGame { it.getViewModel<GameViewModel>().state.value.saveStatus == ScoreSaveStatus.SAVED }
            instrumentation.runOnMainSync {
                assertTrue(replay.findViewById<TextView>(R.id.campaignResult).text.toString().contains("не пройден"))
                replay.finish()
            }
            assertEquals(before, runBlocking { repository.getProgress(user.id) })
        }
    }

    @Test fun lockedIntentCannotBypassCampaignOrder() {
        val intent = Intent(context, GameActivity::class.java)
            .putExtra("userId", user.id).putExtra("campaignLevel", 3)
        ActivityScenario.launch<GameActivity>(intent).use { scenario ->
            val deadline = SystemClock.uptimeMillis() + 10_000
            var locked = false
            while (!locked && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity {
                    val model = it.getViewModel<GameViewModel>()
                    locked = model.state.value.campaignAccess == CampaignAccess.LOCKED
                    if (locked) {
                        assertNull(it.findViewById<BugsGameView>(R.id.gameField))
                        assertEquals(GameEvent.Ignored, model.tap(0f, 0f))
                    }
                }
                SystemClock.sleep(40)
            }
            assertTrue(locked)
        }
    }

    private fun levelButtons(activity: MainActivity): List<Button> {
        val container = activity.findViewById<LinearLayout>(R.id.campaignLevels) ?: return emptyList()
        return (0 until container.childCount).mapNotNull { container.getChildAt(it) as? Button }
    }

    private fun tapBug(activity: GameActivity) {
        val bug = activity.getViewModel<GameViewModel>().state.value.game.bugs.last()
        val field = activity.findViewById<BugsGameView>(R.id.gameField)
        val scale = minOf(field.width, field.height) / Bug.FIELD_SIZE
        val time = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            MotionEvent.obtain(time, time, action, bug.x * scale, bug.y * scale, 0).also {
                field.onTouchEvent(it)
                it.recycle()
            }
        }
    }

    private fun finishRound(activity: GameActivity) = instrumentation.runOnMainSync {
        now.addAndGet(60_000)
        activity.getViewModel<GameViewModel>().tap(0f, 0f)
    }

    private fun awaitGame(condition: (GameActivity) -> Boolean): GameActivity {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            var ready: GameActivity? = null
            instrumentation.runOnMainSync {
                val game = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<GameActivity>().firstOrNull()
                if (game != null && condition(game)) ready = game
            }
            ready?.let { return it }
            SystemClock.sleep(40)
        }
        error("Не дождались состояния игры кампании")
    }

    private fun awaitMenu(scenario: ActivityScenario<MainActivity>, condition: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(40)
        }
        error("Не дождались списка уровней")
    }

    private fun preview(activity: Activity, name: String) {
        if (InstrumentationRegistry.getArguments().getString("campaignPreview") != "true") return
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
