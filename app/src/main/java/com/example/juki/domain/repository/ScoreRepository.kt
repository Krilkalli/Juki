package com.example.juki.domain.repository

interface ScoreRepository {

    suspend fun save(userId: Long, score: Int, difficulty: Int)

    suspend fun getRecords(): List<ScoreRecord>
}