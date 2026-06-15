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
}
