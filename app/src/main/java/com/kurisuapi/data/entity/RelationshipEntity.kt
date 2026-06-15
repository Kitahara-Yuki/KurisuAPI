package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "relationships",
    indices = [Index(value = ["characterId"], unique = true)]
)
data class RelationshipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: Long,
    val level: String = "陌生人", // 陌生人/朋友/好友/知己/恋人
    val score: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
