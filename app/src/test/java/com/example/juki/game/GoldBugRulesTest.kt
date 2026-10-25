package com.example.juki.game

import com.example.juki.domain.model.GoldRate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class GoldBugRulesTest {
    @Test
    fun rewardGrowsProportionallyToGoldPrice() {
        assertEquals(100, points("10000"))
        assertEquals(200, points("20000"))
    }

    @Test
    fun rewardUsesDecimalRoundingWithoutTruncatingKopecks() {
        assertEquals(105, points("10482.37"))
        assertEquals(104, points("10449.99"))
        assertEquals(105, points("10450.00"))
    }

    @Test
    fun positivePriceAlwaysGivesAtLeastOnePoint() {
        assertEquals(1, points("0.01"))
    }

    private fun points(price: String) = GoldBugRules.pointsFor(
        GoldRate("24.10.2026", BigDecimal(price))
    )
}
