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

class GeminiProvider(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) : AiProvider {

    override val providerId = "gemini"

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
            val url = "${baseUrl.trimEnd('/')}/models/$model:generateContent"

            // Bug 7 fix: merge ALL system messages instead of only the first one
            val systemMessages = messages.filter { it.role == "system" }
            val systemInstruction = if (systemMessages.isNotEmpty()) {
                systemMessages.joinToString("\n\n") { it.content }
            } else null

            val contents = messages.filter { it.role != "system" }.map { msg ->
                mapOf(
                    "role" to if (msg.role == "assistant") "model" else "user",
                    "parts" to listOf(mapOf("text" to msg.content))
                )
            }

            val generationConfig = mutableMapOf<String, Any>(
                "temperature" to temperature,
                "maxOutputTokens" to maxTokens
            )

            // Gemini 2.5+ 支持 thinkingConfig（深度思考）。
            // 修复：thinkingConfig 必须嵌套在 generationConfig 内部，放在顶层官方端点会返回 400；
            // 同时需要 includeThoughts=true 才能拿到思考过程摘要。
            // 来源：Google AI for Developers 官方文档
            if (thinkingEnabled) {
                val budget = if (thinkingBudgetTokens > 0) {
                    thinkingBudgetTokens
                } else {
                    minOf(maxTokens / 2, 4096)
                }
                generationConfig["thinkingConfig"] = mapOf(
                    "thinkingBudget" to budget,
                    "includeThoughts" to true
                )
            }

            val requestBody = mutableMapOf<String, Any>(
                "contents" to contents,
                "generationConfig" to generationConfig
            )

            if (systemInstruction != null) {
                requestBody["systemInstruction"] = mapOf(
                    "parts" to listOf(mapOf("text" to systemInstruction))
                )
            }

            val body = gson.toJson(requestBody).toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)  // Bug 6 fix: use header instead of URL param
                .build()

            val response = withTimeout(60_000) { okHttpClient.newCall(httpRequest).execute() }
            // Bug fix: 在 use{} 内同时获取 statusCode 和 body，避免关闭后访问 response
            val (responseCode, responseBody) = response.use { r ->
                Pair(r.code, r.body?.string() ?: "")
            }

            if (responseCode !in 200..299) {
                val errMsg = when (responseCode) {
                    400 -> "请求格式有误，请检查模型名称和参数设置"
                    401, 403 -> "API Key 无效或没有访问权限，请检查 Key 是否正确"
                    429 -> "请求太频繁了，请稍等一会儿再试"
                    500, 502, 503 -> "Google 服务器暂时出了问题，请稍后再试"
                    else -> "请求失败 ($responseCode)，请检查网络连接和 API 地址是否正确"
                }
                return@withContext ProviderResult(content = "", success = false, errorMessage = errMsg)
            }

            val geminiResponse = gson.fromJson(responseBody, GeminiResponse::class.java)
            val parts = geminiResponse.candidates?.firstOrNull()
                ?.content?.parts
            // 提取普通文本（非 thought 的 parts）
            val content = parts
                ?.filter { it.thought != true }
                ?.joinToString("") { it.text ?: "" } ?: ""
            // 提取 Gemini thinking 内容（thought=true 的 parts，其 text 字段包含思考过程）
            val thinkingContent = parts
                ?.filter { it.thought == true }
                ?.joinToString("\n") { it.text ?: "" } ?: ""

            ProviderResult(
                content = content,
                reasoningContent = thinkingContent,
                promptTokens = geminiResponse.usageMetadata?.promptTokenCount ?: 0,
                completionTokens = geminiResponse.usageMetadata?.candidatesTokenCount ?: 0
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
            val url = "${baseUrl.trimEnd('/')}/models/$model:streamGenerateContent?alt=sse"

            val systemMessages = messages.filter { it.role == "system" }
            val systemInstruction = if (systemMessages.isNotEmpty()) {
                systemMessages.joinToString("\n\n") { it.content }
            } else null

            val contents = messages.filter { it.role != "system" }.map { msg ->
                mapOf(
                    "role" to if (msg.role == "assistant") "model" else "user",
                    "parts" to listOf(mapOf("text" to msg.content))
                )
            }

            val generationConfig = mutableMapOf<String, Any>(
                "temperature" to temperature,
                "maxOutputTokens" to maxTokens
            )

            if (thinkingEnabled) {
                val budget = if (thinkingBudgetTokens > 0) {
                    thinkingBudgetTokens
                } else {
                    minOf(maxTokens / 2, 4096)
                }
                generationConfig["thinkingConfig"] = mapOf(
                    "thinkingBudget" to budget,
                    "includeThoughts" to true
                )
            }

            val requestBody = mutableMapOf<String, Any>(
                "contents" to contents,
                "generationConfig" to generationConfig
            )

            if (systemInstruction != null) {
                requestBody["systemInstruction"] = mapOf(
                    "parts" to listOf(mapOf("text" to systemInstruction))
                )
            }

            val body = gson.toJson(requestBody).toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .build()

            val streamClient = okHttpClient.newBuilder()
                .readTimeout(streamTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
            val response = streamClient.newCall(httpRequest).execute()
            response.use { resp ->
                if (resp.code !in 200..299) {
                    throw RuntimeException("Gemini 流式请求失败 (${resp.code})")
                }
                val source = resp.body?.source() ?: return@use
                source.timeout().timeout(streamTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank() || !line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ")
                    try {
                        val chunk = gson.fromJson(data, com.google.gson.JsonObject::class.java)
                        val candidates = chunk.getAsJsonArray("candidates")
                        val candidate = candidates?.get(0)?.asJsonObject
                        val content = candidate?.getAsJsonObject("content")
                        val parts = content?.getAsJsonArray("parts")
                        if (parts != null) {
                            for (part in parts) {
                                val partObj = part.asJsonObject
                                val text = partObj.get("text")?.asString ?: ""
                                val thought = partObj.get("thought")?.asBoolean ?: false
                                if (text.isNotBlank()) {
                                    if (thought) {
                                        emit(StreamToken(reasoningContent = text))
                                    } else {
                                        emit(StreamToken(content = text))
                                    }
                                }
                            }
                        }
                        // API 返回的真实 token 数（最后一个 chunk 携带 usageMetadata）
                        val usageMeta = chunk.getAsJsonObject("usageMetadata")
                        if (usageMeta != null) {
                            val pt = usageMeta.get("promptTokenCount")?.asInt ?: 0
                            val ct = usageMeta.get("candidatesTokenCount")?.asInt ?: 0
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
            android.util.Log.e("Gemini-Stream", "流式失败: ${e.message}", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(apiKey: String, baseUrl: String): List<DiscoveredModel> =
        withContext(Dispatchers.IO) {
            try {
                val url = "${baseUrl.trimEnd('/')}/models"
                val httpRequest = Request.Builder().url(url).get()
                    .header("x-goog-api-key", apiKey)
                    .build()
                val response = withTimeout(60_000) { okHttpClient.newCall(httpRequest).execute() }
                val (statusCode, responseBody) = response.use { r ->
                    Pair(r.code, r.body?.string() ?: "")
                }

                if (statusCode !in 200..299) {
                    val errorMsg = when (statusCode) {
                        400 -> "API Key 格式不正确，请检查是否输入完整"
                        401, 403 -> "API Key 无效或没有访问权限，请检查 Key 是否正确"
                        404 -> "模型列表接口不存在，可能 API 地址配置有误"
                        429 -> "请求太频繁了，请稍等一会儿再试"
                        else -> "服务器返回错误 ($statusCode)"
                    }
                    throw RuntimeException(errorMsg)
                }

                val modelsResponse = gson.fromJson(responseBody, GeminiModelsResponse::class.java)
                val models = modelsResponse.models
                    ?.filter { it.supportedGenerationMethods?.contains("generateContent") == true }
                    ?.map { model ->
                        DiscoveredModel(
                            modelId = model.name?.removePrefix("models/") ?: "",
                            displayName = model.displayName ?: "",
                            contextWindow = model.inputTokenLimit ?: 0,
                            maxOutput = model.outputTokenLimit ?: 0,
                            supportsVision = true,
                            // Bug 22 fix: Gemini 全线支持 function calling (tool_use)
                            supportsTools = true
                        )
                    }

                if (models.isNullOrEmpty()) {
                    throw RuntimeException("获取到的模型列表为空，可能 API Key 没有可用的模型权限")
                }

                models
            } catch (e: RuntimeException) {
                throw e
            } catch (e: Exception) {
                // 网络错误等情况，返回内置推荐列表作为 fallback
                getCuratedModels()
            }
        }

    private fun getCuratedModels() = listOf(
        // 来源：Google AI for Developers webarchive（用户提供的官方文档，2026-06-09）
        DiscoveredModel("gemini-2.5-flash", "Gemini 2.5 Flash", 1_000_000, 64_000,
            supportsVision = true, supportsTools = true, supportsReasoning = true),
        DiscoveredModel("gemini-2.5-pro", "Gemini 2.5 Pro", 1_000_000, 64_000,
            supportsVision = true, supportsTools = true, supportsReasoning = true),
        DiscoveredModel("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite", 1_000_000, 64_000,
            supportsVision = true, supportsTools = true),
    )

    /**
     * Gemini 模型能力探测。
     * Gemini API 的 /models 列表接口已返回 supportedGenerationMethods，
     * 此处作为单模型探测的补充。对于标准 Gemini 模型，直接返回已知能力。
     */
    suspend fun capabilityProbe(
        apiKey: String,
        baseUrl: String,
        model: String
    ): CapabilityInfo {
        // Gemini 全系列支持：视觉、Tool Calling（通过 generateContent）、Streaming
        // 来源：Google AI for Developers 官方文档
        return CapabilityInfo(
            reasoning = true,   // Gemini 2.5+ 支持 thinking mode
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
                // 直接发最小 generateContent 请求验证连通性，不依赖 listModels（它网络失败时会返回 fallback 列表）
                val url = "${baseUrl.trimEnd('/')}/models/gemini-2.5-flash:generateContent"
                val requestBody = gson.toJson(mapOf(
                    "contents" to listOf(mapOf(
                        "role" to "user",
                        "parts" to listOf(mapOf("text" to "hi"))
                    )),
                    "generationConfig" to mapOf("maxOutputTokens" to 1)
                )).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .build()

                val response = withTimeout(60_000) { okHttpClient.newCall(httpRequest).execute() }
                val (statusCode, _) = response.use { r ->
                    Pair(r.code, r.body?.string() ?: "")
                }
                val latency = System.currentTimeMillis() - startTime

                if (statusCode in 200..299) {
                    ConnectionTestResult(success = true, latencyMs = latency)
                } else {
                    val errMsg = when (statusCode) {
                        400 -> "API Key 格式不正确或模型名称无效"
                        401, 403 -> "API Key 无效或没有访问权限，请检查 Key 是否正确"
                        429 -> "请求太频繁了，请稍等一会儿再试"
                        500, 502, 503 -> "Google 服务器暂时出了问题，请稍后再试"
                        else -> "请求失败 ($statusCode)，请检查网络和 API 地址"
                    }
                    ConnectionTestResult(success = false, latencyMs = latency, errorMessage = errMsg)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ConnectionTestResult(false, 0, errorMessage = "网络连接失败，请检查网络是否正常：${e.localizedMessage ?: "未知错误"}")
            }
        }

    private data class GeminiResponse(
        val candidates: List<GeminiCandidate>?,
        val usageMetadata: GeminiUsage?
    )

    private data class GeminiCandidate(
        val content: GeminiContent?
    )

    private data class GeminiContent(
        val parts: List<GeminiPart>?
    )

    private data class GeminiPart(
        val text: String?,
        val thought: Boolean?  // Gemini 2.5+ thinking mode: true 表示该 part 是思考过程
    )

    private data class GeminiUsage(
        @SerializedName("promptTokenCount") val promptTokenCount: Int?,
        @SerializedName("candidatesTokenCount") val candidatesTokenCount: Int?
    )

    private data class GeminiModelsResponse(
        val models: List<GeminiModel>?
    )

    private data class GeminiModel(
        val name: String?,
        @SerializedName("display_name") val displayName: String?,
        @SerializedName("input_token_limit") val inputTokenLimit: Long?,
        @SerializedName("output_token_limit") val outputTokenLimit: Long?,
        @SerializedName("supported_generation_methods") val supportedGenerationMethods: List<String>?
    )
}
