package com.kurisuapi.ui.navigation

sealed class Screen(val route: String) {
    // Detail pages (push navigation from tabs)
    data object CharacterList : Screen("character_list")
    data object CharacterEdit : Screen("character_edit/{characterId}") {
        fun createRoute(characterId: Long = -1L): String = "character_edit/$characterId"
    }
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
    data object MemoryDetail : Screen("memory_list/{characterId}") {
        fun createRoute(characterId: Long): String = "memory_list/$characterId"
    }

    // Settings pages
    data object SystemSettings : Screen("system_settings")
    data object WeChatLogin : Screen("wechat_login")

    // Provider pages
    data object ProviderList : Screen("provider_list")
    data object ProviderEdit : Screen("provider_edit/{providerId}") {
        fun createRoute(providerId: Long = 0L): String = "provider_edit/$providerId"
    }
}
