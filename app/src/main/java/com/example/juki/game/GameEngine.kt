package com.example.juki.game

import com.example.juki.R
import com.example.juki.domain.model.GameSettings
import com.example.juki.domain.model.GoldRate
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Правила раунда и состояние поля без ссылок на Activity, View и датчики. */
class GameEngine(
    private val settings: GameSettings,
    private val random: Random = Random.Default
) {
    private val maxBugs = settings.maxTarakani.coerceIn(1, 20)
    private val speedFactor = 0.4f + settings.gameSpeed.coerceIn(1, 100) / 50f
    private val bugs = mutableListOf<Bug>()
    private var score = 0
    private var hits = 0
    private var misses = 0
    private var remainingMs = 0L
    private var paused = false
    private var ended = false
    private var fieldWidth = Bug.FIELD_SIZE
    private var fieldHeight = Bug.FIELD_SIZE
    private var bonus: TiltBonus? = null
    private var bonusElapsedSec = 0f
    private var goldElapsedSec = 0f
    private var tiltRemainingSec = 0f
    private var tiltX = 0f
    private var tiltY = 0f
    private var goldPoints: Int? = null

    init {
        restart()
    }

    fun snapshot() = GameState(
        score = score,
        hits = hits,
        misses = misses,
        remainingMs = remainingMs,
        paused = paused,
        ended = ended,
        fieldWidth = fieldWidth,
        fieldHeight = fieldHeight,
        // Снимок не содержит изменяемые объекты самого движка.
        bugs = bugs.map { it.copy() },
        bonus = bonus,
        bonusElapsedSec = bonusElapsedSec,
        goldElapsedSec = goldElapsedSec,
        tiltRemainingSec = tiltRemainingSec,
        goldRewardPoints = goldPoints
    )

    fun restart() {
        score = 0
        hits = 0
        misses = 0
        remainingMs = settings.roundDurationSec.coerceIn(1, 120) * 1000L
        paused = false
        ended = false
        bonus = null
        bonusElapsedSec = 0f
        goldElapsedSec = 0f
        tiltRemainingSec = 0f
        setTilt(0f, 0f)
        bugs.clear()
        repeat(minOf(3, maxBugs)) { spawnBug() }
    }

    fun advance(elapsedMs: Long) {
        if (paused || ended || elapsedMs <= 0L) return
        val playingMs = minOf(elapsedMs, remainingMs)
        remainingMs -= playingMs
        updateField(playingMs / 1000f)
        ended = remainingMs == 0L
    }

    fun setPaused(value: Boolean) {
        if (ended) return
        paused = value
        if (paused) setTilt(0f, 0f)
    }

    fun setTilt(x: Float, y: Float) {
        tiltX = if (x.isFinite()) x.coerceIn(-1f, 1f) else 0f
        tiltY = if (y.isFinite()) y.coerceIn(-1f, 1f) else 0f
    }

    fun setGoldRate(rate: GoldRate?) {
        goldPoints = rate?.let(GoldBugRules::pointsFor)
        bugs.indices.forEach { index ->
            val bug = bugs[index]
            if (bug.type == BugType.GOLD) bugs[index] = bug.copy(points = goldPoints ?: 0)
        }
    }

    fun resizeField(width: Float, height: Float) {
        if (!width.isFinite() || !height.isFinite()) return
        if (width < BONUS_RADIUS * 2 || height < BONUS_RADIUS * 2) return
        bugs.forEach { bug ->
            bug.x = (bug.x / fieldWidth * width).coerceIn(bug.radius, width - bug.radius)
            bug.y = (bug.y / fieldHeight * height).coerceIn(bug.radius, height - bug.radius)
        }
        bonus = bonus?.let {
            it.copy(
                x = (it.x / fieldWidth * width).coerceIn(it.radius, width - it.radius),
                y = (it.y / fieldHeight * height).coerceIn(it.radius, height - it.radius)
            )
        }
        fieldWidth = width
        fieldHeight = height
    }

    fun tap(x: Float, y: Float): GameEvent {
        if (paused || ended || !x.isFinite() || !y.isFinite()) return GameEvent.Ignored
        if (bonus?.contains(x, y) == true) {
            bonus = null
            tiltRemainingSec = TiltBonus.EFFECT_DURATION_SEC
            return GameEvent.BonusCollected
        }
        val index = bugs.indexOfLast { it.contains(x, y) }
        if (index < 0) {
            score -= GameResult.MISS_PENALTY
            misses++
            return GameEvent.Missed
        }
        val bug = bugs[index]
        if (bug.type == BugType.GOLD && goldPoints == null) return GameEvent.GoldRateUnavailable
        bugs.removeAt(index)
        score += bug.points
        hits++
        spawnBug()
        return GameEvent.BugHit(bug.points, bug.type)
    }

    private fun updateField(deltaSeconds: Float) {
        bonusElapsedSec += deltaSeconds
        if (bonusElapsedSec >= TiltBonus.SPAWN_INTERVAL_SEC) {
            bonusElapsedSec %= TiltBonus.SPAWN_INTERVAL_SEC
            bonus = TiltBonus(randomX(BONUS_RADIUS), randomY(BONUS_RADIUS), BONUS_RADIUS)
        }
        goldElapsedSec += deltaSeconds
        if (goldElapsedSec >= GoldBugRules.SPAWN_INTERVAL_SEC) {
            goldElapsedSec %= GoldBugRules.SPAWN_INTERVAL_SEC
            bugs.removeAll { it.type == BugType.GOLD }
            if (bugs.size >= maxBugs) bugs.removeAt(bugs.lastIndex)
            bugs += createBug(BugType.GOLD, goldPoints ?: 0)
        }

        val step = deltaSeconds.coerceAtMost(0.05f)
        val tiltStep = minOf(step, tiltRemainingSec)
        tiltRemainingSec = (tiltRemainingSec - deltaSeconds).coerceAtLeast(0f)
        bugs.forEach { bug ->
            if (random.nextFloat() < step * 2.5f) {
                val angle = atan2(bug.vy, bug.vx) + (random.nextFloat() - 0.5f) * 1.05f
                val speed = bug.speed
                bug.vx = cos(angle) * speed
                bug.vy = sin(angle) * speed
            }
            bug.x += bug.vx * step + tiltX * TILT_DRIFT_SPEED * tiltStep
            bug.y += bug.vy * step + tiltY * TILT_DRIFT_SPEED * tiltStep
            if (bug.x < bug.radius) {
                bug.x = bug.radius
                bug.vx = abs(bug.vx)
            } else if (bug.x > fieldWidth - bug.radius) {
                bug.x = fieldWidth - bug.radius
                bug.vx = -abs(bug.vx)
            }
            if (bug.y < bug.radius) {
                bug.y = bug.radius
                bug.vy = abs(bug.vy)
            } else if (bug.y > fieldHeight - bug.radius) {
                bug.y = fieldHeight - bug.radius
                bug.vy = -abs(bug.vy)
            }
        }
        if (bugs.size < maxBugs && random.nextFloat() < step * 1.5f) spawnBug()
    }

    private fun spawnBug() {
        if (bugs.size >= maxBugs) return
        val type = when (random.nextInt(100)) {
            in 0..69 -> BugType.NORMAL
            in 70..94 -> BugType.FAST
            else -> BugType.RARE
        }
        bugs += createBug(type)
    }

    private fun createBug(type: BugType, points: Int = type.points): Bug {
        val resource = when (type) {
            BugType.NORMAL -> R.drawable.common
            BugType.FAST -> R.drawable.fast
            BugType.RARE -> R.drawable.rare
            BugType.GOLD -> R.drawable.ic_gold_bug
        }
        val angle = random.nextFloat() * (Math.PI * 2).toFloat()
        val speed = type.baseSpeed * speedFactor
        return Bug(
            type = type,
            x = randomX(type.radius),
            y = randomY(type.radius),
            vx = cos(angle) * speed,
            vy = sin(angle) * speed,
            drawableRes = resource,
            points = points
        )
    }

    private fun randomX(radius: Float) = radius + random.nextFloat() * (fieldWidth - radius * 2)
    private fun randomY(radius: Float) = radius + random.nextFloat() * (fieldHeight - radius * 2)

    companion object {
        private const val BONUS_RADIUS = 55f
        private const val TILT_DRIFT_SPEED = 600f
    }
}
