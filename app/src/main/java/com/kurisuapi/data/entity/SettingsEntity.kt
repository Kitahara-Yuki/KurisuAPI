package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "settings",
    indices = [Index(value = ["key"], unique = true)]
)
data class SettingsEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
