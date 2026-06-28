package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_history",
    indices = [
        Index(value = ["characterId"]),
        Index(value = ["sessionId"]),
        Index(value = ["characterId", "timestamp"]),
        Index(value = ["sessionId", "timestamp"])
    ]
)
data class ChatHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: Long,
    val sessionId: Long = 0,
    val sender: String, // "user" or "ai"
    val content: String,
    val reasoningContent: String = "",  // AI 思考过程，仅 sender="ai" 时可能有值
    val timestamp: Long = System.currentTimeMillis()
)
