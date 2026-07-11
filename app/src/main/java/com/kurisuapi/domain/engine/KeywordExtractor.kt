package com.kurisuapi.domain.engine

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从用户消息中提取关键词，用于记忆搜索和索引匹配。
 * 纯本地执行，不调用 LLM，毫秒级完成。
 */
@Singleton
class KeywordExtractor @Inject constructor() {

    companion object {
        /** 分隔符：空白、标点、特殊符号 */
        private val SPLITTER = Regex("[\\s，。！？、；：\"'（）【】《》…—,.!?;:()\\[\\]{}]+")

        /** 最小关键词长度（字符数），过滤掉"的"、"了"等无意义单字 */
        private const val MIN_LENGTH = 2
    }

    /**
     * 从文本中提取有意义的关键词。
     * 去重、去停用词（单字）、转小写。
     */
    fun extract(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        return text.lowercase()
            .split(SPLITTER)
            .map { it.trim() }
            .filter { it.length >= MIN_LENGTH }
            .distinct()
    }

    /**
     * 从文本中提取实体词（人名、地名、专有名词）。
     * 实体特征：3-5个连续汉字，非常见动词/形容词结尾。
     * 纯本地字符串处理，不需要 API。
     */
    fun extractEntities(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val hantOnly = Regex("^[\\u4e00-\\u9fff]{3,5}$")  // 3-5个纯汉字
        val stopEndings = setOf("是", "的", "了", "吗", "呢", "吧", "啊", "哦",
            "会", "能", "要", "想", "去", "来", "做", "说", "看", "吃", "喝",
            "不", "很", "都", "也", "就", "还", "有", "在", "个", "和")

        val candidates = text
            .split(SPLITTER)
            .map { it.trim() }
            .filter { it.matches(hantOnly) }
            .filter { token ->
                val lastChar = token.last().toString()
                lastChar !in stopEndings
            }
            .distinct()

        // 单个实体也参与搜索（如人名、地名等专有名词即使只有一个也有价值）
        return candidates.takeIf { it.isNotEmpty() } ?: emptyList()
    }
}
