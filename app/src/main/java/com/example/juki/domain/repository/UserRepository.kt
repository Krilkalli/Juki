package com.example.juki.domain.repository

import com.example.juki.domain.model.Player
import com.example.juki.domain.model.User

interface UserRepository {

    suspend fun getAll(): List<User>

    suspend fun getById(id: Long): User?

    suspend fun register(player: Player): User
}