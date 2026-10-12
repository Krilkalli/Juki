package com.example.juki.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scores",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class ScoreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val userId: Long,
    val score: Int,
    val difficulty: Int,
    val createdAt: Long
)