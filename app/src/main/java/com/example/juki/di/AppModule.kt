package com.example.juki.di

import androidx.room.Room
import com.example.juki.data.local.AppDatabase
import com.example.juki.data.repository.ScoreRepositoryImpl
import com.example.juki.data.repository.UserRepositoryImpl
import com.example.juki.domain.repository.ScoreRepository
import com.example.juki.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val APPLICATION_SCOPE = "applicationScope"

val appModule = module {
    single(named(APPLICATION_SCOPE)) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, "pmu_game.db").build()
    }
    single { get<AppDatabase>().userDao() }
    single { get<AppDatabase>().scoreDao() }
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<ScoreRepository> { ScoreRepositoryImpl(get()) }
}
