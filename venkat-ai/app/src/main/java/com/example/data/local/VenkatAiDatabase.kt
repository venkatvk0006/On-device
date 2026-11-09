package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ConversationEntity

@Database(
    entities = [
        ConversationEntity::class,
        ChatMessageEntity::class,
        com.example.data.model.MemoryNoteEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class VenkatAiDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: VenkatAiDatabase? = null

        fun getDatabase(context: Context): VenkatAiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VenkatAiDatabase::class.java,
                    "venkat_ai_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
