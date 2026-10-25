package com.example.juki.game

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.juki.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GoldGameLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun ratePanelLeavesEnoughPlayingSpaceInBothOrientations() {
        val intent = Intent(context, GameActivity::class.java).apply {
            // Проверяется экран игры; раунд закрывается до сохранения результата.
            putExtra("userId", 1L)
            putExtra("duration", 120)
        }
        ActivityScenario.launch<GameActivity>(intent).use { scenario ->
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            awaitLayout(scenario, Configuration.ORIENTATION_PORTRAIT)
            scenario.onActivity { activity ->
                val field = activity.findViewById<View>(R.id.gameField)
                val root = activity.findViewById<View>(android.R.id.content)
                val panel = activity.findViewById<TextView>(R.id.goldRateText)
                assertTrue(panel.text.isNotBlank())
                assertTrue(panel.isClickable)
                assertTrue("Портретное поле должно занимать больше половины высоты", field.height > root.height * 0.5f)
                savePreview(activity, "portrait")
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            awaitLayout(scenario, Configuration.ORIENTATION_LANDSCAPE)
            scenario.onActivity { activity ->
                val field = activity.findViewById<View>(R.id.gameField)
                val root = activity.findViewById<View>(android.R.id.content)
                val panel = activity.findViewById<TextView>(R.id.goldRateText)
                val fieldPosition = IntArray(2).also { field.getLocationOnScreen(it) }
                val panelPosition = IntArray(2).also { panel.getLocationOnScreen(it) }
                assertTrue("Блок с курсом должен находиться справа от поля", panelPosition[0] >= fieldPosition[0] + field.width)
                assertTrue("Горизонтальное поле должно занимать большую часть ширины", field.width > root.width * 0.65f)
                assertTrue("Горизонтальное поле должно использовать высоту экрана", field.height > root.height * 0.75f)
                savePreview(activity, "landscape")
            }
        }
    }

    private fun awaitLayout(scenario: ActivityScenario<GameActivity>, orientation: Int) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            var ready = false
            scenario.onActivity {
                ready = it.resources.configuration.orientation == orientation &&
                    (it.findViewById<View>(R.id.gameField)?.height ?: 0) > 0
            }
            if (ready) return
            SystemClock.sleep(50)
        }
        error("Экран игры не успел принять нужную ориентацию")
    }

    private fun savePreview(activity: GameActivity, orientation: String) {
        if (InstrumentationRegistry.getArguments().getString("saveGamePreview") != "true") return
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(context.getExternalFilesDir(null), "gold-game-$orientation.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
