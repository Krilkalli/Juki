package com.example.juki.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "campaign_progress",
    primaryKeys = ["userId", "levelNumber"],
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class CampaignProgressEntity(
    val userId: Long,
    val levelNumber: Int,
    val bestScore: Int
)
