package com.example.juki.campaign

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.juki.data.local.AppDatabase
import com.example.juki.data.repository.CampaignRepositoryImpl
import com.example.juki.data.repository.UserRepositoryImpl
import com.example.juki.domain.model.Campaign
import com.example.juki.domain.model.Player
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CampaignRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "campaign-${UUID.randomUUID()}.db"
    private lateinit var database: AppDatabase

    @Before fun open() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
    }

    @After fun close() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private suspend fun player(name: String) = UserRepositoryImpl(database.userDao()).register(Player(
        name = name, surname = "Кампания", otchestvo = "", course = 2, difficulty = 20,
        birthDay = 1, birthMonth = 1, birthYear = 2000, gender = "Мужчина", zodiac = "Козерог"
    ))

    @Test fun progressSurvivesDatabaseReopeningAndOpensLevelsInOrder() = runBlocking {
        val user = player("Первый")
        val repository = CampaignRepositoryImpl(database.campaignDao())
        repository.saveResult(user.id, 1, 100)
        database.close()
        open()
        val restored = CampaignRepositoryImpl(database.campaignDao())
        var progress = restored.getProgress(user.id)
        assertTrue(Campaign.isUnlocked(2, progress))
        assertFalse(Campaign.isUnlocked(3, progress))
        restored.saveResult(user.id, 2, 200)
        progress = restored.getProgress(user.id)
        assertTrue(Campaign.isUnlocked(3, progress))
        restored.saveResult(user.id, 3, 300)
        assertEquals(3, restored.getProgress(user.id).size)
    }

    @Test fun worseReplayCannotReduceBestScoreOrCloseNextLevel() = runBlocking {
        val user = player("Повтор")
        val repository = CampaignRepositoryImpl(database.campaignDao())
        repository.saveResult(user.id, 1, 150)
        repository.saveResult(user.id, 1, -5)
        assertEquals(150, repository.getProgress(user.id).single().bestScore)
        assertTrue(Campaign.isUnlocked(2, repository.getProgress(user.id)))
        repository.saveResult(user.id, 1, 180)
        assertEquals(180, repository.getProgress(user.id).single().bestScore)
    }

    @Test fun playersHaveIndependentProgress() = runBlocking {
        val first = player("Первый")
        val second = player("Второй")
        val repository = CampaignRepositoryImpl(database.campaignDao())
        repository.saveResult(first.id, 1, 100)
        assertTrue(Campaign.isUnlocked(2, repository.getProgress(first.id)))
        assertFalse(Campaign.isUnlocked(2, repository.getProgress(second.id)))
        assertTrue(repository.getProgress(second.id).isEmpty())
    }

    @Test fun failedAttemptCannotUnlockOrSaveLockedLevel() = runBlocking {
        val user = player("Попытка")
        val repository = CampaignRepositoryImpl(database.campaignDao())
        repository.saveResult(user.id, 1, 99)
        try {
            repository.saveResult(user.id, 2, 500)
            fail("Закрытый уровень не должен сохраняться")
        } catch (expected: IllegalArgumentException) {
            assertEquals(99, repository.getProgress(user.id).single().bestScore)
        }
    }
}
