package com.example.juki.di

import com.example.juki.data.local.GoldRateCache
import com.example.juki.data.remote.GoldRateXmlParser
import com.example.juki.data.repository.GoldRateRepositoryImpl
import com.example.juki.domain.repository.GoldRateRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val goldModule = module {
    single { GoldRateCache(androidContext()) }
    single { GoldRateXmlParser() }
    single<GoldRateRepository> { GoldRateRepositoryImpl(get(), get(), get()) }
}
