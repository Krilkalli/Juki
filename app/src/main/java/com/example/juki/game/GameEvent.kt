package com.example.juki.game

sealed interface GameEvent {
    data object Ignored : GameEvent
    data object Missed : GameEvent
    data object BonusCollected : GameEvent
    data object GoldRateUnavailable : GameEvent
    data class BugHit(val points: Int, val type: BugType) : GameEvent
}
