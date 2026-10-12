package com.example.juki.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.juki.data.entity.ScoreEntity
import com.example.juki.domain.model.ScoreRecord

@Dao
interface ScoreDao {

    @Insert
    suspend fun insert(score: ScoreEntity): Long

    @Query(
        """
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
            ORDER BY s2.score DESC, s2.createdAt DESC
            LIMIT 1
        )
        ORDER BY s.score DESC, s.createdAt DESC
        """
    )
    suspend fun getRecords(): List<ScoreRecord>
}