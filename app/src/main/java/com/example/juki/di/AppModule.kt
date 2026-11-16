package com.example.juki.di

import androidx.room.Room
import com.example.juki.data.local.AppDatabase
import com.example.juki.data.local.MIGRATION_1_2
import com.example.juki.data.repository.CampaignRepositoryImpl
import com.example.juki.data.repository.ScoreRepositoryImpl
import com.example.juki.data.repository.UserRepositoryImpl
import com.example.juki.domain.repository.ScoreRepository
import com.example.juki.domain.repository.CampaignRepository
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
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, "pmu_game.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }
    single { get<AppDatabase>().userDao() }
    single { get<AppDatabase>().scoreDao() }
    single { get<AppDatabase>().campaignDao() }
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<ScoreRepository> { ScoreRepositoryImpl(get()) }
    single<CampaignRepository> { CampaignRepositoryImpl(get()) }
}
