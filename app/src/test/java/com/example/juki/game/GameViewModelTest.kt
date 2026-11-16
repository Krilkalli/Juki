package com.example.juki.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.juki.di.APPLICATION_SCOPE
import com.example.juki.di.gameModule
import com.example.juki.domain.model.GameSettings
import com.example.juki.domain.model.Campaign
import com.example.juki.domain.model.CampaignProgress
import com.example.juki.domain.model.GoldRate
import com.example.juki.domain.model.ScoreRecord
import com.example.juki.domain.repository.GoldRateRepository
import com.example.juki.domain.repository.ScoreRepository
import com.example.juki.domain.repository.CampaignRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private lateinit var scheduler: TestCoroutineScheduler
    private lateinit var applicationScope: CoroutineScope
    private lateinit var gold: FakeGoldRepository
    private lateinit var scores: FakeScoreRepository
    private lateinit var campaign: FakeCampaignRepository
    private val stores = mutableMapOf<GameViewModel, ViewModelStore>()

    @Before
    fun setUp() {
        scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
        gold = FakeGoldRepository()
        scores = FakeScoreRepository()
        campaign = FakeCampaignRepository()
    }

    @After
    fun tearDown() {
        stores.values.forEach { it.clear() }
        stores.clear()
        applicationScope.cancel()
        scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun detachingScreenPreservesRoundAndExcludesBackgroundTime() {
        val vm = createViewModel()
        vm.setActive(true)
        vm.tap(0f, 0f)
        advance(1500)
        vm.setActive(false)
        val before = vm.state.value.game
        advance(10_000)
        assertEquals(before, vm.state.value.game)

        vm.resizeField(2200f, 1000f)
        vm.setActive(true)
        assertEquals(before.score, vm.state.value.game.score)
        assertEquals(before.remainingMs, vm.state.value.game.remainingMs)
        assertEquals(before.bugs.single().id, vm.state.value.game.bugs.single().id)
        advance(1000)
        assertEquals(before.remainingMs - 1000, vm.state.value.game.remainingMs)
    }

    @Test
    fun manualPauseAndActiveBonusSurviveScreenReattachment() {
        val vm = createViewModel()
        vm.setActive(true)
        advance(15_000)
        val bonus = vm.state.value.game.bonus!!
        vm.tap(bonus.x, bonus.y)
        advance(1000)
        vm.togglePause()
        val before = vm.state.value.game
        vm.setActive(false)
        advance(5000)
        vm.resizeField(2000f, 1000f)
        vm.setActive(true)
        advance(1000)
        assertTrue(vm.state.value.game.paused)
        assertEquals(before.remainingMs, vm.state.value.game.remainingMs)
        assertEquals(before.tiltRemainingSec, vm.state.value.game.tiltRemainingSec, 0f)
        assertEquals(GameEvent.Ignored, vm.tap(0f, 0f))

        vm.togglePause()
        advance(1000)
        assertEquals(before.remainingMs - 1000, vm.state.value.game.remainingMs)
    }

    @Test
    fun lateTapCannotEarnPointsAfterRoundDeadline() {
        var now = 0L
        val vm = createViewModel(duration = 1, clock = GameClock { now })
        vm.setActive(true)
        val bug = vm.state.value.game.bugs.single()
        now = 1500L
        assertEquals(GameEvent.Ignored, vm.tap(bug.x, bug.y))
        scheduler.runCurrent()
        assertTrue(vm.state.value.game.ended)
        assertEquals(0, vm.state.value.game.score)
        assertEquals(0, vm.state.value.game.hits)
        assertEquals(1, scores.calls.size)
    }

    @Test
    fun completedRoundIsSavedOnceAcrossReattachmentAndRestoration() {
        val handle = SavedStateHandle()
        val vm = createViewModel(duration = 1, handle = handle)
        vm.setActive(true)
        advance(1000)
        assertEquals(ScoreSaveStatus.SAVED, vm.state.value.saveStatus)
        vm.saveResult()
        vm.setActive(false)
        vm.setActive(true)
        scheduler.runCurrent()
        assertEquals(1, scores.calls.size)

        clear(vm)
        val restored = createViewModel(duration = 1, handle = copyHandle(handle))
        assertTrue(restored.state.value.game.ended)
        assertEquals(ScoreSaveStatus.SAVED, restored.state.value.saveStatus)
        assertEquals(1, scores.calls.size)
    }

    @Test
    fun failedSaveCanRetryWithSameRoundId() {
        scores.failNext = true
        val vm = createViewModel(duration = 1)
        vm.setActive(true)
        advance(1000)
        assertEquals(ScoreSaveStatus.ERROR, vm.state.value.saveStatus)
        vm.saveResult()
        scheduler.runCurrent()
        assertEquals(ScoreSaveStatus.SAVED, vm.state.value.saveStatus)
        assertEquals(2, scores.calls.size)
        assertEquals(scores.calls[0].recordId, scores.calls[1].recordId)
        assertEquals(1, scores.saved.size)
    }

    @Test
    fun savingContinuesAfterViewModelIsCleared() {
        scores.gate = CompletableDeferred()
        val vm = createViewModel(duration = 1)
        vm.setActive(true)
        advance(1000)
        assertEquals(ScoreSaveStatus.SAVING, vm.state.value.saveStatus)
        clear(vm)
        scores.gate!!.complete(Unit)
        scheduler.runCurrent()
        assertEquals(1, scores.saved.size)
    }

    @Test
    fun previousSaveCannotChangeNewRoundStatus() {
        scores.gate = CompletableDeferred()
        val vm = createViewModel(duration = 1)
        vm.setActive(true)
        advance(1000)
        val firstId = scores.calls.single().recordId
        vm.restart()
        vm.tap(0f, 0f)
        scores.gate!!.complete(Unit)
        scheduler.runCurrent()
        assertEquals(ScoreSaveStatus.NOT_STARTED, vm.state.value.saveStatus)
        assertFalse(vm.state.value.game.ended)
        assertEquals(-5, vm.state.value.game.score)

        advance(1000)
        assertEquals(2, scores.saved.size)
        assertNotEquals(firstId, scores.calls.last().recordId)
        assertEquals(ScoreSaveStatus.SAVED, vm.state.value.saveStatus)
    }

    @Test
    fun serializedCheckpointRestoresBugsEffectsCountersAndPause() {
        val handle = SavedStateHandle()
        val vm = createViewModel(handle = handle)
        vm.setActive(true)
        vm.tap(0f, 0f)
        advance(15_000)
        val bonus = vm.state.value.game.bonus!!
        vm.tap(bonus.x, bonus.y)
        advance(1000)
        vm.togglePause()
        vm.setActive(false)
        val before = vm.state.value.game
        val restoredHandle = copyHandle(handle)
        clear(vm)

        val restored = createViewModel(handle = restoredHandle)
        assertEquals(before, restored.state.value.game)
        assertTrue(restored.state.value.game.paused)
        assertTrue(restored.state.value.game.tiltRemainingSec > 0f)
    }

    @Test
    fun goldRefreshSurvivesReattachmentWithoutDuplicateRequests() {
        gold.gate = CompletableDeferred()
        val vm = createViewModel()
        assertTrue(vm.state.value.goldRateLoading)
        assertEquals(1, gold.refreshCount)
        vm.setActive(true)
        vm.setActive(false)
        vm.setActive(true)
        vm.refreshGoldRate()
        assertEquals(1, gold.refreshCount)
        gold.mutableRate.value = GoldRate("25.10.2026", BigDecimal("20000"))
        gold.gate!!.complete(Unit)
        scheduler.runCurrent()
        assertFalse(vm.state.value.goldRateLoading)
        assertEquals(200, vm.state.value.game.goldRewardPoints)
        assertEquals("25.10.2026", vm.state.value.goldRate!!.date)
    }

    @Test
    fun goldPriceAndRewardArePublishedTogether() {
        val vm = createViewModel()
        val observed = mutableListOf<GameUiState>()
        val collector = applicationScope.launch(UnconfinedTestDispatcher(scheduler)) {
            vm.state.collect { observed += it }
        }
        gold.mutableRate.value = GoldRate("25.10.2026", BigDecimal("20000"))
        scheduler.runCurrent()
        assertEquals(200, vm.state.value.game.goldRewardPoints)
        assertTrue(observed.all { state ->
            state.game.goldRewardPoints == state.goldRate?.let(GoldBugRules::pointsFor)
        })
        collector.cancel()
    }

    @Test
    fun koinResolvesViewModelParametersAndDependencies() {
        val container = koinApplication {
            modules(gameModule, module {
                single<GoldRateRepository> { gold }
                single<ScoreRepository> { scores }
                single<CampaignRepository> { campaign }
                single(named(APPLICATION_SCOPE)) { applicationScope }
                single<GameClock> { GameClock { scheduler.currentTime } }
                single { SavedStateHandle() }
            })
        }
        try {
            val vm = container.koin.get<GameViewModel> {
                parametersOf(GameSettings(maxTarakani = 1, roundDurationSec = 9), 7L, 2)
            }
            register(vm)
            scheduler.runCurrent()
            assertEquals(9000L, vm.state.value.game.remainingMs)
            assertEquals(1, vm.state.value.game.bugs.size)
        } finally {
            container.close()
        }
    }

    @Test
    fun lockedCampaignCannotStartOrReceiveTaps() {
        val vm = createViewModel(handle = SavedStateHandle(mapOf("campaignLevel" to 2)))
        vm.setActive(true)
        advance(2000)
        assertEquals(CampaignAccess.LOCKED, vm.state.value.campaignAccess)
        assertEquals(60_000L, vm.state.value.game.remainingMs)
        assertEquals(GameEvent.Ignored, vm.tap(0f, 0f))
        assertTrue(scores.calls.isEmpty())
    }

    @Test
    fun campaignUsesLevelSettingsAndPreservesProgressOnRotation() {
        val handle = SavedStateHandle(mapOf("campaignLevel" to 1))
        val vm = createViewModel(duration = 1, handle = handle)
        vm.setActive(true)
        vm.tap(0f, 0f)
        advance(1000)
        vm.setActive(false)
        val before = vm.state.value.game
        clear(vm)
        val restored = createViewModel(handle = copyHandle(handle))
        assertEquals(before, restored.state.value.game)
        assertEquals(59_000L, before.remainingMs)
        assertEquals(1, restored.state.value.campaignLevel!!.number)
        assertEquals(CampaignAccess.ALLOWED, restored.state.value.campaignAccess)
    }

    @Test
    fun finishedCampaignSavesProgressAndRecreationDoesNotSaveTwice() {
        val round = GameEngine(GameSettings()).snapshot().copy(score = 100, remainingMs = 0, ended = true)
        val handle = SavedStateHandle(mapOf("campaignLevel" to 1, "game_state" to round))
        val vm = createViewModel(handle = handle)
        assertEquals(ScoreSaveStatus.SAVED, vm.state.value.saveStatus)
        assertEquals(listOf(CampaignProgress(1, 100)), campaign.progress)
        assertTrue(Campaign.isUnlocked(2, campaign.progress))
        clear(vm)
        createViewModel(handle = copyHandle(handle))
        assertEquals(1, campaign.saveCount)
        assertEquals(1, scores.saved.size)
    }

    @Test
    fun failedCampaignSaveCanBeRetriedWithoutDuplicateScore() {
        campaign.failNext = true
        val round = GameEngine(GameSettings()).snapshot().copy(score = 100, remainingMs = 0, ended = true)
        val vm = createViewModel(handle = SavedStateHandle(mapOf("campaignLevel" to 1, "game_state" to round)))
        assertEquals(ScoreSaveStatus.ERROR, vm.state.value.saveStatus)
        assertTrue(campaign.progress.isEmpty())
        vm.saveResult()
        scheduler.runCurrent()
        assertEquals(ScoreSaveStatus.SAVED, vm.state.value.saveStatus)
        assertEquals(listOf(CampaignProgress(1, 100)), campaign.progress)
        assertEquals(1, scores.saved.size)
    }

    @Test
    fun ordinaryGameDoesNotChangeCampaignProgress() {
        val vm = createViewModel(duration = 1)
        vm.setActive(true)
        advance(1000)
        assertEquals(ScoreSaveStatus.SAVED, vm.state.value.saveStatus)
        assertTrue(campaign.progress.isEmpty())
        assertEquals(0, campaign.saveCount)
    }

    private fun createViewModel(
        duration: Int = 60,
        handle: SavedStateHandle = SavedStateHandle(),
        clock: GameClock = GameClock { scheduler.currentTime }
    ): GameViewModel = GameViewModel(
        GameSettings(maxTarakani = 1, roundDurationSec = duration),
        7L, 2, gold, scores, applicationScope, clock, handle, campaign
    ).also {
        register(it)
        scheduler.runCurrent()
    }

    private fun register(vm: GameViewModel) {
        stores[vm] = ViewModelStore().apply { put("game", vm) }
    }

    private fun clear(vm: GameViewModel) {
        stores.remove(vm)?.clear()
        scheduler.runCurrent()
    }

    private fun advance(millis: Long) {
        scheduler.advanceTimeBy(millis)
        scheduler.runCurrent()
    }

    private fun copyHandle(handle: SavedStateHandle): SavedStateHandle {
        val values = handle.keys().associateWith { key ->
            val value = handle.get<Any>(key)
            if (value is GameState) {
                val bytes = ByteArrayOutputStream().also { output ->
                    ObjectOutputStream(output).use { it.writeObject(value) }
                }.toByteArray()
                ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
            } else value
        }
        return SavedStateHandle(values)
    }

    private class FakeGoldRepository : GoldRateRepository {
        val mutableRate = MutableStateFlow<GoldRate?>(GoldRate("24.10.2026", BigDecimal("10000")))
        override val rate = mutableRate.asStateFlow()
        var refreshCount = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun refresh(): GoldRate {
            refreshCount++
            gate?.await()
            return requireNotNull(rate.value)
        }
    }

    private data class SaveCall(val userId: Long, val score: Int, val difficulty: Int, val recordId: Long)

    private class FakeCampaignRepository : CampaignRepository {
        val progress = mutableListOf<CampaignProgress>()
        var saveCount = 0
        var failNext = false
        override suspend fun getProgress(userId: Long) = progress.toList()
        override suspend fun saveResult(userId: Long, levelNumber: Int, score: Int) {
            if (failNext) {
                failNext = false
                throw IOException("Ошибка сохранения прогресса")
            }
            saveCount++
            val old = progress.find { it.levelNumber == levelNumber }
            progress.removeAll { it.levelNumber == levelNumber }
            progress += CampaignProgress(levelNumber, maxOf(old?.bestScore ?: score, score))
        }
    }

    private class FakeScoreRepository : ScoreRepository {
        val calls = mutableListOf<SaveCall>()
        val saved = mutableMapOf<Long, SaveCall>()
        var failNext = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun save(userId: Long, score: Int, difficulty: Int, recordId: Long) {
            val call = SaveCall(userId, score, difficulty, recordId)
            calls += call
            gate?.await()
            if (failNext) {
                failNext = false
                throw IOException("Проверка повторного сохранения")
            }
            saved.putIfAbsent(recordId, call)
        }
        override fun observeRecords() = flowOf(emptyList<ScoreRecord>())
        override suspend fun getRecords() = emptyList<ScoreRecord>()
    }
}
