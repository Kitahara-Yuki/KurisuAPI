package com.kurisuapi.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kurisuapi.data.dao.*
import com.kurisuapi.data.entity.*

@Database(
    entities = [
        CharacterEntity::class,
        MemoryEntity::class,
        EmotionStateEntity::class,
        RelationshipEntity::class,
        ChatHistoryEntity::class,
        SettingsEntity::class,
        ProviderEntity::class,
        ModelEntity::class,
        UserProfileEntity::class,
        ConversationSessionEntity::class,
        ConversationFolderEntity::class,
        ConversationIndexEntity::class
    ],
    version = 23,
    exportSchema = false
)
abstract class KurisuDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun memoryDao(): MemoryDao
    abstract fun emotionStateDao(): EmotionStateDao
    abstract fun relationshipDao(): RelationshipDao
    abstract fun chatHistoryDao(): ChatHistoryDao
    abstract fun settingsDao(): SettingsDao
    abstract fun providerDao(): ProviderDao
    abstract fun modelDao(): ModelDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun conversationSessionDao(): ConversationSessionDao
    abstract fun conversationFolderDao(): ConversationFolderDao
    abstract fun conversationIndexDao(): ConversationIndexDao
}
