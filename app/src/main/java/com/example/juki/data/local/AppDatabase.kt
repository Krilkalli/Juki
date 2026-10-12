package com.example.juki.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.juki.data.dao.ScoreDao
import com.example.juki.data.dao.UserDao
import com.example.juki.data.entity.ScoreEntity
import com.example.juki.data.entity.UserEntity

@Database(
    entities = [UserEntity::class, ScoreEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun scoreDao(): ScoreDao
}