package com.example.juki.game

import com.example.juki.domain.model.GameSettings
import java.io.Serializable
import kotlin.math.hypot

data class TiltBonus(
    val x: Float,
    val y: Float,
    val radius: Float = 55f
) : Serializable {
    fun contains(touchX: Float, touchY: Float): Boolean { //попало ли нажатие?
        return hypot(touchX - x, touchY - y) <= radius
    }

    companion object {
        const val SPAWN_INTERVAL_SEC = GameSettings.BONUS_INTERVAL_SEC * 1f
        const val EFFECT_DURATION_SEC = 5f
    }
}
