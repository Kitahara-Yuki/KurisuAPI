package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "diary_entries",
    indices = [
        Index("characterId"),
        Index("date")
    ]
)
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: Long,
    val date: String,          // "2026-06-20"
    val content: String,       // 日记正文
    val isManual: Boolean = false,  // 手动创建(true)还是AI生成(false)
    val createdAt: Long = System.currentTimeMillis()
)
