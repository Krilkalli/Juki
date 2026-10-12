package com.example.juki.data.dao

import androidx.room.OnConflictStrategy
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.juki.data.entity.ScoreEntity
import com.example.juki.domain.model.ScoreRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoreDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(score: ScoreEntity): Long

    @Query(RECORDS_QUERY)
    suspend fun getRecords(): List<ScoreRecord>

    @Query(RECORDS_QUERY)
    fun observeRecords(): Flow<List<ScoreRecord>>

    companion object {
        const val RECORDS_QUERY = """
        SELECT u.surname || ' ' || u.name || ' ' || u.otchestvo AS playerName,
               s.score,
               s.difficulty,
               s.createdAt
        FROM scores AS s
        INNER JOIN users AS u ON u.id = s.userId
        WHERE s.id = (
            SELECT s2.id
            FROM scores AS s2
            WHERE s2.userId = s.userId
            ORDER BY s2.score DESC, s2.createdAt DESC, s2.id DESC
            LIMIT 1
        )
        ORDER BY s.score DESC, s.createdAt DESC
        """
    }
}
