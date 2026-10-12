package com.example.juki.domain.model

data class ScoreRecord(
    val playerName: String,
    val score: Int,
    val difficulty: Int,
    val createdAt: Long
)