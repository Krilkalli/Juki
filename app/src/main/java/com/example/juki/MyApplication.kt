package com.example.juki

import android.app.Application
import androidx.room.Room
import com.example.juki.data.local.AppDatabase
import com.example.juki.data.repository.ScoreRepositoryImpl
import com.example.juki.data.repository.UserRepositoryImpl
import com.example.juki.di.goldModule
import com.example.juki.di.networkModule
import com.example.juki.domain.repository.ScoreRepository
import com.example.juki.domain.repository.GoldRateRepository
import com.example.juki.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MyApplication : Application() {

    // Запись результата продолжается, даже если игрок уже вернулся в меню.
    val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )

    private lateinit var database: AppDatabase

    lateinit var userRepository: UserRepository
        private set

    lateinit var scoreRepository: ScoreRepository
        private set

    val goldRateRepository: GoldRateRepository by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MyApplication)
            modules(networkModule, goldModule)
        }

        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "pmu_game.db"
        ).build()

        userRepository = UserRepositoryImpl(database.userDao())
        scoreRepository = ScoreRepositoryImpl(database.scoreDao())
    }
}
