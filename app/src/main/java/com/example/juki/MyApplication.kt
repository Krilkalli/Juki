package com.example.juki

import android.app.Application
import androidx.room.Room
import com.example.juki.data.local.AppDatabase
import com.example.juki.data.repository.ScoreRepositoryImpl
import com.example.juki.data.repository.UserRepositoryImpl
import com.example.juki.domain.repository.ScoreRepository
import com.example.juki.domain.repository.UserRepository

class MyApplication : Application() {

    private lateinit var database: AppDatabase

    lateinit var userRepository: UserRepository
        private set

    lateinit var scoreRepository: ScoreRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "pmu_game.db"
        ).build()

        userRepository = UserRepositoryImpl(database.userDao())
        scoreRepository = ScoreRepositoryImpl(database.scoreDao())
    }
}