package com.kurisuapi.data.api

import com.google.gson.annotations.SerializedName

// --- Request/Response Models ---

data class ChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String,
    @SerializedName("reasoning_content")
    val reasoningContent: String? = null
)

data class ChatResponse(
    val id: String?,
    val choices: List<Choice>?,
    val usage: Usage?
)

data class Choice(
    val index: Int,
    val message: ChatMessage?,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int?,
    @SerializedName("completion_tokens")
    val completionTokens: Int?,
    @SerializedName("total_tokens")
    val totalTokens: Int?
)
