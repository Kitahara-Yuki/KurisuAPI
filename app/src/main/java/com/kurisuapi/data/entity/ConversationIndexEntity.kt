package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 对话索引：记录每段对话的话题概括和关键词，用于快速定位"聊过什么"。
 * 与 MemoryEntity（事实级）互补，构成分层记忆系统。
 */
@Entity(
    tableName = "conversation_indexes",
    indices = [
        Index(value = ["characterId"]),
        Index(value = ["sessionId"])
    ]
)
data class ConversationIndexEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: Long,
    val sessionId: Long,
    val keywords: String,      // AI 提取的关键词，逗号分隔
    val summary: String,       // 这段对话的简短概括（一句话）
    val createdAt: Long = System.currentTimeMillis()
)
