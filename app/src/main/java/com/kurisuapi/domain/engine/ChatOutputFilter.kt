package com.kurisuapi.domain.engine

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 对话模式输出过滤器。
 *
 * 两层职责：
 * 1. 动作描写清除：用正则清除 AI 偶尔产生的动作描写标记（提示词工程的兜底）。
 * 2. 关系阶段护栏：根据当前 Knapp 阶段检测输出是否越界（方案二）。
 *
 * 参考：SillyTavern Regex Extension、Inworld AI TextProcessor、NVIDIA NeMo Guardrails。
 */
@Singleton
class ChatOutputFilter @Inject constructor() {

    companion object {
        // ═══════════════════════════════════════════
        // 动作描写清除（正则）
        // ═══════════════════════════════════════════
        private val CHINESE_PARENS = Regex("（[^）]*）")
        private val CHINESE_BRACKETS = Regex("【[^】]*】")
        private val ASTERISK_ACTION = Regex("(?<!\\*)\\*[^*]+\\*")
        private val ENGLISH_PARENS_CN = Regex("\\([^)]*[\\u4e00-\\u9fff][^)]*\\)")
        private val MULTI_SPACE = Regex(" {2,}")
        private val BLANK_LINE = Regex("\\n\\s*\\n")

        // ═══════════════════════════════════════════
        // 元对话过滤（剧情模式 formatStory）
        // ═══════════════════════════════════════════

        // AI 在"回应"用户而非"叙事"：好的 + 写作行为动词
        private val META_ACTION_REGEX = Regex(
            "^(好的|嗯|好)[，,。]?" +
            "(我来(继续|写|描写|叙述|补充)" +
            "|我继续(写|描写)" +
            "|接下来(我会|请|我们将)" +
            "|下面(我来|我们|请)).*"
        )
        // 说明性标注：（以下为...）（接上文...）
        private val META_NOTE_REGEX = Regex("^[（(]?(以下|接上文|场景过渡|时间跳跃|视角切换).*")
        // "收到/了解/明白" 后面紧跟的是叙事承诺而非角色对话（不含"你的"防止误杀）
        private val META_CONFIRM_REGEX = Regex(
            "^(收到|了解|明白)[：:，,。]?" +
            "(我会(继续|按|为|写|描写|叙述)" +
            "|我将(继续|为|描写)" +
            "|接下来(我会|请)).*"
        )

        // ═══════════════════════════════════════════
        // 阶段护栏：违规定义
        // ═══════════════════════════════════════════

        // 初识阶段严禁
        private val STAGE0_INTIMATE_ADDRESS = listOf(
            "宝贝", "亲爱的", "老公", "老婆", "honey", "darling", "甜心", "宝宝",
            "乖乖", "小可爱", "心肝", "主人"
        )
        private val STAGE0_LOVE_CONFESSION = listOf(
            "我爱你", "好喜欢你", "爱死你了", "离不开你", "你是我的唯一",
            "没有你我", "没你不行", "我等你", "等你回来"
        )
        private val STAGE0_DEEP_DISCLOSURE = listOf(
            "我小时候", "我的创伤", "我受过伤", "我最怕", "我最大的恐惧",
            "我曾经被", "我内心最深处", "我从未告诉过别人"
        )
        private val STAGE0_WE_LANGUAGE = listOf(
            "我们的未来", "我们在一起", "我们的关系", "我们俩",
            "咱俩", "我们之间"
        )

        // 探索阶段严禁（在初识基础上增加）
        private val STAGE1_EXTREME_ADDRESS = listOf(
            "老公", "老婆", "主人", "夫君", "娘子"
        )
        private val STAGE1_SOULMATE_PROMISE = listOf(
            "永远在一起", "一辈子", "没你活不下去", "你是我的全部",
            "我只要你", "非你不可", "永远不分开", "生生世世"
        )

        // 深入阶段严禁
        private val STAGE2_EXTREME_POSSESSION = listOf(
            "你是我的，只能看着我", "不许看别人", "你只能喜欢我",
            "你是我的东西", "谁敢碰你我就"
        )
    }

    /**
     * 过滤 AI 回复中的动作描写标记，返回纯对话文字。
     */
    fun filter(text: String): String {
        var result = text
        result = CHINESE_PARENS.replace(result, "")
        result = CHINESE_BRACKETS.replace(result, "")
        result = ASTERISK_ACTION.replace(result, "")
        result = ENGLISH_PARENS_CN.replace(result, "")
        result = MULTI_SPACE.replace(result, " ")
        result = BLANK_LINE.replace(result, "\n")
        return result.trim()
    }

    // ═══════════════════════════════════════════
    // 方案二：关系阶段护栏
    // ═══════════════════════════════════════════

    /**
     * 检测 AI 输出是否违反当前关系阶段的规则。
     *
     * @param text AI 生成的回复文本
     * @param stage 当前 Knapp 阶段（初识/探索/深入/融合/羁绊）
     * @param personality 角色性格文本（用于病娇等特殊判断）
     * @return 违规信息，如果没有违规返回 null
     */
    fun validateByStage(text: String, stage: String, personality: String = ""): StageViolation? {
        val stageIdx = when (stage) {
            "初识" -> 0
            "探索" -> 1
            "深入" -> 2
            "融合" -> 3
            "羁绊" -> 4
            else -> 0
        }
        val lower = text.lowercase()

        // ── 初识阶段检测 ──
        if (stageIdx <= 0) {
            // 亲昵称呼
            for (word in STAGE0_INTIMATE_ADDRESS) {
                if (text.contains(word)) {
                    return StageViolation("overly_intimate_address", word)
                }
            }
            // 爱意表达
            for (phrase in STAGE0_LOVE_CONFESSION) {
                if (text.contains(phrase)) {
                    return StageViolation("love_confession", phrase)
                }
            }
            // 深度情感分享
            for (phrase in STAGE0_DEEP_DISCLOSURE) {
                if (text.contains(phrase)) {
                    return StageViolation("deep_emotional_disclosure", phrase)
                }
            }
            // "我们"指代关系
            for (phrase in STAGE0_WE_LANGUAGE) {
                if (text.contains(phrase)) {
                    return StageViolation("we_language", phrase)
                }
            }
        }

        // ── 探索阶段检测 ──
        if (stageIdx <= 1) {
            for (word in STAGE1_EXTREME_ADDRESS) {
                if (text.contains(word)) {
                    return StageViolation("extreme_intimate_address", word)
                }
            }
            for (phrase in STAGE1_SOULMATE_PROMISE) {
                if (text.contains(phrase)) {
                    return StageViolation("soulmate_promise", phrase)
                }
            }
        }

        // ── 深入阶段检测（病娇除外）──
        if (stageIdx <= 2) {
            val isYandere = personality.lowercase().contains("病娇")
            if (!isYandere) {
                for (phrase in STAGE2_EXTREME_POSSESSION) {
                    if (text.contains(phrase)) {
                        return StageViolation("extreme_possession", phrase)
                    }
                }
            }
        }

        // 融合和羁绊阶段不设限

        return null
    }

    // ═══════════════════════════════════════════
    // 剧情模式：强制空行分隔
    // ═══════════════════════════════════════════

    /**
     * 剧情模式输出格式化：强制在不同类型的内容块之间插入空行。
     *
     * 识别规则（不限制 AI 写什么，只强制排版）：
     * - 整行被（）包裹 → 描述/动作/环境
     * - 整行被「」包裹 → 内心独白
     * - 其他非空行 → 对话
     *
     * 规则：当前行类型与上一行不同时，插入一个空行。连续同类型不插空行。
     *
     * 预处理：自动补齐 AI 偶尔漏写的半括号。
     */
    fun formatStory(text: String): String {
        val lines = text.split("\n")
        if (lines.isEmpty()) return text

        val result = mutableListOf<String>()
        var lastType: String? = null

        for (line in lines) {
            var trimmed = line.trim()
            if (trimmed.isEmpty()) continue  // 跳过原始空行，统一由算法管理

            // 预处理：补齐 AI 偶尔漏写的半括号
            trimmed = fixUnmatchedBrackets(trimmed)

            // 预处理：跳过 AI 的元对话行（跳出角色评价自己的写作）
            if (isMetaDialogueLine(trimmed)) continue

            val type = when {
                trimmed.startsWith("（") && trimmed.endsWith("）") -> "desc"
                trimmed.startsWith("「") && trimmed.endsWith("」") -> "thought"
                else -> "dialogue"
            }

            if (lastType != null && type != lastType) {
                result.add("")  // 类型切换 → 插入空行
            }
            result.add(trimmed)
            lastType = type
        }

        return result.joinToString("\n").trim()
    }

    /**
     * 自动补齐 AI 漏写的半括号。
     * - 以 ）结尾但开头不是 （ → 补左括号
     * - 以 （开头但结尾不是 ） → 补右括号
     * - 同理处理 「」
     *
     * ponytail: 只检查行首/行尾括号，行中间的 stray bracket 不理。误补概率极低，真出现再说。
     */
    private fun fixUnmatchedBrackets(line: String): String {
        var result = line
        // 描述括号 （）
        if (result.endsWith("）") && !result.startsWith("（")) {
            result = "（$result"
        }
        if (result.startsWith("（") && !result.endsWith("）")) {
            result = "$result）"
        }
        // 内心独白 「」
        if (result.endsWith("」") && !result.startsWith("「")) {
            result = "「$result"
        }
        if (result.startsWith("「") && !result.endsWith("」")) {
            result = "$result」"
        }
        return result
    }

    /**
     * 检测一行是否为 AI 的"元对话"——即 AI 跳出角色，评价自己正在写的内容。
     * 例如："好的，我来继续写" / "接下来我会描写" / "（以下为场景过渡）"
     * 这些行不应出现在最终输出中。
     */
    private fun isMetaDialogueLine(line: String): Boolean {
        if (line.matches(META_ACTION_REGEX)) return true
        if (line.matches(META_NOTE_REGEX)) return true
        if (line.startsWith("回复") || line.startsWith("回答")) return true
        if (line.matches(META_CONFIRM_REGEX)) return true
        return false
    }
}

/**
 * 关系阶段违规记录。
 */
data class StageViolation(
    /** 违规类型：overly_intimate_address / love_confession / deep_emotional_disclosure / we_language / extreme_intimate_address / soulmate_promise / extreme_possession */
    val type: String,
    /** 匹配到的违规文本片段 */
    val matchedText: String
)
