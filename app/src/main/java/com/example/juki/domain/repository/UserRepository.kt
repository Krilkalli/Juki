package com.example.juki.domain.repository

interface UserRepository {

    suspend fun getAll(): List<User>

    suspend fun getById(id: Long): User?

    suspend fun register(player: Player): User
}