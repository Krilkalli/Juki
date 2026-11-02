package com.example.juki.game

import com.example.juki.domain.model.GoldRate

enum class ScoreSaveStatus { NOT_STARTED, SAVING, SAVED, ERROR }

data class GameUiState(
    val game: GameState,
    val goldRate: GoldRate?,
    val goldRateLoading: Boolean = false,
    val goldRateError: Boolean = false,
    val saveStatus: ScoreSaveStatus = ScoreSaveStatus.NOT_STARTED
)
