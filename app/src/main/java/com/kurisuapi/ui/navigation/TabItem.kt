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
    CHARACTER(1, "角色", Icons.Outlined.Person),
    MEMORY(2, "记忆", Icons.Outlined.Psychology),
    CHAT_LOG(3, "日志", Icons.Outlined.History),
    SETTINGS(4, "设置", Icons.Outlined.Settings);

    companion object {
        fun fromIndex(index: Int): TabItem = entries.first { it.index == index }
    }
}
