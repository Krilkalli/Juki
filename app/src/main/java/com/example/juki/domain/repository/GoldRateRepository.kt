package com.example.juki.domain.repository

import com.example.juki.domain.model.GoldRate
import kotlinx.coroutines.flow.StateFlow

interface GoldRateRepository {
    // Последний успешно полученный курс остаётся доступным без интернета.
    val rate: StateFlow<GoldRate?>

    suspend fun refresh(): GoldRate
}
