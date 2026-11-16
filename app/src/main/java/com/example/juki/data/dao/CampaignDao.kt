package com.example.juki.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.juki.data.entity.CampaignProgressEntity

@Dao
interface CampaignDao {
    @Query("SELECT * FROM campaign_progress WHERE userId = :userId ORDER BY levelNumber")
    suspend fun getProgress(userId: Long): List<CampaignProgressEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(progress: CampaignProgressEntity)

    @Query("""
        UPDATE campaign_progress SET bestScore = MAX(bestScore, :score)
        WHERE userId = :userId AND levelNumber = :levelNumber
    """)
    suspend fun updateBest(userId: Long, levelNumber: Int, score: Int)

    @Transaction
    suspend fun saveBest(progress: CampaignProgressEntity) {
        insert(progress)
        updateBest(progress.userId, progress.levelNumber, progress.bestScore)
    }
}
