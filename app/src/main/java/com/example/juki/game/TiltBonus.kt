package com.example.juki.game

import kotlin.math.hypot

data class TiltBonus(
    val x: Float,
    val y: Float,
    val radius: Float = 55f
) {
    fun contains(touchX: Float, touchY: Float): Boolean {
        return hypot(touchX - x, touchY - y) <= radius
    }

    companion object {
        const val SPAWN_INTERVAL_SEC = 15f
        const val EFFECT_DURATION_SEC = 5f
    }
}