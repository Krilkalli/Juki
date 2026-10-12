package com.example.juki

data class GameSettings(
        val gameSpeed: Int = 50,
        val maxTarakani: Int = 5,
        val bonusIntervalSec: Int = 10,
        val roundDurationSec: Int = 60
)
