package ru.sputnik.otk.data

import kotlinx.serialization.Serializable

@Serializable
data class Panel(
    val id: String,
    val fault: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)
