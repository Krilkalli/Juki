package com.example.juki.game

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.juki.domain.model.GameSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.core.parameter.parametersOf

@RunWith(AndroidJUnit4::class)
class GameViewModelKoinTest {
    @Test
    fun koinKeepsRoundViewModelWhenActivityIsRecreated() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = GameSettings()
        val intent = Intent(context, GameActivity::class.java).putExtra("userId", 1L)
        lateinit var first: GameViewModel
        lateinit var before: GameState

        ActivityScenario.launch<GameActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                first = activity.getViewModel { parametersOf(settings, 1L, 0) }
                first.setActive(true)
                first.tap(0f, 0f)
                first.togglePause()
                before = first.state.value.game
                assertEquals(-5, before.score)
                assertTrue(before.paused)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val restored = activity.getViewModel<GameViewModel> {
                    parametersOf(settings, 1L, 0)
                }
                val after = restored.state.value.game
                assertSame(first, restored)
                assertEquals(before.score, after.score)
                assertEquals(before.remainingMs, after.remainingMs)
                assertEquals(before.bugs.map { it.id }, after.bugs.map { it.id })
                assertTrue(after.paused)
            }
        }
    }
}
