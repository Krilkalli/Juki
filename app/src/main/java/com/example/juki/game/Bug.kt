package com.example.juki.game

import java.util.UUID
import kotlin.math.hypot

data class Bug(
        val type: BugType,
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val drawableRes: Int,
        val id: String = UUID.randomUUID().toString()
) {
    val radius: Float
        get() = type.radius

    val speed: Float
        get() = hypot(vx, vy)

    val points: Int
        get() = type.points

    fun contains(touchX: Float, touchY: Float): Boolean {
        return hypot(touchX - x, touchY - y) <= radius
    }

    companion object {
        const val FIELD_SIZE = 1000f
    }
}
