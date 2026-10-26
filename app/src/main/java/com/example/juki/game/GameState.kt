package com.example.juki.game

data class GameState(
    val score: Int,
    val hits: Int,
    val misses: Int,
    val remainingMs: Long,
    val paused: Boolean,
    val ended: Boolean,
    val fieldWidth: Float,
    val fieldHeight: Float,
    val bugs: List<Bug>,
    val bonus: TiltBonus?,
    val bonusElapsedSec: Float,
    val goldElapsedSec: Float,
    val tiltRemainingSec: Float,
    val goldRewardPoints: Int?
) {
    val result: GameResult
        get() = GameResult(score, hits, misses)
}
