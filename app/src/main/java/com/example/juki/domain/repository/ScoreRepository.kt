package com.example.juki.domain.repository

import com.example.juki.domain.model.ScoreRecord
import kotlinx.coroutines.flow.Flow

interface ScoreRepository {

    // Один recordId соответствует одному раунду: повторный вызов не создаёт дубль.
    suspend fun save(userId: Long, score: Int, difficulty: Int, recordId: Long = 0)

    fun observeRecords(): Flow<List<ScoreRecord>>

    suspend fun getRecords(): List<ScoreRecord>
}