package com.example.juki.game

import com.example.juki.domain.model.GoldRate
import com.example.juki.domain.model.CampaignLevel

enum class ScoreSaveStatus { NOT_STARTED, SAVING, SAVED, ERROR }
enum class CampaignAccess { LOADING, ALLOWED, LOCKED, ERROR }

data class GameUiState(
    val game: GameState,
    val goldRate: GoldRate?,
    val goldRateLoading: Boolean = false,
    val goldRateError: Boolean = false,
    val saveStatus: ScoreSaveStatus = ScoreSaveStatus.NOT_STARTED,
    val campaignLevel: CampaignLevel? = null,
    val campaignAccess: CampaignAccess = CampaignAccess.ALLOWED
)
