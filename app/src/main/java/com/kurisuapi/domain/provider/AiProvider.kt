package com.kurisuapi.domain.provider

interface AiProvider {
    val providerId: String

    suspend fun listModels(apiKey: String, baseUrl: String): List<DiscoveredModel>

    suspend fun testConnection(apiKey: String, baseUrl: String): ConnectionTestResult
}

data class ProviderResult(
    val content: String,
    val reasoningContent: String = "",
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val success: Boolean = true,
    val errorMessage: String? = null
)

/**
 * 流式回复的单个 token。
 * content: 可见的回复文字片段
 * reasoningContent: AI 的思考过程片段（DeepSeek/OpenAI 兼容 API 的 reasoning_content）
 * promptTokens / completionTokens: 仅最后一个 token 携带（API 返回的 usage），其余为 0
 */
data class StreamToken(
    val content: String = "",
    val reasoningContent: String = "",
    val promptTokens: Int = 0,
    val completionTokens: Int = 0
)

data class DiscoveredModel(
    val modelId: String,
    val displayName: String = modelId,
    val contextWindow: Long = 0,
    val maxOutput: Long = 0,
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = false,
    val supportsReasoning: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsImageGeneration: Boolean = false,
    val status: String = "active",
    val deprecatedAt: String? = null
)

data class ConnectionTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val modelCount: Int = 0,
    val errorMessage: String? = null
)

/**
 * 模型能力信息，用于动态检测而非硬编码判断
 */
data class CapabilityInfo(
    val reasoning: Boolean = false,
    val toolCalling: Boolean = false,
    val vision: Boolean = false,
    val audio: Boolean = false,
    val imageGeneration: Boolean = false,
    val streaming: Boolean = true
)
