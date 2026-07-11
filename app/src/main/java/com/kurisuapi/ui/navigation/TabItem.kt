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
    DIARY(1, "日记", Icons.Outlined.Book),
    CHAT_LOG(2, "对话", Icons.Outlined.Chat),
    PROFILE(3, "我", Icons.Outlined.Person);

    companion object {
        fun fromIndex(index: Int): TabItem = entries.first { it.index == index }
    }
}
