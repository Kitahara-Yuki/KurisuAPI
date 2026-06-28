package com.kurisuapi.domain.service

import android.util.Log
import com.kurisuapi.data.api.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.kurisuapi.data.repository.ModelRepository
import com.kurisuapi.data.repository.ProviderRepository
import com.kurisuapi.data.repository.SettingsRepository
import com.kurisuapi.domain.provider.*
import com.kurisuapi.domain.provider.StreamToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiService @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository,
    private val providerFactory: ProviderFactory
) {
    data class AiResponse(
        val content: String,
        val reasoningContent: String = "",
        val promptTokens: Int = 0,
        val completionTokens: Int = 0,
        val success: Boolean = true,
        val errorMessage: String? = null
    )

    // Bug fix: removed redundant withContext(Dispatchers.IO) — all provider calls
    // already use withContext(Dispatchers.IO) internally, and suspend functions can be
    // called directly without an extra context switch.
    suspend fun chat(messages: List<ChatMessage>, modelOverride: String? = null): AiResponse {
        try {
            // Get the default provider
            val provider = providerRepository.getDefault()
                ?: return AiResponse(
                    content = "",
                    success = false,
                    errorMessage = "未配置 AI Provider，请在设置中添加"
                )

            // Get API key: provider-specific first, then fallback to legacy settings
            val apiKey = provider.apiKey.ifBlank { settingsRepository.getApiKey() }
            if (apiKey.isBlank()) {
                return AiResponse(
                    content = "",
                    success = false,
                    errorMessage = "还没有设置 API Key，请先在「Provider 管理」中填入 \"${provider.name}\" 的密钥"
                )
            }

            // Get model settings: modelOverride > provider-specific > legacy settings
            val model = modelOverride?.takeIf { it.isNotBlank() }
                ?: provider.model.ifBlank { settingsRepository.getModel() }
            val temperature = if (provider.temperature > 0) provider.temperature else settingsRepository.getTemperature()
            val rawMaxTokens = if (provider.maxTokens > 0) provider.maxTokens else settingsRepository.getMaxTokens()
            val thinkingEnabled = provider.thinkingEnabled
            val reasoningEffort = provider.reasoningEffort
            val thinkingBudgetTokens = provider.thinkingBudgetTokens

            // 上下文窗口限制：确保 maxTokens 不超过上下文窗口
            val ctxWindow = getActiveContextWindow()
            val maxTokens = if (ctxWindow > 0 && rawMaxTokens > ctxWindow) {
                ctxWindow.toInt()
            } else {
                rawMaxTokens
            }

            Log.d("AiService", "Provider thinkingEnabled=$thinkingEnabled, reasoningEffort=$reasoningEffort, thinkingBudgetTokens=$thinkingBudgetTokens, type=${provider.type}, model=$model, maxTokens=$maxTokens, contextWindow=$ctxWindow")

            // Create the appropriate provider implementation
            val aiProvider = providerFactory.create(provider)

            // Call the provider
            return when (aiProvider) {
                is OpenAiCompatibleProvider -> {
                    aiProvider.chatWithConfig(
                        messages = messages,
                        model = model,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        apiKey = apiKey,
                        baseUrl = provider.baseUrl,
                        thinkingEnabled = thinkingEnabled,
                        reasoningEffort = reasoningEffort
                    ).toAiResponse()
                }
                is AnthropicProvider -> {
                    aiProvider.chatWithConfig(
                        messages = messages,
                        model = model,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        apiKey = apiKey,
                        baseUrl = provider.baseUrl,
                        thinkingEnabled = thinkingEnabled,
                        thinkingBudgetTokens = thinkingBudgetTokens
                    ).toAiResponse()
                }
                is GeminiProvider -> {
                    aiProvider.chatWithConfig(
                        messages = messages,
                        model = model,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        apiKey = apiKey,
                        baseUrl = provider.baseUrl,
                        thinkingEnabled = thinkingEnabled,
                        thinkingBudgetTokens = thinkingBudgetTokens
                    ).toAiResponse()
                }
                else -> {
                    // Unreachable: ProviderFactory only creates the three types above
                    AiResponse(
                        content = "",
                        success = false,
                        errorMessage = "不支持的 Provider 类型: ${provider.type}"
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AiResponse(
                content = "",
                success = false,
                errorMessage = "发送消息失败，请检查网络连接后重试：${e.localizedMessage ?: "未知错误"}"
            )
        }
    }

    /**
     * 获取当前活跃的上下文窗口大小（token 数）。
     * 优先级：Provider.contextWindow > Model DB contextWindow > 0（不限制）
     */
    suspend fun getActiveContextWindow(): Long {
        val provider = providerRepository.getDefault() ?: return 0
        // Provider 设置优先
        if (provider.contextWindow > 0) return provider.contextWindow
        // Model 数据库兜底
        val modelId = provider.model.ifBlank { settingsRepository.getModel() }
        if (modelId.isBlank()) return 0
        return modelRepository.getByModelId(provider.id, modelId)?.contextWindow ?: 0
    }

    /**
     * 获取当前活跃模型和 Provider 的显示名称，如 "DeepSeek / deepseek-v4-flash"
     */
    suspend fun getActiveModelDisplay(): String {
        val provider = providerRepository.getDefault() ?: return ""
        val model = provider.model.ifBlank { settingsRepository.getModel() }
        return if (model.isNotBlank()) "${provider.name} / $model" else provider.name
    }

    /** 当前 Provider 是否启用了深度思考模式 */
    suspend fun isActiveProviderThinkingEnabled(): Boolean {
        return providerRepository.getDefault()?.thinkingEnabled == true
    }

    private fun ProviderResult.toAiResponse(): AiResponse {
        return AiResponse(
            content = content,
            reasoningContent = reasoningContent,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            success = success,
            errorMessage = errorMessage
        )
    }

    /**
     * 流式聊天。与 chat() 使用相同的 pre-call 逻辑，但调用 Provider 的 chatStream()
     * 返回 StreamToken 流，包含逐字内容和思考过程。
     */
    suspend fun chatStream(messages: List<ChatMessage>): Flow<StreamToken> {
        val provider = providerRepository.getDefault()
            ?: return flow { throw IllegalStateException("未配置 AI Provider") }

        val apiKey = provider.apiKey.ifBlank { settingsRepository.getApiKey() }
        if (apiKey.isBlank()) {
            return flow { throw IllegalStateException("未配置 API Key") }
        }

        val model = provider.model.ifBlank { settingsRepository.getModel() }
        val temperature = if (provider.temperature > 0) provider.temperature else settingsRepository.getTemperature()
        val rawMaxTokens = if (provider.maxTokens > 0) provider.maxTokens else settingsRepository.getMaxTokens()
        val thinkingEnabled = provider.thinkingEnabled
        val reasoningEffort = provider.reasoningEffort
        val thinkingBudgetTokens = provider.thinkingBudgetTokens

        val ctxWindow = getActiveContextWindow()
        val maxTokens = if (ctxWindow > 0 && rawMaxTokens > ctxWindow) ctxWindow.toInt() else rawMaxTokens
        // 流式读取超时：每次 readUtf8Line() 的最长等待时间，防止服务器卡住不发送数据
        val streamTimeoutMs = if (thinkingEnabled) 40_000L else 30_000L

        Log.d("AiService", "chatStream thinkingEnabled=$thinkingEnabled, reasoningEffort=$reasoningEffort, thinkingBudgetTokens=$thinkingBudgetTokens, type=${provider.type}, model=$model, maxTokens=$maxTokens")

        val aiProvider = providerFactory.create(provider)

        return when (aiProvider) {
            is OpenAiCompatibleProvider -> {
                aiProvider.chatStreamWithConfig(
                    messages = messages,
                    model = model,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    apiKey = apiKey,
                    baseUrl = provider.baseUrl,
                    thinkingEnabled = thinkingEnabled,
                    reasoningEffort = reasoningEffort,
                    streamTimeoutMs = streamTimeoutMs
                )
            }
            is AnthropicProvider -> {
                aiProvider.chatStreamWithConfig(
                    messages = messages,
                    model = model,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    apiKey = apiKey,
                    baseUrl = provider.baseUrl,
                    thinkingEnabled = thinkingEnabled,
                    thinkingBudgetTokens = thinkingBudgetTokens,
                    streamTimeoutMs = streamTimeoutMs
                )
            }
            is GeminiProvider -> {
                aiProvider.chatStreamWithConfig(
                    messages = messages,
                    model = model,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    apiKey = apiKey,
                    baseUrl = provider.baseUrl,
                    thinkingEnabled = thinkingEnabled,
                    thinkingBudgetTokens = thinkingBudgetTokens,
                    streamTimeoutMs = streamTimeoutMs
                )
            }
            else -> {
                flow { throw IllegalStateException("不支持的 Provider 类型: ${provider.type}") }
            }
        }
    }

    /**
     * 生成文本的嵌入向量，用于语义搜索。
     * 使用默认 provider 的 embedding API。
     * @return FloatArray 嵌入向量，失败返回 null
     */
    suspend fun embed(text: String): FloatArray? {
        return try {
            val provider = providerRepository.getDefault() ?: return null
            val apiKey = provider.apiKey.ifBlank { return null }
            val aiProvider = providerFactory.create(provider)
            when (aiProvider) {
                is OpenAiCompatibleProvider -> aiProvider.embed(text, apiKey, provider.baseUrl)
                else -> null // Anthropic/Gemini embedding 暂不支持
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AiService", "Embedding failed: ${e.message}")
            null
        }
    }
}
