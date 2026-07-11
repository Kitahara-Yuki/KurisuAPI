package com.kurisuapi.data.dao

import androidx.room.*
import com.kurisuapi.data.entity.ConversationIndexEntity

@Dao
interface ConversationIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(index: ConversationIndexEntity): Long

    /** 按关键词搜索索引（LIKE 匹配 keywords 和 summary 字段，ESCAPE 防止特殊字符误匹配） */
    @Query("SELECT * FROM conversation_indexes WHERE characterId = :characterId AND (keywords LIKE '%' || :keyword || '%' ESCAPE '\\' OR summary LIKE '%' || :keyword || '%' ESCAPE '\\') ORDER BY createdAt DESC LIMIT :limit")
    suspend fun search(characterId: Long, keyword: String, limit: Int = 10): List<ConversationIndexEntity>

    @Query("DELETE FROM conversation_indexes WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)

    @Query("DELETE FROM conversation_indexes WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: Long)
}
