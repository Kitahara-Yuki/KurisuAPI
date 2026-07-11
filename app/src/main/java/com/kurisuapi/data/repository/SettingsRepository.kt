package com.kurisuapi.data.repository

import com.kurisuapi.data.dao.SettingsDao
import com.kurisuapi.data.entity.SettingsEntity
import com.kurisuapi.domain.model.UserSelfProfile
import com.kurisuapi.util.EncryptedSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao,
    private val encryptedSettings: EncryptedSettings
) {
    companion object {
        const val KEY_API_BASE_URL = "api_base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "api_model"
        const val KEY_TEMPERATURE = "api_temperature"
        const val KEY_MAX_TOKENS = "api_max_tokens"
        const val KEY_FREQUENCY_PENALTY = "api_frequency_penalty"
        const val KEY_PRESENCE_PENALTY = "api_presence_penalty"
        const val KEY_ACTIVE_CHARACTER = "active_character_id"
        const val KEY_CIRCADIAN_ENABLED = "circadian_enabled"
        const val KEY_BOT_PROACTIVE_ENABLED = "bot_proactive_enabled"
        const val KEY_BOT_PROACTIVE_INTERVAL = "bot_proactive_interval"
        const val KEY_BOT_SHOW_THINKING = "bot_show_thinking"
        const val KEY_AUTO_MEMORY_ENABLED = "auto_memory_enabled"
        const val KEY_EULA_ACCEPTED = "eula_accepted"
        const val KEY_PERMISSION_SETUP_SHOWN = "permission_setup_shown"
        const val KEY_ANNOUNCEMENT_DISMISSED = "announcement_dismissed"
        const val KEY_MEMORY_INTERVAL = "memory_interval"
        // 每角色的微信机器人目标会话 ID，key 拼接 characterId
        const val KEY_BOT_SESSION_PREFIX = "bot_session_"
        // 每角色记忆提取进度（上次提取时该角色的累计消息数），key 拼接 characterId
        private const val KEY_LAST_EXTRACT_COUNT_PREFIX = "memory_last_extract_count_"
        // 标记该角色旧记忆是否已完成首次自动规范化，key 拼接 characterId
        private const val KEY_MEMORY_NORMALIZED_PREFIX = "memory_normalized_"
        // 每角色孤独感衰减上次执行时间戳，key 拼接 characterId
        private const val KEY_LONELINESS_DECAY_AT_PREFIX = "loneliness_decay_at_"

        // 主动消息追踪
        const val KEY_PROACTIVE_DAILY_COUNT = "proactive_daily_count"
        const val KEY_PROACTIVE_DAILY_DATE = "proactive_daily_date"
        const val KEY_PROACTIVE_MAX_PER_DAY = "proactive_max_per_day"
        const val KEY_PROACTIVE_QUIET_START = "proactive_quiet_start"
        const val KEY_PROACTIVE_QUIET_END = "proactive_quiet_end"
        const val KEY_HIDE_MODE_LABELS = "hide_mode_labels"

        // 主题设置
        const val KEY_THEME_SEED_COLOR = "theme_seed_color"  // hex 色值，空=默认
        const val KEY_THEME_DARK_MODE = "theme_dark_mode"    // "system" / "light" / "dark"
        const val KEY_THEME_BG_TYPE = "theme_bg_type"        // "color" / "image" / ""
        const val KEY_THEME_BG_COLOR = "theme_bg_color"      // hex 色值
        const val KEY_THEME_BG_IMAGE = "theme_bg_image"      // 图片 URI

        // 用户个人信息
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_AVATAR = "user_avatar"
        const val KEY_USER_GENDER = "user_gender"
        const val KEY_USER_REGION = "user_region"
        const val KEY_USER_BACKGROUND = "user_background"
        const val DEFAULT_USER_NAME = ""
        const val DEFAULT_USER_AVATAR = ""
        const val DEFAULT_USER_GENDER = ""
        const val DEFAULT_USER_REGION = ""
        const val DEFAULT_USER_BACKGROUND = ""

        // 用户资料版本号（每保存一次 +1，用于缓存失效）
        private const val KEY_USER_PROFILE_VERSION = "user_profile_version"

        // 加密存储中的 key 名称
        private const val SECURE_API_KEY = "secure_api_key"
        // 标记是否已从 Room 迁移 API Key 到加密存储
        private const val KEY_MIGRATED = "api_key_migrated_to_secure"

        // Defaults
        const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1/"
        const val DEFAULT_MODEL = "deepseek-v4-flash"
        const val DEFAULT_TEMPERATURE = "0.9"
        const val DEFAULT_MAX_TOKENS = "131072"
        const val DEFAULT_FREQUENCY_PENALTY = "0.6"
        const val DEFAULT_PRESENCE_PENALTY = "0.4"
        const val DEFAULT_PROACTIVE_INTERVAL = 120  // 分钟
        const val DEFAULT_MEMORY_INTERVAL = 6        // 每累计多少条新消息触发一次记忆提取
        const val DEFAULT_PROACTIVE_MAX_PER_DAY = 1   // 每日主动消息上限
        const val DEFAULT_PROACTIVE_QUIET_START = 23 // 安静时段开始（小时）
        const val DEFAULT_PROACTIVE_QUIET_END = 7    // 安静时段结束（小时）
    }

    // ===== 非敏感设置 — 存储在 Room =====

    suspend fun getValue(key: String): String? = settingsDao.getValue(key)

    fun observeValue(key: String): Flow<String?> = settingsDao.observeValue(key)

    suspend fun setValue(key: String, value: String) =
        settingsDao.insertOrUpdate(SettingsEntity(key, value))

    suspend fun getApiBaseUrl(): String {
        val url = getValue(KEY_API_BASE_URL) ?: DEFAULT_BASE_URL
        return if (url.endsWith("/")) url else "$url/"
    }

    suspend fun getModel(): String =
        getValue(KEY_MODEL) ?: DEFAULT_MODEL

    suspend fun getTemperature(): Double =
        (getValue(KEY_TEMPERATURE) ?: DEFAULT_TEMPERATURE).toDoubleOrNull() ?: 0.9

    suspend fun getMaxTokens(): Int =
        (getValue(KEY_MAX_TOKENS) ?: DEFAULT_MAX_TOKENS).toIntOrNull() ?: 131072

    suspend fun getFrequencyPenalty(): Double =
        (getValue(KEY_FREQUENCY_PENALTY) ?: DEFAULT_FREQUENCY_PENALTY).toDoubleOrNull() ?: 0.6

    suspend fun getPresencePenalty(): Double =
        (getValue(KEY_PRESENCE_PENALTY) ?: DEFAULT_PRESENCE_PENALTY).toDoubleOrNull() ?: 0.4

    suspend fun getActiveCharacterId(): Long? =
        getValue(KEY_ACTIVE_CHARACTER)?.toLongOrNull()

    // ===== 昼夜节律 =====

    suspend fun isCircadianEnabled(): Boolean =
        getValue(KEY_CIRCADIAN_ENABLED) == "true" // 默认关闭

    suspend fun setCircadianEnabled(enabled: Boolean) =
        setValue(KEY_CIRCADIAN_ENABLED, if (enabled) "true" else "false")

    // ===== 微信机器人行为设置 =====

    suspend fun isBotProactiveEnabled(): Boolean =
        getValue(KEY_BOT_PROACTIVE_ENABLED) == "true"

    suspend fun setBotProactiveEnabled(enabled: Boolean) =
        setValue(KEY_BOT_PROACTIVE_ENABLED, if (enabled) "true" else "false")

    suspend fun getBotProactiveInterval(): Int =
        (getValue(KEY_BOT_PROACTIVE_INTERVAL) ?: DEFAULT_PROACTIVE_INTERVAL.toString())
            .toIntOrNull() ?: DEFAULT_PROACTIVE_INTERVAL

    suspend fun setBotProactiveInterval(minutes: Int) =
        setValue(KEY_BOT_PROACTIVE_INTERVAL, minutes.coerceIn(10, 1440).toString())

    // ===== 主动消息追踪设置 =====

    suspend fun getProactiveDailyCount(): Int =
        (getValue(KEY_PROACTIVE_DAILY_COUNT) ?: "0").toIntOrNull() ?: 0

    suspend fun setProactiveDailyCount(count: Int) =
        setValue(KEY_PROACTIVE_DAILY_COUNT, count.coerceAtLeast(0).toString())

    suspend fun getProactiveDailyDate(): Long =
        (getValue(KEY_PROACTIVE_DAILY_DATE) ?: "0").toLongOrNull() ?: 0L

    suspend fun setProactiveDailyDate(dayMs: Long) =
        setValue(KEY_PROACTIVE_DAILY_DATE, dayMs.toString())

    suspend fun getProactiveMaxPerDay(): Int =
        (getValue(KEY_PROACTIVE_MAX_PER_DAY) ?: DEFAULT_PROACTIVE_MAX_PER_DAY.toString())
            .toIntOrNull() ?: DEFAULT_PROACTIVE_MAX_PER_DAY

    suspend fun setProactiveMaxPerDay(max: Int) =
        setValue(KEY_PROACTIVE_MAX_PER_DAY, max.coerceIn(1, 20).toString())

    // ===== 安静时段设置 =====

    suspend fun getProactiveQuietStart(): Int =
        (getValue(KEY_PROACTIVE_QUIET_START) ?: DEFAULT_PROACTIVE_QUIET_START.toString())
            .toIntOrNull() ?: DEFAULT_PROACTIVE_QUIET_START

    suspend fun setProactiveQuietStart(hour: Int) =
        setValue(KEY_PROACTIVE_QUIET_START, hour.coerceIn(0, 23).toString())

    suspend fun getProactiveQuietEnd(): Int =
        (getValue(KEY_PROACTIVE_QUIET_END) ?: DEFAULT_PROACTIVE_QUIET_END.toString())
            .toIntOrNull() ?: DEFAULT_PROACTIVE_QUIET_END

    suspend fun setProactiveQuietEnd(hour: Int) =
        setValue(KEY_PROACTIVE_QUIET_END, hour.coerceIn(0, 23).toString())

    suspend fun isBotShowThinkingEnabled(): Boolean =
        getValue(KEY_BOT_SHOW_THINKING) != "false"  // 默认开启

    suspend fun setBotShowThinkingEnabled(enabled: Boolean) =
        setValue(KEY_BOT_SHOW_THINKING, if (enabled) "true" else "false")

    // ===== AI 自动记忆设置 =====

    suspend fun isAutoMemoryEnabled(): Boolean =
        getValue(KEY_AUTO_MEMORY_ENABLED) != "false"  // 默认开启

    suspend fun setAutoMemoryEnabled(enabled: Boolean) =
        setValue(KEY_AUTO_MEMORY_ENABLED, if (enabled) "true" else "false")

    suspend fun getMemoryInterval(): Int =
        (getValue(KEY_MEMORY_INTERVAL) ?: DEFAULT_MEMORY_INTERVAL.toString())
            .toIntOrNull() ?: DEFAULT_MEMORY_INTERVAL

    suspend fun setMemoryInterval(count: Int) =
        setValue(KEY_MEMORY_INTERVAL, count.coerceIn(2, 50).toString())

    /** 上次为该角色提取记忆时的累计消息数 */
    suspend fun getLastExtractCount(characterId: Long): Int =
        getValue(KEY_LAST_EXTRACT_COUNT_PREFIX + characterId)?.toIntOrNull() ?: 0

    suspend fun setLastExtractCount(characterId: Long, count: Int) =
        setValue(KEY_LAST_EXTRACT_COUNT_PREFIX + characterId, count.toString())

    /** 检查该角色旧记忆是否已完成首次自动规范化 */
    suspend fun isMemoryNormalized(characterId: Long): Boolean =
        getValue(KEY_MEMORY_NORMALIZED_PREFIX + characterId) == "true"

    /** 标记该角色旧记忆已完成首次自动规范化 */
    suspend fun setMemoryNormalized(characterId: Long) =
        setValue(KEY_MEMORY_NORMALIZED_PREFIX + characterId, "true")

    /** 获取该角色上次孤独感衰减执行的时间戳（毫秒），未执行过返回 0 */
    suspend fun getLonelinessDecayAt(characterId: Long): Long =
        getValue(KEY_LONELINESS_DECAY_AT_PREFIX + characterId)?.toLongOrNull() ?: 0L

    /** 记录该角色孤独感衰减的执行时间戳 */
    suspend fun setLonelinessDecayAt(characterId: Long, timestampMs: Long) =
        setValue(KEY_LONELINESS_DECAY_AT_PREFIX + characterId, timestampMs.toString())

    // ===== 对话摘要进度 =====

    private val KEY_LAST_SUMMARY_COUNT_PREFIX = "summary_last_count_"

    suspend fun getSummaryLastCount(sessionId: Long): Int =
        getValue(KEY_LAST_SUMMARY_COUNT_PREFIX + sessionId)?.toIntOrNull() ?: 0

    suspend fun setSummaryLastCount(sessionId: Long, count: Int) =
        setValue(KEY_LAST_SUMMARY_COUNT_PREFIX + sessionId, count.toString())

    // ===== 微信机器人会话绑定 =====

    /** 获取该角色绑定的微信机器人目标会话 ID，未绑定时返回 null */
    suspend fun getBotSessionId(characterId: Long): Long? =
        getValue(KEY_BOT_SESSION_PREFIX + characterId)?.toLongOrNull()

    /** 设置或取消该角色的微信机器人目标会话 */
    suspend fun setBotSessionId(characterId: Long, sessionId: Long?) {
        if (sessionId != null && sessionId > 0) {
            setValue(KEY_BOT_SESSION_PREFIX + characterId, sessionId.toString())
        } else {
            settingsDao.delete(KEY_BOT_SESSION_PREFIX + characterId)
        }
    }

    // ===== 用户个人信息 =====

    suspend fun getUserName(): String =
        getValue(KEY_USER_NAME) ?: DEFAULT_USER_NAME

    suspend fun setUserName(name: String) =
        setValue(KEY_USER_NAME, name.ifBlank { DEFAULT_USER_NAME })

    suspend fun getUserAvatar(): String =
        getValue(KEY_USER_AVATAR) ?: DEFAULT_USER_AVATAR

    suspend fun setUserAvatar(url: String) =
        setValue(KEY_USER_AVATAR, url.trim())

    suspend fun getUserGender(): String =
        getValue(KEY_USER_GENDER) ?: DEFAULT_USER_GENDER

    suspend fun setUserGender(gender: String) =
        setValue(KEY_USER_GENDER, gender.trim())

    suspend fun getUserRegion(): String =
        getValue(KEY_USER_REGION) ?: DEFAULT_USER_REGION

    suspend fun setUserRegion(region: String) =
        setValue(KEY_USER_REGION, region.trim())

    suspend fun getUserBackground(): String =
        getValue(KEY_USER_BACKGROUND) ?: DEFAULT_USER_BACKGROUND

    suspend fun setUserBackground(background: String) =
        setValue(KEY_USER_BACKGROUND, background.trim())

    // ===== 用户资料版本号 =====

    suspend fun getUserProfileVersion(): Int =
        (getValue(KEY_USER_PROFILE_VERSION) ?: "0").toIntOrNull() ?: 0

    suspend fun incrementUserProfileVersion(): Int {
        val next = getUserProfileVersion() + 1
        setValue(KEY_USER_PROFILE_VERSION, next.toString())
        return next
    }

    // ===== 对话列表 UI 偏好 =====

    suspend fun isHideModeLabels(): Boolean =
        getValue(KEY_HIDE_MODE_LABELS) == "true"

    suspend fun setHideModeLabels(hide: Boolean) =
        setValue(KEY_HIDE_MODE_LABELS, if (hide) "true" else "false")

    /** 聚合读取用户自设资料（为 PromptBuilder 提供结构化数据）。版本=0 表示从未设置过，返回空。 */
    suspend fun getUserSelfProfile(): UserSelfProfile {
        if (getUserProfileVersion() == 0) return UserSelfProfile()
        return UserSelfProfile(
            name = getUserName(),
            gender = getUserGender(),
            region = getUserRegion(),
            background = getUserBackground()
        )
    }

    // ===== 敏感设置 — 使用 EncryptedSharedPreferences 加密存储 =====

    private val migrationMutex = Mutex()

    /**
     * 获取 API Key（从加密存储读取）。
     * 首次调用时自动将 Room 中的明文 Key 迁移到加密存储并清除明文。
     * 使用互斥锁防止并发迁移导致密钥丢失。
     */
    suspend fun getApiKey(): String = withContext(Dispatchers.IO) {
        migrationMutex.withLock {
            try {
                // 检查是否已迁移
                val migrated = getValue(KEY_MIGRATED)
                if (migrated != "true") {
                    // 从 Room 读取旧的明文 API Key 并迁移到加密存储
                    val oldKey = getValue(KEY_API_KEY)
                    if (!oldKey.isNullOrBlank()) {
                        // 先写入加密存储
                        encryptedSettings.putString(SECURE_API_KEY, oldKey)
                        // 先标记迁移完成，再清除明文（防止中途崩溃导致密钥丢失）
                        settingsDao.insertOrUpdate(SettingsEntity(KEY_MIGRATED, "true"))
                        settingsDao.delete(KEY_API_KEY)
                    } else {
                        // 没有明文 Key 也标记迁移完成，避免每次都重复检查
                        settingsDao.insertOrUpdate(SettingsEntity(KEY_MIGRATED, "true"))
                    }
                }
                encryptedSettings.getString(SECURE_API_KEY)
            } catch (e: Exception) {
                android.util.Log.e("SettingsRepo", "加密存储失败，回退到明文", e)
                getValue(KEY_API_KEY) ?: ""
            }
        }
    }

    /**
     * 保存 API Key（写入加密存储）。
     */
    suspend fun setApiKey(key: String) = withContext(Dispatchers.IO) {
        encryptedSettings.putString(SECURE_API_KEY, key)
        // 确保 Room 中不残留明文
        settingsDao.delete(KEY_API_KEY)
    }
}
