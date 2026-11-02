package com.example.juki

import android.app.Application
import com.example.juki.di.APPLICATION_SCOPE
import com.example.juki.di.appModule
import com.example.juki.di.gameModule
import com.example.juki.di.goldModule
import com.example.juki.di.networkModule
import com.example.juki.domain.repository.ScoreRepository
import com.example.juki.domain.repository.GoldRateRepository
import com.example.juki.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named

class MyApplication : Application() {

    // Запись результата продолжается, даже если игрок уже вернулся в меню.
    val applicationScope: CoroutineScope by inject(named(APPLICATION_SCOPE))
    val userRepository: UserRepository by inject()
    val scoreRepository: ScoreRepository by inject()
    val goldRateRepository: GoldRateRepository by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MyApplication)
            modules(appModule, networkModule, goldModule, gameModule)
        }
    }
}
