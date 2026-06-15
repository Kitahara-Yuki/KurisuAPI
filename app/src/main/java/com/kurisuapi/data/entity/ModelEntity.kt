package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "models",
    indices = [Index(value = ["providerId"])]
)
data class ModelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val providerId: Long,
    val modelId: String,
    val displayName: String,
    val contextWindow: Long = 0,
    val maxOutput: Long = 0,
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = false,
    val supportsReasoning: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsImageGeneration: Boolean = false,
    val isCustom: Boolean = false,
    val isEnabled: Boolean = true,
    val status: String = "active",           // "active" / "deprecated" / "unavailable"
    val deprecatedAt: String? = null,         // 弃用日期，如 "2026-07-24"
    val lastFetchedAt: Long = 0
)
