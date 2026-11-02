package com.example.juki.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.juki.domain.model.GameSettings
import com.example.juki.domain.repository.GoldRateRepository
import com.example.juki.domain.repository.ScoreRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class GameViewModel(
    settings: GameSettings,
    private val userId: Long,
    private val difficulty: Int,
    private val goldRepository: GoldRateRepository,
    private val scoreRepository: ScoreRepository,
    private val applicationScope: CoroutineScope,
    private val clock: GameClock,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val engine = GameEngine(settings).apply {
        savedState.get<GameState>(GAME_STATE)?.let(::restore)
        setGoldRate(goldRepository.rate.value)
    }
    private var recordId = savedState.get<Long>(RECORD_ID) ?: newRecordId()
    private var saveStatus = if (savedState.get<Boolean>(RESULT_SAVED) == true) {
        ScoreSaveStatus.SAVED
    } else {
        ScoreSaveStatus.NOT_STARTED
    }
    private val mutableState = MutableStateFlow(
        GameUiState(engine.snapshot(), goldRepository.rate.value, saveStatus = saveStatus)
    )
    val state = mutableState.asStateFlow()

    // Звук и сообщения не повторяются при повторном подключении экрана.
    private val mutableEvents = MutableSharedFlow<GameEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = mutableEvents.asSharedFlow()

    private var active = false
    private var lastFrame = 0L
    private var ticker: Job? = null
    private var goldRateJob: Job? = null

    init {
        require(userId > 0) { "Для игры нужен зарегистрированный игрок" }
        publish()
        viewModelScope.launch {
            goldRepository.rate.collect { rate ->
                engine.setGoldRate(rate)
                mutableState.update { it.copy(goldRate = rate) }
                publish()
            }
        }
        if (state.value.game.ended) saveResult() else refreshGoldRate()
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        if (!value) {
            advanceClock()
            active = false
            ticker?.cancel()
            ticker = null
            engine.setTilt(0f, 0f)
            publish()
        } else {
            active = true
            startTicker()
        }
    }

    fun togglePause() {
        if (!active) return
        advanceClock()
        if (state.value.game.ended) return
        engine.setPaused(!state.value.game.paused)
        publish()
        startTicker()
    }

    fun tap(x: Float, y: Float): GameEvent {
        if (!active) return GameEvent.Ignored
        // Сначала учитываем время: запоздалое нажатие не должно давать очки.
        advanceClock()
        val event = engine.tap(x, y)
        if (event != GameEvent.Ignored) {
            publish()
            mutableEvents.tryEmit(event)
            if (event == GameEvent.GoldRateUnavailable) refreshGoldRate()
        }
        return event
    }

    fun setTilt(x: Float, y: Float) {
        if (active && !state.value.game.paused && !state.value.game.ended) {
            engine.setTilt(x, y)
        } else {
            engine.setTilt(0f, 0f)
        }
    }

    fun resizeField(width: Float, height: Float) {
        if (state.value.game.fieldWidth == width && state.value.game.fieldHeight == height) return
        advanceClock()
        engine.resizeField(width, height)
        publish()
    }

    fun restart() {
        ticker?.cancel()
        engine.restart()
        engine.setGoldRate(goldRepository.rate.value)
        recordId = newRecordId()
        saveStatus = ScoreSaveStatus.NOT_STARTED
        publish()
        startTicker()
        refreshGoldRate()
    }

    fun refreshGoldRate() {
        if (goldRateJob?.isActive == true) return
        mutableState.update { it.copy(goldRateLoading = true, goldRateError = false) }
        goldRateJob = viewModelScope.launch {
            try {
                goldRepository.refresh()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableState.update { it.copy(goldRateError = true) }
            } finally {
                mutableState.update { it.copy(goldRateLoading = false) }
            }
        }
    }

    fun saveResult() {
        if (!state.value.game.ended || saveStatus == ScoreSaveStatus.SAVED ||
            saveStatus == ScoreSaveStatus.SAVING
        ) return
        saveStatus = ScoreSaveStatus.SAVING
        publish()
        val savedRecordId = recordId
        val finalScore = state.value.game.score
        // Эта задача продолжится даже после закрытия Activity и ViewModel.
        val saving = applicationScope.async {
            scoreRepository.save(userId, finalScore, difficulty, savedRecordId)
        }
        viewModelScope.launch {
            try {
                saving.await()
                if (recordId == savedRecordId) {
                    saveStatus = ScoreSaveStatus.SAVED
                    publish()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (recordId == savedRecordId) {
                    saveStatus = ScoreSaveStatus.ERROR
                    publish()
                }
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = null
        if (!active || state.value.game.paused || state.value.game.ended) return
        lastFrame = clock.nowMillis()
        ticker = viewModelScope.launch {
            while (isActive && active) {
                delay(FRAME_MS)
                advanceClock()
                if (state.value.game.ended) break
            }
        }
    }

    private fun advanceClock() {
        if (!active) return
        val now = clock.nowMillis()
        val elapsed = (now - lastFrame).coerceAtLeast(0L)
        lastFrame = now
        engine.advance(elapsed)
        publish()
        if (state.value.game.ended) saveResult()
    }

    private fun publish() {
        val game = engine.snapshot()
        savedState[GAME_STATE] = game
        savedState[RECORD_ID] = recordId
        savedState[RESULT_SAVED] = saveStatus == ScoreSaveStatus.SAVED
        mutableState.update { it.copy(game = game, saveStatus = saveStatus) }
    }

    companion object {
        private const val FRAME_MS = 20L
        private const val GAME_STATE = "game_state"
        private const val RECORD_ID = "record_id"
        private const val RESULT_SAVED = "result_saved"

        private fun newRecordId(): Long = UUID.randomUUID().mostSignificantBits or Long.MIN_VALUE
    }
}
