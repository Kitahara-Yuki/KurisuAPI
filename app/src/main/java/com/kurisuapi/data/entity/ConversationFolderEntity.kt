package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversation_folders",
    indices = [
        Index(value = ["characterId"])
    ]
)
data class ConversationFolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: Long,
    val name: String,
    val isSystem: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
