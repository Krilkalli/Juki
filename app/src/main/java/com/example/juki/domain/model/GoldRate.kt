package com.example.juki.domain.model

import java.math.BigDecimal

data class GoldRate(
    val date: String,
    val rublesPerGram: BigDecimal
) {
    init {
        require(rublesPerGram > BigDecimal.ZERO) { "Цена золота должна быть положительной" }
    }
}
