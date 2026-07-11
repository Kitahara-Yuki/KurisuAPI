package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "themes",
    indices = [Index(value = ["isActive"])]
)
data class ThemeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val seedColorHex: String = "",
    val bannerColorHex: String = "",
    val bannerTextColorHex: String = "",
    val fontColorHex: String = "",
    val cardColorHex: String = "",
    val chatBgColorHex: String = "",
    val bubbleUserColorHex: String = "",
    val bubbleAiColorHex: String = "",
    // 背景图片
    val chatBgImagePath: String = "",
    val chatBgBlurRadius: Int = 0,
    val chatBgDimPercent: Int = 0,
    val isActive: Boolean = false,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
