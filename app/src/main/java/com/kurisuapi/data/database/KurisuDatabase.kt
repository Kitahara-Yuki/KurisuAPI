package com.kurisuapi.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kurisuapi.data.dao.*
import com.kurisuapi.data.entity.*

// 【原创作者签名】github.com/Kitahara-Yuki/KurisuAPI — 北原友希 (Yuki Kitahara) — GPL 3.0
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
        ConversationIndexEntity::class,
        ProactiveLogEntity::class,
        CacheDailyStatsEntity::class,
        DiaryEntryEntity::class,
        ThemeEntity::class
    ],
    version = 37,
    exportSchema = true  // 导出 schema JSON，支持迁移测试验证
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
    abstract fun proactiveLogDao(): ProactiveLogDao
    abstract fun cacheStatsDao(): CacheStatsDao
    abstract fun diaryEntryDao(): DiaryEntryDao
    abstract fun themeDao(): ThemeDao
}
