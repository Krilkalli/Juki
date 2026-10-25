package com.example.juki.game

enum class BugType(
        val baseSpeed: Float,
        val radius: Float,
        val points: Int
) {
    NORMAL(
            baseSpeed = 120f,
            radius = 40f,
            points = 10
    ),

    FAST(
            baseSpeed = 240f,
            radius = 30f,
            points = 20
    ),

    RARE(
            baseSpeed = 180f,
            radius = 35f,
            points = 50
    ),

    GOLD(
            baseSpeed = 150f,
            radius = 50f,
            points = 0 // Фактическая награда рассчитывается по курсу золота.
    )
}
