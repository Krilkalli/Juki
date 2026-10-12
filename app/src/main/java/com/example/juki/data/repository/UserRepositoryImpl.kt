package com.example.juki.data.repository

import com.example.juki.data.dao.UserDao
import com.example.juki.data.mapper.toDomain
import com.example.juki.data.mapper.toEntity
import com.example.juki.domain.model.Player
import com.example.juki.domain.model.User
import com.example.juki.domain.repository.UserRepository

class UserRepositoryImpl(
    private val userDao: UserDao
) : UserRepository {

    override suspend fun getAll(): List<User> {
        return userDao.getAll().map { it.toDomain() }
    }

    override suspend fun getById(id: Long): User? {
        return userDao.getById(id)?.toDomain()
    }

    override suspend fun register(player: Player): User {
        val userId = userDao.insert(player.toEntity(id = 0))
        return userDao.getById(userId)?.toDomain()
            ?: error("User was not saved")
    }
}