package com.kurisuapi.domain.model

/**
 * 用户自行设定的个人资料（区别于 AI 维护的 [UserProfileEntity]）。
 *
 * 作为契约层，隔离 SettingsRepository 底层 key-value 与 PromptBuilder 上层消费。
 * 未来新增资料字段（如生日、兴趣标签）直接追加到此 data class，
 * PromptBuilder 无需修改。
 */
data class UserSelfProfile(
    val name: String = "",
    val gender: String = "",
    val region: String = "",
    val background: String = ""
) {
    val isEmpty: Boolean
        get() = name.isBlank() && gender.isBlank() && region.isBlank() && background.isBlank()
}
