package com.example.juki.domain.model

import org.junit.Assert.*
import org.junit.Test

class CampaignTest {
    @Test fun newPlayerCanOnlyPlayFirstLevel() {
        assertTrue(Campaign.isUnlocked(1, emptyList()))
        assertFalse(Campaign.isUnlocked(2, emptyList()))
        assertFalse(Campaign.isUnlocked(3, emptyList()))
    }

    @Test fun exactTargetOpensNextLevelButNotTheLast() {
        val progress = listOf(CampaignProgress(1, 100))
        assertTrue(Campaign.isUnlocked(2, progress))
        assertFalse(Campaign.isUnlocked(3, progress))
    }

    @Test fun scoreBelowTargetDoesNotOpenNextLevel() {
        assertFalse(Campaign.isUnlocked(2, listOf(CampaignProgress(1, 99))))
    }

    @Test fun cannotSkipFirstLevelEvenIfSecondResultExists() {
        assertFalse(Campaign.isUnlocked(3, listOf(CampaignProgress(2, 300))))
    }

    @Test fun completedCampaignAllowsReplayingEveryLevel() {
        val progress = Campaign.levels.map { CampaignProgress(it.number, it.targetScore) }
        assertTrue(Campaign.levels.all { Campaign.isUnlocked(it.number, progress) })
    }

    @Test fun unknownLevelIsNeverAvailable() {
        assertFalse(Campaign.isUnlocked(0, emptyList()))
        assertFalse(Campaign.isUnlocked(4, emptyList()))
    }
}
