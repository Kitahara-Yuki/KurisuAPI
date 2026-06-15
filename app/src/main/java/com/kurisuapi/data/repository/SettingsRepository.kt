package com.kurisuapi.data.repository

import com.kurisuapi.data.dao.SettingsDao
import com.kurisuapi.data.entity.SettingsEntity
import com.kurisuapi.util.EncryptedSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
        const val KEY_ACTIVE_CHARACTER = "active_character_id"
        const val KEY_BOT_PROACTIVE_ENABLED = "bot_proactive_enabled"
        const val KEY_BOT_PROACTIVE_INTERVAL = "bot_proactive_interval"
        const val KEY_BOT_SHOW_THINKING = "bot_show_thinking"
        const val KEY_AUTO_MEMORY_ENABLED = "auto_memory_enabled"
        const val KEY_MEMORY_INTERVAL = "memory_interval"
        // 每角色的微信机器人目标会话 ID，key 拼接 characterId
        const val KEY_BOT_SESSION_PREFIX = "bot_session_"
        // 后台任务专用模型（记忆提取、摘要生成），空字符串=使用默认模型
        const val KEY_BACKGROUND_MODEL = "background_model"
        // 每角色记忆提取进度（上次提取时该角色的累计消息数），key 拼接 characterId
        private const val KEY_LAST_EXTRACT_COUNT_PREFIX = "memory_last_extract_count_"
        // 标记该角色旧记忆是否已完成首次自动规范化，key 拼接 characterId
        private const val KEY_MEMORY_NORMALIZED_PREFIX = "memory_normalized_"

        // 加密存储中的 key 名称
        private const val SECURE_API_KEY = "secure_api_key"
        // 标记是否已从 Room 迁移 API Key 到加密存储
        private const val KEY_MIGRATED = "api_key_migrated_to_secure"

        // Defaults
        const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1/"
        const val DEFAULT_MODEL = "deepseek-v4-flash"
        const val DEFAULT_TEMPERATURE = "0.7"
        const val DEFAULT_MAX_TOKENS = "2048"
        const val DEFAULT_PROACTIVE_INTERVAL = 120  // 分钟
        const val DEFAULT_MEMORY_INTERVAL = 6        // 每累计多少条新消息触发一次记忆提取
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
        (getValue(KEY_TEMPERATURE) ?: DEFAULT_TEMPERATURE).toDoubleOrNull() ?: 0.7

    suspend fun getMaxTokens(): Int =
        (getValue(KEY_MAX_TOKENS) ?: DEFAULT_MAX_TOKENS).toIntOrNull() ?: 2048

    suspend fun getActiveCharacterId(): Long? =
        getValue(KEY_ACTIVE_CHARACTER)?.toLongOrNull()

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

    /** 后台任务专用模型 ID，空字符串=使用默认模型 */
    suspend fun getBackgroundModel(): String = getValue(KEY_BACKGROUND_MODEL) ?: ""

    suspend fun setBackgroundModel(modelId: String) = setValue(KEY_BACKGROUND_MODEL, modelId)

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

    // ===== 敏感设置 — 使用 EncryptedSharedPreferences 加密存储 =====

    /**
     * 获取 API Key（从加密存储读取）。
     * 首次调用时自动将 Room 中的明文 Key 迁移到加密存储并清除明文。
     */
    suspend fun getApiKey(): String = withContext(Dispatchers.IO) {
        try {
            // 检查是否已迁移
            val migrated = getValue(KEY_MIGRATED)
            if (migrated != "true") {
                // 从 Room 读取旧的明文 API Key 并迁移到加密存储
                val oldKey = getValue(KEY_API_KEY)
                if (!oldKey.isNullOrBlank()) {
                    encryptedSettings.putString(SECURE_API_KEY, oldKey)
                    // 清除 Room 中的明文
                    settingsDao.delete(KEY_API_KEY)
                }
                // 标记迁移完成
                settingsDao.insertOrUpdate(SettingsEntity(KEY_MIGRATED, "true"))
            }
            encryptedSettings.getString(SECURE_API_KEY)
        } catch (e: Exception) {
            android.util.Log.e("SettingsRepo", "加密存储失败，回退到明文", e)
            getValue(KEY_API_KEY) ?: ""
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
