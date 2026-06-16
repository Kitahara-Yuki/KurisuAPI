package com.kurisuapi.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class TabItem(
    val index: Int,
    val title: String,
    val icon: ImageVector
) {
    HOME(0, "首页", Icons.Outlined.Home),
    MEMORY(1, "记忆", Icons.Outlined.Psychology),
    CHAT_LOG(2, "对话", Icons.Outlined.Send),
    SETTINGS(3, "设置", Icons.Outlined.Settings);

    companion object {
        fun fromIndex(index: Int): TabItem = entries.first { it.index == index }
    }
}
