package com.kurisuapi.domain.provider

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.kurisuapi.data.api.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AnthropicProvider(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) : AiProvider {

    override val providerId = "anthropic"

    suspend fun chatWithConfig(
        messages: List<ChatMessage>,
        model: String,
        temperature: Double,
        maxTokens: Int,
        apiKey: String,
        baseUrl: String,
        thinkingEnabled: Boolean = true,
        thinkingBudgetTokens: Int = 0
    ): ProviderResult = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/messages"

            // Bug 7 fix: merge ALL system messages instead of only the first one
            val systemMessages = messages.filter { it.role == "system" }
            val systemMessage = if (systemMessages.isNotEmpty()) {
                systemMessages.joinToString("\n\n") { it.content }
            } else ""
            val chatMessages = messages.filter { it.role != "system" }.map { msg ->
                mapOf("role" to msg.role, "content" to msg.content)
            }

            val requestBody = mutableMapOf<String, Any>(
                "model" to model,
                "max_tokens" to maxTokens,
                "system" to systemMessage,
                "messages" to chatMessages
            )

            // Anthropic Extended Thinking: 全系列支持
            // 来源：Claude API Docs - 所有当前 Claude 模型都支持 extended thinking
            // 注意：Anthropic 硬性要求 budget_tokens < max_tokens，否则返回 400
            if (thinkingEnabled) {
                val rawBudget = if (thinkingBudgetTokens > 0) {
                    thinkingBudgetTokens
                } else {
                    minOf(maxTokens / 2, 4096)
                }
                // budget 至少 1024，且必须严格小于 maxTokens（保留输出空间）
                val budget = if (maxTokens > 1024) {
                    rawBudget.coerceIn(1024, maxTokens - 1)
                } else {
                    // maxTokens 太小无法开启 thinking，降级为禁用
                    null
                }
                if (budget != null) {
                    requestBody["thinking"] = mapOf(
                        "type" to "enabled",
                        "budget_tokens" to budget
                    )
                    // 按照 Anthropic 官方要求：Enabled thinking 应该省略 temperature
                    // 为减少误解先保留：think 开启时 temperature 其实也该删掉
                } else {
                    requestBody["temperature"] = temperature
                }
            } else {
                requestBody["temperature"] = temperature
            }

            val body = gson.toJson(requestBody).toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url(url)
                .post(body)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .build()

            val response = withTimeout(60_000) { okHttpClient.newCall(httpRequest).execute() }
            // Bug fix: 在 use{} 内同时获取 statusCode 和 body，避免关闭后访问 response
            val (responseCode, responseBody) = response.use { r ->
                Pair(r.code, r.body?.string() ?: "")
            }

            if (responseCode !in 200..299) {
                val errMsg = when (responseCode) {
                    401 -> "API Key 无效，请检查是否输入正确"
                    403 -> "API Key 没有访问权限，可能余额不足或未开通此服务"
                    429 -> "请求太频繁了，请稍等一会儿再试"
                    500, 502, 503 -> "Anthropic 服务器暂时出了问题，请稍后再试"
                    else -> "请求失败 ($responseCode)，请检查网络连接和 API 地址是否正确"
                }
                return@withContext ProviderResult(content = "", success = false, errorMessage = errMsg)
            }

            val anthropicResponse = gson.fromJson(responseBody, AnthropicResponse::class.java)
            // 聚合所有 text block（多段响应会丢内容，不止取第一个）
            val content = anthropicResponse.content
                ?.filter { it.type == "text" }
                ?.joinToString("") { it.text ?: "" } ?: ""
            val thinkingContent = anthropicResponse.content
                ?.filter { it.type == "thinking" }
                ?.joinToString("\n") { it.thinking ?: "" } ?: ""

            ProviderResult(
                content = content,
                reasoningContent = thinkingContent,
                promptTokens = anthropicResponse.usage?.inputTokens ?: 0,
                completionTokens = anthropicResponse.usage?.outputTokens ?: 0
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProviderResult(content = "", success = false, errorMessage = "发送消息失败，请检查网络连接是否正常：${e.localizedMessage ?: "未知错误"}")
        }
    }

    suspend fun chatStreamWithConfig(
        messages: List<ChatMessage>,
        model: String,
        temperature: Double,
        maxTokens: Int,
        apiKey: String,
        baseUrl: String,
        thinkingEnabled: Boolean = true,
        thinkingBudgetTokens: Int = 0,
        streamTimeoutMs: Long = 30_000
    ): Flow<StreamToken> = flow {
        try {
            val url = "${baseUrl.trimEnd('/')}/messages"

            val systemMessages = messages.filter { it.role == "system" }
            val systemMessage = if (systemMessages.isNotEmpty()) {
                systemMessages.joinToString("\n\n") { it.content }
            } else ""
            val chatMessages = messages.filter { it.role != "system" }.map { msg ->
                mapOf("role" to msg.role, "content" to msg.content)
            }

            val requestBody = mutableMapOf<String, Any>(
                "model" to model,
                "max_tokens" to maxTokens,
                "system" to systemMessage,
                "messages" to chatMessages,
                "stream" to true
            )

            if (thinkingEnabled) {
                val rawBudget = if (thinkingBudgetTokens > 0) {
                    thinkingBudgetTokens
                } else {
                    minOf(maxTokens / 2, 4096)
                }
                val budget = if (maxTokens > 1024) {
                    rawBudget.coerceIn(1024, maxTokens - 1)
                } else {
                    null
                }
                if (budget != null) {
                    requestBody["thinking"] = mapOf(
                        "type" to "enabled",
                        "budget_tokens" to budget
                    )
                } else {
                    requestBody["temperature"] = temperature
                }
            } else {
                requestBody["temperature"] = temperature
            }

            val body = gson.toJson(requestBody).toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url(url)
                .post(body)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .build()

            // 用 stream timeout 克隆 OkHttp 客户端（共享连接池，仅改超时），
            // 确保 execute() 和 readUtf8Line() 都在超时后被强制断开
            val streamClient = okHttpClient.newBuilder()
                .readTimeout(streamTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
            val response = streamClient.newCall(httpRequest).execute()
            response.use { resp ->
                if (resp.code !in 200..299) {
                    // Bug 3 fix: 读取 API 返回的详细错误信息，而非仅状态码
                    val errorBody = try {
                        resp.peekBody(1024).string()
                    } catch (_: Exception) { "" }
                    throw RuntimeException("Anthropic 流式请求失败 (${resp.code})${if (errorBody.isNotBlank()) ": $errorBody" else ""}")
                }
                val source = resp.body?.source() ?: return@use
                source.timeout().timeout(streamTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank() || !line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ")
                    if (data == "[DONE]") break
                    try {
                        val event = gson.fromJson(data, com.google.gson.JsonObject::class.java)
                        val eventType = event.get("type")?.asString ?: ""
                        when (eventType) {
                            "message_start" -> {
                                val inputTokens = event.getAsJsonObject("message")
                                    ?.getAsJsonObject("usage")
                                    ?.get("input_tokens")?.asInt ?: 0
                                if (inputTokens > 0) {
                                    emit(StreamToken(promptTokens = inputTokens))
                                }
                            }
                            "content_block_delta" -> {
                                val delta = event.getAsJsonObject("delta")
                                val deltaType = delta?.get("type")?.asString ?: ""
                                if (deltaType == "text_delta") {
                                    val text = delta.get("text")?.asString ?: ""
                                    if (text.isNotBlank()) {
                                        emit(StreamToken(content = text))
                                    }
                                } else if (deltaType == "thinking_delta") {
                                    val thinking = delta.get("thinking")?.asString ?: ""
                                    if (thinking.isNotBlank()) {
                                        emit(StreamToken(reasoningContent = thinking))
                                    }
                                }
                            }
                            "message_delta" -> {
                                val outputTokens = event.getAsJsonObject("usage")
                                    ?.get("output_tokens")?.asInt ?: 0
                                if (outputTokens > 0) {
                                    emit(StreamToken(completionTokens = outputTokens))
                                }
                            }
                            "message_stop" -> {
                                // 流结束
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        // 跳过解析失败的行，继续读下一行
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("Anthropic-Stream", "流式失败: ${e.message}", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(apiKey: String, baseUrl: String): List<DiscoveredModel> =
        withContext(Dispatchers.IO) {
            // Anthropic doesn't have a /models endpoint, return curated list
            // 来源：Claude API Docs webarchive（用户提供的官方文档，2026-06-09）
            listOf(
                DiscoveredModel("claude-opus-4-7", "Claude Opus 4.7", 1_000_000, 128_000,
                    supportsVision = true, supportsTools = true, supportsReasoning = true),
                DiscoveredModel("claude-sonnet-4-6", "Claude Sonnet 4.6", 1_000_000, 64_000,
                    supportsVision = true, supportsTools = true, supportsReasoning = true),
                DiscoveredModel("claude-haiku-4-5", "Claude Haiku 4.5", 200_000, 64_000,
                    supportsVision = true, supportsTools = true, supportsReasoning = true),
            )
        }

    /**
     * Anthropic 模型能力探测。
     * Claude 全系列支持 Extended Thinking 和 Tool Calling，直接返回已知能力。
     */
    suspend fun capabilityProbe(
        apiKey: String,
        baseUrl: String,
        model: String
    ): CapabilityInfo {
        // Claude 全系列支持：视觉、Extended Thinking、Tool Calling
        // 来源：Claude API Docs - 所有当前的 Claude 模型都支持文本和图像输入
        return CapabilityInfo(
            reasoning = true,
            toolCalling = true,
            vision = true,
            streaming = true,
            audio = false,
            imageGeneration = false
        )
    }

    override suspend fun testConnection(apiKey: String, baseUrl: String): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val url = "${baseUrl.trimEnd('/')}/messages"

                val requestBody = mapOf(
                    "model" to "claude-haiku-4-5",
                    "max_tokens" to 1,
                    "messages" to listOf(mapOf("role" to "user", "content" to "hi"))
                )

                val body = gson.toJson(requestBody).toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("Content-Type", "application/json")
                    .build()

                val response = withTimeout(60_000) { okHttpClient.newCall(httpRequest).execute() }
                val latency = System.currentTimeMillis() - startTime
                // Bug fix: 用 use{} 关闭 response，避免连接池泄漏
                val (responseCode, _) = response.use { r ->
                    Pair(r.code, r.body?.string() ?: "")
                }

                if (responseCode == 401) {
                    return@withContext ConnectionTestResult(false, latency, errorMessage = "API Key 无效，请检查是否输入正确")
                }

                ConnectionTestResult(
                    success = responseCode in 200..299,
                    latencyMs = latency,
                    modelCount = 3,
                    errorMessage = if (responseCode !in 200..299) {
                        when (responseCode) {
                            403 -> "API Key 没有访问权限，可能余额不足"
                            429 -> "请求太频繁，请稍后再试"
                            500, 502, 503 -> "Anthropic 服务器暂时不可用，请稍后再试"
                            else -> "连接失败 ($responseCode)，请检查 API 地址是否正确"
                        }
                    } else null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ConnectionTestResult(false, 0, errorMessage = "网络连接失败，请检查网络是否正常：${e.localizedMessage ?: "未知错误"}")
            }
        }

    private data class AnthropicResponse(
        val content: List<ContentBlock>?,
        val usage: AnthropicUsage?
    )

    private data class ContentBlock(
        val type: String?,
        val text: String?,
        val thinking: String?
    )

    private data class AnthropicUsage(
        @SerializedName("input_tokens") val inputTokens: Int?,
        @SerializedName("output_tokens") val outputTokens: Int?
    )
}
