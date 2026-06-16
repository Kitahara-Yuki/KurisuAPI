package com.kurisuapi.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kurisuapi.data.dao.*
import com.kurisuapi.data.database.KurisuDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // v1 → v2: 新增 providers 和 models 表
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `providers` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `baseUrl` TEXT NOT NULL,
                    `apiKey` TEXT NOT NULL DEFAULT '',
                    `isEnabled` INTEGER NOT NULL DEFAULT 1,
                    `isDefault` INTEGER NOT NULL DEFAULT 0,
                    `supportsStreaming` INTEGER NOT NULL DEFAULT 1,
                    `supportsVision` INTEGER NOT NULL DEFAULT 0,
                    `supportsReasoning` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL DEFAULT 0,
                    `updatedAt` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `models` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `providerId` INTEGER NOT NULL,
                    `modelId` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `contextWindow` INTEGER NOT NULL DEFAULT 0,
                    `maxOutput` INTEGER NOT NULL DEFAULT 0,
                    `supportsVision` INTEGER NOT NULL DEFAULT 0,
                    `supportsTools` INTEGER NOT NULL DEFAULT 0,
                    `supportsReasoning` INTEGER NOT NULL DEFAULT 0,
                    `isCustom` INTEGER NOT NULL DEFAULT 0,
                    `isEnabled` INTEGER NOT NULL DEFAULT 1,
                    `lastFetchedAt` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_models_providerId` ON `models` (`providerId`)")
        }
    }

    // v2 → v3: providers 表新增 modelsUrlOverride 列
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `providers` ADD COLUMN `modelsUrlOverride` TEXT DEFAULT NULL")
        }
    }

    // v3 → v4: providers 表新增 model、temperature、maxTokens 列
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `providers` ADD COLUMN `model` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `providers` ADD COLUMN `temperature` REAL NOT NULL DEFAULT 0.7")
            db.execSQL("ALTER TABLE `providers` ADD COLUMN `maxTokens` INTEGER NOT NULL DEFAULT 2048")
        }
    }

    // v4 → v5: models 表新增 status/deprecatedAt/supportsAudio/supportsImageGeneration 列
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `models` ADD COLUMN `supportsAudio` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `models` ADD COLUMN `supportsImageGeneration` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `models` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'active'")
            db.execSQL("ALTER TABLE `models` ADD COLUMN `deprecatedAt` TEXT DEFAULT NULL")
        }
    }

    // v5 → v6: providers 表新增 thinkingEnabled 列
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `providers` ADD COLUMN `thinkingEnabled` INTEGER NOT NULL DEFAULT 0")
        }
    }

    // v6 → v7: chat_history 表新增 reasoningContent 列
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `chat_history` ADD COLUMN `reasoningContent` TEXT NOT NULL DEFAULT ''")
        }
    }

    // v7 → v8: providers 表新增 reasoningEffort, thinkingBudgetTokens 列
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `providers` ADD COLUMN `reasoningEffort` TEXT NOT NULL DEFAULT 'high'")
            db.execSQL("ALTER TABLE `providers` ADD COLUMN `thinkingBudgetTokens` INTEGER NOT NULL DEFAULT 0")
        }
    }

    // v8 → v9: 新增 user_profiles 表 + memories 表新增 source 列
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `user_profiles` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `characterId` INTEGER NOT NULL,
                    `profileText` TEXT NOT NULL DEFAULT '',
                    `updatedAt` INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_profiles_characterId` ON `user_profiles` (`characterId`)")
            db.execSQL("ALTER TABLE `memories` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'manual'")
        }
    }

    // v9 → v10: providers 表新增 isBuiltIn 列
    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `providers` ADD COLUMN `isBuiltIn` INTEGER NOT NULL DEFAULT 0")
            // 为现有数据标记内置 Provider（根据名称匹配）
            db.execSQL("UPDATE `providers` SET `isBuiltIn` = 1 WHERE `name` IN ('DeepSeek','OpenAI','Anthropic','Google Gemini')")
        }
    }

    // v10 → v11: 新增 conversation_sessions 和 conversation_folders 表，chat_history 新增 sessionId 列
    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. 创建 conversation_folders 表
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `conversation_folders` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `characterId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_folders_characterId` ON `conversation_folders` (`characterId`)")

            // 2. 创建 conversation_sessions 表
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `conversation_sessions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `characterId` INTEGER NOT NULL,
                    `folderId` INTEGER DEFAULT NULL,
                    `title` TEXT NOT NULL DEFAULT '',
                    `isArchived` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL DEFAULT 0,
                    `updatedAt` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_sessions_characterId` ON `conversation_sessions` (`characterId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_sessions_folderId` ON `conversation_sessions` (`folderId`)")

            // 3. chat_history 新增 sessionId 列
            db.execSQL("ALTER TABLE `chat_history` ADD COLUMN `sessionId` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_history_sessionId` ON `chat_history` (`sessionId`)")

            // 4. 为每个已有角色创建默认会话并回填 sessionId
            // 查询所有有聊天记录的角色
            val cursor = db.query("SELECT DISTINCT characterId FROM chat_history")
            while (cursor.moveToNext()) {
                val characterId = cursor.getLong(0)
                // 创建默认会话
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO conversation_sessions (characterId, title, isArchived, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?)",
                    arrayOf(characterId, "默认对话", 0, now, now)
                )
                // 获取刚插入的会话 ID
                val sessionCursor = db.query("SELECT last_insert_rowid()")
                sessionCursor.moveToFirst()
                val sessionId = sessionCursor.getLong(0)
                sessionCursor.close()
                // 回填 chat_history 的 sessionId
                db.execSQL(
                    "UPDATE chat_history SET sessionId = ? WHERE characterId = ?",
                    arrayOf(sessionId, characterId)
                )
            }
            cursor.close()
        }
    }

    // v11 → v12: 添加复合索引优化常见查询性能
    // - settings.key 唯一索引（所有查询按 key 过滤）
    // - chat_history (characterId, timestamp) 和 (sessionId, timestamp) 复合索引
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_settings_key` ON `settings` (`key`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_history_characterId_timestamp` ON `chat_history` (`characterId`, `timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_history_sessionId_timestamp` ON `chat_history` (`sessionId`, `timestamp`)")
        }
    }

    // v12 → v13: conversation_folders 表新增 isSystem 列（标记系统创建的不可变文件夹，如"已归档"）
    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `conversation_folders` ADD COLUMN `isSystem` INTEGER NOT NULL DEFAULT 0")
        }
    }

    // v13 → v14: conversation_sessions 表新增 summary 列（AI 生成的对话摘要）
    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `conversation_sessions` ADD COLUMN `summary` TEXT DEFAULT NULL")
        }
    }

    // v14 → v15: conversation_sessions 表新增 chatMode 列（按对话独立的聊天模式）
    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `conversation_sessions` ADD COLUMN `chatMode` TEXT NOT NULL DEFAULT 'chat'")
        }
    }

    // v15 → v16: providers 表新增 contextWindow 列（上下文窗口大小）
    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `providers` ADD COLUMN `contextWindow` INTEGER NOT NULL DEFAULT 0")
        }
    }

    // v16 → v17: 占位迁移（chatMode 列保留，仅用于显示标签，不再提供模式选择）
    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No schema change — chatMode column stays, selection dialog removed
        }
    }

    // v17 → v18: conversation_sessions 新增软删除字段
    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `conversation_sessions` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `conversation_sessions` ADD COLUMN `deletedAt` INTEGER NOT NULL DEFAULT 0")
        }
    }

    // v18 → v19: memories 新增 sessionId + 软删除字段
    private val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `memories` ADD COLUMN `sessionId` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `memories` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `memories` ADD COLUMN `deletedAt` INTEGER NOT NULL DEFAULT 0")
        }
    }

    // v19 → v20: 新增对话索引表
    private val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `conversation_indexes` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `characterId` INTEGER NOT NULL,
                    `sessionId` INTEGER NOT NULL,
                    `keywords` TEXT NOT NULL,
                    `summary` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_indexes_characterId` ON `conversation_indexes` (`characterId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_indexes_sessionId` ON `conversation_indexes` (`sessionId`)")
        }
    }

    // v20 → v21: characters 表新增 appearance 列（角色外观）
    private val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `characters` ADD COLUMN `appearance` TEXT NOT NULL DEFAULT ''")
        }
    }

    // v21 → v22: conversation_sessions 表新增 lastPromptTokens 列（真实 token 数）
    private val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `conversation_sessions` ADD COLUMN `lastPromptTokens` INTEGER NOT NULL DEFAULT 0")
        }
    }

    // v22 → v23: memories 表新增 embedding 列（语义搜索向量）
    private val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `memories` ADD COLUMN `embedding` BLOB DEFAULT NULL")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KurisuDatabase {
        return Room.databaseBuilder(
            context,
            KurisuDatabase::class.java,
            "kurisu_database"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides @Singleton fun provideCharacterDao(db: KurisuDatabase): CharacterDao = db.characterDao()
    @Provides @Singleton fun provideMemoryDao(db: KurisuDatabase): MemoryDao = db.memoryDao()
    @Provides @Singleton fun provideEmotionStateDao(db: KurisuDatabase): EmotionStateDao = db.emotionStateDao()
    @Provides @Singleton fun provideRelationshipDao(db: KurisuDatabase): RelationshipDao = db.relationshipDao()
    @Provides @Singleton fun provideChatHistoryDao(db: KurisuDatabase): ChatHistoryDao = db.chatHistoryDao()
    @Provides @Singleton fun provideSettingsDao(db: KurisuDatabase): SettingsDao = db.settingsDao()
    @Provides @Singleton fun provideProviderDao(db: KurisuDatabase): ProviderDao = db.providerDao()
    @Provides @Singleton fun provideModelDao(db: KurisuDatabase): ModelDao = db.modelDao()
    @Provides @Singleton fun provideUserProfileDao(db: KurisuDatabase): UserProfileDao = db.userProfileDao()
    @Provides @Singleton fun provideConversationSessionDao(db: KurisuDatabase): ConversationSessionDao = db.conversationSessionDao()
    @Provides @Singleton fun provideConversationFolderDao(db: KurisuDatabase): ConversationFolderDao = db.conversationFolderDao()
    @Provides @Singleton fun provideConversationIndexDao(db: KurisuDatabase): ConversationIndexDao = db.conversationIndexDao()
}
