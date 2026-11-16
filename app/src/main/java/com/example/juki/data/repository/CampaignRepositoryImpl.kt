package com.example.juki.data.repository

import com.example.juki.data.dao.CampaignDao
import com.example.juki.data.entity.CampaignProgressEntity
import com.example.juki.domain.model.Campaign
import com.example.juki.domain.model.CampaignProgress
import com.example.juki.domain.repository.CampaignRepository

class CampaignRepositoryImpl(private val dao: CampaignDao) : CampaignRepository {
    override suspend fun getProgress(userId: Long): List<CampaignProgress> =
        dao.getProgress(userId).map { CampaignProgress(it.levelNumber, it.bestScore) }

    override suspend fun saveResult(userId: Long, levelNumber: Int, score: Int) {
        require(Campaign.isUnlocked(levelNumber, getProgress(userId))) { "Уровень ещё не открыт" }
        dao.saveBest(CampaignProgressEntity(userId, levelNumber, score))
    }
}
