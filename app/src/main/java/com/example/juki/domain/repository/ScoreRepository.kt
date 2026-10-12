package com.example.juki.domain.repository

import com.example.juki.domain.model.ScoreRecord

interface ScoreRepository {

    suspend fun save(userId: Long, score: Int, difficulty: Int)

    suspend fun getRecords(): List<ScoreRecord>
}