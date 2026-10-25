package com.example.juki.game

import com.example.juki.domain.model.GoldRate
import java.math.BigDecimal
import java.math.RoundingMode

object GoldBugRules {
    const val SPAWN_INTERVAL_SEC = 20f

    // Одно очко за 100 рублей стоимости грамма золота, с округлением до целого.
    fun pointsFor(rate: GoldRate): Int = rate.rublesPerGram
        .divide(BigDecimal("100"), 0, RoundingMode.HALF_UP)
        .intValueExact()
        .coerceAtLeast(1)
}
