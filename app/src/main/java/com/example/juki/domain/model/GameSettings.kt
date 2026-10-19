package com.example.juki.domain.model

data class GameSettings(
        val gameSpeed: Int = 50,
        val maxTarakani: Int = 5,
        val bonusIntervalSec: Int = BONUS_INTERVAL_SEC,
        val roundDurationSec: Int = 60
) {
    companion object {
        const val BONUS_INTERVAL_SEC = 15
    }
}
