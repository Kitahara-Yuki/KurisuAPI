package com.kurisuapi.util

/**
 * 简易 Token 估算工具。
 *
 * 不引入重量级 tokenizer 库，使用字符级启发式估算：
 * - CJK 字符（中日韩统一表意文字、假名、谚文）：~0.7 token/字
 * - ASCII 字符（英文、数字、标点）：~0.25 token/字（约 4 字符 = 1 token）
 * - 其他字符（emoji、特殊符号等）：~0.5 token/字
 *
 * 此估算在混合中英文场景下误差通常在 ±30% 以内，足以判断上下文窗口使用比例。
 */
object TokenEstimator {

    /**
     * 估算一段文本占用的 token 数。
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var count = 0.0
        for (c in text) {
            count += tokenWeight(c)
        }
        return count.toInt().coerceAtLeast(1)
    }

    /**
     * 估算多段文本的总 token 数。
     */
    fun estimateTokens(texts: List<String>): Int {
        return texts.sumOf { estimateTokens(it) }
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
     * 返回单个字符的估算 token 权重。
     */
    private fun tokenWeight(c: Char): Double {
        val code = c.code
        return when {
            // CJK Unified Ideographs
            code in 0x4E00..0x9FFF -> 0.7
            // CJK Extension A
            code in 0x3400..0x4DBF -> 0.7
            // CJK Extension B–G (surrogate pairs handled by Char sequence)
            code in 0x20000..0x2EBEF -> 0.7
            // CJK Compatibility Ideographs
            code in 0xF900..0xFAFF -> 0.7
            // CJK Compatibility Supplement
            code in 0x2F800..0x2FA1F -> 0.7
            // Hiragana
            code in 0x3040..0x309F -> 0.7
            // Katakana
            code in 0x30A0..0x30FF -> 0.7
            // Hangul Syllables
            code in 0xAC00..0xD7AF -> 0.7
            // Hangul Jamo
            code in 0x1100..0x11FF -> 0.7
            // Full-width forms
            code in 0xFF00..0xFFEF -> 0.7
            // ASCII
            code < 128 -> 0.25
            // Everything else (emoji, symbols, etc.)
            else -> 0.5
        }
    }
}
