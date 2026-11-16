package com.example.juki.domain.model

data class CampaignLevel(
    val number: Int,
    val title: String,
    val targetScore: Int,
    val settings: GameSettings
)

data class CampaignProgress(
    val levelNumber: Int,
    val bestScore: Int
)

object Campaign {
    val levels = listOf(
        CampaignLevel(1, "Разминка", 100, GameSettings(gameSpeed = 40, maxTarakani = 3)),
        CampaignLevel(2, "Быстрые жуки", 200, GameSettings(gameSpeed = 60, maxTarakani = 5)),
        CampaignLevel(3, "Последний рубеж", 300, GameSettings(gameSpeed = 80, maxTarakani = 7))
    )

    fun level(number: Int): CampaignLevel? = levels.find { it.number == number }

    fun isPassed(level: CampaignLevel, score: Int): Boolean = score >= level.targetScore

    fun isUnlocked(number: Int, progress: List<CampaignProgress>): Boolean {
        if (level(number) == null) return false
        return levels.filter { it.number < number }.all { previous ->
            progress.any { it.levelNumber == previous.number && isPassed(previous, it.bestScore) }
        }
    }
}
