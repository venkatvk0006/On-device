package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "remembered_notes")
data class MemoryNoteEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val note: String,
    val timestamp: Long = System.currentTimeMillis()
)
