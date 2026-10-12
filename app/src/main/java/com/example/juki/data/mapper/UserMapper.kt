package com.example.juki.data.mapper

import com.example.juki.data.entity.UserEntity
import com.example.juki.domain.model.Player
import com.example.juki.domain.model.User

fun UserEntity.toDomain() = User(
    id = id,
    name = name,
    surname = surname,
    otchestvo = otchestvo,
    course = course,
    difficulty = difficulty,
    birthDay = birthDay,
    birthMonth = birthMonth,
    birthYear = birthYear,
    gender = gender,
    zodiac = zodiac
)

fun Player.toEntity(id: Long) = UserEntity(
    id = id,
    name = name,
    surname = surname,
    otchestvo = otchestvo,
    course = course,
    difficulty = difficulty,
    birthDay = birthDay,
    birthMonth = birthMonth,
    birthYear = birthYear,
    gender = gender,
    zodiac = zodiac
)
