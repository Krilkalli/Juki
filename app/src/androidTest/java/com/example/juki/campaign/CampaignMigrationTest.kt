package com.example.juki.campaign

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.juki.data.local.AppDatabase
import com.example.juki.data.local.MIGRATION_1_2
import com.example.juki.data.repository.CampaignRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CampaignMigrationTest {
    @Test fun upgradingLabDatabasePreservesPlayersAndRecords() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-${UUID.randomUUID()}.db"
        // Схема версии 1 из лабораторной 8, до добавления кампании.
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("""
                CREATE TABLE users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL, surname TEXT NOT NULL, otchestvo TEXT NOT NULL,
                    course INTEGER NOT NULL, difficulty INTEGER NOT NULL,
                    birthDay INTEGER NOT NULL, birthMonth INTEGER NOT NULL, birthYear INTEGER NOT NULL,
                    gender TEXT NOT NULL, zodiac TEXT NOT NULL
                )
            """.trimIndent())
            old.execSQL("""
                CREATE TABLE scores (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId INTEGER NOT NULL,
                    score INTEGER NOT NULL, difficulty INTEGER NOT NULL, createdAt INTEGER NOT NULL,
                    FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE
                )
            """.trimIndent())
            old.execSQL("CREATE INDEX index_scores_userId ON scores(userId)")
            old.execSQL("INSERT INTO users VALUES (1, 'Игрок', 'Старый', '', 2, 20, 1, 1, 2000, 'Мужчина', 'Козерог')")
            old.execSQL("INSERT INTO scores VALUES (-1, 1, 250, 20, 1)")
            old.version = 1
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2).build()
        try {
            assertEquals("Игрок", database.userDao().getById(1)!!.name)
            assertEquals(250, database.scoreDao().getRecords().single().score)
            val campaign = CampaignRepositoryImpl(database.campaignDao())
            assertTrue(campaign.getProgress(1).isEmpty())
            campaign.saveResult(1, 1, 100)
            assertEquals(100, campaign.getProgress(1).single().bestScore)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
