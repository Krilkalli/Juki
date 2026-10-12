package com.example.juki

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.juki.data.local.AppDatabase
import com.example.juki.data.repository.ScoreRepositoryImpl
import com.example.juki.data.repository.UserRepositoryImpl
import com.example.juki.domain.model.Player
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RoomRepositoriesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "lab5-test-${UUID.randomUUID()}.db"
    private lateinit var database: AppDatabase

    @Before fun openDatabase() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
    }

    @After fun closeDatabase() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun player(name: String) = Player(
        name = name, surname = "Тест", otchestvo = "", course = 2,
        difficulty = 20, birthDay = 12, birthMonth = 10, birthYear = 2000,
        gender = "Мужчина", zodiac = "Весы"
    )

    @Test fun usersAndScoresSurviveReopeningDatabase() = runBlocking {
        val users = UserRepositoryImpl(database.userDao())
        val user = users.register(player("Кирилл"))
        ScoreRepositoryImpl(database.scoreDao()).save(user.id, 42, user.difficulty, -1)
        database.close()
        openDatabase()
        val restored = UserRepositoryImpl(database.userDao()).getById(user.id)
        assertEquals(user, restored)
        val records = ScoreRepositoryImpl(database.scoreDao()).getRecords()
        assertEquals(1, records.size)
        assertEquals(42, records.single().score)
        assertEquals(20, records.single().difficulty)
        assertEquals("Тест Кирилл", records.single().playerName.trim())
    }

    @Test fun replayCreatesNewScoreButRecreationDoesNotDuplicateRound() = runBlocking {
        val user = UserRepositoryImpl(database.userDao()).register(player("Первый"))
        val scores = ScoreRepositoryImpl(database.scoreDao())
        scores.save(user.id, -5, 20, -1)
        scores.save(user.id, -5, 20, -1)
        scores.save(user.id, 0, 20, -2)
        val cursor = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM scores")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(2, it.getInt(0))
        }
        assertEquals(0, scores.getRecords().single().score)
    }

    @Test fun recordsBelongToTheirPlayersAndKeepTheirBestScores() = runBlocking {
        val users = UserRepositoryImpl(database.userDao())
        val first = users.register(player("Первый"))
        val second = users.register(player("Второй"))
        val scores = ScoreRepositoryImpl(database.scoreDao())
        scores.save(first.id, 50, 20, -1)
        scores.save(first.id, 10, 40, -2)
        scores.save(second.id, 70, 30, -3)
        val records = scores.getRecords()
        assertEquals(listOf(70, 50), records.map { it.score })
        assertEquals(listOf("Тест Второй", "Тест Первый"), records.map { it.playerName.trim() })
        assertEquals(listOf(30, 20), records.map { it.difficulty })
    }

    @Test fun unknownPlayerCannotReceiveScores() = runBlocking {
        try {
            ScoreRepositoryImpl(database.scoreDao()).save(999, 10, 20, -1)
            fail("Expected a foreign key violation")
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            assertTrue(ScoreRepositoryImpl(database.scoreDao()).getRecords().isEmpty())
        }
    }
}
