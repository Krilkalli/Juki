package com.example.juki.game

import com.example.juki.domain.model.GameSettings
import com.example.juki.domain.model.GoldRate
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import kotlin.random.Random

class GameEngineTest {
    private fun engine(duration: Int = 60) = GameEngine(
        GameSettings(maxTarakani = 1, roundDurationSec = duration), Random(42)
    )

    @Test
    fun hitAndMissUpdateOneRoundResult() {
        val engine = engine()
        val bug = engine.snapshot().bugs.single()
        assertEquals(GameEvent.BugHit(bug.points, bug.type), engine.tap(bug.x, bug.y))
        assertEquals(GameEvent.Missed, engine.tap(0f, 0f))
        val state = engine.snapshot()
        assertEquals(bug.points - 5, state.score)
        assertEquals(1, state.hits)
        assertEquals(1, state.misses)
        assertEquals(50f, state.result.accuracy, 0f)
    }

    @Test
    fun pauseFreezesRoundAndBonusTimersAndRejectsInput() {
        val engine = engine()
        engine.advance(15_000)
        val bonus = engine.snapshot().bonus!!
        assertEquals(GameEvent.BonusCollected, engine.tap(bonus.x, bonus.y))
        engine.setPaused(true)
        val paused = engine.snapshot()
        engine.advance(30_000)
        assertEquals(GameEvent.Ignored, engine.tap(0f, 0f))
        assertEquals(paused, engine.snapshot())
        engine.setPaused(false)
        engine.advance(1000)
        assertEquals(44_000L, engine.snapshot().remainingMs)
        assertEquals(4f, engine.snapshot().tiltRemainingSec, 0f)
    }

    @Test
    fun resizingPreservesScoreBugIdentityAndTimerProgress() {
        val engine = engine()
        engine.tap(0f, 0f)
        engine.resizeField(1000f, 2000f)
        engine.advance(19_000)
        val before = engine.snapshot()
        engine.resizeField(2200f, 1000f)
        val after = engine.snapshot()
        assertEquals(before.score, after.score)
        assertEquals(before.remainingMs, after.remainingMs)
        assertEquals(before.goldElapsedSec, after.goldElapsedSec, 0f)
        assertEquals(before.bugs.single().id, after.bugs.single().id)
        assertEquals(before.bugs.single().x * 2.2f, after.bugs.single().x, 0.001f)
        assertEquals(before.bugs.single().y / 2f, after.bugs.single().y, 0.001f)
        engine.advance(1000)
        assertEquals(BugType.GOLD, engine.snapshot().bugs.single().type)
    }

    @Test
    fun activeTiltBonusSurvivesResize() {
        val engine = engine()
        engine.advance(15_000)
        val bonus = engine.snapshot().bonus!!
        engine.tap(bonus.x, bonus.y)
        engine.advance(1000)
        engine.resizeField(2000f, 1000f)
        assertEquals(4f, engine.snapshot().tiltRemainingSec, 0f)
        assertNull(engine.snapshot().bonus)
    }

    @Test
    fun goldRateCanArriveAfterBugAppearedWithoutLosingTheBug() {
        val engine = engine()
        engine.advance(20_000)
        engine.snapshot().bonus?.let { engine.tap(it.x, it.y) }
        val golden = engine.snapshot().bugs.single()
        assertEquals(GameEvent.GoldRateUnavailable, engine.tap(golden.x, golden.y))
        assertEquals(0, engine.snapshot().hits)
        engine.setGoldRate(GoldRate("24.10.2026", BigDecimal("10482.37")))
        assertEquals(golden.id, engine.snapshot().bugs.single().id)
        assertEquals(GameEvent.BugHit(105, BugType.GOLD), engine.tap(golden.x, golden.y))
        assertEquals(105, engine.snapshot().score)
        assertEquals(1, engine.snapshot().hits)
    }

    @Test
    fun roundEndsAtZeroAndRejectsFurtherHits() {
        val engine = engine(duration = 1)
        engine.advance(5000)
        val finished = engine.snapshot()
        assertEquals(0L, finished.remainingMs)
        assertTrue(finished.ended)
        assertEquals(GameEvent.Ignored, engine.tap(0f, 0f))
        engine.advance(1000)
        assertEquals(finished, engine.snapshot())
    }

    @Test
    fun snapshotsCannotMutateEngineOrChangePreviousFrames() {
        val engine = engine()
        val first = engine.snapshot()
        val originalX = first.bugs.single().x
        first.bugs.single().x = -999f
        assertEquals(originalX, engine.snapshot().bugs.single().x, 0f)
        val previous = engine.snapshot()
        val previousBug = previous.bugs.single().copy()
        engine.advance(50)
        assertEquals(previousBug, previous.bugs.single())
    }

    @Test
    fun replayResetsScoreTimeAndEffectsButKeepsCurrentGoldRate() {
        val engine = engine()
        engine.setGoldRate(GoldRate("24.10.2026", BigDecimal("10000")))
        engine.tap(0f, 0f)
        engine.advance(20_000)
        engine.setPaused(true)
        engine.restart()
        val state = engine.snapshot()
        assertEquals(0, state.score)
        assertEquals(0, state.hits)
        assertEquals(0, state.misses)
        assertEquals(60_000L, state.remainingMs)
        assertEquals(0f, state.goldElapsedSec, 0f)
        assertEquals(0f, state.bonusElapsedSec, 0f)
        assertEquals(0f, state.tiltRemainingSec, 0f)
        assertEquals(100, state.goldRewardPoints)
        assertFalse(state.paused)
        assertFalse(state.ended)
        assertNull(state.bonus)
        assertFalse(state.bugs.any { it.type == BugType.GOLD })
    }
}
