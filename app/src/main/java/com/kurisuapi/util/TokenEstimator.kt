package com.kurisuapi.util

/**
 * 简易 Token 估算工具。
 *
 * 不引入重量级 tokenizer 库，使用字符级启发式估算：
 * - CJK 字符（中日韩统一表意文字、假名、谚文）：
 *   DeepSeek 系模型：~0.7 token/字（有独立中文词汇表，中文编码效率高）
 *   其他模型（OpenAI/Anthropic/Gemini）：~1.5 token/字
 * - ASCII 字符（英文、数字、标点）：~0.25 token/字（约 4 字符 = 1 token）
 * - 其他字符（emoji、特殊符号等）：~0.5 token/字
 *
 * 此估算在混合中英文场景下误差通常在 ±30% 以内，足以判断上下文窗口使用比例。
 */
object TokenEstimator {

    /**
     * 估算一段文本占用的 token 数。
     * @param text 要估算的文本
     * @param providerName 服务商名称，用于区分不同模型的 CJK 权重。
     *                     包含 "DeepSeek" 时 CJK=0.7，否则 CJK=1.5（保守预估）。
     */
    fun estimateTokens(text: String, providerName: String = ""): Int {
        if (text.isEmpty()) return 0
        val cjkWeight = getCjkWeight(providerName)
        var count = 0.0
        for (c in text) {
            count += tokenWeight(c, cjkWeight)
        }
        return count.toInt().coerceAtLeast(1)
    }

    /**
     * 估算多段文本的总 token 数。
     */
    fun estimateTokens(texts: List<String>, providerName: String = ""): Int {
        return texts.sumOf { estimateTokens(it, providerName) }
    }

    /**
     * 格式化 token 数量为人类可读的字符串。
     * 例如：8200 → "8.2K"，128000 → "128K"
     */
    fun formatTokenCount(tokens: Long): String {
        return when {
            tokens >= 1_048_576 -> "${"%.2f".format(tokens / 1_048_576.0)}M"
            tokens >= 1_024 -> "${"%.2f".format(tokens / 1_024.0)}K"
            else -> tokens.toString()
        }
    }

    /**
     * 根据服务商名称返回 CJK 字符权重。
     * DeepSeek 有专门的中文词汇表，中文编码效率高（约 0.7 token/字）。
     * OpenAI、Anthropic、Gemini 等模型中文编码效率较低（约 1.5 token/字），保守预估。
     */
    private fun getCjkWeight(providerName: String): Double {
        return if (providerName.contains("DeepSeek", ignoreCase = true)) 0.7 else 1.5
    }

    /**
     * 返回单个字符的估算 token 权重。
     */
    private fun tokenWeight(c: Char, cjkWeight: Double): Double {
        val code = c.code
        return when {
            // CJK Unified Ideographs
            code in 0x4E00..0x9FFF -> cjkWeight
            // CJK Extension A
            code in 0x3400..0x4DBF -> cjkWeight
            // CJK Extension B–G (surrogate pairs handled by Char sequence)
            code in 0x20000..0x2EBEF -> cjkWeight
            // CJK Compatibility Ideographs
            code in 0xF900..0xFAFF -> cjkWeight
            // CJK Compatibility Supplement
            code in 0x2F800..0x2FA1F -> cjkWeight
            // Hiragana
            code in 0x3040..0x309F -> cjkWeight
            // Katakana
            code in 0x30A0..0x30FF -> cjkWeight
            // Hangul Syllables
            code in 0xAC00..0xD7AF -> cjkWeight
            // Hangul Jamo
            code in 0x1100..0x11FF -> cjkWeight
            // Full-width forms
            code in 0xFF00..0xFFEF -> cjkWeight
            // ASCII
            code < 128 -> 0.25
            // Everything else (emoji, symbols, etc.)
            else -> 0.5
        }
    }
}
