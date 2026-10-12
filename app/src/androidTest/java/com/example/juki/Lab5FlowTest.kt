package com.example.juki

import android.os.SystemClock
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.viewpager2.widget.ViewPager2
import com.example.juki.domain.model.User
import com.example.juki.game.GameActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Lab5FlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application get() = instrumentation.targetContext.applicationContext as MyApplication

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Timed out waiting for UI/database" }
            SystemClock.sleep(50)
            instrumentation.waitForIdleSync()
        }
    }

    @Test fun registrationSelectionAndFinishedRoundUseRoom() {
        val name = "Игрок${System.nanoTime()}"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitCondition {
                var ready = false
                scenario.onActivity {
                    ready = it.findViewById<Button>(R.id.btnRegister)?.isEnabled == true
                }
                ready
            }
            scenario.onActivity { activity ->
                activity.currentUser = null
                activity.findViewById<Spinner>(R.id.spinnerPlayers).setSelection(0)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.btnStartGame).performClick()
                assertEquals(0, activity.findViewById<ViewPager2>(R.id.viewPager).currentItem)
                activity.findViewById<EditText>(R.id.name).setText(name)
                activity.findViewById<EditText>(R.id.surname).setText("Лабораторная")
                activity.findViewById<Button>(R.id.btnRegister).performClick()
            }
            var registered: User? = null
            awaitCondition {
                scenario.onActivity { if (it.currentUser?.name == name) registered = it.currentUser }
                registered != null
            }
            val user = registered!!
            assertEquals(user, runBlocking { application.userRepository.getById(user.id) })
            scenario.recreate()
            awaitCondition {
                var restored = false
                scenario.onActivity {
                    restored = it.currentUser?.id == user.id &&
                        it.findViewById<Spinner>(R.id.spinnerPlayers)?.isEnabled == true
                }
                restored
            }
            scenario.onActivity { it.findViewById<Spinner>(R.id.spinnerPlayers).setSelection(0) }
            awaitCondition {
                var cleared = false
                scenario.onActivity { cleared = it.currentUser == null }
                cleared
            }
            scenario.onActivity { activity ->
                val spinner = activity.findViewById<Spinner>(R.id.spinnerPlayers)
                val index = (1 until spinner.count).first { spinner.getItemAtPosition(it).toString().contains(name) }
                spinner.setSelection(index)
            }
            awaitCondition {
                var selected = false
                scenario.onActivity { selected = it.currentUser?.id == user.id }
                selected
            }
            scenario.onActivity {
                it.gameSettings = it.gameSettings.copy(roundDurationSec = 1)
                it.findViewById<Button>(R.id.btnStartGame).performClick()
            }
            var game: GameActivity? = null
            awaitCondition {
                instrumentation.runOnMainSync {
                    game = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<GameActivity>().firstOrNull()
                }
                game != null
            }
            awaitCondition { runBlocking { application.scoreRepository.getRecords().any { it.playerName.contains(name) } } }
            val record = runBlocking { application.scoreRepository.getRecords().single { it.playerName.contains(name) } }
            assertEquals(0, record.score)
            assertEquals(user.difficulty, record.difficulty)
            instrumentation.runOnMainSync { game!!.recreate() }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300)
            val database = androidx.room.Room.databaseBuilder(
                instrumentation.targetContext, com.example.juki.data.local.AppDatabase::class.java, "pmu_game.db"
            ).build()
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM scores WHERE userId = ${user.id}").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
            database.close()
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<GameActivity>().forEach { it.finish() }
            }
        }
    }
}
