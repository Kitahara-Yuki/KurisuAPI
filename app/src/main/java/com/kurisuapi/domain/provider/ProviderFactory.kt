package com.kurisuapi.domain.provider

import android.util.Log
import com.kurisuapi.data.entity.ProviderEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderFactory @Inject constructor(
    private val openAiCompatibleProvider: OpenAiCompatibleProvider,
    private val anthropicProvider: AnthropicProvider,
    private val geminiProvider: GeminiProvider
) {
    companion object {
        private const val TAG = "ProviderFactory"
        private val KNOWN_TYPES = setOf("openai_compatible", "anthropic", "gemini")
    }

    fun create(provider: ProviderEntity): AiProvider {
        return when (provider.type) {
            "anthropic" -> anthropicProvider
            "gemini" -> geminiProvider
            else -> {
                // Bug 8 fix: 未知 Provider 类型打印警告，方便排查配置错误
                if (provider.type !in KNOWN_TYPES) {
                    Log.w(TAG, "未知 Provider 类型 \"${provider.type}\"，将作为 OpenAI 兼容格式处理")
                }
                openAiCompatibleProvider
            }
        }
    }

    fun create(type: String): AiProvider {
        return when (type) {
            "anthropic" -> anthropicProvider
            "gemini" -> geminiProvider
            else -> {
                if (type !in KNOWN_TYPES) {
                    Log.w(TAG, "未知 Provider 类型 \"$type\"，将作为 OpenAI 兼容格式处理")
                }
                openAiCompatibleProvider
            }
        }
    }
}
