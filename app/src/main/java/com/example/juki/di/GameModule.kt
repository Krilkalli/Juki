package com.example.juki.di

import android.os.SystemClock
import com.example.juki.domain.model.GameSettings
import com.example.juki.game.GameClock
import com.example.juki.game.GameViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val gameModule = module {
    single<GameClock> { GameClock { SystemClock.uptimeMillis() } }
    viewModel { (settings: GameSettings, userId: Long, difficulty: Int) ->
        GameViewModel(
            settings = settings,
            userId = userId,
            difficulty = difficulty,
            goldRepository = get(),
            scoreRepository = get(),
            applicationScope = get(named(APPLICATION_SCOPE)),
            clock = get(),
            savedState = get(),
            campaignRepository = get()
        )
    }
}
