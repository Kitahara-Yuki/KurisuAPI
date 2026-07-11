package com.kurisuapi.data.repository

import com.kurisuapi.data.dao.ModelDao
import com.kurisuapi.data.dao.ProviderDao
import com.kurisuapi.data.entity.ModelEntity
import com.kurisuapi.data.entity.ProviderEntity
import com.kurisuapi.util.EncryptedSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRepository @Inject constructor(
    private val providerDao: ProviderDao,
    private val modelDao: ModelDao,
    private val encryptedSettings: EncryptedSettings
) {
    companion object {
        private const val PROVIDER_KEY_PREFIX = "provider_api_key_"
    }

    private fun secKey(id: Long) = "$PROVIDER_KEY_PREFIX$id"

    /** 从加密存储读取 API Key，同时处理旧明文 Key 的自动迁移 */
    private fun decryptKey(id: Long, entityApiKey: String): String {
        if (id <= 0) return entityApiKey
        val encrypted = encryptedSettings.getString(secKey(id), "")
        return if (encrypted.isNotEmpty()) {
            encrypted
        } else if (entityApiKey.isNotBlank()) {
            // 迁移：旧明文 Key 尚未加密，自动搬入加密存储
            encryptedSettings.putString(secKey(id), entityApiKey)
            entityApiKey
        } else {
            ""
        }
    }

    /** 将 API Key 存入加密存储，返回替换后的实体（apiKey 字段清空） */
    private fun encryptKey(id: Long, entity: ProviderEntity): ProviderEntity {
        if (id <= 0) return entity
        return if (entity.apiKey.isNotBlank()) {
            encryptedSettings.putString(secKey(id), entity.apiKey)
            entity.copy(apiKey = "")
        } else {
            entity
        }
    }

    /** 移除加密存储中的 API Key */
    private fun removeKey(id: Long) {
        if (id > 0) encryptedSettings.remove(secKey(id))
    }

    /** 为实体填充解密后的 API Key */
    private fun populateKey(entity: ProviderEntity?): ProviderEntity? {
        return entity?.copy(apiKey = decryptKey(entity.id, entity.apiKey))
    }

    fun getAll(): Flow<List<ProviderEntity>> =
        providerDao.getAll().map { list -> list.map { it.copy(apiKey = decryptKey(it.id, it.apiKey)) } }

    fun getEnabled(): Flow<List<ProviderEntity>> =
        providerDao.getEnabled().map { list -> list.map { it.copy(apiKey = decryptKey(it.id, it.apiKey)) } }

    suspend fun getEnabledOnce(): List<ProviderEntity> =
        providerDao.getEnabledOnce().map { it.copy(apiKey = decryptKey(it.id, it.apiKey)) }

    suspend fun getById(id: Long): ProviderEntity? =
        populateKey(providerDao.getById(id))

    suspend fun getDefault(): ProviderEntity? =
        populateKey(providerDao.getDefault())

    fun observeDefault(): Flow<ProviderEntity?> =
        providerDao.observeDefault().map { populateKey(it) }

    suspend fun insert(provider: ProviderEntity): Long {
        val id = providerDao.insert(provider)
        // 新插入后立即加密 Key（此时 id 已生成）
        if (provider.apiKey.isNotBlank()) {
            encryptedSettings.putString(secKey(id), provider.apiKey)
            providerDao.getById(id)?.let { existing ->
                providerDao.update(existing.copy(apiKey = ""))
            }
        }
        return id
    }

    suspend fun update(provider: ProviderEntity) {
        // 如果有新的 API Key，写入加密存储；如果被清空则移除旧 Key
        if (provider.apiKey.isNotBlank()) {
            encryptedSettings.putString(secKey(provider.id), provider.apiKey)
        } else {
            encryptedSettings.remove(secKey(provider.id))
        }
        providerDao.update(provider.copy(apiKey = ""))
    }

    suspend fun delete(provider: ProviderEntity) {
        removeKey(provider.id)
        providerDao.delete(provider)
    }

    suspend fun setDefault(id: Long) {
        providerDao.setDefaultAtomic(id)
    }

    suspend fun count(): Int = providerDao.count()

    private val providerMutex = Mutex()

    suspend fun initDefaultProviders() = providerMutex.withLock {
        if (providerDao.count() > 0) return

        // 默认 Provider 配置（来源：各厂商官方文档，2026-06-09 验证）
        val deepSeekId = providerDao.insert(
            ProviderEntity(
                name = "DeepSeek", type = "openai_compatible",
                baseUrl = "https://api.deepseek.com/", isDefault = true, isBuiltIn = true,
                model = "deepseek-v4-flash",
                thinkingEnabled = true
            )
        )
        val openAiId = providerDao.insert(
            ProviderEntity(
                name = "OpenAI", type = "openai_compatible",
                baseUrl = "https://api.openai.com/v1/", isBuiltIn = true,
                model = "gpt-5.5"
            )
        )
        val anthropicId = providerDao.insert(
            ProviderEntity(
                name = "Anthropic", type = "anthropic",
                baseUrl = "https://api.anthropic.com/", isBuiltIn = true,
                model = "claude-sonnet-4-6"
            )
        )
        val geminiId = providerDao.insert(
            ProviderEntity(
                name = "Google Gemini", type = "gemini",
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/", isBuiltIn = true,
                model = "gemini-2.5-flash"
            )
        )

        // 为默认 Provider 预置模型条目（含上下文窗口大小），确保上下文额度显示立即可用。
        // 后续 ModelRegistryService.syncProviderModels() 会以官方数据覆盖。
        val now = System.currentTimeMillis()
        modelDao.insertAll(listOf(
            // DeepSeek
            ModelEntity(providerId = deepSeekId, modelId = "deepseek-v4-flash", displayName = "DeepSeek V4 Flash", contextWindow = 128_000, maxOutput = 32_768, isEnabled = true, lastFetchedAt = now),
            ModelEntity(providerId = deepSeekId, modelId = "deepseek-v4", displayName = "DeepSeek V4", contextWindow = 128_000, maxOutput = 32_768, isEnabled = true, lastFetchedAt = now),
            // OpenAI
            ModelEntity(providerId = openAiId, modelId = "gpt-5.5", displayName = "GPT-5.5", contextWindow = 128_000, maxOutput = 16_384, isEnabled = true, lastFetchedAt = now),
            // Anthropic
            ModelEntity(providerId = anthropicId, modelId = "claude-sonnet-4-6", displayName = "Claude Sonnet 4.6", contextWindow = 200_000, maxOutput = 32_768, isEnabled = true, lastFetchedAt = now),
            ModelEntity(providerId = anthropicId, modelId = "claude-opus-4-8", displayName = "Claude Opus 4.8", contextWindow = 200_000, maxOutput = 32_768, isEnabled = true, lastFetchedAt = now),
            ModelEntity(providerId = anthropicId, modelId = "claude-haiku-4-5", displayName = "Claude Haiku 4.5", contextWindow = 200_000, maxOutput = 32_768, isEnabled = true, lastFetchedAt = now),
            // Gemini
            ModelEntity(providerId = geminiId, modelId = "gemini-2.5-flash", displayName = "Gemini 2.5 Flash", contextWindow = 1_048_576, maxOutput = 8_192, isEnabled = true, lastFetchedAt = now),
            ModelEntity(providerId = geminiId, modelId = "gemini-2.5-pro", displayName = "Gemini 2.5 Pro", contextWindow = 2_097_152, maxOutput = 8_192, isEnabled = true, lastFetchedAt = now),
        ))
    }
}
