package com.kurisuapi.domain.service

import android.util.Log
import com.kurisuapi.data.entity.ModelEntity
import com.kurisuapi.data.entity.ProviderEntity
import com.kurisuapi.data.repository.ModelRepository
import com.kurisuapi.data.repository.ProviderRepository
import com.kurisuapi.domain.provider.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模型注册服务：负责自动同步模型列表、标记弃用模型、探测模型能力。
 *
 * 职责：
 * 1. Provider 初始化时自动获取官方模型列表
 * 2. 更新本地缓存（已有的自定义模型不覆盖）
 * 3. 校验已失效模型，标记 Deprecated 模型
 * 4. 通过 capabilityProbe 动态检测模型能力
 */
@Singleton
class ModelRegistryService @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val modelRepository: ModelRepository,
    private val providerFactory: ProviderFactory
) {
    companion object {
        private const val TAG = "ModelRegistry"

        // DeepSeek 弃用模型映射（来源：api-docs.deepseek.com，2026-06-09 验证）
        // deepseek-chat 和 deepseek-reasoner 将于 2026-07-24 弃用
        private val DEPRECATED_MODELS = mapOf(
            "deepseek-chat" to DeprecatedInfo("2026-07-24", "对应 deepseek-v4-flash 非思考模式"),
            "deepseek-reasoner" to DeprecatedInfo("2026-07-24", "对应 deepseek-v4-flash 思考模式")
        )
    }

    private data class DeprecatedInfo(
        val deprecatedAt: String,
        val replacement: String
    )

    /**
     * 同步指定 Provider 的模型列表。
     * 从官方 API 获取最新列表，更新本地缓存，标记弃用模型。
     */
    suspend fun syncProviderModels(provider: ProviderEntity): SyncResult = withContext(Dispatchers.IO) {
        try {
            val aiProvider = providerFactory.create(provider)
            val apiKey = provider.apiKey
            val baseUrl = provider.baseUrl

            if (apiKey.isBlank()) {
                return@withContext SyncResult(success = false, message = "未配置 API Key，跳过同步")
            }

            // 获取模型列表
            val discovered = try {
                if (aiProvider is OpenAiCompatibleProvider && !provider.modelsUrlOverride.isNullOrBlank()) {
                    aiProvider.listModels(apiKey, baseUrl, provider.modelsUrlOverride)
                } else {
                    aiProvider.listModels(apiKey, baseUrl)
                }
            } catch (e: Exception) {
                Log.w(TAG, "获取 ${provider.name} 模型列表失败: ${e.message}")
                return@withContext SyncResult(success = false, message = "获取模型列表失败：${e.message}")
            }

            if (discovered.isEmpty()) {
                return@withContext SyncResult(success = false, message = "获取到的模型列表为空")
            }

            // 转换为 ModelEntity 并标记弃用状态
            val now = System.currentTimeMillis()
            val modelEntities = discovered.map { model ->
                val deprecatedInfo = DEPRECATED_MODELS[model.modelId]
                ModelEntity(
                    providerId = provider.id,
                    modelId = model.modelId,
                    displayName = model.displayName,
                    contextWindow = model.contextWindow,
                    maxOutput = model.maxOutput,
                    supportsVision = model.supportsVision,
                    supportsTools = model.supportsTools,
                    supportsReasoning = model.supportsReasoning,
                    supportsAudio = model.supportsAudio,
                    supportsImageGeneration = model.supportsImageGeneration,
                    status = if (deprecatedInfo != null) "deprecated" else model.status,
                    deprecatedAt = deprecatedInfo?.deprecatedAt ?: model.deprecatedAt,
                    isCustom = false,
                    lastFetchedAt = now
                )
            }

            // 原子替换：删除旧的非自定义模型，插入新列表
            modelRepository.replaceFetchedModels(provider.id, modelEntities)

            // 同步完成后，对前 3 个模型做能力探测并更新到 DB
            // （只探测少量模型避免过多 API 调用）
            val modelsToProbe = discovered.take(3).filter { it.status != "deprecated" }
            for (model in modelsToProbe) {
                try {
                    probeAndUpdateCapabilities(provider, model.modelId)
                } catch (e: Exception) {
                    Log.w(TAG, "探测 ${model.modelId} 能力失败: ${e.message}")
                }
            }

            Log.i(TAG, "${provider.name} 同步完成：${modelEntities.size} 个模型")

            SyncResult(
                success = true,
                message = "已同步 ${modelEntities.size} 个模型",
                modelCount = modelEntities.size,
                deprecatedCount = modelEntities.count { it.status == "deprecated" }
            )
        } catch (e: Exception) {
            Log.e(TAG, "同步 ${provider.name} 失败", e)
            SyncResult(success = false, message = "同步失败：${e.message}")
        }
    }

    /**
     * 同步所有已启用的 Provider。
     */
    suspend fun syncAllProviders(): Map<Long, SyncResult> {
        val results = mutableMapOf<Long, SyncResult>()
        // 使用 first() 获取一次快照，而不是 collect
        val providers = providerRepository.getEnabledOnce()
        for (provider in providers) {
            if (provider.apiKey.isNotBlank()) {
                results[provider.id] = syncProviderModels(provider)
            }
        }
        return results
    }

    /**
     * 探测指定模型的能力并更新到数据库。
     */
    suspend fun probeAndUpdateCapabilities(provider: ProviderEntity, modelId: String) {
        try {
            val aiProvider = providerFactory.create(provider)
            val capability = when (aiProvider) {
                is OpenAiCompatibleProvider -> aiProvider.capabilityProbe(
                    provider.apiKey, provider.baseUrl, modelId
                )
                is AnthropicProvider -> aiProvider.capabilityProbe(
                    provider.apiKey, provider.baseUrl, modelId
                )
                is GeminiProvider -> aiProvider.capabilityProbe(
                    provider.apiKey, provider.baseUrl, modelId
                )
                else -> CapabilityInfo()
            }

            // Bug 1 fix: 持久化全部 5 个能力字段（数据库 supportsStreaming 列不存在，流式探测结果暂不持久化）
            modelRepository.updateCapabilities(
                providerId = provider.id,
                modelId = modelId,
                supportsReasoning = capability.reasoning,
                supportsAudio = capability.audio,
                supportsImageGen = capability.imageGeneration,
                supportsVision = capability.vision,
                supportsTools = capability.toolCalling
            )
        } catch (e: Exception) {
            Log.w(TAG, "探测 $modelId 能力失败: ${e.message}")
        }
    }

    /**
     * 检查当前使用的模型是否已弃用，返回警告信息。
     */
    suspend fun checkDeprecationWarning(providerId: Long, modelId: String): String? {
        val deprecatedInfo = DEPRECATED_MODELS[modelId] ?: return null
        return "⚠ $modelId 将于 ${deprecatedInfo.deprecatedAt} 弃用，建议切换到 ${deprecatedInfo.replacement}"
    }

    data class SyncResult(
        val success: Boolean,
        val message: String,
        val modelCount: Int = 0,
        val deprecatedCount: Int = 0
    )
}
