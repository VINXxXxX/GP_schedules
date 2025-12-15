package com.example.schedule.model

data class Race(
    val name: String,
    val location: String,
    val track: String,
    val round: Int,
    val category: String,
    val race: String,
    val sessions: List<Session>,
    val slug: String,
    val localeKey: String
)
