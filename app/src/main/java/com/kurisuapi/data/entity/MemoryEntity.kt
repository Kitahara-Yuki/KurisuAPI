package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memories",
    indices = [
        Index(value = ["characterId"]),
        Index(value = ["sessionId"]),
        Index(value = ["isDeleted"]),
        Index(value = ["characterId", "updatedAt"]),
        Index(value = ["characterId", "isDeleted", "importance"])
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
    val recallCount: Int = 0,  // 被检索到的次数（衰减计算）
    val lastRecalledAt: Long = 0,  // 最后被检索的时间戳
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun clampImportance(value: Int): Int = value.coerceIn(1, 10)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryEntity) return false
        if (id != other.id) return false
        if (characterId != other.characterId) return false
        if (content != other.content) return false
        if (importance != other.importance) return false
        if (source != other.source) return false
        if (sessionId != other.sessionId) return false
        if (isDeleted != other.isDeleted) return false
        if (deletedAt != other.deletedAt) return false
        if (embedding != null && other.embedding != null) {
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (embedding != null || other.embedding != null) return false
        if (recallCount != other.recallCount) return false
        if (lastRecalledAt != other.lastRecalledAt) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + characterId.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + importance.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + isDeleted.hashCode()
        result = 31 * result + deletedAt.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + recallCount.hashCode()
        result = 31 * result + lastRecalledAt.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}
