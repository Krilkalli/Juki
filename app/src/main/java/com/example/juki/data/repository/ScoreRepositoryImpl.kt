package com.example.juki.data.repository

import com.example.juki.data.dao.ScoreDao
import com.example.juki.data.entity.ScoreEntity
import com.example.juki.domain.model.ScoreRecord
import com.example.juki.domain.repository.ScoreRepository

class ScoreRepositoryImpl(
    private val scoreDao: ScoreDao
) : ScoreRepository {

    override suspend fun save(userId: Long, score: Int, difficulty: Int) {
        scoreDao.insert(
            ScoreEntity(
                id = 0,
                userId = userId,
                score = score,
                difficulty = difficulty,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun getRecords(): List<ScoreRecord> {
        return scoreDao.getRecords()
    }
}