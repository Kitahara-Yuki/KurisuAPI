package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val avatar: String = "",
    val gender: String = "",
    val age: Int = 0,
    val personality: String = "",
    val appearance: String = "",
    val speakingStyle: String = "",
    val background: String = "",
    val systemPrompt: String = "",
    val exampleDialogues: String = "",  // 示例对话，用于 Prompt 中注入角色说话风格
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
