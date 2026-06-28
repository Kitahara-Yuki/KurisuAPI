package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memories",
    indices = [Index(value = ["characterId"])],
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ConversationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: Long,
    val content: String,
    val importance: Int = 5, // 1-10
    val source: String = "manual",  // "manual"=用户手动添加, "auto"=AI 自动提取
    val sessionId: Long = 0,   // 关联的对话 ID，0=未关联（旧数据）
    val isDeleted: Boolean = false,  // 软删除标记
    val deletedAt: Long = 0,   // 删除时间戳
    val embedding: ByteArray? = null,  // 语义搜索向量（Float32 数组的原始字节）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun clampImportance(value: Int): Int = value.coerceIn(1, 10)
    }
}
