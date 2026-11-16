package com.example.juki.domain.repository

import com.example.juki.domain.model.CampaignProgress

interface CampaignRepository {
    suspend fun getProgress(userId: Long): List<CampaignProgress>
    suspend fun saveResult(userId: Long, levelNumber: Int, score: Int)
}
