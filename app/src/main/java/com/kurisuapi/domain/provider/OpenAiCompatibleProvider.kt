package com.kurisuapi.domain.provider

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.kurisuapi.BuildConfig
import com.kurisuapi.data.api.ChatMessage
import com.kurisuapi.data.api.ChatResponse
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

class OpenAiCompatibleProvider(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) : AiProvider {

    override val providerId = "openai_compatible"

    companion object {
        private const val TAG = "OpenAICompat"

        /** 根据 API 地址自动识别嵌入模型 */
        private fun resolveEmbeddingModel(baseUrl: String): String {
            val host = baseUrl.lowercase()
            return when {
                "deepseek" in host -> "deepseek-embed"
                "openai" in host -> "text-embedding-3-small"
                "zhipu" in host || "bigmodel" in host -> "embedding-2"
                "moonshot" in host -> "moonshot-v1-8k"
                "dashscope" in host || "aliyun" in host -> "text-embedding-v3"
                "siliconflow" in host -> "BAAI/bge-large-zh-v1.5"
                else -> "text-embedding-3-small"  // 通用兼容
            }
        }

        // 已知的兼容后缀，按长度降序排列（最长优先匹配）
        private val KNOWN_COMPAT_SUFFIXES = listOf(
            "/api/claudecode",
            "/api/anthropic",
            "/apps/anthropic",
            "/api/coding",
            "/claudecode",
            "/anthropic",
            "/step_plan",
            "/coding",
            "/claude"
        )
    }

    suspend fun chatWithConfig(
        messages: List<ChatMessage>,
        model: String,
        temperature: Double,
        maxTokens: Int,
        apiKey: String,
        baseUrl: String,
        thinkingEnabled: Boolean = true,
        reasoningEffort: String = "high",
        frequencyPenalty: Double = 0.0,
        presencePenalty: Double = 0.0
    ): ProviderResult = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/chat/completions"

            // 构建请求体：与 DeepSeek 官方 cURL 示例一致
            // 官方文档：https://api-docs.deepseek.com/zh-cn/guides/thinking_mode
            val requestMap = mutableMapOf<String, Any>(
                "model" to model,
                "messages" to messages.map { msg ->
                    mapOf("role" to msg.role, "content" to msg.content)
                },
                "max_tokens" to maxTokens
            )

            if (thinkingEnabled) {
                // 思考模式开启：传 thinking.type=enabled + reasoning_effort
                // 官方文档：https://api-docs.deepseek.com/zh-cn/guides/thinking_mode
                requestMap["thinking"] = mapOf("type" to "enabled")
                requestMap["reasoning_effort"] = reasoningEffort
                // 官方说明：思考模式下 temperature 等参数不生效，但仍兼容传入
                requestMap["temperature"] = temperature
            } else {
                // 思考模式关闭：不传 thinking 字段，API 按普通模式处理
                requestMap["temperature"] = temperature
            }

            // 频率惩罚和存在惩罚（OpenAI 兼容参数，DeepSeek 也支持）
            requestMap["frequency_penalty"] = frequencyPenalty
            requestMap["presence_penalty"] = presencePenalty

            val body = gson.toJson(requestMap).toRequestBody("application/json".toMediaType())

            // 调试日志
            if (BuildConfig.DEBUG) {
                Log.d("OpenAiProvider", "thinkingEnabled=$thinkingEnabled, reasoningEffort=$reasoningEffort, freqPenalty=$frequencyPenalty, presPenalty=$presencePenalty")
                Log.d("OpenAiProvider", "Request body keys: ${requestMap.keys}")
            }

            val httpRequest = Request.Builder()
                .url(url)
                .post(body)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .build()

            val response = withTimeout(60_000) { okHttpClient.newCall(httpRequest).execute() }
            val responseBody = response.use { r ->
                val code = r.code
                val body = r.body?.string() ?: ""
                if (code !in 200..299) {
                    // Bug 7 fix: 尝试从 API 返回的 JSON 中提取详细错误信息
                    val apiError = try {
                        gson.fromJson(body, com.google.gson.JsonObject::class.java)
                            ?.getAsJsonObject("error")?.get("message")?.asString
                    } catch (_: Exception) { null }
                    val errMsg = apiError ?: when (code) {
                        401 -> "API Key 无效，请检查是否输入正确"
                        403 -> "API Key 没有访问权限，可能余额不足或未开通此服务"
                        404 -> "API 地址不正确，请检查 Base URL 设置"
                        429 -> "请求太频繁了，请稍等一会儿再试"
                        500, 502, 503 -> "服务器暂时出了问题，请稍后再试"
                        else -> "请求失败 ($code)，请检查网络连接和 API 地址"
                    }
                    return@withContext ProviderResult(content = "", success = false, errorMessage = errMsg)
                }
                body
            }

            val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
            val message = chatResponse.choices?.firstOrNull()?.message

            ProviderResult(
                content = message?.content ?: "",
                reasoningContent = message?.reasoningContent ?: "",
                promptTokens = chatResponse.usage?.promptTokens ?: 0,
                completionTokens = chatResponse.usage?.completionTokens ?: 0
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProviderResult(content = "", success = false, errorMessage = "发送消息失败，请检查网络连接是否正常：${e.localizedMessage ?: "未知错误"}")
        }
    }

    /**
     * 带思考参数的流式聊天。与 chatWithConfig 保持一致的参数约定。
     */
    suspend fun chatStreamWithConfig(
        messages: List<ChatMessage>,
        model: String,
        temperature: Double,
        maxTokens: Int,
        apiKey: String,
        baseUrl: String,
        thinkingEnabled: Boolean = true,
        reasoningEffort: String = "high",
        streamTimeoutMs: Long = 30_000,
        frequencyPenalty: Double = 0.0,
        presencePenalty: Double = 0.0
    ): Flow<StreamToken> = flow {
        try {
            val url = "${baseUrl.trimEnd('/')}/chat/completions"

            val requestMap = mutableMapOf<String, Any>(
                "model" to model,
                "messages" to messages.map { msg ->
                    mapOf("role" to msg.role, "content" to msg.content)
                },
                "max_tokens" to maxTokens,
                "stream" to true
            )

            if (thinkingEnabled) {
                // 思考模式开启：传 thinking.type=enabled + reasoning_effort
                requestMap["thinking"] = mapOf("type" to "enabled")
                requestMap["reasoning_effort"] = reasoningEffort
                // 思考模式下 temperature 不生效但仍兼容传入
                requestMap["temperature"] = temperature
            } else {
                // 思考模式关闭：不传 thinking 字段
                if (temperature > 0) {
                    requestMap["temperature"] = temperature
                }
            }

            requestMap["frequency_penalty"] = frequencyPenalty
            requestMap["presence_penalty"] = presencePenalty

            val body = gson.toJson(requestMap).toRequestBody("application/json".toMediaType())
            val httpRequest = Request.Builder()
                .url(url)
                .post(body)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .build()

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
                    throw RuntimeException("流式请求失败 (${resp.code})${if (errorBody.isNotBlank()) ": $errorBody" else ""}")
                }
                val source = resp.body?.source() ?: return@use
                source.timeout().timeout(streamTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank() || !line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ")
                    if (data == "[DONE]") break
                    try {
                        val chunk = gson.fromJson(data, com.google.gson.JsonObject::class.java)
                        val delta = chunk
                            .getAsJsonArray("choices")?.get(0)?.asJsonObject
                            ?.getAsJsonObject("delta")
                        val content = delta?.get("content")?.asString ?: ""
                        val reasoning = delta?.get("reasoning_content")?.asString ?: ""
                        if (content.isNotBlank() || reasoning.isNotBlank()) {
                            emit(StreamToken(content = content, reasoningContent = reasoning))
                        }
                        // API 返回的真实 token 数（最后一个 chunk 携带 usage）
                        val usage = chunk.getAsJsonObject("usage")
                        if (usage != null) {
                            val pt = usage.get("prompt_tokens")?.asInt ?: 0
                            val ct = usage.get("completion_tokens")?.asInt ?: 0
                            if (pt > 0) emit(StreamToken(promptTokens = pt, completionTokens = ct))
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
            android.util.Log.e("OpenAI-Stream", "流式失败: ${e.message}", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 带 modelsUrlOverride 的重载版本
     */
    suspend fun listModels(
        apiKey: String,
        baseUrl: String,
        modelsUrlOverride: String?
    ): List<DiscoveredModel> = withContext(Dispatchers.IO) {
        val candidates = buildModelsUrlCandidates(baseUrl, modelsUrlOverride)
        var lastError: String? = null

        for (url in candidates) {
            try {
                val httpRequest = Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer $apiKey")
                    .build()

                val response = withTimeout(60_000) { okHttpClient.newCall(httpRequest).execute() }
                val statusCode = response.code
                val responseBody = response.use { it.body?.string() ?: "" }

                if (statusCode in 200..299) {
                    // 成功，解析模型列表
                    if (responseBody.isBlank()) {
                        lastError = "服务器返回了空数据，可能暂时不可用"
                        continue
                    }
                    val modelsResponse = gson.fromJson(responseBody, ModelsListResponse::class.java)
                    val models = modelsResponse.data?.map { model ->
                        DiscoveredModel(
                            modelId = model.id ?: "",
                            displayName = (model.id ?: "").replace("-", " ")
                                .replaceFirstChar { it.uppercase() },
                            contextWindow = model.contextWindow ?: 0
                            // 能力检测改为动态：不在这里硬编码，由 ModelRegistryService 通过 capabilityProbe 探测
                        )
                    }
                    if (!models.isNullOrEmpty()) {
                        return@withContext models
                    }
                    lastError = "获取到了响应但格式不对，可能不是兼容的 API"
                    continue
                }

                // 404/405 静默跳过，尝试下一个候选
                if (statusCode == 404 || statusCode == 405) {
                    lastError = "该 Provider 不支持模型列表接口，已自动跳过"
                    continue
                }

                // 401/403/429 等直接报错，不继续尝试
                val errorMsg = when (statusCode) {
                    401 -> "API Key 无效，请检查是否输入正确"
                    403 -> "API Key 没有访问权限，可能余额不足或未开通此服务"
                    429 -> "请求太频繁了，请稍等一会儿再试"
                    else -> "服务器返回错误 ($statusCode)"
                }
                throw RuntimeException(errorMsg)

            } catch (e: RuntimeException) {
                throw e // 401/403/429 等直接抛出
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = "网络连接失败，请检查网络是否正常：${e.localizedMessage ?: "未知错误"}"
                continue
            }
        }

        // 所有候选都失败
        throw RuntimeException(
            lastError ?: "未能获取到模型列表，请检查 API 地址和 Key 是否正确"
        )
    }

    override suspend fun listModels(apiKey: String, baseUrl: String): List<DiscoveredModel> {
        return listModels(apiKey, baseUrl, modelsUrlOverride = null)
    }

    override suspend fun testConnection(apiKey: String, baseUrl: String): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()

                // 尝试获取模型列表（多候选 URL，404 会自动跳过）
                val models: List<DiscoveredModel> = try {
                    listModels(apiKey, baseUrl)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                val latency = System.currentTimeMillis() - startTime

                if (models.isNotEmpty()) {
                    return@withContext ConnectionTestResult(
                        success = true,
                        latencyMs = latency,
                        modelCount = models.size
                    )
                }

                // 所有候选 URL 均失败，回退到 chat/completions 验证连通性
                val testUrl = "${baseUrl.trimEnd('/')}/chat/completions"
                val testBody = gson.toJson(mapOf(
                    "model" to "test",
                    "messages" to listOf(mapOf("role" to "user", "content" to "hi")),
                    "max_tokens" to 1
                )).toRequestBody("application/json".toMediaType())

                val testRequest = Request.Builder()
                    .url(testUrl)
                    .post(testBody)
                    .header("Authorization", "Bearer $apiKey")
                    .build()

                val testResponse = withTimeout(60_000) { okHttpClient.newCall(testRequest).execute() }
                val testCode = testResponse.use { it.code }
                val testLatency = System.currentTimeMillis() - startTime

                if (testCode == 401) {
                    return@withContext ConnectionTestResult(
                        success = false,
                        latencyMs = testLatency,
                        errorMessage = "API Key 无效，请检查是否输入正确"
                    )
                }

                val chatSuccess = testCode in 200..299
                ConnectionTestResult(
                    success = chatSuccess,
                    latencyMs = testLatency,
                    modelCount = 0,
                    errorMessage = if (!chatSuccess) {
                        when (testCode) {
                            403 -> "API Key 没有访问权限"
                            404 -> "API 地址不正确，请检查 Base URL 设置"
                            429 -> "请求太频繁，请稍后再试"
                            500, 502, 503 -> "服务器暂时不可用，请稍后再试"
                            else -> "连接失败 ($testCode)，请检查 API 地址是否正确"
                        }
                    } else null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ConnectionTestResult(
                    success = false,
                    latencyMs = 0,
                    errorMessage = "网络连接失败，请检查网络是否正常：${e.localizedMessage ?: "未知错误"}"
                )
            }
        }

    /**
     * 带 modelsUrlOverride 的重载版本
     */
    suspend fun testConnection(
        apiKey: String,
        baseUrl: String,
        modelsUrlOverride: String?
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()

            val models: List<DiscoveredModel> = try {
                listModels(apiKey, baseUrl, modelsUrlOverride)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val latency = System.currentTimeMillis() - startTime

            if (models.isNotEmpty()) {
                return@withContext ConnectionTestResult(
                    success = true,
                    latencyMs = latency,
                    modelCount = models.size
                )
            }

            val testUrl = "${baseUrl.trimEnd('/')}/chat/completions"
            val testBody = gson.toJson(mapOf(
                "model" to "test",
                "messages" to listOf(mapOf("role" to "user", "content" to "hi")),
                "max_tokens" to 1
            )).toRequestBody("application/json".toMediaType())

            val testRequest = Request.Builder()
                .url(testUrl)
                .post(testBody)
                .header("Authorization", "Bearer $apiKey")
                .build()

            val testResponse = withTimeout(60_000) { okHttpClient.newCall(testRequest).execute() }
            val testLatency = System.currentTimeMillis() - startTime
            // Bug fix: 用 use{} 关闭 response，避免连接池泄漏
            val testCode = testResponse.use { it.code }

            if (testCode == 401) {
                return@withContext ConnectionTestResult(
                    success = false,
                    latencyMs = testLatency,
                    errorMessage = "API Key 无效，请检查是否输入正确"
                )
            }

            val chatSuccess = testCode in 200..299
            ConnectionTestResult(
                success = chatSuccess,
                latencyMs = testLatency,
                modelCount = 0,
                errorMessage = if (!chatSuccess) {
                    when (testCode) {
                        403 -> "API Key 没有访问权限"
                        429 -> "请求太频繁，请稍后再试"
                        500, 502, 503 -> "服务器暂时不可用，请稍后再试"
                        else -> "连接失败 ($testCode)，请检查 API 地址是否正确"
                    }
                } else null
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ConnectionTestResult(
                success = false,
                latencyMs = 0,
                errorMessage = "网络连接失败，请检查网络是否正常：${e.localizedMessage ?: "未知错误"}"
            )
        }
    }

    /**
     * 动态探测模型能力：发送一条带 thinking 参数的测试请求，检查响应是否包含 reasoning_content。
     * 不依赖硬编码的模型名称判断。
     */
    suspend fun capabilityProbe(
        apiKey: String,
        baseUrl: String,
        model: String
    ): CapabilityInfo = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/chat/completions"
            // 发送带 thinking 的最小请求来探测 reasoning 能力
            val probeBody = gson.toJson(mapOf(
                "model" to model,
                "messages" to listOf(mapOf("role" to "user", "content" to "hi")),
                "max_tokens" to 1,
                "thinking" to mapOf("type" to "enabled"),
                "reasoning_effort" to "high"
            )).toRequestBody("application/json".toMediaType())

            val probeRequest = Request.Builder()
                .url(url)
                .post(probeBody)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .build()

            val response = withTimeout(60_000) { okHttpClient.newCall(probeRequest).execute() }
            val responseBody = response.use { it.body?.string() ?: "" }

            if (responseBody.isBlank()) {
                return@withContext CapabilityInfo()
            }

            // 检查响应中是否包含 reasoning_content 字段
            val hasReasoning = responseBody.contains("\"reasoning_content\"")

            // 检查响应中是否包含 tool_calls 相关字段（通过发送 tools 参数测试）
            val hasTools = responseBody.contains("\"tool_calls\"") ||
                    responseBody.contains("\"function_call\"")

            CapabilityInfo(
                reasoning = hasReasoning,
                toolCalling = hasTools,
                vision = false,  // 需要单独的 vision 测试
                streaming = true,
                audio = false,
                imageGeneration = false
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 探测失败时返回默认能力
            CapabilityInfo()
        }
    }

    // ==================== URL 候选构造 ====================

    /**
     * 构造模型列表端点的候选 URL 列表（参考 CC Switch model_fetch.rs）
     *
     * 候选顺序：
     * 1. modelsUrlOverride 非空 → 只返回它
     * 2. baseUrl 已以版本段 /v{N} 结尾 → 直接拼 /models
     * 3. 否则拼 /v1/models
     * 4. 若命中兼容后缀，剥离后再尝试 /v1/models 和 /models
     */
    internal fun buildModelsUrlCandidates(
        baseUrl: String,
        modelsUrlOverride: String? = null
    ): List<String> {
        // 用户手动指定了 models URL
        modelsUrlOverride?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return listOf(it)
        }

        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return emptyList()

        val candidates = mutableListOf<String>()

        if (endsWithVersionSegment(trimmed)) {
            // 已含版本段（如 /v1、/api/paas/v4），直接拼 /models
            candidates.add("$trimmed/models")
            // 非 /v1 的版本段，额外尝试 /v1/models 作为兜底
            if (!trimmed.endsWith("/v1")) {
                candidates.add("$trimmed/v1/models")
            }
        } else {
            // 无版本段，拼 /v1/models
            candidates.add("$trimmed/v1/models")
        }

        // 兼容后缀剥离：如 /anthropic、/coding 等
        stripCompatSuffix(trimmed)?.let { root ->
            val cleanRoot = root.trimEnd('/')
            if (cleanRoot.isNotEmpty() && cleanRoot.contains("://")) {
                candidates.add("$cleanRoot/v1/models")
                candidates.add("$cleanRoot/models")
            }
        }

        // 去重
        return candidates.distinct()
    }

    /**
     * 判断 URL 路径末尾是否为版本段 /v{N}（如 /v1、/v4、/v10）
     */
    private fun endsWithVersionSegment(url: String): Boolean {
        val lastSegment = url.substringAfterLast('/')
        if (!lastSegment.startsWith("v")) return false
        val digits = lastSegment.substring(1)
        return digits.isNotEmpty() && digits.all { it.isDigit() }
    }

    /**
     * 若 URL 以已知兼容后缀结尾，返回剥离后的根路径
     */
    private fun stripCompatSuffix(url: String): String? {
        for (suffix in KNOWN_COMPAT_SUFFIXES) {
            if (url.endsWith(suffix)) {
                return url.substring(0, url.length - suffix.length)
            }
        }
        return null
    }

    // Internal models for JSON parsing
    private data class ModelsListResponse(
        val data: List<ModelItem>?
    )

    private data class ModelItem(
        val id: String?,
        @SerializedName("context_window")
        val contextWindow: Long? = null
    )

    /**
     * 生成文本的嵌入向量（embedding）。
     * 调用 OpenAI-compatible 的 /v1/embeddings 接口。
     * @return FloatArray 嵌入向量，失败返回 null
     */
    suspend fun embed(text: String, apiKey: String, baseUrl: String): FloatArray? = withContext(Dispatchers.IO) {
        try {
            val embeddingModel = resolveEmbeddingModel(baseUrl)
            val requestMap = mapOf(
                "model" to embeddingModel,
                "input" to text
            )
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/embeddings")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(gson.toJson(requestMap).toRequestBody("application/json".toMediaType()))
                .build()
            // Bug 4 fix: 用 use{} 包裹响应，确保连接自动关闭，避免连接池泄漏
            val body = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Embedding API returned ${response.code}")
                    return@withContext null
                }
                response.body?.string() ?: return@withContext null
            }
            val json = gson.fromJson(body, com.google.gson.JsonObject::class.java)
            val embeddingArray = json.getAsJsonArray("data")
                ?.get(0)?.asJsonObject
                ?.getAsJsonArray("embedding")
                ?: return@withContext null
            FloatArray(embeddingArray.size()) { i ->
                embeddingArray.get(i).asFloat
            }
        } catch (e: Exception) {
            Log.w(TAG, "Embedding generation failed: ${e.message}")
            null
        }
    }
}
