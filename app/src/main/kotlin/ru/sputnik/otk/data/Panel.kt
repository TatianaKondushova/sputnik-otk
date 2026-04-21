package ru.sputnik.otk.data

data class Panel(
    val id: String,
    val fault: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)
