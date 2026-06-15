package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "emotion_states",
    indices = [Index(value = ["characterId"], unique = true)]
)
data class EmotionStateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: Long,
    val happy: Int = 50,
    val sad: Int = 0,
    val angry: Int = 0,
    val lonely: Int = 0,
    val affection: Int = 50,
    val updatedAt: Long = System.currentTimeMillis()
)
