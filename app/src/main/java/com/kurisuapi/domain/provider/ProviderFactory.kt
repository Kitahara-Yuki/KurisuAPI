package com.kurisuapi.domain.provider

import com.kurisuapi.data.entity.ProviderEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderFactory @Inject constructor(
    private val openAiCompatibleProvider: OpenAiCompatibleProvider,
    private val anthropicProvider: AnthropicProvider,
    private val geminiProvider: GeminiProvider
) {
    fun create(provider: ProviderEntity): AiProvider {
        return when (provider.type) {
            "anthropic" -> anthropicProvider
            "gemini" -> geminiProvider
            else -> openAiCompatibleProvider
        }
    }

    fun create(type: String): AiProvider {
        return when (type) {
            "anthropic" -> anthropicProvider
            "gemini" -> geminiProvider
            else -> openAiCompatibleProvider
        }
    }
}
