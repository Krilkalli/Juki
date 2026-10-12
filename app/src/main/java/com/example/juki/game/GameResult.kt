package com.example.juki.game

data class GameResult(
        val score: Int,
        val hits: Int,
        val misses: Int
) {
    val accuracy: Float
        get() {
            val total = hits + misses
            return if (total == 0) {
                0f
            } else {
                hits * 100f / total
            }
        }

    companion object {
        const val MISS_PENALTY = 5
    }
}
