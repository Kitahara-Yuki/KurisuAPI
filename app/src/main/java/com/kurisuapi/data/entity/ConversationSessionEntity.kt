package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversation_sessions",
    indices = [
        Index(value = ["characterId"]),
        Index(value = ["folderId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ConversationFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class ConversationSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: Long,
    val folderId: Long? = null,
    val title: String = "",
    val isArchived: Boolean = false,
    val summary: String? = null,  // AI 生成的对话摘要，null 表示尚未生成
    val chatMode: String = CHAT_MODE_CHAT,  // 对话模式：chat=对话模式 story=剧情模式
    val lastPromptTokens: Int = 0,   // 最后一次 API 返回的真实 prompt_tokens，用于显示而非估算
    val isDeleted: Boolean = false,  // 软删除标记
    val deletedAt: Long = 0,         // 删除时间戳，0=未删除
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val CHAT_MODE_CHAT = "chat"
        const val CHAT_MODE_STORY = "story"
    }
}
