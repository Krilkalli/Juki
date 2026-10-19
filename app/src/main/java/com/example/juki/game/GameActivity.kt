package com.example.juki.game

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.juki.MyApplication
import com.example.juki.domain.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.juki.domain.model.GameSettings
import com.example.juki.R
import kotlin.math.ceil

class GameActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var statsText: TextView
    private lateinit var timeText: TextView
    private lateinit var pauseButton: Button
    private lateinit var settings: GameSettings
    private var gameView: BugsGameView? = null
    private var tiltController: TiltController? = null
    private var sensorWarningShown = false

    private val scoreRepository get() = (application as MyApplication).scoreRepository
    private var userId = 0L
    private var difficulty = 0
    private var recordId = newRecordId()
    private var savingScore = false
    private var resultSaved = false

    private var score = 0
    private var hits = 0
    private var misses = 0
    private var remainingMs = 0L
    private var paused = false
    private var ended = false
    private var foreground = false
    private var lastFrame = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (!foreground || paused || ended) return

            val elapsed = advanceClock()
            if (remainingMs == 0L) {
                finishRound()
                return
            }

            gameView?.tick(elapsed / 1000f)
            updateLabels()
            handler.postDelayed(this, 20L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        userId = intent.getLongExtra("userId", 0)
        difficulty = intent.getIntExtra("difficulty", 0)
        if (userId <= 0) {
            Toast.makeText(this, R.string.select_player_first, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        recordId = savedInstanceState?.getLong("recordId") ?: recordId
        resultSaved = savedInstanceState?.getBoolean("resultSaved") ?: false

        settings = GameSettings(
            gameSpeed = intent.getIntExtra("speed", 50).coerceIn(1, 100),
            maxTarakani = intent.getIntExtra("maxBugs", 5).coerceIn(1, 20),
            bonusIntervalSec = intent.getIntExtra("bonus", 10).coerceIn(1, 30),
            roundDurationSec = intent.getIntExtra("duration", 60).coerceIn(1, 120)
        )
        score = savedInstanceState?.getInt("score") ?: 0
        hits = savedInstanceState?.getInt("hits") ?: 0
        misses = savedInstanceState?.getInt("misses") ?: 0
        paused = savedInstanceState?.getBoolean("paused") ?: false
        ended = savedInstanceState?.getBoolean("ended") ?: false
        remainingMs = savedInstanceState?.getLong("remaining")
            ?: settings.roundDurationSec * 1000L

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        setContentView(root)
        tiltController = TiltController(
            this,
            rotation = { root.display?.rotation ?: Surface.ROTATION_0 },
            onTilt = { x, y -> gameView?.setTilt(x, y) }
        )

        val padding = dp(12)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                padding + bars.left,
                padding + bars.top,
                padding + bars.right,
                padding + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)

        if (ended || remainingMs == 0L) {
            ended = true
            showResult()
        } else {
            showGame()
        }
    }

    private fun showGame() {
        root.removeAllViews()
        statsText = createText("", 18f)
        timeText = createText("", 18f)
        root.addView(statsText)
        root.addView(timeText)

        val field = BugsGameView(this).apply {
            configure(settings)
            onHit = { points ->
                if (acceptHit()) {
                    score += points
                    hits++
                    updateLabels()
                }
            }
            onMiss = {
                if (acceptHit()) {
                    score -= GameResult.MISS_PENALTY
                    misses++
                    updateLabels()
                }
            }
            prepareField()
        }
        gameView = field
        root.addView(
            field,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        pauseButton = Button(this).apply {
            text = getString(
                if (paused) R.string.lab4_resume else R.string.lab4_pause
            )
            setOnClickListener { togglePause() }
        }
        root.addView(pauseButton)
        addButton(getString(R.string.lab4_menu)) { finish() }
        updateLabels()
    }

    private fun acceptHit(): Boolean {
        if (!foreground || paused || ended) return false
        // Reject taps after the deadline even if the next frame is delayed.
        val elapsed = (SystemClock.uptimeMillis() - lastFrame).coerceAtLeast(0L)
        if (elapsed >= remainingMs) {
            remainingMs = 0L
            finishRound()
            return false
        }
        return true
    }

    private fun advanceClock(): Long {
        val now = SystemClock.uptimeMillis()
        val elapsed = (now - lastFrame).coerceAtLeast(0L)
        lastFrame = now
        remainingMs = (remainingMs - elapsed).coerceAtLeast(0L)
        return elapsed
    }

    private fun togglePause() {
        if (!paused) {
            advanceClock()
            if (remainingMs == 0L) {
                finishRound()
                return
            }
            paused = true
            handler.removeCallbacks(ticker)
            tiltController?.stop()
            gameView?.setInputEnabled(false)
            updateLabels()
        } else {
            paused = false
            startLoop()
        }
        pauseButton.text = getString(
            if (paused) R.string.lab4_resume else R.string.lab4_pause
        )
    }

    private fun startLoop() {
        handler.removeCallbacks(ticker)
        val active = foreground && !paused && !ended && gameView != null
        gameView?.setInputEnabled(active)
        if (active) {
            if (tiltController?.start() != true && !sensorWarningShown) {
                sensorWarningShown = true
                Toast.makeText(this, R.string.tilt_sensor_unavailable, Toast.LENGTH_LONG).show()
            }
            lastFrame = SystemClock.uptimeMillis()
            handler.post(ticker)
        } else {
            tiltController?.stop()
        }
    }

    private fun updateLabels() {
        statsText.text = getString(R.string.lab4_stats, score, hits, misses)
        val seconds = ceil(remainingMs / 1000.0).toInt()
        timeText.text = getString(R.string.lab4_time, seconds)
    }

    private fun finishRound() {
        ended = true
        handler.removeCallbacks(ticker)
        gameView?.setInputEnabled(false)
        showResult()
    }

    private fun showResult() {
        tiltController?.stop()
        gameView?.onHit = null
        gameView?.onMiss = null
        gameView = null
        root.removeAllViews()

        val result = GameResult(score, hits, misses)
        root.addView(createText(getString(R.string.lab4_result_title), 26f))
        root.addView(
            createText(
                getString(
                    R.string.lab4_result,
                    result.score,
                    result.hits,
                    result.misses,
                    result.accuracy
                ),
                22f
            )
        )
        val saveStatus = createText("", 16f)
        root.addView(saveStatus)
        val saveAgain = Button(this).apply {
            setText(R.string.score_save_retry)
            visibility = android.view.View.GONE
            setOnClickListener { saveResult(saveStatus, this) }
        }
        root.addView(saveAgain)
        saveResult(saveStatus, saveAgain)
        addButton(getString(R.string.lab4_retry)) {
            recordId = newRecordId()
            resultSaved = false
            savingScore = false
            score = 0
            hits = 0
            misses = 0
            paused = false
            ended = false
            remainingMs = settings.roundDurationSec * 1000L
            showGame()
            startLoop()
        }
        addButton(getString(R.string.lab4_menu)) { finish() }
    }

    private fun saveResult(status: TextView, retry: Button) {
        if (resultSaved) {
            status.setText(R.string.score_saved)
            return
        }
        if (savingScore) return
        savingScore = true
        status.setText(R.string.score_saving)
        retry.visibility = android.view.View.GONE
        val savedRecordId = recordId
        val finalScore = score
        val application = application as MyApplication
        val saving = application.applicationScope.async(Dispatchers.IO) {
            scoreRepository.save(userId, finalScore, difficulty, savedRecordId)
        }
        lifecycleScope.launch {
            try {
                saving.await()
                if (recordId == savedRecordId) {
                    resultSaved = true
                    status.setText(R.string.score_saved)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (recordId == savedRecordId) {
                    status.setText(R.string.score_save_error)
                    retry.visibility = android.view.View.VISIBLE
                }
            } finally {
                if (recordId == savedRecordId) savingScore = false
            }
        }
    }

    private fun createText(value: String, size: Float): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setPadding(0, dp(8), 0, dp(8))
        }
    }

    private fun addButton(label: String, action: () -> Unit) {
        root.addView(
            Button(this).apply {
                text = label
                setOnClickListener { action() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        startLoop()
    }

    override fun onPause() {
        if (foreground && !paused && !ended) {
            advanceClock()
            if (remainingMs == 0L) finishRound()
        }
        foreground = false
        handler.removeCallbacks(ticker)
        tiltController?.stop()
        gameView?.setInputEnabled(false)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        tiltController?.stop()
        gameView?.onHit = null
        gameView?.onMiss = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (foreground && !paused && !ended) {
            advanceClock()
            if (remainingMs == 0L) finishRound()
        }
        outState.putLong("recordId", recordId)
        outState.putBoolean("resultSaved", resultSaved)
        outState.putInt("score", score)
        outState.putInt("hits", hits)
        outState.putInt("misses", misses)
        outState.putLong("remaining", remainingMs)
        outState.putBoolean("paused", paused)
        outState.putBoolean("ended", ended)
        super.onSaveInstanceState(outState)
    }

    companion object {
        // Отрицательные ключи раундов не пересекаются с автоинкрементом Room.
        // При пересоздании экрана ключ восстанавливается, поэтому дубль игнорируется.
        private fun newRecordId(): Long = UUID.randomUUID().mostSignificantBits or Long.MIN_VALUE

        fun start(context: Context, settings: GameSettings, user: User) {
            val intent = Intent(context, GameActivity::class.java).apply {
                putExtra("userId", user.id)
                putExtra("difficulty", user.difficulty)
                putExtra("speed", settings.gameSpeed)
                putExtra("maxBugs", settings.maxTarakani)
                putExtra("bonus", settings.bonusIntervalSec)
                putExtra("duration", settings.roundDurationSec)
            }
            context.startActivity(intent)
        }
    }
}
