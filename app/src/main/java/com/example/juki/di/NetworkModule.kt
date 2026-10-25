package com.example.juki.di

import com.example.juki.data.remote.CbrApi
import org.koin.dsl.module
import retrofit2.Retrofit

val networkModule = module {
    single {
        Retrofit.Builder()
            .baseUrl("https://www.cbr.ru/")
            .build()
    }
    single { get<Retrofit>().create(CbrApi::class.java) }
}
