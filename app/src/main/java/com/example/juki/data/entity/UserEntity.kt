package com.example.juki.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val name: String,
    val surname: String,
    val otchestvo: String,
    val course: Int,
    val difficulty: Int,
    val birthDay: Int,
    val birthMonth: Int,
    val birthYear: Int,
    val gender: String,
    val zodiac: String
)