package com.kurisuapi.ui.navigation

sealed class Screen(val route: String) {
    // Detail pages (push navigation from tabs)
    data object CharacterList : Screen("character_list")
    data object CharacterEdit : Screen("character_edit/{characterId}") {
        fun createRoute(characterId: Long = -1L): String = "character_edit/$characterId"
    }
    data object CharacterGenerate : Screen("character_generate")
    data object EmotionDetail : Screen("emotion_detail/{characterId}") {
        fun createRoute(characterId: Long): String = "emotion_detail/$characterId"
    }
    data object RelationshipDetail : Screen("relationship_detail/{characterId}") {
        fun createRoute(characterId: Long): String = "relationship_detail/$characterId"
    }
    data object ChatLogDetail : Screen("chat_log/{sessionId}") {
        fun createRoute(sessionId: Long): String = "chat_log/$sessionId"
    }
    data object ConversationList : Screen("conversation_list/{characterId}") {
        fun createRoute(characterId: Long): String = "conversation_list/$characterId"
    }
    data object DiaryList : Screen("diary_list/{characterId}") {
        fun createRoute(characterId: Long): String = "diary_list/$characterId"
    }
    data object MemoryList : Screen("memory_list/{characterId}") {
        fun createRoute(characterId: Long): String = "memory_list/$characterId"
    }

    // Profile pages
    data object ProfileEdit : Screen("profile_edit")
    data object ThemeConfig : Screen("theme_config")
    data object ThemeList : Screen("theme_list")
    data object ThemeEdit : Screen("theme_edit/{themeId}") {
        fun createRoute(themeId: Long = -1L): String = "theme_edit/$themeId"
    }

    // Settings pages
    data object Settings : Screen("settings")
    data object SystemSettings : Screen("system_settings")
    data object WeChatLogin : Screen("wechat_login")

    data object LogViewer : Screen("log_viewer")

    // Provider pages
    data object ProviderList : Screen("provider_list")
    data object ProviderEdit : Screen("provider_edit/{providerId}") {
        fun createRoute(providerId: Long = 0L): String = "provider_edit/$providerId"
    }
}
