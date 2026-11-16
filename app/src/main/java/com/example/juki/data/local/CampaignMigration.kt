package com.example.juki.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Добавляем таблицу прогресса, не удаляя игроков и рекорды лабораторных работ.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS campaign_progress (
                userId INTEGER NOT NULL,
                levelNumber INTEGER NOT NULL,
                bestScore INTEGER NOT NULL,
                PRIMARY KEY(userId, levelNumber),
                FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE
            )
        """.trimIndent())
    }
}
