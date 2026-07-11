package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String,           // "openai_compatible", "anthropic", "gemini"
    val baseUrl: String,
    val apiKey: String = "",
    val modelsUrlOverride: String? = null,  // 手动指定的模型列表 URL，为空时自动推导
    val model: String = "",                  // 当前使用的模型名称，为空时回退到全局设置
    val temperature: Double = 0.7,           // 生成温度 0.0-2.0
    val maxTokens: Int = 2048,               // 最大输出 token 数
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val isBuiltIn: Boolean = false,           // 是否为内置 Provider（不可改名/改格式）
    val supportsStreaming: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsReasoning: Boolean = false,
    val thinkingEnabled: Boolean = false,         // Bug 16 fix: 默认关闭深度思考，避免新加的非 DeepSeek provider 第一次请求就报错
    val reasoningEffort: String = "high",           // 思考强度: "low", "medium", "high"（仅 OpenAI 兼容格式有效）
    val thinkingBudgetTokens: Int = 0,              // 思考预算 token 数，0=自动计算（Anthropic/Gemini 有效）
    val contextWindow: Long = 0,                     // 上下文窗口大小（token），0=未设置/自动从模型获取
    val frequencyPenalty: Double = 0.0,              // 频率惩罚 -2.0 ~ 2.0，0=Provider 级别不覆盖
    val presencePenalty: Double = 0.0,               // 存在惩罚 -2.0 ~ 2.0，0=Provider 级别不覆盖
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
