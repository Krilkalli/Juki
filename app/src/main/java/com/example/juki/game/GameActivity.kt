package com.example.juki.game

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.juki.R
import com.example.juki.domain.model.GameSettings
import com.example.juki.domain.model.User
import com.example.juki.widget.GoldWidgetRenderer
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil

class GameActivity : AppCompatActivity() {
    private val viewModel: GameViewModel by viewModel {
        parametersOf(readSettings(), intent.getLongExtra("userId", 0L), intent.getIntExtra("difficulty", 0))
    }
    private lateinit var root: LinearLayout
    private lateinit var statsText: TextView
    private lateinit var timeText: TextView
    private lateinit var goldRateText: TextView
    private lateinit var pauseButton: Button
    private var saveStatusText: TextView? = null
    private var retrySaveButton: Button? = null
    private var gameView: BugsGameView? = null
    private var tiltController: TiltController? = null
    private var bugSoundPlayer: BugSoundPlayer? = null
    private var showingResult: Boolean? = null
    private var previousState: GameUiState? = null
    private var foreground = false
    private var controlsActive = false
    private var sensorWarningShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.getLongExtra("userId", 0L) <= 0) {
            Toast.makeText(this, R.string.select_player_first, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        sensorWarningShown = savedInstanceState?.getBoolean("sensorWarningShown") ?: false
        root = LinearLayout(this)
        setContentView(root)
        val padding = dp(12)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        bugSoundPlayer = BugSoundPlayer(this)
        tiltController = TiltController(
            this,
            rotation = { root.display?.rotation ?: Surface.ROTATION_0 },
            onTilt = viewModel::setTilt
        )
        render(viewModel.state.value)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch {
                    viewModel.events.collect { event ->
                        val game = viewModel.state.value.game
                        if (foreground && !game.paused && !game.ended) handleEvent(event)
                    }
                }
            }
        }
    }

    private fun render(state: GameUiState) {
        val old = previousState
        previousState = state
        val rebuild = showingResult != state.game.ended
        if (rebuild) {
            showingResult = state.game.ended
            if (state.game.ended) showResult(state) else showGame(state)
        }
        if (state.game.ended) {
            renderSaveStatus(state.saveStatus)
        } else {
            gameView?.render(state.game)
            if (rebuild || old == null || old.game.score != state.game.score || old.game.hits != state.game.hits ||
                old.game.misses != state.game.misses
            ) {
                statsText.text = getString(R.string.lab4_stats, state.game.score, state.game.hits, state.game.misses)
            }
            val seconds = ceil(state.game.remainingMs / 1000.0).toInt()
            val oldSeconds = old?.game?.remainingMs?.let { ceil(it / 1000.0).toInt() }
            if (rebuild || seconds != oldSeconds) timeText.text = getString(R.string.lab4_time, seconds)
            if (rebuild || old == null || old.game.paused != state.game.paused) {
                pauseButton.setText(if (state.game.paused) R.string.lab4_resume else R.string.lab4_pause)
            }
            if (rebuild || old == null || old.goldRate != state.goldRate || old.goldRateLoading != state.goldRateLoading ||
                old.goldRateError != state.goldRateError || old.game.goldRewardPoints != state.game.goldRewardPoints
            ) updateGoldLabel(state)
        }
        updateInteraction(state.game)
        if (state.goldRate != null && !state.goldRateLoading && !state.goldRateError &&
            (old == null || old.goldRate != state.goldRate || old.goldRateLoading || old.goldRateError)
        ) GoldWidgetRenderer.updateAll(this)
    }

    private fun showGame(state: GameUiState) {
        detachField()
        root.removeAllViews()
        saveStatusText = null
        retrySaveButton = null
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        root.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        statsText = createText("", if (landscape) 16f else 18f).apply { id = R.id.gameStatsText }
        timeText = createText("", if (landscape) 16f else 18f).apply { id = R.id.gameTimeText }
        goldRateText = createText("", 13f).apply {
            id = R.id.goldRateText
            setTextColor(android.graphics.Color.rgb(94, 61, 11))
            setBackgroundResource(R.drawable.gold_rate_panel)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { viewModel.refreshGoldRate() }
        }
        val field = BugsGameView(this).apply {
            id = R.id.gameField
            onFieldTap = { x, y -> viewModel.tap(x, y) }
            onFieldSizeChanged = viewModel::resizeField
            render(state.game)
        }
        gameView = field
        pauseButton = Button(this).apply {
            id = R.id.gamePauseButton
            setOnClickListener { viewModel.togglePause() }
        }
        if (landscape) {
            root.addView(field, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            val controls = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                addView(statsText)
                addView(timeText)
                addView(goldRateText)
                addView(pauseButton)
            }
            addButton(getString(R.string.lab4_menu), controls) { finish() }
            val sidebar = ScrollView(this).apply {
                isFillViewport = true
                addView(controls)
            }
            root.addView(sidebar, LinearLayout.LayoutParams(dp(176), LinearLayout.LayoutParams.MATCH_PARENT))
        } else {
            root.addView(statsText)
            root.addView(timeText)
            root.addView(goldRateText)
            root.addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            root.addView(pauseButton)
            addButton(getString(R.string.lab4_menu)) { finish() }
        }
    }

    private fun updateGoldLabel(state: GameUiState) {
        val rate = state.goldRate
        val label = if (rate == null) {
            getString(if (state.goldRateLoading) R.string.gold_game_rate_loading else R.string.gold_game_rate_missing)
        } else {
            val price = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU")).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }.format(rate.rublesPerGram)
            val current = getString(R.string.gold_game_rate, price, rate.date, state.game.goldRewardPoints ?: 0)
            if (state.goldRateError) getString(R.string.gold_game_rate_cached, current) else current
        }
        goldRateText.text = label
        goldRateText.alpha = if (state.goldRateLoading && rate != null) 0.65f else 1f
        goldRateText.contentDescription = getString(R.string.gold_game_refresh_description, label)
    }

    private fun updateInteraction(game: GameState) {
        val active = foreground && !game.paused && !game.ended && gameView != null
        gameView?.setInputEnabled(active)
        if (controlsActive == active) return
        controlsActive = active
        if (active) {
            if (tiltController?.start() != true && !sensorWarningShown) {
                sensorWarningShown = true
                Toast.makeText(this, R.string.tilt_sensor_unavailable, Toast.LENGTH_LONG).show()
            }
        } else {
            tiltController?.stop()
            bugSoundPlayer?.stop()
        }
    }

    private fun handleEvent(event: GameEvent) {
        when (event) {
            GameEvent.BonusCollected -> bugSoundPlayer?.play()
            GameEvent.GoldRateUnavailable -> Toast.makeText(this, R.string.gold_bug_wait_for_rate, Toast.LENGTH_SHORT).show()
            is GameEvent.BugHit -> if (event.type == BugType.GOLD) {
                Toast.makeText(this, getString(R.string.gold_bug_hit_reward, event.points), Toast.LENGTH_SHORT).show()
            }
            GameEvent.Ignored, GameEvent.Missed -> Unit
        }
    }

    private fun showResult(state: GameUiState) {
        detachField()
        root.removeAllViews()
        root.orientation = LinearLayout.VERTICAL
        val result = state.game.result
        root.addView(createText(getString(R.string.lab4_result_title), 26f))
        root.addView(createText(
            getString(R.string.lab4_result, result.score, result.hits, result.misses, result.accuracy), 22f
        ).apply { id = R.id.gameResultText })
        saveStatusText = createText("", 16f).apply { id = R.id.gameSaveStatus }
        root.addView(saveStatusText)
        retrySaveButton = Button(this).apply {
            id = R.id.gameRetrySave
            setText(R.string.score_save_retry)
            setOnClickListener { viewModel.saveResult() }
        }
        root.addView(retrySaveButton)
        addButton(getString(R.string.lab4_retry)) { viewModel.restart() }.id = R.id.gameRetry
        addButton(getString(R.string.lab4_menu)) { finish() }
    }

    private fun renderSaveStatus(status: ScoreSaveStatus) {
        saveStatusText?.setText(when (status) {
            ScoreSaveStatus.NOT_STARTED, ScoreSaveStatus.SAVING -> R.string.score_saving
            ScoreSaveStatus.SAVED -> R.string.score_saved
            ScoreSaveStatus.ERROR -> R.string.score_save_error
        })
        retrySaveButton?.visibility = if (status == ScoreSaveStatus.ERROR) View.VISIBLE else View.GONE
    }

    private fun detachField() {
        gameView?.apply {
            setInputEnabled(false)
            onFieldTap = null
            onFieldSizeChanged = null
        }
        gameView = null
    }

    private fun readSettings() = GameSettings(
        gameSpeed = intent.getIntExtra("speed", 50).coerceIn(1, 100),
        maxTarakani = intent.getIntExtra("maxBugs", 5).coerceIn(1, 20),
        roundDurationSec = intent.getIntExtra("duration", 60).coerceIn(1, 120)
    )

    private fun createText(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun addButton(label: String, container: LinearLayout = root, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { action() }
            container.addView(this, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        if (!::root.isInitialized) return
        foreground = true
        viewModel.setActive(true)
        render(viewModel.state.value)
    }

    override fun onPause() {
        if (::root.isInitialized) {
            foreground = false
            viewModel.setActive(false)
            updateInteraction(viewModel.state.value.game)
        }
        super.onPause()
    }

    override fun onDestroy() {
        detachField()
        tiltController?.stop()
        bugSoundPlayer?.release()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("sensorWarningShown", sensorWarningShown)
        super.onSaveInstanceState(outState)
    }

    companion object {
        fun start(context: Context, settings: GameSettings, user: User) {
            context.startActivity(Intent(context, GameActivity::class.java).apply {
                putExtra("userId", user.id)
                putExtra("difficulty", user.difficulty)
                putExtra("speed", settings.gameSpeed)
                putExtra("maxBugs", settings.maxTarakani)
                putExtra("duration", settings.roundDurationSec)
            })
        }
    }
}
