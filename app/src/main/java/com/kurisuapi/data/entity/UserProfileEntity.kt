package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_profiles",
    indices = [Index(value = ["characterId"], unique = true)]
)
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: Long,
    val profileText: String = "",   // AI 维护的完整用户画像文本
    val updatedAt: Long = System.currentTimeMillis()
)
